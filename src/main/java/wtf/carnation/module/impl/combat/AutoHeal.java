package wtf.carnation.module.impl.combat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;

/** Uses a soup or a golden apple when the health drops below the threshold. */
public class AutoHeal extends Module {
    private final NumberValue delay = new NumberValue("Delay", 500, 300, 1000, 50);
    private final NumberValue health = new NumberValue("Health Percent", 0.5, 0.0, 1.0, 0.05);
    private final ModeValue mode = new ModeValue("Mode", new String[]{"Soup", "Head"}, "Soup");

    private long lastUse;
    private int restoreSlot = -1;

    public AutoHeal() {
        super("AutoHeal", Category.COMBAT, "血量低于阈值时自动喝汤或金苹果");
    }

    @Override
    protected void onDisable() {
        restore();
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null) return;
        if (restoreSlot >= 0) {
            restore();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastUse < delay.get()) return;
        if (mc.player.getHealth() / mc.player.getMaxHealth() >= health.get()) return;

        int slot = findSlot();
        if (slot < 0) return;
        lastUse = now;
        restoreSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.gameMode != null) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        }
    }

    private int findSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            if (mode.is("Soup") && stack.is(Items.MUSHROOM_STEW)) return slot;
            if (mode.is("Head") && stack.is(Items.GOLDEN_APPLE)) return slot;
        }
        return -1;
    }

    private void restore() {
        if (restoreSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(restoreSlot);
        }
        restoreSlot = -1;
    }
}
