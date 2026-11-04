package wtf.carnation.module.impl.misc;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.utils.BlockUtils;
import wtf.carnation.utils.ChunkUtils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Port of Naven's {@code tech.naven.modules.impl.combat.GhostHand} (category Misc).
 *
 * <p>Naven hooks EventClick; this client has no EventClick yet, so the use-key edge is sampled
 * on the pre-tick. The ghost interaction itself is identical.</p>
 */
public class GhostHand extends Module {
    private static final Set<Block> BLOCKS = new HashSet<>();
    private boolean useKeyWasDown;
    private long lastGhostMs;

    static {
        BLOCKS.add(Blocks.CHEST);
        BLOCKS.add(Blocks.ENDER_CHEST);
        BLOCKS.add(Blocks.TRAPPED_CHEST);
        BLOCKS.add(Blocks.SHULKER_BOX);
    }

    public GhostHand() {
        super("GhostHand", Category.MISC, "Allows interacting with blocks through walls");
    }

    @EventTarget
    public void onTick(RunTickEvent event) {
        if (!event.isPre()) return;
        boolean useDown = mc.options.keyUse.isDown();
        boolean justPressed = useDown && !this.useKeyWasDown;
        this.useKeyWasDown = useDown;

        if (mc.player == null || mc.level == null || mc.gui.screen() != null || mc.player.isUsingItem()) return;
        if (!useDown) return;
        if (!justPressed && System.currentTimeMillis() - this.lastGhostMs < 250L) return;

        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult normalHit = (BlockHitResult) mc.hitResult;
            Block aimed = mc.level.getBlockState(normalHit.getBlockPos()).getBlock();
            if (BLOCKS.contains(aimed)
                    && normalHit.getLocation().distanceTo(mc.player.getEyePosition(1.0F)) <= mc.player.blockInteractionRange()) {
                return;
            }
        }

        if (System.currentTimeMillis() - this.lastGhostMs < 250L) return;
        if (this.ghostInteractWithChest()) {
            this.lastGhostMs = System.currentTimeMillis();
        }
    }

    public boolean ghostInteractWithChest() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return false;

        Vec3 eyePos = mc.player.getEyePosition(1.0F);
        Vec3 lookVec = mc.player.getViewVector(1.0F);
        double range = mc.player.blockInteractionRange();
        Vec3 reachEnd = eyePos.add(lookVec.scale(range));
        BlockEntity target = null;
        BlockHitResult fakeHit = null;
        double closestDist = Double.MAX_VALUE;

        for (BlockEntity blockEntity : ChunkUtils.getLoadedBlockEntities().toList()) {
            if (blockEntity == null) continue;
            if (!BLOCKS.contains(blockEntity.getBlockState().getBlock())) continue;
            if (!BlockUtils.canBeClicked(blockEntity.getBlockPos())) continue;

            AABB box = BlockUtils.getBoundingBox(blockEntity.getBlockPos());
            Optional<Vec3> hit = box.clip(eyePos, reachEnd);
            if (hit.isEmpty()) continue;

            double dist = hit.get().distanceTo(eyePos);
            if (dist >= closestDist) continue;

            closestDist = dist;
            target = blockEntity;
            fakeHit = new BlockHitResult(hit.get(), Direction.UP, blockEntity.getBlockPos(), false);
        }

        if (target == null || fakeHit == null) return false;

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, fakeHit);
        mc.player.swing(InteractionHand.MAIN_HAND,
                net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        return true;
    }
}
