package wtf.carnation.module.impl.misc;

import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.Value;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.TimeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Port of Naven's {@code Spammer}. */
public class Spammer extends Module {
    private final Random random = new Random();
    private final NumberValue delay = new NumberValue("Delay", 6000.0, 0.0, 15000.0, 100.0);
    private final ModeValue prefix = new ModeValue("Prefix", new String[]{"None", "@", "/shout "}, "None");
    private final List<BoolValue> values = new ArrayList<>();
    private final TimeHelper timer = new TimeHelper();

    public Spammer() {
        super("Spammer", Category.MISC, "Spam chat!");
    }

    @EventTarget
    public void onMotion(RunTickEvent e) {
        if (e.isPre() || mc.player == null || !this.timer.delay(this.delay.get())) return;

        String messagePrefix = this.prefix.is("None") ? "" : this.prefix.get();
        List<String> styles = this.values.stream().filter(BoolValue::get).map(Value::getName).toList();
        if (styles.isEmpty()) return;

        String style = styles.get(this.random.nextInt(styles.size()));
        if (mc.player.connection != null) mc.player.connection.sendChat(messagePrefix + style);
        this.timer.reset();
    }

    public List<BoolValue> getSpamValues() {
        return this.values;
    }
}
