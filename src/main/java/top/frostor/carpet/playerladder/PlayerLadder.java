package top.frostor.carpet.playerladder;

import carpet.api.settings.SettingsManager;
import carpet.utils.CommandHelper;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.flag.FeatureFlags;
import top.frostor.carpet.FrostorSettings;

/**
 * Player Ladder feature ported from PlayerLadder (ru.nern.playerladder),
 * with a Carpet master switch and Player Ladder settings exposed through commands.
 */
public final class PlayerLadder {
    private static SettingsManager observedManager;

    private PlayerLadder() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                PlayerLadderCommand.register(
                        dispatcher,
                        CommandBuildContext.simple(registryAccess, FeatureFlags.VANILLA_SET)
                ));
        ServerLifecycleEvents.SERVER_STARTED.register(PlayerLadderSettings.MANAGER::attachServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PlayerLadderSettings.MANAGER.detachServer();
            SharedHandler.onServerStopping();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SharedHandler.onLogOut(handler.player));

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if(level.isClientSide() || !FrostorSettings.playerLadder) return InteractionResult.PASS;

            if(!PlayerLadderSettings.interactionMode.matches(
                    player.isShiftKeyDown(),
                    player.getMainHandItem().isEmpty(),
                    player.getOffhandItem().isEmpty()
            )) return InteractionResult.PASS;

            InteractionResult result = switch (PlayerLadderSettings.clickMode) {
                case RIDE -> SharedHandler.rideEntity(player, entity, level, hand);
                case PICK_UP -> SharedHandler.pickUpEntity(player, entity, level, hand);
                case NONE -> InteractionResult.PASS;
            };
            if(result instanceof InteractionResult.Success
                    && PlayerLadderSettings.interactionMode == PlayerLadderInteractionMode.CARRY_ON) {
                SharedHandler.onSuccessfulCarryOnInteraction(player);
            }
            return result;
        });
    }

    public static void registerRuleObserver(SettingsManager settingsManager) {
        if(settingsManager == observedManager) return;
        observedManager = settingsManager;
        settingsManager.registerRuleObserver((source, rule, value) -> {
            if(rule.name().equals("playerLadder"))
                CommandHelper.notifyPlayersCommandsChanged(source.getServer());
        });
    }
}
