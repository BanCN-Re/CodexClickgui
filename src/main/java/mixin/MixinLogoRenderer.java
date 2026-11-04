package mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the vanilla Minecraft logo so the Rhine wordmark owns the title screen. */
@Mixin(LogoRenderer.class)
public class MixinLogoRenderer {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V",
            at = @At("HEAD"), cancellable = true)
    private void carnation$hideOnTitle(GuiGraphicsExtractor graphics, int width, float alpha, CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() instanceof TitleScreen) ci.cancel();
    }
}
