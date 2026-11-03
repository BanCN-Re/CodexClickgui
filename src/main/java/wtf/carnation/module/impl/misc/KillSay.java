package wtf.carnation.module.impl.misc;

import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Sends a message when a player you hit leaves the game. */
public class KillSay extends Module {
    private final NumberValue delay = new NumberValue("Delay", 6000, 0, 15000, 100);
    private final ModeValue prefix = new ModeValue("Prefix", new String[]{"None", "@", "!", "/shout "}, "None");
    private final ModeValue style = new ModeValue("Style",
            new String[]{"%s died", "gg %s", "nice %s"}, "%s died");

    private final Queue<String> messages = new ConcurrentLinkedQueue<>();
    private long lastSend;

    public KillSay() {
        super("KillSay", Category.MISC, "击杀后自动发送消息");
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null) return;
        if (mc.player.tickCount < 10) {
            this.messages.clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (this.messages.isEmpty() || now - this.lastSend < this.delay.get()) return;
        String message = this.messages.poll();
        if (message == null) return;
        if (mc.player.connection != null) mc.player.connection.sendChat(message);
        this.lastSend = now;
    }

    /** Called by the aura module when a player is hit. */
    public static void queueKill(String playerName) {
        KillSay module = ModuleAccess.killSay();
        if (module == null) return;
        String prefixText = module.prefix.is("None") ? "" : module.prefix.get();
        module.messages.offer(prefixText + module.style.get().replace("%s", playerName));
    }

    /** Small accessor so the aura can reach the module without a hard dependency. */
    static final class ModuleAccess {
        private static KillSay instance;

        static KillSay killSay() {
            if (instance == null) {
                instance = wtf.carnation.Client.instance.getModuleManager().getModule(KillSay.class);
            }
            return instance != null && instance.isEnabled() ? instance : null;
        }
    }
}
