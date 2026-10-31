package mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.Client;
import wtf.carnation.event.impl.UpdateEvent;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (Client.instance != null) {
            Client.instance.getEventManager().call(new UpdateEvent());
        }
    }
}
