package wtf.carnation.script;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.UpdateEvent;
import wtf.carnation.event.impl.input.KeyEvent;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.Value;
import wtf.carnation.utils.ChatUtil;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class LuaModule extends Module {
    private final Path file;
    private final Map<String, LuaValue> callbacks;
    private boolean failed;

    LuaModule(Path file, String name, Category category, String description,
              Map<String, LuaValue> callbacks, List<Value<?>> values) {
        super(name, category, description);
        this.file = file;
        this.callbacks = Map.copyOf(callbacks);
        addValues(values.toArray(new Value<?>[0]));
    }

    public Path file() {
        return file;
    }

    @Override
    protected void onEnable() {
        invoke("onEnable");
    }

    @Override
    protected void onDisable() {
        invoke("onDisable");
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        invoke("onUpdate");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        invoke("onRender2D", CoerceJavaToLua.coerce(event.graphics()), LuaValue.valueOf(event.partialTicks()));
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        invoke("onKey", LuaValue.valueOf(event.key()), LuaValue.valueOf(event.state()));
    }

    private void invoke(String callback, LuaValue... arguments) {
        if (failed) return;
        LuaValue function = callbacks.get(callback);
        if (function == null || function.isnil()) return;
        try {
            function.invoke(arguments);
        } catch (RuntimeException e) {
            failed = true;
            Client.logger.error("Lua module {} in {} failed in {}", getName(), file, callback, e);
            ChatUtil.addChatMessage("Lua " + getName() + " 已关闭: " + e.getMessage());
            setState(false);
        }
    }
}
