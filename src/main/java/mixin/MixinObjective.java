package mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.carnation.Client;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.render.ScoreboardSpoof;

/** Replaces the sidebar title when ScoreboardSpoof is enabled. */
@Mixin(Objective.class)
public class MixinObjective {

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void carnation$spoofTitle(CallbackInfoReturnable<Component> cir) {
        if (Client.instance == null) return;
        Module module = Client.instance.getModuleManager().getModuleByName("ScoreboardSpoof");
        if (module instanceof ScoreboardSpoof spoof && spoof.isEnabled()) {
            cir.setReturnValue(Component.literal(spoof.title()));
        }
    }
}
