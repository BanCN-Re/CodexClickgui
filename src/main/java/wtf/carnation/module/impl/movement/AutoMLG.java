package wtf.carnation.module.impl.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.UpdateEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.ChatUtil;

/** Places a water bucket under the player while falling. */
public class AutoMLG extends Module {
    private final NumberValue distance = new NumberValue("Fall Distance", 3.0, 3.0, 15.0, 0.1);

    private boolean rotating;
    private boolean placeWater;
    private BlockPos above;
    private int originalSlot = -1;
    private int timeout;

    public AutoMLG() {
        super("AutoMLG", Category.MOVEMENT, "下落时自动放置水");
    }

    @Override
    protected void onDisable() {
        restoreSlot();
        this.rotating = false;
        this.placeWater = false;
        this.above = null;
    }

    public static boolean isOnGround(double height) {
        if (MinecraftAccess.level() == null || MinecraftAccess.player() == null) return false;
        Iterable<VoxelShape> collisions = MinecraftAccess.level().getBlockCollisions(MinecraftAccess.player(),
                MinecraftAccess.player().getBoundingBox().move(0.0, height, 0.0));
        return collisions.iterator().hasNext();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.fallDistance > this.distance.get()) {
            if (this.rotating && isOnGround(mc.player.getDeltaMovement().y)) {
                this.placeWater = true;
            } else if (isOnGround(mc.player.getDeltaMovement().y * 2.0)) {
                for (int slot = 0; slot < 9; slot++) {
                    ItemStack item = mc.player.getInventory().getItem(slot);
                    if (!item.isEmpty() && item.is(Items.WATER_BUCKET)) {
                        this.originalSlot = mc.player.getInventory().getSelectedSlot();
                        mc.player.getInventory().setSelectedSlot(slot);
                        this.rotating = true;
                        this.timeout = 5;
                        break;
                    }
                }
            }
        }

        if (--this.timeout == 0 && this.rotating) {
            this.rotating = false;
            ChatUtil.addChatMessage("AutoMLG: 放置水失败");
        }

        if (this.placeWater) {
            this.placeWater = false;
            if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK
                    && ((BlockHitResult) mc.hitResult).getDirection() == Direction.UP) {
                this.above = ((BlockHitResult) mc.hitResult).getBlockPos().above();
                useBucket();
            } else {
                ChatUtil.addChatMessage("AutoMLG: 放置水失败");
                this.rotating = false;
            }
            return;
        }

        if (this.above != null && mc.hitResult instanceof BlockHitResult hit) {
            this.rotating = false;
            if (hit.getBlockPos().above().equals(this.above)) {
                useBucket();
            } else {
                ChatUtil.addChatMessage("AutoMLG: 移动导致水桶回收失败");
            }
            restoreSlot();
            this.above = null;
        }
    }

    private void useBucket() {
        if (mc.gameMode == null || mc.player == null) return;
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
    }

    private void restoreSlot() {
        if (this.originalSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(this.originalSlot);
        }
        this.originalSlot = -1;
    }

    /** Small helper so the static check works without a client instance. */
    static final class MinecraftAccess {
        private static net.minecraft.client.Minecraft client() {
            return net.minecraft.client.Minecraft.getInstance();
        }

        static net.minecraft.client.player.LocalPlayer player() {
            return client().player;
        }

        static net.minecraft.client.multiplayer.ClientLevel level() {
            return client().level;
        }
    }
}
