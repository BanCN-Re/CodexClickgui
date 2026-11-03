package mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.Client;
import wtf.carnation.event.api.Phase;
import wtf.carnation.event.impl.movement.RunTickEvent;

@Mixin(Minecraft.class)
public class MixinMinecraft {

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
}
