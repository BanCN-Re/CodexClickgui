package wtf.carnation.module.impl.movement;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.UpdateEvent;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;

/**
 * Fireball jump: selects a fire charge, looks down, jumps and holds use for a few ticks,
 * then restores the previous hotbar slot.
 */
public class LongJump extends Module {
    private final NumberValue useTicks = new NumberValue("Use Ticks", 3.0, 1.0, 10.0, 1.0);
    private final BoolValue restoreSlot = new BoolValue("Restore Slot", true);

    private boolean using;
    private int ticks;
    private int previousSlot = -1;

    public LongJump() {
        super("LongJump", Category.MOVEMENT, "使用火焰弹起跳并向前推进");
    }

    @Override
    protected void onDisable() {
        stopUsing();
        previousSlot = -1;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || using) return;
        if (!mc.options.keyJump.isDown() || !mc.player.onGround()) return;
        int slot = findFireCharge();
        if (slot < 0) return;

        previousSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        mc.player.setXRot(90.0F);
        mc.player.jumpFromGround();
        if (mc.gameMode != null) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }
        using = true;
        ticks = 0;
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || !using) return;
        if (++ticks < useTicks.getInt()) return;
        stopUsing();
    }

    private void stopUsing() {
        if (!using) return;
        using = false;
        ticks = 0;
        if (mc.player != null) {
            mc.player.stopUsingItem();
            if (restoreSlot.get() && previousSlot >= 0) {
                mc.player.getInventory().setSelectedSlot(previousSlot);
            }
        }
        previousSlot = -1;
    }

    private int findFireCharge() {
        if (mc.player == null) return -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(Items.FIRE_CHARGE)) return slot;
        }
        return -1;
    }
}
