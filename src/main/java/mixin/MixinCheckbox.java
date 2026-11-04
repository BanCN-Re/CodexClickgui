package mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.ui.rhine.RhineScreens;

/** Checkboxes on the Rhine screens become a hairline square with a paper tick. */
@Mixin(Checkbox.class)
public class MixinCheckbox {
    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void carnation$skin(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                float partialTick, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen != null && RhineScreens.handles(screen)) {
            RhineScreens.renderCheckbox(graphics, (Checkbox) (Object) this, mouseX, mouseY);
            ci.cancel();
        }
    }
}
