package wtf.carnation.module.impl.render;

import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

public class ViewClip extends Module {
    public ViewClip() {
        super("ViewClip", Category.RENDER, "摄像机贴墙时不再被拉近，可直接穿墙观察");
    }
}
