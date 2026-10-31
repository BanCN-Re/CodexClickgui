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
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
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

    public Vec3 position() {
        return mc.player == null ? Vec3.ZERO : mc.player.position();
    }

    public Vec3 eyePosition() {
        return mc.player == null ? Vec3.ZERO : mc.player.getEyePosition();
    }

    public Vec3 velocity() {
        return mc.player == null ? Vec3.ZERO : mc.player.getDeltaMovement();
    }

    public void setVelocity(double x, double y, double z) {
        if (mc.player != null) mc.player.setDeltaMovement(x, y, z);
    }

    public void addVelocity(double x, double y, double z) {
        if (mc.player == null) return;
        Vec3 velocity = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(velocity.x + x, velocity.y + y, velocity.z + z);
    }

    public float yaw() {
        return mc.player == null ? 0F : mc.player.getYRot();
    }

    public float pitch() {
        return mc.player == null ? 0F : mc.player.getXRot();
    }

    public void setRotation(float yaw, float pitch) {
        if (mc.player == null) return;
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }

    public void setPosition(double x, double y, double z) {
        if (mc.player != null) mc.player.setPos(x, y, z);
    }

    public float health() {
        return mc.player == null ? 0F : mc.player.getHealth();
    }

    public float maxHealth() {
        return mc.player == null ? 0F : mc.player.getMaxHealth();
    }

    public int foodLevel() {
        return mc.player == null ? 0 : mc.player.getFoodData().getFoodLevel();
    }

    public java.util.List<LivingEntity> entities(double range, String filter) {
        java.util.List<LivingEntity> result = new java.util.ArrayList<>();
        if (!isInGame()) return result;
        String kind = filter == null ? "all" : filter.toLowerCase(java.util.Locale.ROOT);
        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(Math.max(0, range)))) {
            if (entity == mc.player || !entity.isAlive()) continue;
            boolean players = entity instanceof Player;
            if ("players".equals(kind) && !players) continue;
            if ("mobs".equals(kind) && players) continue;
            result.add(entity);
        }
        result.sort(java.util.Comparator.comparingDouble(mc.player::distanceToSqr));
        return result;
    }

    public BlockState blockState(double x, double y, double z) {
        return mc.level == null ? null : mc.level.getBlockState(BlockPos.containing(x, y, z));
    }

    public String blockName(double x, double y, double z) {
        BlockState state = blockState(x, y, z);
        return state == null ? "" : state.getBlock().toString();
    }

    public boolean isAir(double x, double y, double z) {
        BlockState state = blockState(x, y, z);
        return state != null && state.isAir();
    }

    public boolean isReplaceable(double x, double y, double z) {
        BlockState state = blockState(x, y, z);
        return state != null && state.canBeReplaced();
    }

    public BlockHitResult crosshairBlock() {
        return mc.hitResult instanceof BlockHitResult hit ? hit : null;
    }

    public EntityHitResult crosshairEntity() {
        return mc.hitResult instanceof EntityHitResult hit ? hit : null;
    }

    /** Starts and advances one destruction packet for a block; call every tick for survival breaking. */
    public boolean attackBlock(double x, double y, double z, String faceName) {
        if (mc.player == null || mc.gameMode == null || mc.level == null) return false;
        Direction face = direction(faceName);
        BlockPos pos = BlockPos.containing(x, y, z);
        boolean started = mc.gameMode.startDestroyBlock(pos, face);
        if (!started) return false;
        mc.gameMode.continueDestroyBlock(pos, face);
        mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        return true;
    }

    public boolean stopBreaking() {
        if (mc.gameMode == null) return false;
        mc.gameMode.stopDestroyBlock();
        return true;
    }

    public boolean useItemMain() {
        if (mc.player == null || mc.gameMode == null) return false;
        InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        if (result.consumesAction()) {
            mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            return true;
        }
        return false;
    }

    public boolean useItemOffhand() {
        if (mc.player == null || mc.gameMode == null) return false;
        InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
        if (result.consumesAction()) {
            mc.player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
            return true;
        }
        return false;
    }

    public boolean interactEntity(Entity entity, boolean secondary) {
        if (mc.player == null || mc.gameMode == null || entity == null) return false;
        EntityHitResult hit = new EntityHitResult(entity);
        InteractionResult result = mc.gameMode.interact(mc.player, entity, hit, InteractionHand.MAIN_HAND);
        if (result.consumesAction()) {
            mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            return true;
        }
        return false;
    }

    public int findHotbarItem(String query) {
        if (mc.player == null || query == null || query.isBlank()) return -1;
        String needle = query.toLowerCase(java.util.Locale.ROOT);
        Inventory inventory = mc.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            String name = inventory.getItem(slot).toString().toLowerCase(java.util.Locale.ROOT);
            if (name.contains(needle)) return slot;
        }
        return -1;
    }

    public void setMovementInput(boolean forward, boolean backward, boolean left, boolean right,
                                 boolean jump, boolean sneak, boolean sprint) {
        if (mc.player == null) return;
        mc.player.input.keyPresses = new Input(forward, backward, left, right, jump, sneak, sprint);
        mc.player.setShiftKeyDown(sneak);
        mc.player.setSprinting(sprint);
    }

    public void sendCommand(String command) {
        if (mc.player != null && command != null && !command.isBlank()) {
            mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        }
    }

    public boolean toggleModule(String name) {
        var module = Client.instance.getModuleManager().getModuleByName(name);
        if (module == null) return false;
        module.toggle();
        return module.isEnabled();
    }

    public boolean moduleEnabled(String name) {
        var module = Client.instance.getModuleManager().getModuleByName(name);
        return module != null && module.isEnabled();
    }

    public int windowWidth() {
        return mc.getWindow().getWidth();
    }

    public int windowHeight() {
        return mc.getWindow().getHeight();
    }

    public int fps() {
        return mc.getFps();
    }

    private static Direction direction(String name) {
        if (name != null) {
            try {
                return Direction.valueOf(name.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Direction.UP;
    }
}
