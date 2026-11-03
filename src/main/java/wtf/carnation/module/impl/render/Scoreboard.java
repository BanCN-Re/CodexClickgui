package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Custom sidebar scoreboard rendered above the vanilla HUD. */
public class Scoreboard extends Module {
    private final BoolValue background = new BoolValue("Background", true);
    private final NumberValue maxLines = new NumberValue("Max Lines", 10, 3, 20, 1);

    public Scoreboard() {
        super("Scoreboard", Category.RENDER, "右侧自定义计分板");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;
        Objective objective = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return;

        List<PlayerScoreEntry> entries = new ArrayList<>(
                mc.level.getScoreboard().listPlayerScores(objective));
        entries.removeIf(PlayerScoreEntry::isHidden);
        entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
        if (entries.isEmpty()) return;
        if (entries.size() > maxLines.getInt()) entries = entries.subList(0, maxLines.getInt());

        GuiGraphicsExtractor g = event.graphics();
        float scale = UiFont.useGuiScale();
        int guiScale = mc.getWindow().getGuiScale();
        float screenW = mc.getWindow().getWidth();
        float rowH = 18;
        float width = UiFont.SEMIBOLD.width(objective.getDisplayName().getString()) + 16;
        for (PlayerScoreEntry entry : entries) {
            width = Math.max(width, UiFont.SMALL.width(entry.ownerName().getString()) + 44);
        }
        float left = screenW - width - 6;
        float top = 8;
        float height = rowH * (entries.size() + 1) + 6;

        g.pose().pushMatrix();
        g.pose().scale(scale / guiScale, scale / guiScale);
        if (background.get()) {
            Render2D.roundedRect(g, left, top, width, height, 4, 0xC0121218);
        }
        UiFont.SEMIBOLD.drawCentered(g, objective.getDisplayName().getString(),
                left + width / 2, top + rowH / 2 + 2, 0xFF7C5CFF);
        float y = top + rowH + 2;
        for (PlayerScoreEntry entry : entries) {
            UiFont.SMALL.draw(g, entry.ownerName().getString(), left + 8, y + rowH / 2, 0xFFEDEDF5);
            UiFont.SMALL.drawRight(g, String.valueOf(entry.value()), left + width - 8,
                    y + rowH / 2, 0xFF8C8C9C);
            y += rowH;
        }
        g.pose().popMatrix();
    }
}
