package top.frostor.carpet.bigenderchest;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import top.frostor.carpet.FrostorSettings;
import top.frostor.carpet.bigenderchest.menu.BigEnderChestMenu;
import top.frostor.carpet.bigenderchest.menu.BigEnderChestTerminalMenu;
import top.frostor.carpet.bigenderchest.menu.BigEnderChestView;
import top.frostor.carpet.bigenderchest.page.FakePageDataStore;
import top.frostor.carpet.bigenderchest.page.PageRegistry;

import java.util.Collections;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BigEnderChestSessions {
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static MinecraftServer server;
    private static PageRegistry pageRegistry;
    private static FakePageDataStore dataStore;
    private static int tickCounter;

    private BigEnderChestSessions() {
    }

    public static void initialize(MinecraftServer minecraftServer) {
        closeAll();
        server = minecraftServer;
        pageRegistry = new PageRegistry(minecraftServer);
        dataStore = new FakePageDataStore(minecraftServer);
    }

    public static boolean open(ServerPlayer player) {
        Session current = session(player);
        return current == null
                ? open(player, null, null)
                : open(
                        player,
                        current.sourceLevel(),
                        current.sourcePosition()
                );
    }

    public static boolean open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if(!FrostorSettings.bigEnderChest || server == null || pageRegistry == null) {
            return false;
        }
        Session session = beginSession(player);
        boolean terminal = BigEnderChestProtocolServer.isTerminalClient(player);
        OptionalInt menuId = player.openMenu(terminal
                ? terminalMenuProvider(session.service())
                : vanillaMenuProvider(session.service()));
        if(menuId.isEmpty() || session.menu == null) {
            close(player);
            return false;
        }
        session.containerId = menuId.getAsInt();
        session.startBlockAnimation(level, pos);
        announceSession(session);
        return true;
    }

    static ExtendedMenuProvider<Unit> terminalMenuProvider(BigEnderChestService service) {
        return new ExtendedMenuProvider<>() {
            @Override
            public Unit getScreenOpeningData(ServerPlayer player) {
                return Unit.INSTANCE;
            }

            @Override
            public Component getDisplayName() {
                return Component.translatable("big_ender_chest.menu.title");
            }

            @Override
            public BigEnderChestTerminalMenu createMenu(
                    int syncId,
                    Inventory inventory,
                    Player player
            ) {
                return new BigEnderChestTerminalMenu(syncId, inventory, service);
            }
        };
    }

    private static SimpleMenuProvider vanillaMenuProvider(BigEnderChestService service) {
        return new SimpleMenuProvider(
                (syncId, inventory, ignored) -> new BigEnderChestMenu(
                        syncId,
                        inventory,
                        service,
                        new BigEnderChestView(service)
                ),
                Component.translatable("big_ender_chest.menu.title")
        );
    }

    /**
     * Used by compatibility hooks that provide their own {@link net.minecraft.world.MenuProvider}.
     */
    public static BigEnderChestMenu createExternalMenu(int syncId, Inventory inventory) {
        if(!(inventory.player instanceof ServerPlayer player)
                || !FrostorSettings.bigEnderChest
                || server == null
                || pageRegistry == null) {
            return null;
        }
        if(BigEnderChestProtocolServer.isTerminalClient(player)) {
            player.level().getServer().execute(() -> open(player));
            return null;
        }
        Session session = beginSession(player);
        BigEnderChestView view = new BigEnderChestView(session.service());
        return new BigEnderChestMenu(syncId, inventory, session.service(), view);
    }

    private static Session beginSession(ServerPlayer player) {
        close(player);
        BigEnderChestService service = new BigEnderChestService(player, pageRegistry, dataStore);
        Session session = new Session(player, service);
        SESSIONS.put(player.getUUID(), session);
        return session;
    }

    private static void announceSession(Session session) {
        if(session.protocolAnnounced) return;
        session.protocolAnnounced = true;
        BigEnderChestProtocolServer.sessionOpened(session);
    }

    public static <T extends AbstractContainerMenu> T attachMenu(
            T menu,
            BigEnderChestService service
    ) {
        Session session = SESSIONS.get(service.owner().getUUID());
        if(session == null || session.service != service) return menu;
        session.menu = menu;
        session.containerId = menu.containerId;
        return menu;
    }

    public static void menuRemoved(
            net.minecraft.world.entity.player.Player player,
            AbstractContainerMenu menu
    ) {
        if(!(player instanceof ServerPlayer serverPlayer)) return;
        Session session = SESSIONS.get(serverPlayer.getUUID());
        if(session != null && session.menu == menu) {
            close(serverPlayer);
        }
    }

    public static void close(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if(session == null) return;
        session.menu = null;
        session.stopBlockAnimation();
        session.service.close();
        BigEnderChestProtocolServer.sessionClosed(session);
    }

    public static void onDisconnect(ServerPlayer player) {
        close(player);
    }

    public static void tick(MinecraftServer minecraftServer) {
        if(!FrostorSettings.bigEnderChest) {
            closeAll();
            return;
        }
        boolean saveTick = ++tickCounter >= 20;
        if(saveTick) tickCounter = 0;
        for(Session session : SESSIONS.values()) {
            if(session.owner.hasDisconnected() || !session.service.isOpen()) {
                close(session.owner);
                continue;
            }
            if(!session.isWithinSourceRange()) {
                close(session.owner);
                continue;
            }
            session.service.tick();
            if(session.menu instanceof BigEnderChestMenu vanillaMenu) {
                vanillaMenu.tickVirtualActions();
            }
            if(saveTick) session.service.saveDirtyPages();
            if(session.menu != null) {
                announceSession(session);
                if(session.menu instanceof BigEnderChestMenu vanillaMenu) {
                    vanillaMenu.view().refresh();
                }
                if(session.service.revision() != session.lastPublishedRevision) {
                    session.menu.broadcastChanges();
                    session.lastPublishedRevision = session.service.revision();
                }
            }
        }
    }

    public static void closeAll() {
        for(Session session : java.util.List.copyOf(SESSIONS.values())) {
            if(session.menu != null) session.closeContainer();
            session.menu = null;
            session.stopBlockAnimation();
            session.service.close();
            BigEnderChestProtocolServer.sessionClosed(session);
        }
        SESSIONS.clear();
    }

    public static void shutdown() {
        closeAll();
        if(pageRegistry != null) pageRegistry.flush();
        if(dataStore != null) dataStore.flush();
    }

    public static Session session(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    public static Map<UUID, Session> sessions() {
        return Collections.unmodifiableMap(SESSIONS);
    }

    public static boolean isWithinSourceRange(ServerPlayer player) {
        Session session = session(player);
        return session != null && session.isWithinSourceRange();
    }

    public static PageRegistry pageRegistry() {
        return pageRegistry;
    }

    public static final class Session {
        private final ServerPlayer owner;
        private final BigEnderChestService service;
        private final BigEnderChestBlockAnimation blockAnimation;
        private final long sessionId;
        private int containerId = -1;
        private AbstractContainerMenu menu;
        private boolean protocolAnnounced;
        private long lastPublishedRevision = -1;

        private Session(ServerPlayer owner, BigEnderChestService service) {
            this.owner = owner;
            this.service = service;
            this.blockAnimation = new BigEnderChestBlockAnimation(owner);
            this.sessionId = owner.getRandom().nextLong();
        }

        public ServerPlayer owner() {
            return owner;
        }

        public BigEnderChestService service() {
            return service;
        }

        public long sessionId() {
            return sessionId;
        }

        public int containerId() {
            return containerId;
        }

        public AbstractContainerMenu menu() {
            return menu;
        }

        public BigEnderChestMenu vanillaMenu() {
            return menu instanceof BigEnderChestMenu vanillaMenu ? vanillaMenu : null;
        }

        public boolean isWithinSourceRange() {
            return blockAnimation.isWithinRange();
        }

        private ServerLevel sourceLevel() {
            return blockAnimation.sourceLevel();
        }

        private BlockPos sourcePosition() {
            return blockAnimation.sourcePosition();
        }

        public void closeContainer() {
            if(owner.connection != null) {
                int targetContainer = containerId >= 0
                        ? containerId
                        : owner.containerMenu.containerId;
                owner.connection.handleContainerClose(
                        new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(targetContainer)
                );
            }
        }

        private void startBlockAnimation(ServerLevel level, BlockPos pos) {
            if(level != null && pos != null) {
                blockAnimation.start(level, pos);
            }
        }

        private void stopBlockAnimation() {
            blockAnimation.stop();
        }
    }
}
