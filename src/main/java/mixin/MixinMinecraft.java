package mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.carnation.Client;
import wtf.carnation.event.api.Phase;
import wtf.carnation.event.impl.combat.EventClick;
import wtf.carnation.event.impl.movement.RunTickEvent;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    /**
     * Naven hooks the same place: {@code startAttack} is what the game calls for every consumed
     * attack input, so this event is "the player actually swung", not "the key is down".
     */
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void carnation$click(CallbackInfoReturnable<Boolean> cir) {
        if (Client.instance == null) return;
        EventClick event = new EventClick();
        Client.instance.getEventManager().call(event);
        if (event.isCancelled()) cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void carnation$tickPre(CallbackInfo ci) {
        if (Client.instance == null) return;
        Client.instance.getEventManager().call(new RunTickEvent(Phase.PRE));
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void carnation$tickPost(CallbackInfo ci) {
        if (Client.instance == null) return;
        Client.instance.getEventManager().call(new RunTickEvent(Phase.POST));
    }

    @Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
    private void carnation$glow(net.minecraft.world.entity.Entity entity,
                                org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (wtf.carnation.module.impl.render.Glow.shouldGlow(entity)) cir.setReturnValue(true);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void carnation$music(CallbackInfo ci) {
        wtf.carnation.audio.RhineMusic.tick();
    }
}
