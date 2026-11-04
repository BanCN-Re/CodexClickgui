package wtf.carnation.module.impl.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.utils.InventoryUtils;

/**
 * Port of Naven's {@code AutoTools}.
 *
 * <p>The held-item spoofing of Naven's EventUpdateHeldItem is not available here, so the silent
 * option only affects the slot switching.</p>
 */
public class AutoTools extends Module {
    private final BoolValue checkSword = new BoolValue("Check Sword", true);
    private final BoolValue switchBack = new BoolValue("Switch Back", true);
    private final BoolValue silent = new BoolValue("Silent", true);
    private int originSlot = -1;

    public AutoTools() {
        super("AutoTools", Category.MISC, "Automatically switches to the best tool for the job");
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (mc.player == null || mc.gameMode == null || mc.level == null) return;
        if (e.isPre()) {
            if (!mc.gameMode.isDestroying()) return;
            if (this.checkSword.get() && InventoryUtils.isSword(mc.player.getMainHandItem())) return;
            if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult hitResult = (BlockHitResult) mc.hitResult;
                int bestTool = this.getBestTool(hitResult.getBlockPos());
                if (bestTool != -1 && bestTool != mc.player.getInventory().getSelectedSlot()) {
                    this.originSlot = mc.player.getInventory().getSelectedSlot();
                    mc.player.getInventory().setSelectedSlot(bestTool);
                }
            }
        } else if (!mc.gameMode.isDestroying() && this.switchBack.get() && this.originSlot != -1) {
            mc.player.getInventory().setSelectedSlot(this.originSlot);
            this.originSlot = -1;
        }
    }

    private int getBestTool(BlockPos pos) {
        BlockState blockState = mc.level.getBlockState(pos);
        Block block = blockState.getBlock();
        int slot = 0;
        float damage = 1.0F;

        for (int index = 0; index < 9; index++) {
            ItemStack stack = mc.player.getInventory().getItem(index);
            if (InventoryUtils.isGodItem(stack) || stack.isEmpty() || blockState.isAir()) continue;
            if (InventoryUtils.isSword(stack) && !(block instanceof WebBlock)) continue;

            float speed = stack.getItem().getDestroySpeed(stack, blockState);
            if (speed > 1.0F && !(block instanceof DropExperienceBlock) && !(block instanceof RedStoneOreBlock)) {
                int efficiency = InventoryUtils.getEnchantmentLevel(Enchantments.EFFICIENCY, stack);
                if (efficiency > 0) speed += efficiency * efficiency + 1;
            }
            if (speed > damage) {
                slot = index;
                damage = speed;
            }
        }
        return damage > 1.0F ? slot : -1;
    }
}
