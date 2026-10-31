package top.frostor.carpet.playerladder;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.InteractionResult;
import top.frostor.carpet.FrostorSettings;

/**
 * Player Ladder feature ported from PlayerLadder (ru.nern.playerladder),
 * with its local config replaced by Carpet rules in {@link FrostorSettings}.
 */
public final class PlayerLadder {
    private PlayerLadder() {
    }

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SharedHandler.onLogOut(handler.player));

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if(!FrostorSettings.playerLadder) return InteractionResult.PASS;

            return switch (FrostorSettings.playerLadderClickMode) {
                case RIDE -> SharedHandler.rideEntity(player, entity, level, hand);
                case PICK_UP -> SharedHandler.pickUpEntity(player, entity, level, hand);
                case DO_NOTHING -> InteractionResult.PASS;
            };
        });
    }
}
