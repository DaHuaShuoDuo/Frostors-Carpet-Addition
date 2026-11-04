package top.frostor.carpet.bigenderchest;

import carpet.patches.EntityPlayerMPFake;
import carpet.patches.FakeClientConnection;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import top.frostor.carpet.FrostorSettings;
import top.frostor.carpet.FrostorsCarpetAddition;
import top.frostor.carpet.bigenderchest.page.PageMetadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-lifetime manager for fake players owned by big ender chest pages.
 */
final class BigEnderChestFakePlayers {
    private static final long IDLE_TICKS = 200;
    private static final long SPAWN_RETRY_TICKS = 100;
    private static final Map<UUID, Lease> LEASES = new HashMap<>();
    private static long tickCounter;

    private BigEnderChestFakePlayers() {
    }

    static void initialize() {
        LEASES.clear();
        tickCounter = 0;
    }

    static void request(
            ServerPlayer owner,
            PageMetadata metadata,
            Container offlineContainer
    ) {
        Lease lease = LEASES.computeIfAbsent(metadata.pageUuid(), ignored -> new Lease(metadata));
        lease.owner = owner;
        lease.offlineContainer = offlineContainer;
        lease.lastUseTick = tickCounter;
    }

    static void touch(PageMetadata metadata) {
        Lease lease = LEASES.get(metadata.pageUuid());
        if(lease != null) {
            lease.lastUseTick = tickCounter;
        }
    }

    static boolean isManaged(UUID pageUuid) {
        return LEASES.containsKey(pageUuid);
    }

    static void tick(MinecraftServer server) {
        tick(server, FrostorSettings.bigEnderChestLiveFakePlayers);
    }

    static void tick(MinecraftServer server, boolean enabled) {
        if(!enabled) {
            stopAll(server);
            return;
        }
        tickCounter++;
        for(Lease lease : List.copyOf(LEASES.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(lease.metadata.pageUuid());
            if(player != null) {
                if(!lease.owned) {
                    LEASES.remove(lease.metadata.pageUuid());
                    continue;
                }
                if(tickCounter - lease.lastUseTick >= IDLE_TICKS) {
                    LEASES.remove(lease.metadata.pageUuid());
                    copyItems(player.getEnderChestInventory(), lease.offlineContainer);
                    if(player instanceof EntityPlayerMPFake fake) {
                        fake.kill(Component.translatable(
                                "big_ender_chest.fake_page.disconnect"
                        ));
                    }
                }
                continue;
            }
            if(tickCounter - lease.lastUseTick >= IDLE_TICKS) {
                LEASES.remove(lease.metadata.pageUuid());
                continue;
            }
            if(lease.owner == null
                    || lease.lastSpawnAttempt != Long.MIN_VALUE
                    && tickCounter - lease.lastSpawnAttempt < SPAWN_RETRY_TICKS) {
                continue;
            }
            lease.lastSpawnAttempt = tickCounter;
            if(spawn(lease)) {
                lease.owned = true;
            }
        }
    }

    static void shutdown(MinecraftServer server) {
        stopAll(server);
    }

    private static boolean spawn(Lease lease) {
        try {
            ServerPlayer owner = lease.owner;
            GameProfile profile = new GameProfile(
                    lease.metadata.pageUuid(),
                    lease.metadata.pageName()
            );
            ClientInformation clientInformation = ClientInformation.createDefault();
            EntityPlayerMPFake fake = EntityPlayerMPFake.respawnFake(
                    owner.level().getServer(),
                    owner.level(),
                    profile,
                    clientInformation
            );
            owner.level().getServer().getPlayerList()
                    .loadPlayerData(fake.nameAndId())
                    .ifPresent(tag -> fake.load(TagValueInput.create(
                            ProblemReporter.DISCARDING,
                            owner.level().getServer().registryAccess(),
                            tag
                    )));
            copyItems(lease.offlineContainer, fake.getEnderChestInventory());
            owner.level().getServer().getPlayerList().placeNewPlayer(
                    new FakeClientConnection(PacketFlow.SERVERBOUND),
                    fake,
                    new CommonListenerCookie(profile, 0, clientInformation, false)
            );
            fake.setGameMode(GameType.SPECTATOR);
            fake.getAbilities().flying = true;
            fake.onUpdateAbilities();
            fake.teleportTo(
                    owner.level(),
                    owner.getX(),
                    owner.getY() + 1.0,
                    owner.getZ(),
                    Set.of(),
                    owner.getYRot(),
                    owner.getXRot(),
                    true
            );
            return owner.level().getServer().getPlayerList()
                    .getPlayer(lease.metadata.pageUuid()) != null;
        }catch(RuntimeException exception) {
            FrostorsCarpetAddition.LOGGER.error(
                    "Failed to create live big ender chest page {}",
                    lease.metadata.pageName(),
                    exception
            );
            return false;
        }
    }

    private static void stopAll(MinecraftServer server) {
        for(Lease lease : List.copyOf(LEASES.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(lease.metadata.pageUuid());
            if(player != null && lease.owned) {
                copyItems(player.getEnderChestInventory(), lease.offlineContainer);
                if(player instanceof EntityPlayerMPFake fake) {
                    fake.kill(Component.translatable("big_ender_chest.fake_page.disconnect"));
                }else {
                    player.connection.disconnect(
                            Component.translatable("big_ender_chest.fake_page.disconnect")
                    );
                }
            }
        }
        LEASES.clear();
    }

    private static void copyItems(Container source, Container target) {
        if(source == null || target == null || source == target) return;
        for(int slot = 0; slot < target.getContainerSize(); slot++) {
            target.setItem(slot, source.getItem(slot).copy());
        }
    }

    private static final class Lease {
        private final PageMetadata metadata;
        private ServerPlayer owner;
        private Container offlineContainer;
        private long lastUseTick;
        private long lastSpawnAttempt = Long.MIN_VALUE;
        private boolean owned;

        private Lease(PageMetadata metadata) {
            this.metadata = metadata;
        }
    }
}
