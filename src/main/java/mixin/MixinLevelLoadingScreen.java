package mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.ui.rhine.RhineLoading;

/** Replaces the vanilla chunk map / "Loading terrain..." screen with the Rhine loading view. */
@Mixin(LevelLoadingScreen.class)
public class MixinLevelLoadingScreen {
    @Shadow
    private float smoothedProgress;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void carnation$loading(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick, CallbackInfo ci) {
        RhineLoading.render(graphics, this.smoothedProgress, (net.minecraft.client.gui.screens.Screen) (Object) this);
        ci.cancel();
    }
}
