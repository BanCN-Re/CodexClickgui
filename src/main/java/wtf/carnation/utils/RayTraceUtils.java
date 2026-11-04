package wtf.carnation.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;
import java.util.function.Predicate;

/** Naven's {@code tech.naven.utils.RayTraceUtils}, keeping the block-face picking Scaffold needs. */
public final class RayTraceUtils {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final double EPSILON = 1.0E-7D;
    public static Vec3 eyePos = null;

    private RayTraceUtils() {
    }

    public static void updateEyePos() {
        if (mc.player != null) eyePos = mc.player.getEyePosition(1.0F);
    }

    public static Vec3 eye() {
        if (eyePos != null) return eyePos;
        if (mc.player == null) return Vec3.ZERO;
        eyePos = mc.player.getEyePosition(1.0F);
        return eyePos;
    }

    public static Vec3 calculateViewVector(float xRot, float yRot) {
        float f = xRot * (float) (Math.PI / 180.0);
        float f1 = -yRot * (float) (Math.PI / 180.0);
        float f2 = Mth.cos(f1);
        float f3 = Mth.sin(f1);
        float f4 = Mth.cos(f);
        float f5 = Mth.sin(f);
        return new Vec3(f3 * f4, -f5, f2 * f4);
    }

    public static HitResult pick(double hitDistance, float partialTicks, boolean hitFluids, float yRot, float xRot) {
        if (mc.player == null || mc.level == null) return null;
        Vec3 from = new Vec3(mc.player.getX(), mc.player.getY() + 1.62, mc.player.getZ());
        Vec3 direction = calculateViewVector(xRot, yRot);
        Vec3 to = from.add(direction.x * hitDistance, direction.y * hitDistance, direction.z * hitDistance);
        return mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
                hitFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, mc.player));
    }

    public static net.minecraft.world.phys.EntityHitResult calculateIntercept(AABB box, Vec3 from, Vec3 to) {
        Optional<Vec3> intercept = box.clip(from, to);
        return intercept.map(vec -> new net.minecraft.world.phys.EntityHitResult(null, vec)).orElse(null);
    }

    public static boolean didHitBlockFace(Player player, float yaw, float pitch, BlockPos targetPos,
                                          Direction expectedFace, boolean strict) {
        if (player == null || expectedFace == null) return false;
        BlockHitResult result = getFacedBlock(yaw, pitch);
        if (result == null || !targetPos.equals(result.getBlockPos())) return false;
        return !strict || expectedFace == result.getDirection();
    }

    public static BlockHitResult getFacedBlock(float yaw, float pitch) {
        return getFacedBlock(yaw, pitch, RayTraceUtils::isIgnoredBlock);
    }

    public static BlockHitResult getFacedBlock(float yaw, float pitch, Predicate<BlockState> ignorePredicate) {
        if (mc.player == null || mc.level == null) return null;
        double reachDistance = mc.player.blockInteractionRange();
        if (yaw == 0.0F && pitch == 0.0F) return null;

        Vec3 startPos = eye();
        Vec3 direction = calculateViewVector(pitch, yaw);
        Vec3 endPos = startPos.add(direction.x * reachDistance, direction.y * reachDistance, direction.z * reachDistance);
        if (direction.x == 0.0) direction = new Vec3(EPSILON, direction.y, direction.z);
        if (direction.y == 0.0) direction = new Vec3(direction.x, EPSILON, direction.z);
        if (direction.z == 0.0) direction = new Vec3(direction.x, direction.y, EPSILON);

        BlockPos currentPos = BlockPos.containing(startPos.x, startPos.y, startPos.z);
        int stepX = (int) Math.signum(direction.x);
        int stepY = (int) Math.signum(direction.y);
        int stepZ = (int) Math.signum(direction.z);

        double nextBoundaryX = stepX > 0 ? currentPos.getX() + 1 : currentPos.getX();
        double nextBoundaryY = stepY > 0 ? currentPos.getY() + 1 : currentPos.getY();
        double nextBoundaryZ = stepZ > 0 ? currentPos.getZ() + 1 : currentPos.getZ();

        double tMaxX = (nextBoundaryX - startPos.x) / direction.x;
        double tMaxY = (nextBoundaryY - startPos.y) / direction.y;
        double tMaxZ = (nextBoundaryZ - startPos.z) / direction.z;
        double tDeltaX = stepX / direction.x;
        double tDeltaY = stepY / direction.y;
        double tDeltaZ = stepZ / direction.z;

        while (startPos.distanceTo(Vec3.atCenterOf(currentPos)) <= reachDistance) {
            if (!mc.level.isEmptyBlock(currentPos)) {
                BlockState state = mc.level.getBlockState(currentPos);
                if (!ignorePredicate.test(state)) {
                    VoxelShape shape;
                    FluidState fluidState = mc.level.getFluidState(currentPos);
                    if (fluidState.isSource() && fluidState.getType() == Fluids.WATER) {
                        shape = Shapes.block();
                    } else {
                        shape = state.getCollisionShape(mc.level, currentPos, CollisionContext.of(mc.player));
                    }
                    if (!shape.isEmpty()) {
                        for (AABB localBox : shape.toAabbs()) {
                            AABB box = localBox.move(currentPos.getX(), currentPos.getY(), currentPos.getZ());
                            Optional<Vec3> intercept = box.clip(startPos, endPos);
                            if (intercept.isPresent()) {
                                Vec3 hitVec = intercept.get();
                                Direction side = getHitFaceFromBox(hitVec, box);
                                boolean inside = box.contains(startPos);
                                return new BlockHitResult(hitVec, side, currentPos, inside);
                            }
                        }
                    }
                }
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    currentPos = currentPos.offset(stepX, 0, 0);
                    tMaxX += tDeltaX;
                } else {
                    currentPos = currentPos.offset(0, 0, stepZ);
                    tMaxZ += tDeltaZ;
                }
            } else if (tMaxY < tMaxZ) {
                currentPos = currentPos.offset(0, stepY, 0);
                tMaxY += tDeltaY;
            } else {
                currentPos = currentPos.offset(0, 0, stepZ);
                tMaxZ += tDeltaZ;
            }
        }
        return null;
    }

    public static boolean isIgnoredBlock(BlockState state) {
        return state.getBlock() instanceof BushBlock
                || state.getBlock() instanceof SnowLayerBlock
                || state.getBlock() instanceof AirBlock
                || state.getBlock() instanceof LiquidBlock;
    }

    private static Direction getHitFaceFromBox(Vec3 hit, AABB box) {
        final double eps = 1.0E-7;
        if (Math.abs(hit.x - box.minX) <= eps) return Direction.WEST;
        if (Math.abs(hit.x - box.maxX) <= eps) return Direction.EAST;
        if (Math.abs(hit.y - box.minY) <= eps) return Direction.DOWN;
        if (Math.abs(hit.y - box.maxY) <= eps) return Direction.UP;
        if (Math.abs(hit.z - box.minZ) <= eps) return Direction.NORTH;
        if (Math.abs(hit.z - box.maxZ) <= eps) return Direction.SOUTH;

        double m = Math.abs(hit.x - box.minX);
        Direction d = Direction.WEST;
        if (Math.abs(hit.x - box.maxX) < m) { m = Math.abs(hit.x - box.maxX); d = Direction.EAST; }
        if (Math.abs(hit.y - box.minY) < m) { m = Math.abs(hit.y - box.minY); d = Direction.DOWN; }
        if (Math.abs(hit.y - box.maxY) < m) { m = Math.abs(hit.y - box.maxY); d = Direction.UP; }
        if (Math.abs(hit.z - box.minZ) < m) { m = Math.abs(hit.z - box.minZ); d = Direction.NORTH; }
        if (Math.abs(hit.z - box.maxZ) < m) { d = Direction.SOUTH; }
        return d;
    }
}
