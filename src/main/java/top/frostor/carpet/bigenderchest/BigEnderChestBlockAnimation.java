package top.frostor.carpet.bigenderchest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;

final class BigEnderChestBlockAnimation {
    private final ServerPlayer player;
    private EnderChestBlockEntity activeChest;

    BigEnderChestBlockAnimation(ServerPlayer player) {
        this.player = player;
    }

    void start(ServerLevel level, BlockPos pos) {
        if(activeChest != null) return;
        if(!(level.getBlockEntity(pos) instanceof EnderChestBlockEntity chest)) return;

        PlayerEnderChestContainer enderInventory = player.getEnderChestInventory();
        activeChest = chest;
        enderInventory.setActiveChest(chest);
        enderInventory.startOpen(player);
    }

    void stop() {
        if(activeChest == null) return;

        activeChest = null;
        player.getEnderChestInventory().stopOpen(player);
    }

    boolean isActive() {
        return activeChest != null;
    }
}
