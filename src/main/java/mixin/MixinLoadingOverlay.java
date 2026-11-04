package mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.boot.RhineBoot;

/**
 * Replaces the vanilla Mojang logo / progress bar with the Rhine Lab boot sequence.
 *
 * <p>{@code extractRenderState} is cancelled at HEAD, so the whole vanilla frame (logo, brand fill,
 * progress bar, fade) is gone. Two pieces of its lifecycle still have to be honoured:
 * {@code fadeInStart} feeds {@code isReadyToFadeOut} (without it {@code tick()} would never end the
 * overlay), and the overlay must remove itself once the closing flash is complete.
 *
 * <p>Only the first overlay of the process is taken over: a resource reload in game (F3+T) keeps
 * the vanilla screen instead of replaying the whole animation.
 */
@Mixin(LoadingOverlay.class)
public class MixinLoadingOverlay {
    @Unique
    private static final Logger CARNATION$LOGGER = LoggerFactory.getLogger("Carnation/Boot");

    @Unique
    private static boolean carnation$bootClaimed;
    @Unique
    private static boolean carnation$bootDone;

    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    @Final
    private ReloadInstance reload;
    @Shadow
    @Final
    private boolean fadeIn;
    @Shadow
    private long fadeInStart;
    @Shadow
    private long fadeOutStart;
    @Shadow
    private float currentProgress;

    @Unique
    private RhineBoot carnation$boot;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void carnation$bootSequence(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                        float partialTick, CallbackInfo ci) {
        if (carnation$boot == null) {
            if (carnation$bootClaimed) return;
            carnation$bootClaimed = true;
            carnation$boot = new RhineBoot(Util.getMillis(), 0L, false);
            CARNATION$LOGGER.info("Rhine Lab boot sequence engaged");
        }

        long now = Util.getMillis();
        if (this.fadeIn && this.fadeInStart == -1L) this.fadeInStart = now;

        try {
            carnation$boot.render(graphics, this.reload.getActualProgress(), this.currentProgress,
                    this.fadeOutStart, now);
        } catch (Throwable throwable) {
            // A broken boot screen must never cost the player the game: drop back to the vanilla
            // overlay for this and every later reload.
            carnation$boot = null;
            carnation$bootClaimed = true;
            CARNATION$LOGGER.error("Boot sequence failed, falling back to the vanilla loading screen", throwable);
            return;
        }

        if (carnation$boot.finished() && !carnation$bootDone) {
            carnation$bootDone = true;
            this.minecraft.gui.setOverlay(null);
            CARNATION$LOGGER.info("Rhine Lab boot sequence finished");
        }
        ci.cancel();
    }
}
