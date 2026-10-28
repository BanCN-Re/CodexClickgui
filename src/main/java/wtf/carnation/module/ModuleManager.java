package wtf.carnation.module;

import lombok.Getter;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.input.KeyEvent;
import wtf.carnation.module.impl.misc.Test;
import wtf.carnation.module.impl.movement.Sprint;
import wtf.carnation.module.impl.render.ClickGui;
import wtf.carnation.module.value.Value;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    @Getter
    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        registerModules(new Sprint(), new Test(), new ClickGui());
    }

    private void registerModules(Module... modules) {
        for (Module module : modules) {
            registerModule(module);
        }
    }

    public void registerModule(Module module) {
        if (getModuleByName(module.getName()) != null) {
            throw new IllegalArgumentException("Module already registered: " + module.getName());
        }
        // Every Value field becomes a setting, in declaration order.
        for (Field field : module.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                if (field.get(module) instanceof Value<?> value) module.addValues(value);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        modules.add(module);
    }

    public void unregisterModule(Module module) {
        if (module == null) return;
        if (module.isEnabled()) module.setState(false);
        modules.remove(module);
    }

    public Module getModuleByName(String name) {
        if (name == null) return null;
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(name)) return module;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> type) {
        for (Module module : modules) {
            if (type.isInstance(module)) return (T) module;
        }
        return null;
    }

    public List<Module> getModulesByCategory(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.getCategory() == category) result.add(module);
        }
        return result;
    }

    @EventTarget
    public void onKey(KeyEvent e) {
        if (!e.state()) return;
        for (Module module : modules) {
            if (module.getKey() != 0 && module.getKey() == e.key()) {
                module.toggle();
            }
        }
    }
}
