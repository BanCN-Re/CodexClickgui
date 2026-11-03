package wtf.carnation.module.impl.render;

import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;

public class TimeChanger extends Module {
    private final ModeValue preset = new ModeValue("Preset",
            new String[]{"Custom", "Day", "Noon", "Sunset", "Night", "Midnight"}, "Day");
    private final NumberValue time = new NumberValue("Time", 1000, 0, 24000, 500);

    public TimeChanger() {
        super("TimeChanger", Category.RENDER, "修改客户端看到的世界时间");
    }

    /** Clock ticks used by the Level hook. */
    public long time() {
        return switch (preset.get()) {
            case "Noon" -> 6000;
            case "Sunset" -> 12000;
            case "Night" -> 13000;
            case "Midnight" -> 18000;
            case "Day" -> 1000;
            default -> time.get().longValue();
        };
    }

    @Override
    protected void onEnable() {
        // keep the custom slider in sync with a preset selection
    }
}
