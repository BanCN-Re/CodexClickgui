package wtf.carnation.module.impl.misc;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.UpdateEvent;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Icons;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.movement.Sprint;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.ui.codex.CodexTheme;
import wtf.carnation.utils.ChatUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Exercises the client end to end: every setting type, the tick event, chat output and a HUD card
 * drawn through the renderpearl shape pipeline.
 */
public class Test extends Module {
    private final ModeValue mode = new ModeValue("Mode", new String[]{"Info", "Minimal"}, "Info");
    private final BoolValue hud = new BoolValue("HUD", true);
    private final BoolValue coords = new BoolValue("Coords", true, () -> mode.is("Info"));
    private final NumberValue scale = new NumberValue("Scale", 1.0, 0.75, 2.0, 0.25, hud::get);
    private final NumberValue x = new NumberValue("X", 6, 0, 400, 1, hud::get);
    private final NumberValue y = new NumberValue("Y", 6, 0, 300, 1, hud::get);
    private final BoolValue chat = new BoolValue("Chat Message", true);

    private int ticks;

    public Test() {
        super("Test", Category.MISC, "测试模块：设置项、Tick 事件、聊天输出与 HUD 渲染");
    }

    @Override
    protected void onEnable() {
        ticks = 0;
        if (chat.get()) {
            ChatUtil.addChatMessage("Test enabled: events, settings and the renderpearl HUD pipeline are running.");
        }
    }

    @Override
    protected void onDisable() {
        if (chat.get()) ChatUtil.addChatMessage("Test disabled after " + ticks + " ticks.");
    }

    @EventTarget
    public void onUpdate(UpdateEvent e) {
        ticks++;
    }

    @EventTarget
    public void onRender2D(Render2DEvent e) {
        if (!hud.get() || mc.player == null) return;
        GuiGraphicsExtractor g = e.graphics();

        List<String> lines = new ArrayList<>();
        if (mode.is("Info")) {
            lines.add(mc.getFps() + " FPS");
            if (coords.get()) {
                lines.add(String.format("XYZ %.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            }
            Sprint sprint = Client.instance.getModuleManager().getModule(Sprint.class);
            lines.add("Sprint " + (sprint != null && sprint.isEnabled() ? "on" : "off") + "  ·  " + ticks + " ticks");
        }

        // Lay the card out in screen pixels times a baked font scale, so the TrueType text stays sharp.
        float pixelScale = UiFont.useScale(scale.getFloat());
        int guiScale = mc.getWindow().getGuiScale();
        String title = Client.name + " " + Client.ver;
        float width = UiFont.SEMIBOLD.width(title) + 60;
        for (String line : lines) width = Math.max(width, UiFont.SMALL.width(line) + 36);
        float height = 46 + lines.size() * 22 + (lines.isEmpty() ? 0 : 6);

        g.pose().pushMatrix();
        g.pose().translate(x.getFloat(), y.getFloat());
        g.pose().scale(pixelScale / guiScale, pixelScale / guiScale);

        Render2D.shadow(g, 0, 0, width, height, 14, 22, 6, 0x80000000);
        Render2D.roundedRect(g, 0, 0, width, height, 14, CodexTheme.HUD);
        Render2D.roundedOutline(g, 0, 0, width, height, 14, 1, CodexTheme.CARD_BORDER);
        UiFont.ICON_S.icon(g, Icons.TERMINAL, 26, 24, CodexTheme.ORANGE);
        UiFont.SEMIBOLD.drawMiddle(g, title, 42, 24, CodexTheme.TEXT);
        if (!lines.isEmpty()) Render2D.rect(g, 16, 44, width - 32, 1, CodexTheme.HEADER_DIVIDER);
        float lineY = 62;
        for (String line : lines) {
            UiFont.SMALL.drawMiddle(g, line, 18, lineY, CodexTheme.TEXT_SIDEBAR);
            lineY += 22;
        }
        g.pose().popMatrix();
    }
}
