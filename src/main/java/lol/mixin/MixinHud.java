package lol.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.Client;
import wtf.carnation.event.impl.render.Render2DEvent;

@Mixin(Hud.class)
public class MixinHud {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Client.instance == null || ((Hud) (Object) this).isHidden()) return;
        Client.instance.getEventManager().call(
                new Render2DEvent(graphics, deltaTracker.getGameTimeDeltaPartialTick(true)));
    }
}
