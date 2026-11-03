package wtf.carnation.module.impl.render;

import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.ModeValue;

public class ScoreboardSpoof extends Module {
    private final ModeValue title = new ModeValue("Title",
            new String[]{"Naven", "Carnation", "Server", "Lobby", "Scoreboard"}, "Naven");

    public ScoreboardSpoof() {
        super("ScoreboardSpoof", Category.RENDER, "替换侧边栏计分板标题");
    }

    public String title() {
        return title.get();
    }
}
