package mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.ui.rhine.RhineScreens;

/**
 * Widget skin for the Rhine screens.
 *
 * <p>On the title screen every vanilla widget is invisible (the title paints its own menu). On the
 * world / multiplayer / options / pause screens, plain buttons are redrawn as outlined Rhine rows;
 * lists, sliders and edit boxes keep their vanilla rendering.
 */
@Mixin(AbstractWidget.class)
public class MixinAbstractWidget {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void carnation$skin(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                float partialTick, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) return;

        if (screen instanceof TitleScreen) {
            ci.cancel();
            return;
        }

        if (RhineScreens.handles(screen) && (Object) this instanceof Button button
                && !((Object) this instanceof SpriteIconButton)
                && !((Object) this instanceof PlainTextButton)) {
            RhineScreens.renderButton(graphics, button, mouseX, mouseY);
            ci.cancel();
        }
    }
}
