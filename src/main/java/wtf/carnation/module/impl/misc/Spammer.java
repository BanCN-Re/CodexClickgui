package wtf.carnation.module.impl.misc;

import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;

/** Spams chat with a configurable message. */
public class Spammer extends Module {
    private final NumberValue delay = new NumberValue("Delay", 6000, 0, 15000, 100);
    private final ModeValue prefix = new ModeValue("Prefix", new String[]{"None", "@", "/shout "}, "None");
    private final ModeValue message = new ModeValue("Message",
            new String[]{"hello", "gg", "nice"}, "hello");

    private long lastSend;

    public Spammer() {
        super("Spammer", Category.MISC, "定时刷屏聊天");
    }

    @EventTarget
    public void onRunTick(RunTickEvent event) {
        if (!event.isPre() || mc.player == null || mc.player.connection == null) return;
        long now = System.currentTimeMillis();
        if (now - this.lastSend < this.delay.get()) return;
        String prefixText = this.prefix.is("None") ? "" : this.prefix.get();
        mc.player.connection.sendChat(prefixText + this.message.get());
        this.lastSend = now;
    }
}
