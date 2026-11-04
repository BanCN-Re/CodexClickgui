package wtf.carnation.boot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.util.Util;

/**
 * Plays the boot sequence on demand (the {@code .boot} command) instead of at startup.
 * Same renderer as the loading overlay; the progress bar runs on the animation clock, and the
 * overlay removes itself after the closing flash.
 */
public final class RhineBootOverlay extends Overlay {
    private final RhineBoot boot;
    private final float progress;
    private boolean closed;

    /** @param progress 0..1 to pin the bar, or a negative value to follow the animation clock. */
    public RhineBootOverlay(long now, long offsetMs, boolean replay, float progress) {
        this.boot = new RhineBoot(now, offsetMs, replay);
        this.progress = progress;
    }

    public RhineBootOverlay(long now, long offsetMs, boolean replay) {
        this(now, offsetMs, replay, -1f);
    }

    @Override
    public boolean isPausing() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boot.render(graphics, progress, progress, -1L, Util.getMillis());
        if (boot.finished() && !closed) {
            closed = true;
            Minecraft.getInstance().gui.setOverlay(null);
        }
    }
}
