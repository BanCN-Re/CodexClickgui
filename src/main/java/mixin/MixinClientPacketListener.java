package mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.Client;
import wtf.carnation.event.impl.network.ChatEvent;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void onSendChat(String message, CallbackInfo ci) {
        if (Client.instance == null) return;
        if (Client.instance.getEventManager().call(new ChatEvent(message)).isCancelled()) {
            ci.cancel();
        }
    }
}
