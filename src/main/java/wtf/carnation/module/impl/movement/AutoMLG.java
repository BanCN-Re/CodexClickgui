package wtf.carnation.module.impl.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.ChatUtil;

/**
 * Port of Naven's {@code tech.naven.modules.impl.move.AutoMLG}.
 *
 * <p>The water is placed from the pre-tick (Naven waits for EventClick); the recycle path is
 * kept and runs once the bucket has been used again.</p>
 */
public class AutoMLG extends Module {
    private final NumberValue distance = new NumberValue("Fall Distance", 3.0, 3.0, 15.0, 0.1);

    public boolean rotation = false;
    private BlockPos above;
    private boolean placeWater = false;
    private int originalSlot;
    private int timeout;

    public AutoMLG() {
        super("AutoMLG", Category.MOVEMENT, "Automatically places water when falling");
    }

    public static boolean isOnGround(double height) {
        if (mc.player == null || mc.level == null) return false;
        Iterable<VoxelShape> collisions = mc.level.getBlockCollisions(mc.player,
                mc.player.getBoundingBox().move(0.0, height, 0.0));
        return collisions.iterator().hasNext();
    }

    @EventTarget
    public void onPre(RunTickEvent e) {
        if (!e.isPre() || mc.player == null) return;

        if (mc.player.fallDistance > this.distance.get()) {
            if (this.rotation && isOnGround(mc.player.getDeltaMovement().y)) {
                this.placeWater = true;
            } else if (isOnGround(mc.player.getDeltaMovement().y * 2.0)) {
                for (int i = 0; i < 9; i++) {
                    ItemStack item = mc.player.getInventory().getItem(i);
                    if (!item.isEmpty() && item.getItem() == Items.WATER_BUCKET) {
                        this.originalSlot = mc.player.getInventory().getSelectedSlot();
                        mc.player.getInventory().setSelectedSlot(i);
                        this.rotation = true;
                        this.timeout = 5;
                    }
                }
            }
        }

        if (--this.timeout == 0 && this.rotation) {
            this.rotation = false;
            ChatUtil.addChatMessage("Failed to place water!");
        }

        if (this.placeWater) {
            this.placeWater = false;
            if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK
                    && ((BlockHitResult) mc.hitResult).getDirection() == Direction.UP) {
                this.above = ((BlockHitResult) mc.hitResult).getBlockPos().above();
                this.useItem(mc.player, mc.level, InteractionHand.MAIN_HAND);
            } else {
                ChatUtil.addChatMessage("Failed to place water!");
                this.rotation = false;
            }
        } else if (this.above != null) {
            this.rotation = false;
            if (mc.hitResult instanceof BlockHitResult hit) {
                BlockPos target = hit.getBlockPos().above();
                if (target.equals(this.above)) {
                    this.useItem(mc.player, mc.level, InteractionHand.MAIN_HAND);
                } else {
                    ChatUtil.addChatMessage("Failed to recycle the water dues to moving!");
                }
            }
            mc.player.getInventory().setSelectedSlot(this.originalSlot);
            this.above = null;
        }
    }

    public InteractionResult useItem(Player player, Level level, InteractionHand hand) {
        return mc.gameMode.useItem(player, hand);
    }
}
