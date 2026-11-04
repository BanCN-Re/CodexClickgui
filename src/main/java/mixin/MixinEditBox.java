package mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.ui.rhine.RhineScreens;

/** Search fields on the Rhine screens are drawn as hairline boxes instead of the vanilla black slab. */
@Mixin(EditBox.class)
public class MixinEditBox {
    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void carnation$skin(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                float partialTick, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen != null && RhineScreens.handles(screen)) {
            RhineScreens.renderEditBox(graphics, (EditBox) (Object) this);
            ci.cancel();
        }
    }
}
