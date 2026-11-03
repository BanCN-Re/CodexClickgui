package wtf.carnation.module.impl.render;

import net.minecraft.resources.Identifier;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.ModeValue;

/** Real post effect: the frame is colour-graded by the vanilla PostChain pipeline. */
public class PostProcess extends Module {
    private final ModeValue mode = new ModeValue("Mode",
            new String[]{"Warm", "Cool", "Noir", "Green"}, "Warm");

    public PostProcess() {
        super("PostProcess", Category.RENDER, "全屏颜色分级（PostChain 后处理）");
    }

    public Identifier effectId() {
        return switch (mode.get()) {
            case "Cool" -> Identifier.fromNamespaceAndPath("carnation", "color_cool");
            case "Noir" -> Identifier.fromNamespaceAndPath("carnation", "color_noir");
            case "Green" -> Identifier.fromNamespaceAndPath("carnation", "color_green");
            default -> Identifier.fromNamespaceAndPath("carnation", "color_warm");
        };
    }
}
