package wtf.carnation.module.impl.render;

import com.mojang.blaze3d.platform.InputConstants;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.ui.codex.CodexScreen;
import wtf.carnation.ui.codex.ZCodeScreen;

public class ClickGui extends Module {
    private final ModeValue style = new ModeValue("Style", new String[]{"Codex", "ZCode"}, "Codex");

    public ClickGui() {
        super("ClickGui", Category.RENDER, "打开 Codex / ZCode 风格的模块面板");
        setKey(InputConstants.KEY_RSHIFT);
        addValues(style);
    }

    @Override
    public boolean isMomentaryAction() {
        return true;
    }

    @Override
    protected void onEnable() {
        boolean zcode = style.is("ZCode");
        mc.execute(() -> mc.gui.setScreen(zcode ? new ZCodeScreen() : new CodexScreen()));
        setState(false);
    }
}
