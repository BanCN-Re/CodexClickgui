package wtf.carnation.module.impl.misc;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

/**
 * Port of Naven's {@code MidPearl}.
 *
 * <p>Naven forced the carried-item packet through MultiPlayerGameModeAccessor;
 * 26.3 sends it from the inventory tick, so the swap is enough here.</p>
 */
public class MidPearl extends Module {
    private boolean doWork;
    private boolean switchBack;
    private int lastSlot = -1;

    public MidPearl() {
        super("MidPearl", Category.MISC, "Middle-click to throw an ender pearl");
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
    public void onTick(RunTickEvent event) {
        if (!event.isPre() || mc.player == null || mc.level == null || mc.gameMode == null
                || !mc.player.isAlive()) {
            return;
        }

        if (mc.options.keyPickItem.isDown()) {
            this.doWork = true;
            this.switchBack = false;
        }

        if (this.doWork) {
            int pearlSlot = this.findPearlSlot();
            if (pearlSlot != -1) {
                this.lastSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(pearlSlot);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
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
        for (int i = 0; i <= 8; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == Items.ENDER_PEARL) return i;
        }
        return -1;
    }
}
