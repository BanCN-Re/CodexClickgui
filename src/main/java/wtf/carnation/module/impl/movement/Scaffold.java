package wtf.carnation.module.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import mixin.KeyMappingAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.NetherFungusBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventPriority;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.MoveInputEvent;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.FallingPlayer;
import wtf.carnation.utils.InventoryUtils;
import wtf.carnation.utils.MathUtils;
import wtf.carnation.utils.MoveUtil;
import wtf.carnation.utils.PlayerUtils;
import wtf.carnation.utils.RayTraceUtils;
import wtf.carnation.utils.RotationUtils;
import wtf.carnation.utils.Vector2f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Port of Naven's {@code tech.naven.modules.impl.move.Scaffold}.
 *
 * <p>The placement search, rotation search, silent rotation packets, slot switching and telly
 * bridge mode are the original's. This client has no EventClick / EventUpdateHeldItem /
 * EventUpdateFoV hooks, so the placement is driven from the pre-tick and the held-item/FoV
 * spoofing is not applied.</p>
 */
public class Scaffold extends Module {
    public static final List<Block> blacklistedBlocks = Arrays.asList(
            Blocks.AIR, Blocks.WATER, Blocks.LAVA, Blocks.ENCHANTING_TABLE, Blocks.GLASS_PANE,
            Blocks.IRON_BARS, Blocks.SNOW, Blocks.COAL_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE,
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.TORCH, Blocks.ANVIL, Blocks.NOTE_BLOCK,
            Blocks.JUKEBOX, Blocks.TNT, Blocks.GOLD_ORE, Blocks.IRON_ORE, Blocks.LAPIS_ORE,
            Blocks.STONE_PRESSURE_PLATE, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, Blocks.STONE_BUTTON, Blocks.LEVER,
            Blocks.TALL_GRASS, Blocks.TRIPWIRE, Blocks.TRIPWIRE_HOOK, Blocks.RAIL, Blocks.CORNFLOWER,
            Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM, Blocks.VINE, Blocks.SUNFLOWER, Blocks.LADDER,
            Blocks.FURNACE, Blocks.SAND, Blocks.CACTUS, Blocks.DISPENSER, Blocks.DROPPER,
            Blocks.CRAFTING_TABLE, Blocks.COBWEB, Blocks.PUMPKIN, Blocks.COBBLESTONE_WALL,
            Blocks.OAK_FENCE, Blocks.REDSTONE_TORCH, Blocks.FLOWER_POT);

    public final Vector2f correctRotation = new Vector2f(0.0F, 0.0F);
    public final Vector2f rots = new Vector2f(0.0F, 0.0F);
    public final Vector2f lastRots = new Vector2f(0.0F, 0.0F);

    public final ModeValue mode = new ModeValue("Mode", new String[]{"Normal", "Telly Bridge"}, "Normal");
    private final NumberValue tellyTicks = new NumberValue("Telly Ticks", 1.0, 0.0, 3.0, 1.0);
    private final NumberValue tellyPlaceDelay = new NumberValue("Place Delay", 0.0, 0.0, 20.0, 1.0);
    public final BoolValue renderItemSpoof = new BoolValue("Render Item Spoof", true);
    public final BoolValue onTickRot = new BoolValue("On Tick", false);
    private final NumberValue rotationSpeed = new NumberValue("Rotation Speed", 60.0, 30.0, 180.0, 1.0);
    private final NumberValue bps = new NumberValue("BPS", 10.0, 2.0, 10.0, 0.1);
    public final BoolValue keepFoV = new BoolValue("Keep FoV", true);
    private final NumberValue fov = new NumberValue("FoV", 1.15, 1.0, 2.0, 0.05);

    private final List<Runnable> pendingTicks = new ArrayList<>();
    private final Vector2f lastSentRot = new Vector2f(0.0F, 0.0F);

    int oldSlot;
    private int altSlot = -1;
    private boolean slotActive;
    private int slotActiveTick;
    private int slotTicks;
    private BlockPosWithFacing pos;
    private int airTicks;
    private int onGroundTicks;
    private int rotationDelay;
    private int velocityDelay;
    private int tellyPlaceDelayTimer;
    private int lastSneakTicks;
    private int offGroundTicks;
    private boolean canBuildNow = true;
    private BlockPos lastC05Position;
    private int baseY = -1;
    private String aimBranch = "none";
    private boolean usedSkipTicks;
    private double lastHorizontalSpeed;
    private int lastPlaceTick = -100;

