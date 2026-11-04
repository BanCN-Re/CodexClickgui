package wtf.carnation.module.impl.render;

import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;

/**
 * Port of Naven's {@code NoRender}.
 *
 * <p>The gate lives in {@code MixinHud}: {@code Hud.extractEffects} is cancelled while the module
 * is on and {@code disableEffects} is set, which drops the status-effect icon column from the HUD.
 * Naven declares no other behaviour for this module; the fire/hurt-camera/status-effect switches
 * that used to live here belong to {@link LowFire}, {@link NoHurtCam}, {@link AntiBlindness} and
 * {@link AntiNausea}, which each own their own hook.</p>
 */
public class NoRender extends Module {
    public final BoolValue disableEffects = new BoolValue("Disable Effects", true);

    public NoRender() {
        super("NoRender", Category.RENDER, "Disables rendering");
    }
}
