package wtf.carnation.module.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;

/** Keeps WASD/space alive while a screen (inventory, container) is open. */
public class InventoryMove extends Module {
    private final BoolValue sprint = new BoolValue("Sprint", false);

    public InventoryMove() {
        super("InventoryMove", Category.MOVEMENT, "打开背包或容器时仍可用 WASD 移动");
    }

    @EventTarget
    public void onRunTick(RunTickEvent event) {
        if (!event.isPre() || mc.player == null) return;
        if (mc.gui.screen() == null) return;
        force(mc.options.keyUp, InputConstants.KEY_W);
        force(mc.options.keyDown, InputConstants.KEY_S);
        force(mc.options.keyLeft, InputConstants.KEY_A);
        force(mc.options.keyRight, InputConstants.KEY_D);
        force(mc.options.keyJump, InputConstants.KEY_SPACE);
        if (sprint.get()) {
            force(mc.options.keySprint, InputConstants.KEY_LCONTROL);
        }
    }

    @Override
    protected void onDisable() {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
    }

    private static void force(KeyMapping key, int code) {
        key.setDown(InputConstants.isKeyDown(code));
    }
}
