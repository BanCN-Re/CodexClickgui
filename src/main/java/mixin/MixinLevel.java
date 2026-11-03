package mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.carnation.Client;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.render.TimeChanger;

/** Overrides the client-side world clock. */
@Mixin(Level.class)
public class MixinLevel {

    @Inject(method = "getOverworldClockTime", at = @At("HEAD"), cancellable = true)
    private void carnation$timeChanger(CallbackInfoReturnable<Long> cir) {
        if (Client.instance == null) return;
        Module module = Client.instance.getModuleManager().getModuleByName("TimeChanger");
        if (module instanceof TimeChanger changer && changer.isEnabled()) {
            cir.setReturnValue(changer.time());
        }
    }
}
