package mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.carnation.ui.rhine.RhineTitle;

/**
 * Restyles the real {@link TitleScreen} instead of replacing it: the vanilla widgets keep their
 * handlers, get moved into the Rhine menu grid and are painted over by {@link RhineTitle}.
 *
 * <p>The panorama, logo, splash and version string are suppressed so nothing of the vanilla title
 * shows through the paper background. Widget rendering is cancelled in {@code MixinAbstractWidget};
 * their clicks still work because the widgets stay alive.
 */
@Mixin(TitleScreen.class)
public class MixinTitleScreen {
    @Unique
    private RhineTitle carnation$title;

    @Inject(method = "init", at = @At("TAIL"))
    private void carnation$layout(CallbackInfo ci) {
        if (carnation$title == null) {
            carnation$title = new RhineTitle(Util.getMillis());
        }
        Screen screen = (Screen) (Object) this;
        carnation$title.layout((TitleScreen) (Object) this, screen.width, screen.height);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void carnation$render(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick, CallbackInfo ci) {
        if (carnation$title == null) {
            carnation$title = new RhineTitle(Util.getMillis());
            Screen screen = (Screen) (Object) this;
            carnation$title.layout((TitleScreen) (Object) this, screen.width, screen.height);
        }
        carnation$title.render(graphics, mouseX, mouseY, partialTick);

    }

    /** The Realms invitation overlay belongs to the vanilla layout; keep the terminal clean. */
    @Inject(method = "realmsNotificationsEnabled", at = @At("HEAD"), cancellable = true)
    private void carnation$noRealmsOverlay(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    /** The version string is drawn white in the bottom-left corner; the terminal prints its own. */
    @Redirect(method = "extractRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private void carnation$hideVersion(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
    }
}
