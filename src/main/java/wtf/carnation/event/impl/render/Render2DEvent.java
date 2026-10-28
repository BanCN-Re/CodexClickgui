package wtf.carnation.event.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import wtf.carnation.event.api.Event;

/** Fired after the vanilla HUD has been extracted, so client HUD elements draw on top of it. */
public record Render2DEvent(GuiGraphicsExtractor graphics, float partialTicks) implements Event {
}
