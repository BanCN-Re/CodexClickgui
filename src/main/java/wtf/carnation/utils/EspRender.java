package wtf.carnation.utils;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;

/** 2D ESP boxes and labels built on top of {@link Projection}. */
public final class EspRender {

    private EspRender() {
    }

    /** Pushes the same window-pixel pose the HUD modules use, so projected coordinates line up. */
    public static void begin(GuiGraphicsExtractor g) {
        float scale = UiFont.useGuiScale();
        int guiScale = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        g.pose().pushMatrix();
        g.pose().scale(scale / guiScale, scale / guiScale);
    }

    public static void end(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    public static void box(GuiGraphicsExtractor g, Entity entity, int color, boolean filled) {
        AABB bounds = entity.getBoundingBox();
        boxAt(g, new Vec3(bounds.minX, bounds.minY, bounds.minZ),
                new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ), color, filled);
    }

    /** Draws a screen-space box for an axis-aligned world region. */
    public static void boxAt(GuiGraphicsExtractor g, Vec3 min, Vec3 max, int color, boolean filled) {
        Vec3 center = new Vec3((min.x + max.x) / 2, 0, (min.z + max.z) / 2);
        Vec3 feet = Projection.project(new Vec3(center.x, min.y, center.z));
        Vec3 head = Projection.project(new Vec3(center.x, max.y, center.z));
        if (feet == null || head == null) return;

        float height = (float) Math.abs(feet.y - head.y);
        if (height < 2 || height > 4000) return;
        float width = Math.max(4, height * (float) ((max.x - min.x) / Math.max(0.1, max.y - min.y)));
        float left = (float) feet.x - width / 2;
        float top = (float) Math.min(feet.y, head.y);

        if (filled) {
            Render2D.rect(g, left, top, width, height, (color & 0x00FFFFFF) | 0x30000000);
        }
        Render2D.rect(g, left, top, width, 1, color);
        Render2D.rect(g, left, top + height - 1, width, 1, color);
        Render2D.rect(g, left, top, 1, height, color);
        Render2D.rect(g, left + width - 1, top, 1, height, color);
    }

    public static void label(GuiGraphicsExtractor g, Entity entity, String text, float yOffset, int color) {
        labelAt(g, entity.position().add(0, entity.getBbHeight() + yOffset, 0), text, color);
    }

    public static void labelAt(GuiGraphicsExtractor g, Vec3 world, String text, int color) {
        Vec3 point = Projection.project(world);
        if (point == null) return;
        float width = UiFont.SMALL.width(text);
        Render2D.roundedRect(g, (float) point.x - width / 2 - 3, (float) point.y - 9,
                width + 6, 18, 3, 0xB0121218);
        UiFont.SMALL.drawCentered(g, text, (float) point.x, (float) point.y, color);
    }
}
