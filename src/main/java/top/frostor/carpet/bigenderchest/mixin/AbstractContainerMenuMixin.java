package top.frostor.carpet.bigenderchest.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.frostor.carpet.bigenderchest.menu.BigEnderChestMenu;
import top.frostor.carpet.bigenderchest.menu.BigEnderChestView;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void playerladder$guardBigEnderChestSlots(
            int slotIndex,
            int button,
            ContainerInput input,
            Player player,
            CallbackInfo callback
    ) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if(slotIndex < 0
                || slotIndex < BigEnderChestView.ENTRY_SLOTS
                || slotIndex >= BigEnderChestView.MENU_SLOTS
                || !BigEnderChestView.isVirtualMenu(menu)) {
            return;
        }

        if(!player.level().isClientSide() && menu instanceof BigEnderChestMenu bigMenu) {
            bigMenu.handleProtectedClick(slotIndex, button, input);
        }
        callback.cancel();
    }
}
