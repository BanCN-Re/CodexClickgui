package mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.wallpaper.VideoWallpaper;

/**
 * Swaps the title-screen panorama for the wallpaper video. Everything below the background
 * (logo, buttons, gradients from widgets) keeps rendering normally.
 */
@Mixin(TitleScreen.class)
public class MixinTitleScreen {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void carnation$wallpaperBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                               float partialTick, CallbackInfo ci) {
        if (!VideoWallpaper.renderInto(graphics)) return;
        ci.cancel();
    }
}
