package mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.ui.rhine.RhineScreens;

/**
 * Background pass for the Rhine screens.
 *
 * <p>The title draws paper and skips the panorama. Menu screens swap their dirt background for the
 * terminal field; the pause screen keeps its blurred world and only receives a translucent wash.
 */
@Mixin(Screen.class)
public class MixinScreen {
    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void carnation$skipPanorama(GuiGraphicsExtractor graphics, float partialTick, CallbackInfo ci) {
        if ((Object) this instanceof TitleScreen) ci.cancel();
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void carnation$frameBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                           float partialTick, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (screen instanceof TitleScreen || screen instanceof PauseScreen) return;
        if (RhineScreens.handles(screen)) {
            RhineScreens.renderBackground(graphics, screen);
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void carnation$pauseWash(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                     float partialTick, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (screen instanceof PauseScreen) RhineScreens.renderPauseOverlay(graphics, screen);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void carnation$screenFade(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                      float partialTick, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (screen instanceof TitleScreen) return;
        if (RhineScreens.handles(screen)) RhineScreens.renderFade(graphics, screen);
    }
}
