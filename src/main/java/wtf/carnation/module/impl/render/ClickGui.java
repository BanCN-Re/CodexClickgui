package wtf.carnation.module.impl.render;

import com.mojang.blaze3d.platform.InputConstants;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.ui.codex.CodexScreen;
import wtf.carnation.ui.codex.ZCodeScreen;
import wtf.carnation.ui.naven.NavenScreen;

public class ClickGui extends Module {
    private final ModeValue style = new ModeValue("Style",
            new String[]{"Codex", "ZCode", "Naven"}, "Codex");

    public ClickGui() {
        super("ClickGui", Category.RENDER, "打开 Codex / ZCode / Naven 风格的模块面板");
        setKey(InputConstants.KEY_RSHIFT);
        addValues(style);
    }

    @Override
    public boolean isMomentaryAction() {
        return true;
    }

    @Override
    protected void onEnable() {
        String mode = style.get();
        mc.execute(() -> mc.gui.setScreen(switch (mode) {
            case "ZCode" -> new ZCodeScreen();
            case "Naven" -> new NavenScreen();
            default -> new CodexScreen();
        }));
        setState(false);
    }
}
