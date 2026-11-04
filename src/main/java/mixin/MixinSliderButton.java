package mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.ui.rhine.RhineScreens;

/** Sliders on the Rhine screens become a hairline track with a paper knob. */
@Mixin(AbstractSliderButton.class)
public class MixinSliderButton {
    @Shadow
    protected double value;

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void carnation$skin(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                float partialTick, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen != null && RhineScreens.handles(screen)) {
            RhineScreens.renderSlider(graphics, (AbstractSliderButton) (Object) this, value, mouseX, mouseY);
            ci.cancel();
        }
    }
}
