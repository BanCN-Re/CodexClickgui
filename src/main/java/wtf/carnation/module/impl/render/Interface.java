package wtf.carnation.module.impl.render;

import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;

import java.util.LinkedHashMap;
import java.util.Map;

/** HUD master switch: keeps the listed HUD modules in sync with its own toggles. */
public class Interface extends Module {
    private final BoolValue watermark = new BoolValue("Watermark", true);
    private final BoolValue moduleList = new BoolValue("ModuleList", true);
    private final BoolValue hotKeys = new BoolValue("HotKeys", false);
    private final BoolValue compass = new BoolValue("Compass", false);
    private final BoolValue scoreboard = new BoolValue("Scoreboard", false);

    private final Map<String, Boolean> applied = new LinkedHashMap<>();

    public Interface() {
        super("Interface", Category.RENDER, "统一控制 HUD 元素的显示");
    }

    @Override
    protected void onEnable() {
        applied.clear();
        sync();
    }

    @EventTarget
    public void onRunTick(RunTickEvent event) {
        if (event.isPre()) sync();
    }

    private void sync() {
        if (Client.instance == null) return;
        apply("Watermark", watermark.get());
        apply("ModuleList", moduleList.get());
        apply("HotKeys", hotKeys.get());
        apply("Compass", compass.get());
        apply("Scoreboard", scoreboard.get());
    }

    private void apply(String name, boolean wanted) {
        Boolean last = applied.get(name);
        if (last != null && last == wanted) return;
        Module module = Client.instance.getModuleManager().getModuleByName(name);
        if (module != null && module.isEnabled() != wanted) module.setState(wanted);
        applied.put(name, wanted);
    }
}
