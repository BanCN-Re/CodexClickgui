package wtf.carnation.module.impl.render;

import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;

/** Port of Naven's {@code Scoreboard}. */
public class Scoreboard extends Module {
    public final BoolValue hideScore = new BoolValue("Hide Red Score", true);
    public final NumberValue down = new NumberValue("Down", 120.0, 0.0, 300.0, 1.0);

    public Scoreboard() {
        super("Scoreboard", Category.RENDER, "Modifies the scoreboard");
    }
}
