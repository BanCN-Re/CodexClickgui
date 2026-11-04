package mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the yellow "Also try..." splash text on the Rhine title screen. */
@Mixin(SplashRenderer.class)
public class MixinSplashRenderer {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void carnation$hideOnTitle(GuiGraphicsExtractor graphics, int width, Font font, float alpha,
                                       CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() instanceof TitleScreen) ci.cancel();
    }
}
