package wtf.carnation.module;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import wtf.carnation.Client;
import wtf.carnation.module.value.Value;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Module {
    protected static final Minecraft mc = Minecraft.getInstance();
    private final String name;
    private final Category category;
    /** One line shown under the module name in the GUI. */
    private String description = "";
    private final List<Value<?>> values = new ArrayList<>();
    private int key;
    private String suffix;
    private boolean enabled;

    public Module(String name, Category category) {
        this.name = name;
        this.category = category;
    }

    public Module(String name, Category category, String description) {
        this(name, category);
        this.description = description;
    }

    /** Momentary modules perform an action and must never be restored as enabled. */
    public boolean isMomentaryAction() {
        return false;
    }

    public void setState(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        try {
            if (enabled) {
                Client.instance.getEventManager().register(this);
                playToggleSound(true);
                onEnable();
            } else {
                Client.instance.getEventManager().unregister(this);
                playToggleSound(false);
                onDisable();
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    public void toggle() {
        setState(!enabled);
    }

    /**
     * Explicitly registers settings. ModuleManager already registers every Value field by
     * reflection, so this is idempotent.
     */
    public void addValues(Value<?>... settings) {
        for (Value<?> value : settings) {
            if (value != null && !values.contains(value)) values.add(value);
        }
    }

    public Value<?> getValue(String name) {
        for (Value<?> value : values) {
            if (value.getName().equalsIgnoreCase(name)) return value;
        }
        return null;
    }

    protected void onEnable() {}

    protected void onDisable() {}

    private void playToggleSound(boolean enabled) {
        if (mc.player != null && !isMomentaryAction()) {
            mc.player.playSound(enabled ? SoundEvents.WOODEN_BUTTON_CLICK_ON : SoundEvents.WOODEN_BUTTON_CLICK_OFF,
                    1.5F, enabled ? 1.3F : 0.8F);
        }
    }
}
