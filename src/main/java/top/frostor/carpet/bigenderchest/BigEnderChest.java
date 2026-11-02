package top.frostor.carpet.bigenderchest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import top.frostor.carpet.FrostorSettings;

public final class BigEnderChest {
    private static boolean registered;

    private BigEnderChest() {
    }

    public static void register() {
        if(registered) return;
        registered = true;

        BigEnderChestProtocolServer.register();
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if(!(level instanceof ServerLevel serverLevel)
                    || !(player instanceof ServerPlayer serverPlayer)
                    || !FrostorSettings.bigEnderChest
                    || !player.getItemInHand(hand).isEmpty()) {
                return InteractionResult.PASS;
            }
            if(!level.getBlockState(hitResult.getBlockPos()).is(Blocks.ENDER_CHEST)) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hitResult.getBlockPos();
            return BigEnderChestSessions.open(serverPlayer, serverLevel, pos)
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        });
        ServerLifecycleEvents.SERVER_STARTED.register(BigEnderChestSessions::initialize);
        ServerLifecycleEvents.SERVER_STOPPING.register(ignored -> BigEnderChestSessions.shutdown());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, ignored) ->
                BigEnderChestSessions.onDisconnect(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BigEnderChestSessions.tick(server);
            BigEnderChestProtocolServer.tick(server);
        });
    }
}
