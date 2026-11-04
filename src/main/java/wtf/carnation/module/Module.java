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
            // Like Naven: every toggle gets a toast, except the ClickGUI's own momentary action.
            // The HUD's Notification switch decides whether the toasts are drawn.
            if (!this.isMomentaryAction() && Client.instance != null) {
                Client.instance.getNotificationManager().addNotification(
                        new wtf.carnation.notification.Notification(
                                enabled ? wtf.carnation.notification.NotificationLevel.SUCCESS
                                        : wtf.carnation.notification.NotificationLevel.ERROR,
                                this.name + (enabled ? " Enabled!" : " Disabled!"), 3000L));
                // The HUD caches its sorted module list; a toggle re-sorts it.
                arrayListDirty = true;
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    /** Set when a module toggles, so the HUD's ArrayList rebuilds its sort order. */
    public static boolean arrayListDirty = true;

    /**
     * Naven's {@code Module.getPrettyName}: CamelCase split into words.
     *
     * <p>{@code AutoClicker} becomes {@code Auto Clicker}, so the ArrayList can show spaced names
     * instead of the identifiers the commands use.</p>
     */
    public String getPrettyName() {
        if (this.name.isEmpty()) return this.name;
        StringBuilder builder = new StringBuilder(this.name.length() + 4);
        char[] chars = this.name.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            builder.append(chars[i]);
            if (Character.isLowerCase(chars[i]) && Character.isUpperCase(chars[i + 1])) {
                builder.append(' ');
            }
        }
        builder.append(chars[chars.length - 1]);
        return builder.toString();
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
