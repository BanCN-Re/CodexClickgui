package wtf.carnation.module.impl.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;

/** Switches to the best tool for the block that is being mined. */
public class AutoTools extends Module {
    private final BoolValue checkSword = new BoolValue("Check Sword", true);
    private final BoolValue switchBack = new BoolValue("Switch Back", true);

    private int originSlot = -1;

    public AutoTools() {
        super("AutoTools", Category.MISC, "自动切换到最合适的工具");
    }

    @Override
    protected void onDisable() {
        restore();
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (event.isPre()) {
            if (!mc.gameMode.isDestroying()) return;
            if (this.checkSword.get() && mc.player.getMainHandItem().is(
                    net.minecraft.tags.ItemTags.SWORDS)) {
                return;
            }
            if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
            int best = bestTool(hit.getBlockPos());
            if (best != -1 && best != mc.player.getInventory().getSelectedSlot()) {
                this.originSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(best);
            }
        } else if (!mc.gameMode.isDestroying() && this.switchBack.get()) {
            restore();
        }
    }

    private void restore() {
        if (this.originSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(this.originSlot);
        }
        this.originSlot = -1;
    }

    private int bestTool(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();
        int slot = -1;
        float best = 1.0F;
        for (int index = 0; index < 9; index++) {
            ItemStack stack = mc.player.getInventory().getItem(index);
            if (stack.isEmpty() || state.isAir()) continue;
            if (stack.is(net.minecraft.tags.ItemTags.SWORDS)) continue;
            float speed = stack.getDestroySpeed(state);
            if (speed > best) {
                slot = index;
                best = speed;
            }
        }
        return slot;
    }
}
