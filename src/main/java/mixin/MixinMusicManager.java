package mixin;

import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla's music scheduler is disabled; {@code RhineMusic} owns the looping score. */
@Mixin(MusicManager.class)
public class MixinMusicManager {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void carnation$noVanillaMusic(CallbackInfo ci) {
        ci.cancel();
    }
}
