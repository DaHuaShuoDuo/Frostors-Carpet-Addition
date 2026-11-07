package top.frostor.carpet.playerladder;

import carpet.patches.EntityPlayerMPFake;
import com.google.common.collect.Sets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import top.frostor.carpet.FrostorSettings;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SharedHandler {
    /** Tracks the successful carry-on press until the player releases Shift. */
    private static final Map<UUID, CarryOnDismountState> CARRY_ON_DISMOUNT_STATES = new ConcurrentHashMap<>();
    /** Scopes the vanilla canRide bypass to one carry-on startRiding call. */
    private static final ThreadLocal<Entity> CARRY_ON_MOUNTING_RIDER = new ThreadLocal<>();
    private static final Set<EntityType<?>> entityTypesToExclude = Sets.newHashSet();
    private static final Set<TagKey<EntityType<?>>> entityTagsToExclude = Sets.newHashSet();
    private static String appliedExcludedEntries = null;

    public static InteractionResult rideEntity(Player player, Entity newVehicle, Level level, InteractionHand hand) {
        if(!canInteract(player, newVehicle, level, hand)) return InteractionResult.PASS;

        Entity vehicle = PlayerStack.highestOrSelf(
                newVehicle,
                player,
                PlayerLadderSettings.rideDepthLimit,
                Entity::isVehicle,
                Entity::getFirstPassenger
        );
        if(vehicle == null || !isPlayerStackAllowed(vehicle, player)) return InteractionResult.FAIL;

        boolean mounted = PlayerLadderSettings.interactionMode == PlayerLadderInteractionMode.CARRY_ON
                ? startRidingForCarryOn(player, vehicle)
                : player.startRiding(vehicle);
        return mounted ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    public static InteractionResult pickUpEntity(Player player, Entity newPassenger, Level level, InteractionHand hand) {
        if(!canInteract(player, newPassenger, level, hand)) return InteractionResult.PASS;

        Entity vehicle = PlayerStack.highestOrSelf(
                player,
                newPassenger,
                PlayerLadderSettings.pickupDepthLimit,
                Entity::isVehicle,
                Entity::getFirstPassenger
        );
        if(vehicle == null || !isPlayerStackAllowed(vehicle, newPassenger)) return InteractionResult.FAIL;

        return newPassenger.startRiding(vehicle) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    private static boolean canInteract(Player player, Entity target, Level level, InteractionHand hand) {
        return !level.isClientSide() && hand == InteractionHand.MAIN_HAND
                && player.getItemInHand(hand).isEmpty() && canPickUpOrRideLiving(target);
    }

    /**
     * Whether the resulting ride chain may contain 3 or more players.
     * When {@link PlayerLadderSettings#allowRiderStacking} is disabled only two players may ride each other.
     */
    private static boolean isPlayerStackAllowed(Entity vehicle, Entity newPassenger) {
        return PlayerStack.allowsRideChain(
                PlayerLadderSettings.allowRiderStacking,
                vehicle,
                newPassenger,
                Player.class::isInstance,
                Entity::getVehicle
        );
    }

    private static boolean canPickUpOrRideLiving(Entity entity) {
        if(entity instanceof Player)
            return PlayerLadderSettings.allowFakePlayers || !isCarpetFakePlayer(entity);

        if(!PlayerLadderSettings.allowNonPlayers) return false;

        ensureExcludedEntries();
        return !entityTypesToExclude.contains(entity.getType()) &&
                entityTagsToExclude.stream().noneMatch(tag -> entity.typeHolder().is(tag));
    }

    /**
     * Whether the entity is a fake player spawned by Carpet.
     */
    public static boolean isCarpetFakePlayer(Entity entity) {
        return entity instanceof EntityPlayerMPFake;
    }

    public static void onMount(Entity vehicle, Entity passenger) {
        syncPassengers(vehicle);
    }

    public static void onDismount(Entity vehicle) {
        syncPassengers(vehicle);
    }

    private static void syncPassengers(Entity vehicle) {
        if(!vehicle.level().isClientSide()
                && vehicle instanceof ServerPlayer player
                && player.connection != null)
            player.connection.send(new ClientboundSetPassengersPacket(vehicle));
    }

    public static void onPlayerTick(Player player) {
        if(player.level().isClientSide()) return;
        if(!FrostorSettings.playerLadder) {
            CARRY_ON_DISMOUNT_STATES.clear();
            return;
        }

        boolean shouldDismount = shouldDismountForShift(player);
        if(player.onGround() && player.isVehicle() && shouldDismount)
            player.getFirstPassenger().stopRiding();
    }

    public static boolean shouldSuppressCarryOnDismount(Player player) {
        if(!ensureCarryOnDismountProtectionActive()) return false;

        CarryOnDismountState state = CARRY_ON_DISMOUNT_STATES.get(player.getUUID());
        if(state == null) return false;

        boolean suppress = !state.shouldDismount(player.isShiftKeyDown());
        removeReleasedState(player, state);
        return suppress;
    }

    public static boolean isCarryOnMounting(Entity rider) {
        return CARRY_ON_MOUNTING_RIDER.get() == rider;
    }

    public static void onSuccessfulCarryOnInteraction(Player player) {
        CARRY_ON_DISMOUNT_STATES.computeIfAbsent(player.getUUID(), ignored -> new CarryOnDismountState())
                .onSuccessfulInteraction();
    }

    public static void onLogOut(Player player) {
        CARRY_ON_DISMOUNT_STATES.remove(player.getUUID());
        if(player.isPassenger() && player.getVehicle() instanceof Player)
            player.stopRiding();
    }

    public static void onServerStopping() {
        CARRY_ON_DISMOUNT_STATES.clear();
        CARRY_ON_MOUNTING_RIDER.remove();
    }

    public static void onGameModeChange(Player player) {
        if(player.isVehicle() && (PlayerLadderSettings.dismountOnGameModeChange || player.gameMode() == GameType.SPECTATOR))
            player.getFirstPassenger().stopRiding();
    }

    private static boolean startRidingForCarryOn(Entity rider, Entity vehicle) {
        CARRY_ON_MOUNTING_RIDER.set(rider);
        try {
            return rider.startRiding(vehicle);
        }finally{
            CARRY_ON_MOUNTING_RIDER.remove();
        }
    }

    private static boolean shouldDismountForShift(Player player) {
        if(!ensureCarryOnDismountProtectionActive()) return player.isShiftKeyDown();

        CarryOnDismountState state = CARRY_ON_DISMOUNT_STATES.get(player.getUUID());
        if(state == null) return player.isShiftKeyDown();

        boolean dismount = state.shouldDismount(player.isShiftKeyDown());
        removeReleasedState(player, state);
        return dismount;
    }

    private static void removeReleasedState(Player player, CarryOnDismountState state) {
        if(!state.isAwaitingRelease()) CARRY_ON_DISMOUNT_STATES.remove(player.getUUID(), state);
    }

    private static boolean ensureCarryOnDismountProtectionActive() {
        if(FrostorSettings.playerLadder
                && PlayerLadderSettings.interactionMode == PlayerLadderInteractionMode.CARRY_ON)
            return true;

        CARRY_ON_DISMOUNT_STATES.clear();
        return false;
    }

    private static void addExcludedEntityType(String entity) {
        Identifier id = Identifier.tryParse(entity);
        if(id != null) {
            BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(entityTypesToExclude::add);
        }
    }

    private static void addExcludedEntityTag(String tag) {
        Identifier id = Identifier.tryParse(tag.substring(1));
        if(id != null) {
            TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, id);
            entityTagsToExclude.add(tagKey);
        }
    }

    /**
     * Carpet rules are plain static fields without change callbacks, so the exclusion list is
     * re-parsed whenever the rule value differs from the last applied one.
     */
    private static void ensureExcludedEntries() {
        String entries = PlayerLadderSettings.ignoredEntities;
        if(entries.equals(appliedExcludedEntries)) return;
        appliedExcludedEntries = entries;

        entityTagsToExclude.clear();
        entityTypesToExclude.clear();

        for(String entry : entries.split(",")) {
            String trimmed = entry.trim();
            if(trimmed.isEmpty()) continue;

            if(trimmed.startsWith("#")) {
                addExcludedEntityTag(trimmed);
            }else{
                addExcludedEntityType(trimmed);
            }
        }
    }
}
