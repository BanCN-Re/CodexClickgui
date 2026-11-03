package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import wtf.carnation.Client;
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

/** Right-edge list of enabled modules, sorted by name. */
public class ModuleList extends Module {
    private final BoolValue background = new BoolValue("Background", true);
    private final BoolValue info = new BoolValue("Info", true);

    public ModuleList() {
        super("ModuleList", Category.RENDER, "右侧显示当前启用的模块列表");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || Client.instance == null) return;
        List<Module> modules = new ArrayList<>();
        for (Module module : Client.instance.getModuleManager().getModules()) {
            if (module.isEnabled() && module != this && !module.getName().equals("ClickGui")) {
                modules.add(module);
            }
        }
        modules.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));

        GuiGraphicsExtractor g = event.graphics();
        float scale = UiFont.useGuiScale();
        int guiScale = mc.getWindow().getGuiScale();
        float screenW = mc.getWindow().getWidth();
        float screenH = mc.getWindow().getHeight();
        float rowH = 22;

        g.pose().pushMatrix();
        g.pose().scale(scale / guiScale, scale / guiScale);

        float y = 8;
        for (Module module : modules) {
            String label = module.getName();
            String suffix = module.getSuffix();
            if (info.get() && suffix != null && !suffix.isBlank()) {
                label += " " + suffix;
            }
            float width = UiFont.SMALL.width(label) + 14;
            float x = screenW - width - 6;
            if (background.get()) {
                Render2D.roundedRect(g, x, y, width, rowH, 4, 0xB0121218);
            }
            Render2D.roundedRect(g, x, y, 2, rowH, 1, 0xFF7C5CFF);
            UiFont.SMALL.drawMiddle(g, label, x + 9, y + rowH / 2, 0xFFEDEDF5);
            y += rowH + 2;
            if (y > screenH) break;
        }
        g.pose().popMatrix();
    }
}
