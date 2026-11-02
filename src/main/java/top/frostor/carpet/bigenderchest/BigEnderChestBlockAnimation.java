package top.frostor.carpet.bigenderchest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.phys.Vec3;

final class BigEnderChestBlockAnimation {
    private static final double MAX_OPEN_DISTANCE_SQUARED = 8.0 * 8.0;

    private final ServerPlayer player;
    private EnderChestBlockEntity activeChest;
    private ServerLevel activeLevel;
    private BlockPos activePos;

    BigEnderChestBlockAnimation(ServerPlayer player) {
        this.player = player;
    }

    void start(ServerLevel level, BlockPos pos) {
        if(activeChest != null) return;
        if(!(level.getBlockEntity(pos) instanceof EnderChestBlockEntity chest)) return;

        PlayerEnderChestContainer enderInventory = player.getEnderChestInventory();
        activeChest = chest;
        activeLevel = level;
        activePos = pos.immutable();
        enderInventory.setActiveChest(chest);
        enderInventory.startOpen(player);
    }

    void stop() {
        if(activeChest == null) return;

        activeChest = null;
        activeLevel = null;
        activePos = null;
        player.getEnderChestInventory().stopOpen(player);
    }

    boolean isActive() {
        return activeChest != null;
    }

    boolean isWithinRange() {
        if(activeLevel == null || activePos == null) return true;
        return player.level() == activeLevel
                && player.distanceToSqr(Vec3.atCenterOf(activePos))
                <= MAX_OPEN_DISTANCE_SQUARED;
    }

    ServerLevel sourceLevel() {
        return activeLevel;
    }

    BlockPos sourcePosition() {
        return activePos;
    }
}
