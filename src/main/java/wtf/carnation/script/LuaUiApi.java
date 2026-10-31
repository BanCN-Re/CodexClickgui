package wtf.carnation.script;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;

public final class LuaUiApi {
    public void rect(GuiGraphicsExtractor g, double x, double y, double width, double height, int color) {
        Render2D.rect(g, (float) x, (float) y, (float) width, (float) height, color);
    }

    public void roundedRect(GuiGraphicsExtractor g, double x, double y, double width, double height,
                            double radius, int color) {
        Render2D.roundedRect(g, (float) x, (float) y, (float) width, (float) height,
                (float) radius, color);
    }

    public void roundedOutline(GuiGraphicsExtractor g, double x, double y, double width, double height,
                               double radius, double thickness, int color) {
        Render2D.roundedOutline(g, (float) x, (float) y, (float) width, (float) height,
                (float) radius, (float) thickness, color);
    }

    public void shadow(GuiGraphicsExtractor g, double x, double y, double width, double height,
                       double radius, double blur, int color) {
        Render2D.shadow(g, (float) x, (float) y, (float) width, (float) height,
                (float) radius, (float) blur, color);
    }

    public void circle(GuiGraphicsExtractor g, double centerX, double centerY, double radius, int color) {
        Render2D.circle(g, (float) centerX, (float) centerY, (float) radius, color);
    }

    public void circleOutline(GuiGraphicsExtractor g, double centerX, double centerY, double radius,
                              double thickness, int color) {
        Render2D.circleOutline(g, (float) centerX, (float) centerY, (float) radius,
                (float) thickness, color);
    }

    public void line(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2,
                     double width, int color) {
        Render2D.line(g, (float) x1, (float) y1, (float) x2, (float) y2, (float) width, color);
    }

    public void text(GuiGraphicsExtractor g, String text, double x, double y, int color) {
        UiFont.BODY.draw(g, text, (float) x, (float) y, color);
    }

    public void textMiddle(GuiGraphicsExtractor g, String text, double x, double centerY, int color) {
        UiFont.BODY.drawMiddle(g, text, (float) x, (float) centerY, color);
    }

    public void textCentered(GuiGraphicsExtractor g, String text, double centerX, double centerY, int color) {
        UiFont.BODY.drawCentered(g, text, (float) centerX, (float) centerY, color);
    }

    public void textRight(GuiGraphicsExtractor g, String text, double right, double centerY, int color) {
        UiFont.BODY.drawRight(g, text, (float) right, (float) centerY, color);
    }

    public void title(GuiGraphicsExtractor g, String text, double x, double y, int color) {
        UiFont.TITLE.draw(g, text, (float) x, (float) y, color);
    }

    public void small(GuiGraphicsExtractor g, String text, double x, double y, int color) {
        UiFont.SMALL.draw(g, text, (float) x, (float) y, color);
    }

    public void semibold(GuiGraphicsExtractor g, String text, double x, double y, int color) {
        UiFont.SEMIBOLD.draw(g, text, (float) x, (float) y, color);
    }

    public void icon(GuiGraphicsExtractor g, String name, double centerX, double centerY, int color) {
        UiFont.ICON_M.icon(g, iconGlyph(name), (float) centerX, (float) centerY, color);
    }

    public double textWidth(String text) {
        return UiFont.BODY.width(text);
    }

    public double smallWidth(String text) {
        return UiFont.SMALL.width(text);
    }

    public double semiboldWidth(String text) {
        return UiFont.SEMIBOLD.width(text);
    }

    public String ellipsize(String text, double maxWidth) {
        return UiFont.BODY.ellipsize(text, (float) maxWidth);
    }

    private static char iconGlyph(String name) {
        if (name == null) return wtf.carnation.graphics.Icons.HOUSE;
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "combat", "swords" -> wtf.carnation.graphics.Icons.SWORDS;
            case "movement", "footprints" -> wtf.carnation.graphics.Icons.FOOTPRINTS;
            case "player", "user" -> wtf.carnation.graphics.Icons.USER;
            case "world", "globe" -> wtf.carnation.graphics.Icons.GLOBE;
            case "render", "eye" -> wtf.carnation.graphics.Icons.EYE;
            case "search" -> wtf.carnation.graphics.Icons.SEARCH;
            case "settings" -> wtf.carnation.graphics.Icons.SETTINGS;
            case "keyboard" -> wtf.carnation.graphics.Icons.KEYBOARD;
            case "terminal" -> wtf.carnation.graphics.Icons.TERMINAL;
            case "check" -> wtf.carnation.graphics.Icons.CIRCLE_CHECK;
            case "alert" -> wtf.carnation.graphics.Icons.CIRCLE_ALERT;
            case "plus" -> wtf.carnation.graphics.Icons.PLUS;
            case "x", "close" -> wtf.carnation.graphics.Icons.X;
            case "left" -> wtf.carnation.graphics.Icons.ARROW_LEFT;
            case "right" -> wtf.carnation.graphics.Icons.ARROW_RIGHT;
            case "up" -> wtf.carnation.graphics.Icons.ARROW_UP;
            case "message" -> wtf.carnation.graphics.Icons.MESSAGE_CIRCLE;
            default -> wtf.carnation.graphics.Icons.ELLIPSIS;
        };
    }
}
