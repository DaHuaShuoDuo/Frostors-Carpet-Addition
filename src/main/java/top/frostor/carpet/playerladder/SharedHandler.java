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

import java.util.Set;

public class SharedHandler {
    private static final Set<EntityType<?>> entityTypesToExclude = Sets.newHashSet();
    private static final Set<TagKey<EntityType<?>>> entityTagsToExclude = Sets.newHashSet();
    private static String appliedExcludedEntries = null;

    public static InteractionResult rideEntity(Player player, Entity newVehicle, Level level, InteractionHand hand) {
        if(!canInteract(player, newVehicle, level, hand)) return InteractionResult.PASS;

        Entity vehicle = getHighestOrSelf(newVehicle, player, FrostorSettings.playerLadderStepUpLimit);
        if(vehicle == null || !isPlayerStackAllowed(vehicle, player)) return InteractionResult.FAIL;

        player.startRiding(vehicle);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult pickUpEntity(Player player, Entity newPassenger, Level level, InteractionHand hand) {
        if(!canInteract(player, newPassenger, level, hand)) return InteractionResult.PASS;

        Entity vehicle = getHighestOrSelf(player, newPassenger, FrostorSettings.playerLadderPickUpLimit);
        if(vehicle == null || !isPlayerStackAllowed(vehicle, newPassenger)) return InteractionResult.FAIL;

        newPassenger.startRiding(vehicle);
        return InteractionResult.SUCCESS;
    }

    private static boolean canInteract(Player player, Entity target, Level level, InteractionHand hand) {
        return !level.isClientSide() && hand == InteractionHand.MAIN_HAND
                && player.getItemInHand(hand).isEmpty() && canPickUpOrRideLiving(target);
    }

    private static Entity getHighestOrSelf(Entity vehicle, Entity newPassenger, int limit) {
        int count = -1;
        while (vehicle.isVehicle()) {
            count++;
            vehicle = vehicle.getFirstPassenger();
            if(vehicle == newPassenger || count >= limit) return null;
        }
        return vehicle;
    }

    /**
     * Whether the resulting ride chain may contain 3 or more players.
     * When {@code playerLadderRideExtension} is disabled only two players may ride each other.
     */
    private static boolean isPlayerStackAllowed(Entity vehicle, Entity newPassenger) {
        if(FrostorSettings.playerLadderRideExtension) return true;

        int players = newPassenger instanceof Player ? 1 : 0;
        for(Entity entity = vehicle; entity != null; entity = entity.getVehicle()) {
            if(entity instanceof Player && ++players >= 3) return false;
        }
        return true;
    }

    private static boolean canPickUpOrRideLiving(Entity entity) {
        if(entity instanceof Player)
            return FrostorSettings.playerLadderRideFakePlayer || !isCarpetFakePlayer(entity);

        if(!FrostorSettings.playerLadderInteractWithAnyLiving) return false;

        ensureExcludedEntries();
        return !entityTypesToExclude.contains(entity.getType()) &&
                entityTagsToExclude.stream().noneMatch(tag -> entity.getType().builtInRegistryHolder().is(tag));
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
        if(!vehicle.level().isClientSide() && vehicle instanceof Player player)
            ((ServerPlayer) player).connection.send(new ClientboundSetPassengersPacket(vehicle));
    }

    public static void onPlayerTick(Player player) {
        if(!player.level().isClientSide() && player.onGround() && player.isVehicle() && player.isCrouching())
            player.getFirstPassenger().stopRiding();
    }

    public static void onLogOut(Player player) {
        if(player.isPassenger() && player.getVehicle() instanceof Player)
            player.stopRiding();
    }

    public static void onGameModeChange(Player player) {
        if(player.isVehicle() && (FrostorSettings.playerLadderDismountOnGameModeChange || player.gameMode() == GameType.SPECTATOR))
            player.getFirstPassenger().stopRiding();
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
        String entries = FrostorSettings.playerLadderExcludedEntities;
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
