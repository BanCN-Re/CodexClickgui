package mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.carnation.Client;
import wtf.carnation.event.api.Phase;
import wtf.carnation.event.impl.UpdateEvent;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.RespawnEvent;
import wtf.carnation.module.impl.movement.NoSlow;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {

    @Inject(method = "tick", at = @At("HEAD"))
    private void carnation$tickPre(CallbackInfo ci) {
        if (Client.instance == null) return;
        Client.instance.getEventManager().call(new UpdateEvent());
        Client.instance.getEventManager().call(new MotionEvent(Phase.PRE));
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void carnation$tickPost(CallbackInfo ci) {
        if (Client.instance == null) return;
        Client.instance.getEventManager().call(new MotionEvent(Phase.POST));
    }

    @Inject(method = "respawn", at = @At("HEAD"))
    private void carnation$respawn(CallbackInfo ci) {
        if (Client.instance == null) return;
        Client.instance.getEventManager().call(new RespawnEvent());
    }

    /** NoSlow: 26.3 asks the player for both the slowdown flag and its speed multiplier. */
    @Inject(method = "isSlowDueToUsingItem", at = @At("HEAD"), cancellable = true)
    private void carnation$noSlowFlag(CallbackInfoReturnable<Boolean> cir) {
        if (Client.instance == null) return;
        NoSlow noSlow = Client.instance.getModuleManager().getModule(NoSlow.class);
        if (noSlow != null && noSlow.isEnabled() && noSlow.shouldCancelSlowdown()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "itemUseSpeedMultiplier", at = @At("HEAD"), cancellable = true)
    private void carnation$noSlowMultiplier(CallbackInfoReturnable<Float> cir) {
        if (Client.instance == null) return;
        NoSlow noSlow = Client.instance.getModuleManager().getModule(NoSlow.class);
        if (noSlow != null && noSlow.isEnabled() && noSlow.shouldCancelSlowdown()) {
            cir.setReturnValue(1.0F);
        }
    }
}
