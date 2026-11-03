package wtf.carnation.module.impl.render;

import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;

/** Master switch that disables the common screen effects. */
public class NoRender extends Module {
    private final BoolValue fire = new BoolValue("Fire", true);
    private final BoolValue hurtCam = new BoolValue("HurtCam", true);
    private final BoolValue blindness = new BoolValue("Blindness", true);
    private final BoolValue nausea = new BoolValue("Nausea", true);

    private Double previousTilt;
    private Double previousScreen;
    private boolean applied;

    public NoRender() {
        super("NoRender", Category.RENDER, "一键关闭火焰、受伤抖动、失明与反胃");
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        if (mc.options != null && applied) {
            if (previousTilt != null) mc.options.damageTiltStrength().set(previousTilt);
            if (previousScreen != null) mc.options.screenEffectScale().set(previousScreen);
        }
        applied = false;
        set("AntiBlindness", false);
        set("AntiNausea", false);
    }

    @EventTarget
    public void onRunTick(RunTickEvent event) {
        if (event.isPre()) apply();
    }

    private void apply() {
        if (mc.options == null) return;
        if (!applied) {
            previousTilt = mc.options.damageTiltStrength().get();
            previousScreen = mc.options.screenEffectScale().get();
            applied = true;
        }
        if (hurtCam.get()) mc.options.damageTiltStrength().set(0.0);
        if (fire.get()) mc.options.screenEffectScale().set(0.0);
        set("AntiBlindness", blindness.get());
        set("AntiNausea", nausea.get());
    }

    private static void set(String name, boolean enabled) {
        if (Client.instance == null) return;
        Module module = Client.instance.getModuleManager().getModuleByName(name);
        if (module != null && module.isEnabled() != enabled) module.setState(enabled);
    }
}