    public Scaffold() {
        super("Scaffold", Category.MOVEMENT, "Automatically places blocks under you");
    }

    public static boolean isValidStack(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof BlockItem) || stack.getCount() <= 1) return false;
        if (!isItemValid(stack)) return false;
        String name = stack.getDisplayName().getString();
        if (name.contains("Click") || name.contains("鐐瑰嚮")) return false;
        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (block instanceof FlowerBlock || block instanceof BushBlock || block instanceof NetherFungusBlock
                || block instanceof CropBlock || block instanceof SlabBlock) {
            return false;
        }
        return !blacklistedBlocks.contains(block);
    }

    private static boolean isItemValid(ItemStack stack) {
        String name = stack.getDisplayName().getString();
        return !name.contains("Click") && !name.contains("Right") && !name.contains("点击")
                && !name.contains("Teleport") && !name.contains("使用") && !name.contains("传送")
                && !name.contains("再来");
    }

    public static boolean isOnBlockEdge(float inflate) {
        if (mc.level == null || mc.player == null) return false;
        double halfWidth = mc.player.getBbWidth() / 2.0;
        if (inflate >= halfWidth) return true;
        double margin = Math.min(inflate, halfWidth * 0.99);
        Vec3 position = mc.player.position();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                BlockPos corner = BlockPos.containing(position.x + sx * margin, position.y - 1.0,
                        position.z + sz * margin);
                BlockState state = mc.level.getBlockState(corner);
                if (!state.entityCanStandOn(mc.level, corner, mc.player)) return true;
            }
        }
        return false;
    }

    public static double getSpeed() {
        if (mc.player == null) return 0.0;
        Vec3 delta = mc.player.getDeltaMovement();
        return Math.sqrt(delta.x * delta.x + delta.z * delta.z);
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            this.oldSlot = mc.player.getInventory().getSelectedSlot();
            this.altSlot = this.oldSlot;
            this.slotActive = false;
            this.slotActiveTick = 0;
            this.slotTicks = 0;
            this.rots.set(mc.player.getYRot() - 180.0F, mc.player.getXRot());
            this.correctRotation.set(this.rots.getX(), this.rots.getY());
            this.lastRots.set(mc.player.yRotO - 180.0F, mc.player.xRotO);
            this.lastSentRot.set(mc.player.getYRot(), mc.player.getXRot());
            this.pos = null;
            this.baseY = 10000;
            this.velocityDelay = 0;
            this.rotationDelay = 0;
            this.canBuildNow = true;
            this.lastC05Position = null;
            this.airTicks = 0;
            this.tellyPlaceDelayTimer = 0;
        }
        this.pendingTicks.clear();
    }

    @Override
    protected void onDisable() {
        this.pendingTicks.clear();
        if (mc.player != null) {
            boolean jump = isKeyDown(mc.options.keyJump);
            boolean shift = isKeyDown(mc.options.keyShift);
            mc.options.keyJump.setDown(jump);
            mc.options.keyShift.setDown(shift);
            mc.options.keyUse.setDown(false);
            if (this.slotActive) {
                mc.player.getInventory().setSelectedSlot(this.altSlot);
                this.slotActive = false;
            }
        }
        this.canBuildNow = true;
        this.lastC05Position = null;
    }

    private static boolean isKeyDown(net.minecraft.client.KeyMapping mapping) {
        int key = ((KeyMappingAccessor) mapping).carnation$getKey().getValue();
        return InputConstants.isKeyDown(key);
    }

    @EventTarget
    @EventPriority(1)
    public void onEarlyTick(RunTickEvent e) {
        if (!e.isPre() || mc.player == null || mc.level == null || mc.gui.screen() != null) return;

        RayTraceUtils.updateEyePos();
        if (!this.pendingTicks.isEmpty()) {
            List<Runnable> tasks = new ArrayList<>(this.pendingTicks);
            this.pendingTicks.clear();
            tasks.forEach(Runnable::run);
        }

        if (this.slotActive && this.slotActiveTick != this.slotTicks) {
            mc.player.getInventory().setSelectedSlot(this.altSlot);
            this.slotActive = false;
        }
        this.slotTicks++;
        if (this.velocityDelay > 0) this.velocityDelay--;

        double horizontalSpeed = Math.sqrt(mc.player.getDeltaMovement().x * mc.player.getDeltaMovement().x
                + mc.player.getDeltaMovement().z * mc.player.getDeltaMovement().z);
        if (!mc.player.onGround() && horizontalSpeed - this.lastHorizontalSpeed > 0.25) {
            this.velocityDelay = Math.max(this.velocityDelay, 40);
        }
        this.lastHorizontalSpeed = horizontalSpeed;
        if (mc.player.onGround() && this.velocityDelay <= 30) this.velocityDelay = 0;

        boolean offhandPlaceable = isValidStack(mc.player.getOffhandItem());
        int placeableSlot = -1;
        if (!offhandPlaceable) {
            for (int i = 0; i < 9; i++) {
                if (isValidStack(mc.player.getInventory().getItem(i))) {
                    placeableSlot = i;
                    break;
                }
            }
        }
        boolean hasBlocks = offhandPlaceable || placeableSlot != -1;
        if (placeableSlot != -1 && placeableSlot != mc.player.getInventory().getSelectedSlot()) {
            if (!this.slotActive) this.altSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(placeableSlot);
            this.slotActive = true;
            this.slotActiveTick = this.slotTicks;
        }

        if (mc.player.onGround()) this.offGroundTicks = 0;
        else this.offGroundTicks++;

        boolean jumpHeld = isKeyDown(mc.options.keyJump);
        if (this.baseY == -1 || this.baseY > (int) Math.floor(mc.player.getY()) - 1
                || mc.player.onGround() || !PlayerUtils.movementInput() || jumpHeld
                || this.mode.is("Normal")) {
            this.baseY = (int) Math.floor(mc.player.getY()) - 1;
        }

        this.updatePlacement();
        this.tellyPlaceDelayTimer = this.pos == null ? 0 : this.tellyPlaceDelayTimer + 1;

        boolean airborne = !mc.player.onGround();
        boolean knocked = airborne && (this.velocityDelay > 0 || mc.player.hurtTime > 0);
        boolean falling = airborne && mc.player.getDeltaMovement().y < -0.1;
        this.canBuildNow = true;
        if (hasBlocks && (falling || knocked)) {
            if (this.pos == null && knocked) this.findClutchTarget();
            if (this.pos != null) {
                FallingPlayer sim = new FallingPlayer(mc.player);
                sim.calculate(2);
                double distanceToTarget = sim.getEyePos().distanceTo(Vec3.atCenterOf(this.pos.position()));
                if (this.pos.position().getY() > sim.y || distanceToTarget >= 4.5) this.canBuildNow = false;
            } else if (knocked) {
                this.canBuildNow = false;
            }
        }
        if (mc.player.onGround()) this.canBuildNow = true;

        if (hasBlocks && (!this.canBuildNow || this.velocityDelay > 0 || mc.player.hurtTime > 0)
                && this.rotationDelay <= 8) {
            this.usedSkipTicks = true;
            boolean useC06 = this.onTickRot.get() || !this.canBuildNow || this.pos == null;
            if (useC06) {
                this.aimBranch = "C06-real-cam";
                this.rots.set(mc.player.getYRot(), mc.player.getXRot());
                this.correctRotation.set(this.rots.getX(), this.rots.getY());
                this.rotationDelay++;
            } else {
                Vec3 targetVec = getFaceCenter(this.pos.position(), this.pos.facing());
                Vec3 predictedEye = new Vec3(
                        mc.player.getX() + mc.player.getDeltaMovement().x,
                        mc.player.getY() + mc.player.getEyeHeight() + mc.player.getDeltaMovement().y,
                        mc.player.getZ() + mc.player.getDeltaMovement().z);
                Vector2f rotationToBlock = RotationUtils.getRotations(predictedEye, targetVec).toVector();
                this.rots.set(rotationToBlock.getX(), rotationToBlock.getY());
                this.correctRotation.set(this.rots.getX(), this.rots.getY());
                this.rotationDelay++;
                this.pendingTicks.add(() -> {
                    float yaw = rotationToBlock.getX();
                    if (yaw > -360.0F && yaw < 360.0F) yaw += 720.0F;
                    this.lastSentRot.set(rotationToBlock.getX(), rotationToBlock.getY());
                    send(new ServerboundMovePlayerPacket.Rot(yaw, rotationToBlock.getY(),
                            mc.player.onGround(), mc.player.horizontalCollision));
                    this.doSnap();
                });
            }
        } else {
            this.canBuildNow = true;
            this.clearSkipTicks();
            boolean wasInRescue = this.rotationDelay > 0;
            this.rotationDelay = 0;
            if (wasInRescue) {
                this.aimBranch = "resume-jitter";
                Vector2f resumeRot = new Vector2f(mc.player.getYRot() + rotationJitter(),
                        mc.player.getXRot() + rotationJitter());
                send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY(),
                        mc.player.getZ(), resumeRot.getX(), resumeRot.getY(), mc.player.onGround(),
                        mc.player.horizontalCollision));
            }

            if (this.onTickRot.get()) {
                this.aimBranch = "C06-real-cam";
                this.rots.set(mc.player.getYRot(), mc.player.getXRot());
                this.correctRotation.set(this.rots.getX(), this.rots.getY());
            } else {
                this.calculateTargetRotation();
            }

            if (this.mode.is("Telly Bridge")) {
                mc.options.keyJump.setDown(PlayerUtils.movementInput() || jumpHeld);
                if (this.onGroundTicks == 1) {
                    this.rots.setY(75.5F);
                    this.correctRotation.setY(75.5F);
                }
                if (this.airTicks < this.tellyTicks.getInt() && PlayerUtils.movementInput()) {
                    this.rots.setX(mc.player.getYRot());
                    this.correctRotation.setX(mc.player.getYRot());
                    this.lastRots.set(this.rots.getX(), this.rots.getY());
                    return;
                }
            }
        }
        this.lastRots.set(this.rots.getX(), this.rots.getY());

        if (this.pos != null && (!this.mode.is("Telly Bridge") || this.offGroundTicks >= 1)) {
            this.doPlaceFromTick();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent e) {
        if (e.isSend() || mc.player == null || mc.level == null) return;
        if (e.getPacket() instanceof ClientboundSetEntityMotionPacket motion && motion.id() == mc.player.getId()) {
            Vec3 movement = motion.movement();
            double length = new Vec3(movement.x, 0.0, movement.z).length();
            if (length >= 0.6) this.velocityDelay = 60;
        }
    }

    @EventTarget
    @EventPriority(4)
    public void onMoveInput(MoveInputEvent e) {
        double limit = this.bps.get();
        if (limit >= 10.0) return;
        if (getSpeed() > limit / 20.0) {
            e.setForward(0.0F);
            e.setStrafe(0.0F);
        }
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return;
        if (!e.isPre()) {
            if (mc.player.onGround()) {
                this.airTicks = 0;
                this.onGroundTicks++;
            } else {
                this.airTicks++;
                this.onGroundTicks = 0;
            }
            return;
        }
        if (this.pos == null) return;
        if (!this.canBuildNow && this.rotationDelay > 0) mc.options.keyJump.setDown(false);
        if (this.onTickRot.get() || !this.canBuildNow && this.rotationDelay > 0) {
            if (this.mode.is("Telly Bridge") && this.airTicks < this.tellyTicks.getInt()) return;
            this.c06Place();
            return;
        }
        if (this.mode.is("Telly Bridge") && this.airTicks < this.tellyTicks.getInt()) return;
        if (!this.canBuildNow && !this.isPlacementReachable()) return;
        if (this.rotationDelay <= 0 && !this.mode.is("Telly Bridge") && !this.isRotationValidForPlacement(this.rots)) {
            return;
        }
        this.doSnap();
    }

    private static void send(net.minecraft.network.protocol.Packet<?> packet) {
        if (packet != null && mc.getConnection() != null) mc.getConnection().send(packet);
    }

    private void updatePlacement() {
        if (mc.player == null || mc.level == null) return;
        Vec3 eye = mc.player.getEyePosition();
        if (!this.canBuildNow) eye = mc.player.getEyePosition().add(mc.player.getDeltaMovement().multiply(2.0, 2.0, 2.0));
        if (mc.player.getDeltaMovement().y < 0.01) {
            FallingPlayer sim = new FallingPlayer(mc.player);
            sim.calculate(2);
            eye = new Vec3(eye.x, Math.max(sim.getEyePos().y, eye.y), eye.z);
        }

        BlockPos belowFeet = BlockPos.containing(eye.x, (float) this.baseY + 0.1F, eye.z);
        int feetX = belowFeet.getX();
        int feetZ = belowFeet.getZ();
        if (mc.level.getBlockState(belowFeet).entityCanStandOn(mc.level, belowFeet, mc.player)) return;
        if (this.checkBlock(eye, belowFeet)) return;

        for (int radius = 1; radius <= 6; radius++) {
            if (this.checkBlock(eye, new BlockPos(feetX, this.baseY - radius, feetZ))) return;
            for (int x = 1; x <= radius; x++) {
                for (int z = 0; z <= radius - x; z++) {
                    int yOff = radius - x - z;
                    for (int signX = 0; signX <= 1; signX++) {
                        for (int signZ = 0; signZ <= 1; signZ++) {
                            BlockPos test = new BlockPos(feetX + (signX == 0 ? x : -x),
                                    this.baseY - yOff, feetZ + (signZ == 0 ? z : -z));
                            if (this.checkBlock(eye, test)) return;
                        }
                    }
                }
            }
        }
    }

    private void findClutchTarget() {
        if (mc.player == null || mc.level == null) return;
        BlockPos feet = mc.player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 0; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos candidate = feet.offset(dx, dy, dz);
                    if (candidate.getY() >= feet.getY()) continue;
                    BlockState state = mc.level.getBlockState(candidate);
                    if (state.isAir() || !state.getFluidState().isEmpty()) continue;
                    double distance = candidate.distSqr(feet);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }
        if (best == null) return;
        for (Direction direction : Direction.values()) {
            BlockPos airPos = best.relative(direction);
            BlockState airState = mc.level.getBlockState(airPos);
            if (!airState.isAir() && !airState.canBeReplaced()) continue;
            if (mc.level.getBlockState(best).entityCanStandOnFace(mc.level, best, mc.player, direction)) {
                this.pos = new BlockPosWithFacing(best, direction.getOpposite());
                return;
            }
        }
    }

    private boolean checkBlock(Vec3 from, BlockPos bp) {
        if (!(mc.level.getBlockState(bp).getBlock() instanceof AirBlock)) return false;
        Vec3 center = new Vec3(bp.getX() + 0.5, bp.getY() + 0.5F, bp.getZ() + 0.5);
        for (Direction direction : Direction.values()) {
            Vec3 hit = center.add(new Vec3(direction.getUnitVec3i().getX() * 0.5,
                    direction.getUnitVec3i().getY() * 0.5, direction.getUnitVec3i().getZ() * 0.5));
            Vec3i baseBlock = bp.offset(direction.getUnitVec3i());
            BlockPos offset = new BlockPos(baseBlock.getX(), baseBlock.getY(), baseBlock.getZ());
            if (mc.level.getBlockState(offset).entityCanStandOnFace(mc.level, offset, mc.player, direction)) {
                Vec3 relevant = hit.subtract(from);
                if (relevant.lengthSqr() <= 20.25 && relevant.normalize()
                        .dot(Vec3.atLowerCornerOf(direction.getUnitVec3i()).normalize()) >= 0.0) {
                    this.pos = new BlockPosWithFacing(offset, direction.getOpposite());
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isRotationValidForPlacement(Vector2f rotation) {
        if (this.pos == null || rotation == null || mc.player == null) return false;
        return RayTraceUtils.didHitBlockFace(mc.player, rotation.getX(), rotation.getY(),
                this.pos.position(), this.pos.facing(), true);
    }

    private boolean isPlacementReachable() {
        if (this.pos == null || mc.player == null) return false;
        Vec3 blockCenter = new Vec3(this.pos.position().getX() + 0.5, this.pos.position().getY() + 0.5,
                this.pos.position().getZ() + 0.5);
        Vec3 hitPoint = blockCenter.add(new Vec3(this.pos.facing().getUnitVec3i().getX() * 0.5,
                this.pos.facing().getUnitVec3i().getY() * 0.5, this.pos.facing().getUnitVec3i().getZ() * 0.5));
        Vec3 toBlock = hitPoint.subtract(mc.player.getEyePosition());
        return toBlock.lengthSqr() <= 20.25 && toBlock.normalize()
                .dot(Vec3.atLowerCornerOf(this.pos.facing().getUnitVec3i().multiply(-1)).normalize()) >= 0.0;
    }

    private void calculateTargetRotation() {
        if (this.pos == null || mc.player == null) return;
        float realYaw = mc.player.getYRot();
        if (mc.options.keyDown.isDown()) {
            realYaw += 180.0F;
            if (mc.options.keyLeft.isDown()) realYaw += 45.0F;
            else if (mc.options.keyRight.isDown()) realYaw -= 45.0F;
        } else if (mc.options.keyUp.isDown()) {
            if (mc.options.keyLeft.isDown()) realYaw -= 45.0F;
            else if (mc.options.keyRight.isDown()) realYaw += 45.0F;
        } else if (mc.options.keyRight.isDown()) {
            realYaw += 90.0F;
        } else if (mc.options.keyLeft.isDown()) {
            realYaw -= 90.0F;
        }

        float yaw = realYaw - 180.0F;
        float idealPitch = 75.5F;
        float currentPitch = this.correctRotation.getY();

        this.aimBranch = "ideal-75.5";
        if (this.isRotationValidForPlacement(new Vector2f(yaw, idealPitch))) {
            this.applyRotationWithSpeed(yaw, idealPitch);
            return;
        }
        this.aimBranch = "keep-current";
        if (this.isRotationValidForPlacement(new Vector2f(yaw, currentPitch))) {
            this.applyRotationWithSpeed(yaw, currentPitch);
            return;
        }
        this.aimBranch = "nearest-from-current";
        Float nearest = this.findValidPitchNear(yaw, currentPitch);
        if (nearest != null) {
            this.applyRotationWithSpeed(yaw, nearest);
            return;
        }
        this.aimBranch = "nearest-to-ideal";
        Float towardIdeal = this.findValidPitchNear(yaw, idealPitch);
        if (towardIdeal != null) {
            this.applyRotationWithSpeed(yaw, towardIdeal);
            return;
        }
        this.aimBranch = "optimal-search";
        Vector2f optimal = this.findOptimalRotation(yaw);
        if (optimal != null) {
            this.applyRotationWithSpeed(optimal.getX(), optimal.getY());
            return;
        }
        this.aimBranch = "hold";
        this.applyRotationWithSpeed(yaw, currentPitch);
    }

    private Float findValidPitchNear(float yaw, float center) {
        if (this.pos == null) return null;
        for (float offset = 0.0F; offset <= 30.0F; offset += 0.3F) {
            float down = Mth.clamp(center - offset, -90.0F, 90.0F);
            if (this.isRotationValidForPlacement(new Vector2f(yaw, down))) return down;
            float up = center + offset;
            if (up <= 90.0F && this.isRotationValidForPlacement(new Vector2f(yaw, up))) return up;
        }
        return null;
    }

    private Vector2f findOptimalRotation(float baseYaw) {
        if (this.pos == null) return null;
        for (float yawOffset = 0.0F; yawOffset < 180.0F; yawOffset++) {
            float currentPitch = this.correctRotation.getY();
            for (float pitchOffset = 0.0F; pitchOffset < 25.0F; pitchOffset++) {
                for (int i = 0; i < 2; i++) {
                    float testPitch = Mth.clamp(currentPitch - pitchOffset * (i == 0 ? 1.0F : -1.0F), -90.0F, 90.0F);
                    if (this.isRotationValidForPlacement(new Vector2f(baseYaw + yawOffset, testPitch))) {
                        return new Vector2f(baseYaw + yawOffset, testPitch);
                    }
                    if (this.isRotationValidForPlacement(new Vector2f(baseYaw - yawOffset, testPitch))) {
                        return new Vector2f(baseYaw - yawOffset, testPitch);
                    }
                }
            }
        }
        return null;
    }

    private void applyRotationWithSpeed(float yaw, float pitch) {
        if (this.pos == null || mc.player == null) return;
        this.correctRotation.set(yaw, pitch);
        float speed = this.rotationSpeed.getFloat();
        float nextYaw = RotationUtils.rotateToYaw(speed, this.rots.getX(), this.correctRotation.getX());
        float nextPitch = RotationUtils.updateRotation(this.rots.getY(), this.correctRotation.getY(), speed);
        Vector2f quantized = patchConstantRotation(new Vector2f(nextYaw, nextPitch), this.lastSentRot);
        this.rots.set(quantized.getX(), quantized.getY());
    }

    private static Vector2f patchConstantRotation(Vector2f rotation, Vector2f anchor) {
        double sensitivity = mc.options.sensitivity().get() * 0.6 + 0.2;
        double step = sensitivity * sensitivity * sensitivity * 8.0 * 0.15;
        float yaw = anchor.getX() + (float) (Math.round((rotation.getX() - anchor.getX()) / step) * step);
        float pitch = Mth.clamp(anchor.getY() + (float) (Math.round((rotation.getY() - anchor.getY()) / step) * step),
                -90.0F, 90.0F);
        return new Vector2f(yaw, pitch);
    }

    private boolean hasPlaceableInHand() {
        if (mc.player == null) return false;
        return isValidStack(mc.player.getMainHandItem()) || isValidStack(mc.player.getOffhandItem());
    }

    private InteractionHand placementHand() {
        if (mc.player == null || isValidStack(mc.player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        return isValidStack(mc.player.getOffhandItem()) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private boolean shouldBuild() {
        if (mc.player == null || mc.level == null) return false;
        BlockPos below = BlockPos.containing(mc.player.getX(), mc.player.getY() - 0.5, mc.player.getZ());
        return mc.level.isEmptyBlock(below) && this.hasPlaceableInHand();
    }

    private float rotationJitter() {
        float offset = (float) (Math.random() * 0.04 + 0.03);
        return Math.random() > 0.5 ? offset : -offset;
    }

    private void clearSkipTicks() {
        if (this.usedSkipTicks) this.usedSkipTicks = false;
    }

    private boolean placeDelayBlocked() {
        if (mc.player == null || !this.mode.is("Telly Bridge")) return false;
        int placeDelay = this.tellyPlaceDelay.getInt();
        return placeDelay > 0 && mc.player.tickCount - this.lastPlaceTick < placeDelay;
    }

    private void markPlaced() {
        if (mc.player != null) this.lastPlaceTick = mc.player.tickCount;
    }

    private void c06Place() {
        if (this.pos == null || mc.player == null || mc.gameMode == null) return;
        if (this.placeDelayBlocked()) return;
        Direction facing = this.pos.facing();
        if (facing == null) return;
        if (facing == Direction.UP && !mc.player.onGround() && PlayerUtils.movementInput()
                && !mc.options.keyJump.isDown() && !this.mode.is("Normal")) {
            return;
        }
        if (!this.hasPlaceableInHand()) return;

        InteractionHand hand = this.placementHand();
        Vec3 faceCenter = getFaceCenter(this.pos.position(), facing);
        Vector2f targetRot = RotationUtils.getRotations(mc.player.getEyePosition(), faceCenter).toVector();
        if (!this.isRotationValidForPlacement(targetRot)) return;

        float origYaw = mc.player.getYRot();
        float origPitch = mc.player.getXRot();
        send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                targetRot.getX() + rotationJitter(), targetRot.getY() + rotationJitter(),
                mc.player.onGround(), mc.player.horizontalCollision));
        BlockHitResult hit = new BlockHitResult(getHitVec(this.pos.position(), facing), facing,
                this.pos.position(), false);
        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);
        if (result == InteractionResult.SUCCESS) {
            mc.player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            this.markPlaced();
        }
        send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                origYaw + rotationJitter(), origPitch + rotationJitter(),
                mc.player.onGround(), mc.player.horizontalCollision));
    }

    private void doSnap() {
        if (this.pos == null || mc.player == null || mc.gameMode == null) return;
        if (this.placeDelayBlocked()) return;
        if (!this.hasPlaceableInHand()) return;

        InteractionHand hand = this.placementHand();
        if (!this.canBuildNow && !this.pos.position().equals(this.lastC05Position)) {
            this.lastC05Position = this.pos.position();
            this.aimBranch = "c05-snap";
            Vec3 c05Target = getFaceCenter(this.pos.position(), this.pos.facing());
            Vec3 c05Eye = new Vec3(mc.player.getX() + mc.player.getDeltaMovement().x,
                    mc.player.getY() + mc.player.getEyeHeight() + mc.player.getDeltaMovement().y,
                    mc.player.getZ() + mc.player.getDeltaMovement().z);
            Vector2f c05Rotation = RotationUtils.getRotations(c05Eye, c05Target).toVector();
            float yaw = c05Rotation.getX();
            if (yaw > -360.0F && yaw < 360.0F) yaw += 720.0F;
            this.lastSentRot.set(c05Rotation.getX(), c05Rotation.getY());
            send(new ServerboundMovePlayerPacket.Rot(yaw, c05Rotation.getY(),
                    mc.player.onGround(), mc.player.horizontalCollision));
        }

        Direction facing = this.pos.facing();
        if (facing == null) return;
        boolean jumpHeld = isKeyDown(mc.options.keyJump);
        if (facing == Direction.UP && !mc.player.onGround() && PlayerUtils.movementInput()
                && !jumpHeld && !this.mode.is("Normal")) {
            return;
        }
        if (!this.shouldBuild()) return;
        if (!this.isRotationValidForPlacement(this.rots)) return;

        BlockHitResult position = RayTraceUtils.getFacedBlock(this.rots.getX(), this.rots.getY());
        if (position == null) return;
        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, position);
        if (result == InteractionResult.SUCCESS) {
            mc.player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            this.markPlaced();
        }
    }

    private void doPlaceFromTick() {
        if (this.pos == null || mc.player == null || !isValidStack(mc.player.getMainHandItem())) return;
        if (this.placeDelayBlocked()) return;
        Direction face = this.pos.facing();
        boolean jumpHeld = isKeyDown(mc.options.keyJump);
        if (face != null && (face != Direction.UP || mc.player.onGround() || !PlayerUtils.movementInput()
                || jumpHeld || this.mode.is("Normal")) && this.shouldBuild()) {
            InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(getVec3(this.pos.position(), face), face, this.pos.position(), false));
            if (result == InteractionResult.SUCCESS) {
                mc.player.swing(InteractionHand.MAIN_HAND,
                        net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                this.markPlaced();
                this.pos = null;
            }
        }
    }

    public static Vec3 getVec3(BlockPos pos, Direction face) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        if (face != Direction.UP && face != Direction.DOWN) {
            y += 0.08;
        } else {
            x += MathUtils.getRandomDoubleInRange(0.3, -0.3);
            z += MathUtils.getRandomDoubleInRange(0.3, -0.3);
        }
        if (face == Direction.WEST || face == Direction.EAST) z += MathUtils.getRandomDoubleInRange(0.3, -0.3);
        if (face == Direction.SOUTH || face == Direction.NORTH) x += MathUtils.getRandomDoubleInRange(0.3, -0.3);
        return new Vec3(x, y, z);
    }

    public static Vec3 getFaceCenter(BlockPos pos, Direction direction) {
        return new Vec3(pos.getX() + 0.5 + direction.getUnitVec3i().getX() * 0.5,
                pos.getY() + 0.5 + direction.getUnitVec3i().getY() * 0.5,
                pos.getZ() + 0.5 + direction.getUnitVec3i().getZ() * 0.5);
    }

    public static Vec3 getHitVec(BlockPos pos, Direction direction) {
        return getFaceCenter(pos, direction);
    }

    public record BlockPosWithFacing(BlockPos position, Direction facing) {
    }
}
