package wtf.carnation.module.impl.render;

import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

public class ItemPhysics extends Module {
    public ItemPhysics() {
        super("ItemPhysics", Category.RENDER, "掉落物不再旋转，静止平铺在地面");
    }
}
