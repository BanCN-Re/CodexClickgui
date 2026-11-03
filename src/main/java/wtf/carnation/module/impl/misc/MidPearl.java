package wtf.carnation.module.impl.misc;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

/** Throws an ender pearl when the pick-item key is pressed. */
public class MidPearl extends Module {
    private boolean doWork;
    private boolean switchBack;
    private int lastSlot = -1;

    public MidPearl() {
        super("MidPearl", Category.MISC, "按取物键丢末影珍珠");
    }

    @Override
    protected void onEnable() {
        this.lastSlot = -1;
        this.doWork = false;
        this.switchBack = false;
    }

    @Override
    protected void onDisable() {
        if (this.switchBack && mc.player != null && this.lastSlot >= 0) {
            mc.player.getInventory().setSelectedSlot(this.lastSlot);
        }
        this.switchBack = false;
        this.doWork = false;
        this.lastSlot = -1;
    }

    @EventTarget
    public void onRunTick(RunTickEvent event) {
        if (!event.isPre()) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (mc.options.keyPickItem.isDown()) {
            this.doWork = true;
            this.switchBack = false;
        }

        if (this.doWork) {
            int pearlSlot = findPearlSlot();
            if (pearlSlot != -1) {
                this.lastSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(pearlSlot);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                mc.player.swing(InteractionHand.MAIN_HAND,
                        net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                this.doWork = false;
                this.switchBack = true;
            }
        }

        if (this.switchBack) {
            if (this.lastSlot >= 0) mc.player.getInventory().setSelectedSlot(this.lastSlot);
            this.switchBack = false;
        }
    }

    private int findPearlSlot() {
        if (mc.player == null) return -1;
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(Items.ENDER_PEARL)) return slot;
        }
        return -1;
    }
}
