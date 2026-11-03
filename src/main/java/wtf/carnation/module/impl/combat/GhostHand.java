package wtf.carnation.module.impl.combat;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.UpdateEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;

import java.util.HashSet;
import java.util.Set;

/** Interacts with chests and shulkers through walls. */
public class GhostHand extends Module {
    private static final Set<net.minecraft.world.level.block.Block> BLOCKS = new HashSet<>();

    static {
        BLOCKS.add(Blocks.CHEST);
        BLOCKS.add(Blocks.ENDER_CHEST);
        BLOCKS.add(Blocks.TRAPPED_CHEST);
        BLOCKS.add(Blocks.SHULKER_BOX);
    }

    private final NumberValue range = new NumberValue("Range", 4.5, 1.0, 6.0, 0.5);
    private boolean useKeyWasDown;
    private long lastGhostMs;

    public GhostHand() {
        super("GhostHand", Category.COMBAT, "隔着墙也能打开箱子与潜影盒");
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        boolean useDown = mc.options.keyUse.isDown();
        boolean justPressed = useDown && !useKeyWasDown;
        this.useKeyWasDown = useDown;
        if (!justPressed) return;
        if (mc.player == null || mc.level == null || mc.gui.screen() != null || mc.player.isUsingItem()) return;
        long now = System.currentTimeMillis();
        if (now - lastGhostMs < 250L) return;
        if (ghostInteract()) {
            lastGhostMs = now;
        }
    }

    private boolean ghostInteract() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return false;
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 look = mc.player.getViewVector(1.0F);
        double reach = range.get();
        Vec3 reachEnd = eye.add(look.scale(reach));

        BlockEntity target = null;
        BlockHitResult fakeHit = null;
        double closest = Double.MAX_VALUE;
        int chunkRadius = 8;
        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;

        for (int cx = pcx - chunkRadius; cx <= pcx + chunkRadius; cx++) {
            for (int cz = pcz - chunkRadius; cz <= pcz + chunkRadius; cz++) {
                var chunk = mc.level.getChunk(cx, cz);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity == null) continue;
                    if (!BLOCKS.contains(blockEntity.getBlockState().getBlock())) continue;
                    AABB box = blockEntity.getBlockState().getShape(mc.level, blockEntity.getBlockPos()).bounds();
                    var hit = box.clip(eye, reachEnd);
                    if (hit.isEmpty()) continue;
                    double distance = hit.get().distanceTo(eye);
                    if (distance >= closest) continue;
                    closest = distance;
                    target = blockEntity;
                    fakeHit = new BlockHitResult(hit.get(), Direction.UP, blockEntity.getBlockPos(), false);
                }
            }
        }

        if (target == null || fakeHit == null) return false;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, fakeHit);
        mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        return true;
    }
}
