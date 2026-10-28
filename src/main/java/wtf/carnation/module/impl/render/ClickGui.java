package wtf.carnation.module.impl.render;

import com.mojang.blaze3d.platform.InputConstants;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.ui.codex.CodexScreen;

public class ClickGui extends Module {
    public ClickGui() {
        super("ClickGui", Category.RENDER, "打开 Codex 风格的模块面板");
        setKey(InputConstants.KEY_RSHIFT);
    }

    @Override
    public boolean isMomentaryAction() {
        return true;
    }

    @Override
    protected void onEnable() {
        mc.execute(() -> mc.gui.setScreen(new CodexScreen()));
        setState(false);
    }
}
