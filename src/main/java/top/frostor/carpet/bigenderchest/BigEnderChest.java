package top.frostor.carpet.bigenderchest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import top.frostor.carpet.FrostorSettings;
import top.frostor.carpet.bigenderchest.compat.QuickShulkerIntegration;
import top.frostor.carpet.bigenderchest.menu.BigEnderChestTerminalMenu;

public final class BigEnderChest {
    private static boolean registered;

    private BigEnderChest() {
    }

    public static void register() {
        if(registered) return;
        registered = true;

        BigEnderChestTerminalMenu.menuType();
        BigEnderChestProtocolServer.register();
        UseBlockCallback.EVENT.register(BigEnderChest::useBlock);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            BigEnderChestSessions.initialize(server);
            if(FabricLoader.getInstance().isModLoaded("quickshulker")) {
                QuickShulkerIntegration.register();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(ignored -> BigEnderChestSessions.shutdown());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, ignored) ->
                BigEnderChestSessions.onPlayerJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, ignored) -> {
            BigEnderChestSessions.onPagePlayerDisconnect(handler.player);
            BigEnderChestSessions.onDisconnect(handler.player);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BigEnderChestSessions.tick(server);
            BigEnderChestProtocolServer.tick(server);
        });
    }

    static InteractionResult useBlock(
            Player player,
            Level level,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if(!(level instanceof ServerLevel serverLevel)
                || !(player instanceof ServerPlayer serverPlayer)
                || !canOpenBigEnderChest(player, level, hitResult)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = hitResult.getBlockPos();
        return BigEnderChestSessions.open(serverPlayer, serverLevel, pos)
                ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
    }

    static boolean canOpenBigEnderChest(
            Player player,
            Level level,
            BlockHitResult hitResult
    ) {
        return FrostorSettings.bigEnderChest
                && level instanceof ServerLevel
                && player instanceof ServerPlayer
                && level.getBlockState(hitResult.getBlockPos()).is(Blocks.ENDER_CHEST);
    }
}
