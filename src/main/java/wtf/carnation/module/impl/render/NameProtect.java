package wtf.carnation.module.impl.render;

import net.minecraft.network.chat.Component;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.utils.TextReplacer;

/**
 * Port of Naven's {@code NameProtect}.
 *
 * <p>The text-rewriting hooks live in the chat/tab mixins; this module keeps the name state and
 * the replacement helpers the rest of the client calls.</p>
 */
public class NameProtect extends Module {
    public static NameProtect instance;

    private static final String REPLACEMENT_TEXT = "§dHidden§f";
    private static final String REPLACEMENT_PLAIN = "Hidden";
    private static String lastPlayerName;

    public NameProtect() {
        super("NameProtect", Category.RENDER, "Protect your name");
        instance = this;
    }

    @EventTarget
    public void onTick(RunTickEvent event) {
        if (event.isPre()) this.refresh();
    }

    @Override
    protected void onEnable() {
        this.refresh();
    }

    @Override
    protected void onDisable() {
        lastPlayerName = null;
        TextReplacer.setName(null, null, null);
    }

    private void refresh() {
        if (!this.isEnabled() || mc.player == null) return;
        String playerName = mc.player.getName().getString();
        if (playerName.isEmpty() || playerName.equals(lastPlayerName)) return;
        lastPlayerName = playerName;
        if (REPLACEMENT_PLAIN.contains(playerName)) {
            TextReplacer.setName(null, null, null);
            return;
        }
        TextReplacer.setName(playerName, REPLACEMENT_PLAIN, REPLACEMENT_TEXT);
    }

    public static String getName(String string) {
        return TextReplacer.replace(string);
    }

    /** Used by MixinChatComponent. */
    public String alias() {
        return REPLACEMENT_PLAIN;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled() && !TextReplacer.isEmpty();
    }

    public static Component hookNameTag(net.minecraft.world.entity.Entity entity, Component in) {
        if (in == null || instance == null || !instance.isEnabled()) return in;
        if (mc != null && mc.player != null && entity == mc.player) {
            return Component.literal(REPLACEMENT_TEXT);
        }
        String flat = in.getString();
        if (!TextReplacer.contains(flat)) return in;
        return Component.literal(TextReplacer.replace(flat)).setStyle(in.getStyle());
    }

    public static Component rewriteComponent(Component in) {
        if (in == null || TextReplacer.isEmpty() || !TextReplacer.contains(in.getString())) return in;
        return Component.literal(TextReplacer.replace(in.getString())).setStyle(in.getStyle());
    }
}
