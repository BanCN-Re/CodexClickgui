package mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.Client;
import wtf.carnation.event.impl.input.MouseClickEvent;

/** Naven's MixinMouseHandler: mouse button events for keybinds and LongJump. */
@Mixin(MouseHandler.class)
public class MixinMouseHandler {

    @Inject(method = "onButton", at = @At("HEAD"))
    private void carnation$onButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (Client.instance == null) return;
        Client.instance.getEventManager().call(
                new MouseClickEvent(buttonInfo.button(), action == 1));
    }
}
