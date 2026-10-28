package wtf.carnation.script;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.Client;
import wtf.carnation.InstanceAccess;
import wtf.carnation.utils.ChatUtil;

public final class LuaApi implements InstanceAccess {
    public boolean isInGame() {
        return mc.player != null && mc.level != null;
    }

    public Minecraft minecraft() {
        return mc;
    }

    public LocalPlayer player() {
        return mc.player;
    }

    public ClientLevel world() {
        return mc.level;
    }

    public void chat(String message) {
        ChatUtil.addChatMessage(message);
    }

    public void log(String message) {
        Client.logger.info("[Lua] {}", message);
    }

    public LivingEntity nearestLiving(double range, boolean playersOnly) {
        if (!isInGame()) return null;
        double radius = Math.max(0, range);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(radius))) {
            if (entity == mc.player || !entity.isAlive() || playersOnly && !(entity instanceof Player)) continue;
            double distance = mc.player.distanceToSqr(entity);
            if (distance < nearestDistance) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public boolean canAttack() {
        return mc.player != null && mc.player.getAttackStrengthScale(0.5F) >= 1.0F;
    }

    public void lookAt(Entity entity) {
        if (mc.player == null || entity == null) return;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 target = entity.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }

    public double boostHorizontal(double multiplier, double maxSpeed) {
        if (mc.player == null) return 0;
        Vec3 velocity = mc.player.getDeltaMovement();
        double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (speed < 0.02) return speed;
        double boosted = Math.min(speed * Math.max(1.0, multiplier), Math.max(speed, maxSpeed));
        if (boosted > speed) {
            double scale = boosted / speed;
            mc.player.setDeltaMovement(velocity.x * scale, velocity.y, velocity.z * scale);
        }
        return boosted;
    }

    public void attack(Entity entity) {
        if (mc.player == null || mc.gameMode == null || entity == null) return;
        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
    }

    /** Turns the view towards a world position, e.g. the anchor face before placing. */
    public void lookAtPos(double x, double y, double z) {
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        double dx = x - eye.x;
        double dy = y - eye.y;
        double dz = z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        mc.player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, horizontal)));
    }

    /** The selected hotbar slot, 0-8; -1 when not in a world. */
    public int hotbarSlot() {
        return mc.player == null ? -1 : mc.player.getInventory().getSelectedSlot();
    }

    /** Switches the hotbar slot; scripts must restore the previous one afterwards. */
    public boolean setHotbarSlot(int slot) {
        if (mc.player == null) return false;
        mc.player.getInventory().setSelectedSlot(Math.max(0, Math.min(8, slot)));
        return true;
    }

    public ItemStack hotbarStack(int slot) {
        return mc.player == null || slot < 0 || slot > 8 ? null : mc.player.getInventory().getItem(slot);
    }

    /** First hotbar slot holding a placeable block item, or -1. */
    public int findHotbarBlock() {
        if (mc.player == null) return -1;
        Inventory inventory = mc.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) return slot;
        }
        return -1;
    }

    public void setSneaking(boolean sneaking) {
        if (mc.player == null) return;
        // 26.3 reads sneak state from input.keyPresses, so flip the shift bit in the
        // Input record (and keep the legacy entity flag in sync). KeyboardInput rebuilds
        // keyPresses every tick, so scripts must re-apply this in onUpdate to hold it.
        Input presses = mc.player.input.keyPresses;
        mc.player.input.keyPresses = new Input(presses.forward(), presses.backward(),
                presses.left(), presses.right(), presses.jump(), sneaking, presses.sprint());
        mc.player.setShiftKeyDown(sneaking);
    }

    public boolean isSneaking() {
        return mc.player != null && mc.player.isShiftKeyDown();
    }

    /**
     * Places the currently selected block into the replaceable block (air, water, grass…)
     * at (x,y,z) against any sturdy neighbour face, so the scaffold use case needs no hand-built
     * BlockHitResult (luajava cannot construct one). Returns a short status the script can log:
     * placed / rejected:… / no_block / no_face / not_replaceable / no_player.
     */
    public String placeBlockAdjacent(double x, double y, double z) {
        if (mc.player == null || mc.gameMode == null || mc.level == null) return "no_player";
        BlockPos target = BlockPos.containing(x, y, z);
        if (!mc.level.getBlockState(target).canBeReplaced()) return "not_replaceable";
        ItemStack stack = mc.player.getInventory().getItem(mc.player.getInventory().getSelectedSlot());
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) return "no_block";
        for (Direction face : Direction.values()) {
            BlockPos anchor = target.relative(face.getOpposite());
            if (!mc.level.getBlockState(anchor).isFaceSturdy(mc.level, anchor, face.getOpposite())) continue;
            Vec3 location = Vec3.atCenterOf(target).add(
                    face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            BlockHitResult hit = new BlockHitResult(location, face, target, false);
            InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            if (result.consumesAction()) {
                mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                return "placed";
            }
            return "rejected:" + result;
        }
        return "no_face";
    }
}
