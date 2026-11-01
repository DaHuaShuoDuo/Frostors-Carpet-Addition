package top.frostor.carpet.onlineprofile.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.world.level.storage.LevelResource;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.frostor.carpet.FrostorSettings;
import top.frostor.carpet.FrostorsCarpetAddition;
import top.frostor.carpet.onlineprofile.OnlineProfileFallback;

import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    @Final
    private Connection connection;

    @Shadow
    protected abstract void startClientVerification(GameProfile profile);

    @Inject(
            method = "handleHello",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/server/network/ServerLoginPacketListenerImpl;requestedUsername:Ljava/lang/String;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void onlineprofile$useUsernameProfile(ServerboundHelloPacket packet, CallbackInfo ci) {
        if(!FrostorSettings.onlineProfileFallback
                || !OnlineProfileFallback.isDedicatedServer()
                || !server.usesAuthentication()
                || connection.isMemoryConnection()) {
            return;
        }

        GameProfile profile = OnlineProfileFallback.resolve(packet.name());
        if(profile == null) return;

        Path playerData = server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(profile.id() + ".dat");
        if(!Files.isRegularFile(playerData)) {
            FrostorsCarpetAddition.LOGGER.warn(
                    "Not applying online profile fallback for '{}': no existing player data for {}",
                    packet.name(),
                    profile.id()
            );
            return;
        }

        FrostorsCarpetAddition.LOGGER.warn(
                "Online-mode authentication is being bypassed by username for '{}'; using Mojang profile {}",
                packet.name(),
                profile.id()
        );
        startClientVerification(profile);
        ci.cancel();
    }

    @Inject(method = "startClientVerification", at = @At("HEAD"))
    private void onlineprofile$rememberVerifiedProfile(GameProfile profile, CallbackInfo ci) {
        if(FrostorSettings.onlineProfileFallback
                && OnlineProfileFallback.isDedicatedServer()
                && server.usesAuthentication()) {
            OnlineProfileFallback.remember(profile);
        }
    }
}
