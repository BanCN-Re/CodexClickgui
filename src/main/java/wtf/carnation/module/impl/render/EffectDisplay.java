package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffectInstance;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EffectDisplay extends Module {
    private final BoolValue background = new BoolValue("Background", true);
    private final BoolValue duration = new BoolValue("Duration", true);

    public EffectDisplay() {
        super("EffectDisplay", Category.RENDER, "右侧显示当前药水效果与剩余时间");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
        if (effects.isEmpty()) return;
        effects.sort(Comparator.comparing(e -> e.getEffect().value().getDisplayName().getString()));

        GuiGraphicsExtractor g = event.graphics();
        float scale = UiFont.useGuiScale();
        int guiScale = mc.getWindow().getGuiScale();
        float screenW = mc.getWindow().getWidth();
        float right = screenW - 8;
        float y = 10;
        float rowH = 20;

        g.pose().pushMatrix();
        g.pose().scale(scale / guiScale, scale / guiScale);
        for (MobEffectInstance effect : effects) {
            String name = effect.getEffect().value().getDisplayName().getString();
            if (effect.getAmplifier() > 0) name += " " + roman(effect.getAmplifier() + 1);
            String time = duration.get() ? format(effect.getDuration()) : "";
            float width = Math.max(UiFont.SMALL.width(name), UiFont.SMALL.width(time)) + 14;
            float left = right - width;
            if (background.get()) {
                Render2D.roundedRect(g, left, y, width, rowH, 4, 0xB0121218);
            }
            UiFont.SMALL.draw(g, name, left + 6, y + rowH / 2, 0xFFEDEDF5);
            if (!time.isEmpty()) UiFont.SMALL.drawRight(g, time, right - 6, y + rowH / 2, 0xFF8C8C9C);
            y += rowH + 2;
        }
        g.pose().popMatrix();
    }

    private static String format(int ticks) {
        int seconds = ticks / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static String roman(int value) {
        return switch (value) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(value);
        };
    }
}
