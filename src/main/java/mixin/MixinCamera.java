package mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.carnation.Client;
import wtf.carnation.module.Module;

@Mixin(Camera.class)
public class MixinCamera {

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void carnation$viewClip(float desiredDistance, CallbackInfoReturnable<Float> cir) {
        if (Client.instance == null) return;
        Module viewClip = Client.instance.getModuleManager().getModuleByName("ViewClip");
        if (viewClip != null && viewClip.isEnabled()) {
            cir.setReturnValue(4.0F);
        }
    }
}
