package wtf.carnation.script;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.UpdateEvent;
import wtf.carnation.event.impl.input.KeyEvent;
import wtf.carnation.event.impl.network.ChatEvent;
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
    private volatile boolean failed;

    LuaModule(Path file, String name, Category category, String description,
              Map<String, LuaValue> callbacks, List<Value<?>> values) {
        super(name, category, description);
        this.file = file;
        this.callbacks = Map.copyOf(callbacks);
        addValues(values.toArray(new Value<?>[0]));
        LuaPacketDispatcher.INSTANCE.register(this);
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

    @EventTarget
    public void onChat(ChatEvent event) {
        LuaValue result = invokeResult("onChat", LuaValue.valueOf(event.getMessage()));
        if (result != null && result.isboolean() && !result.toboolean()) {
            event.setCancelled(true);
        }
    }

    /**
     * Called by {@link LuaPacketDispatcher} from the {send, receive} hook.
     * Returns {@code true} when the script set {@code event.cancel = true}.
     */
    public boolean dispatchPacket(String callback, LuaTable event) {
        LuaValue function = callbacks.get(callback);
        if (function == null || function.isnil() || !isEnabled()) return false;
        String packet = event.get("packet").tojstring();
        if (!matchesFilter(callback, packet)) return false;
        invokeResult(callback, event);
        LuaValue cancel = event.get("cancel");
        return !cancel.isnil() && cancel.toboolean();
    }

    /** Lets the dispatcher skip building a table when no script subscribed to this packet. */
    public boolean wantsPacket(String packetName, boolean outbound) {
        String callback = outbound ? "onPacketSend" : "onPacketReceive";
        if (!isEnabled() || callbacks.get(callback) == null) return false;
        return matchesFilter(callback, packetName);
    }

    private boolean matchesFilter(String callback, String packetName) {
        LuaValue filter = callbacks.get(callback + "Filter");
        if (filter == null || !filter.istable()) return true;
        for (int i = 1; i <= filter.length(); i++) {
            if (filter.get(i).tojstring().equalsIgnoreCase(packetName)) return true;
        }
        return false;
    }

    private void invoke(String callback, LuaValue... arguments) {
        invokeResult(callback, arguments);
    }

    private synchronized LuaValue invokeResult(String callback, LuaValue... arguments) {
        if (failed) return null;
        LuaValue function = callbacks.get(callback);
        if (function == null || function.isnil()) return null;
        try {
            return function.invoke(arguments).arg1();
        } catch (RuntimeException e) {
            failed = true;
            Client.logger.error("Lua module {} in {} failed in {}", getName(), file, callback, e);
            ChatUtil.addChatMessage("Lua " + getName() + " 已关闭: " + e.getMessage());
            setState(false);
            return null;
        }
    }
}
