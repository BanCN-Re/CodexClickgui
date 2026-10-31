package mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.Client;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void onKeyPress(long window, int action, KeyEvent keyEvent, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (Client.instance == null || window != mc.getWindow().handle() || mc.gui.screen() != null) return;
        if (keyEvent.key() <= 0 || action == InputConstants.REPEAT) return;
        Client.instance.getEventManager().call(
                new wtf.carnation.event.impl.input.KeyEvent(keyEvent.key(), action == InputConstants.PRESS));
    }
}
