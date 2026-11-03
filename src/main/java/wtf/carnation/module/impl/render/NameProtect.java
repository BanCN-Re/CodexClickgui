package wtf.carnation.module.impl.render;

import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.ModeValue;

public class NameProtect extends Module {
    private final ModeValue alias = new ModeValue("Alias",
            new String[]{"Player", "You", "Anonymous", "Me", "User"}, "Player");

    public NameProtect() {
        super("NameProtect", Category.RENDER, "聊天里把自己的名字替换成别名");
    }

    public String alias() {
        return alias.get();
    }
}
