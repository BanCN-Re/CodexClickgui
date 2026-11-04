package wtf.carnation.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Port of Naven's {@code tech.naven.utils.rotation.RotationUtils}.
 *
 * <p>Keeps the original aiming math — angle helpers, hitbox-point brute force for the aura's
 * rotation data, and the sensitivity-fixed rotation used for server-accurate angles.</p>
 */
public final class RotationUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    private RotationUtils() {
    }

    public static float getAngleDifference(float a, float b) {
        return ((a - b) % 360.0F + 540.0F) % 360.0F - 180.0F;
    }

    public static Vec3 getLook() {
        return getLook(mc.player.getYRot(), mc.player.getXRot());
    }

    public static Vector2f getFixedRotation(float yaw, float pitch, float lastYaw, float lastPitch) {
        float f = (float) (mc.options.sensitivity().get() * 0.6F + 0.2F);
        float gcd = f * f * f * 1.2F;
        float deltaYaw = yaw - lastYaw;
        float deltaPitch = pitch - lastPitch;
        float fixedDeltaYaw = deltaYaw - deltaYaw % gcd;
        float fixedDeltaPitch = deltaPitch - deltaPitch % gcd;
        return new Vector2f(lastYaw + fixedDeltaYaw, lastPitch + fixedDeltaPitch);
    }

    public static Vec3 getLook(float yaw, float pitch) {
        float f = Mth.cos(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f1 = Mth.sin(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f2 = -Mth.cos(-pitch * (float) (Math.PI / 180.0));
        float f3 = Mth.sin(-pitch * (float) (Math.PI / 180.0));
        return new Vec3(f1 * f2, f3, f * f2);
    }

    public static Rotation getRotations(Vec3 eye, Vec3 target) {
        double x = target.x - eye.x;
        double y = target.y - eye.y;
        double z = target.z - eye.z;
        double diffXZ = Math.sqrt(x * x + z * z);
        float yaw = (float) Math.toDegrees(Math.atan2(z, x)) - 90.0F;
        float pitch = (float) (-Math.toDegrees(Math.atan2(y, diffXZ)));
        return new Rotation(Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch));
    }

    public static Vector2f diffCalcVector(double diffX, double diffY, double diffZ) {
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));
        return new Vector2f(Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch));
    }

    public static Vector2f getRotations(Entity entity) {
        if (entity == null) return new Vector2f(0.0F, 0.0F);
        double diffX = entity.getX() - mc.player.getX();
        double diffZ = entity.getZ() - mc.player.getZ();
        double diffY = entity.getY() + entity.getEyeHeight() - (mc.player.getY() + mc.player.getEyeHeight());
        return diffCalcVector(diffX, diffY, diffZ);
    }

    public static float rotateToYaw(float yawSpeed, float currentYaw, float calcYaw) {
        return updateRotation(currentYaw, calcYaw, yawSpeed);
    }

    public static float updateRotation(float current, float calc, float maxDelta) {
        float f = Mth.wrapDegrees(calc - current);
        if (f > maxDelta) f = maxDelta;
        if (f < -maxDelta) f = -maxDelta;
        return current + f;
    }

    public static boolean inFoV(Entity entity, float fov) {
        Vector2f rotations = getRotations(entity);
        float diff = Math.abs(mc.player.getYRot() % 360.0F - rotations.x);
        float minDiff = Math.abs(Math.min(diff, 360.0F - diff));
        return minDiff <= fov;
    }

    public static float getDistanceBetweenAngles(float angle1, float angle2) {
        float angle3 = Math.abs(angle1 - angle2) % 360.0F;
        if (angle3 > 180.0F) angle3 = 0.0F;
        return angle3;
    }

    public static double getMinDistance(Entity target, Vector2f rotations) {
        double minDistance = Double.MAX_VALUE;
        for (float eye : List.of(mc.player.getEyeHeight())) {
            Vec3 playerPosition = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            Vec3 eyePos = playerPosition.add(0.0, eye, 0.0);
            minDistance = Math.min(minDistance, getDistance(target, eyePos, rotations));
        }
        return minDistance;
    }

    public static double getDistance(Entity target, Vec3 eyePos, Vector2f rotations) {
        AABB targetBox = target.getBoundingBox();
        HitResult position = getIntercept(targetBox, rotations, eyePos, 6.0);
        if (position != null) {
            return position.getLocation().distanceTo(eyePos);
        }
        return 1000.0;
    }

    public static HitResult getIntercept(AABB targetBox, Vector2f rotations, Vec3 eyePos, double reach) {
        Vec3 vec31 = getLook(rotations.x, rotations.y);
        Vec3 vec32 = eyePos.add(vec31.x * reach, vec31.y * reach, vec31.z * reach);
        return ProjectileUtil.getEntityHitResult(mc.player, eyePos, vec32, targetBox,
                entity -> !entity.isSpectator() && entity.isPickable(), reach * reach);
    }

    public static Vec3 getClosestPoint(Vec3 vec, AABB aabb) {
        double closestX = Math.max(aabb.minX, Math.min(vec.x, aabb.maxX));
        double closestY = Math.max(aabb.minY, Math.min(vec.y, aabb.maxY));
        double closestZ = Math.max(aabb.minZ, Math.min(vec.z, aabb.maxZ));
        return new Vec3(closestX, closestY, closestZ);
    }

    public static Vec3 getVectorForRotation(Rotation rotation) {
        float yawCos = (float) Math.cos(-rotation.getYaw() * (float) (Math.PI / 180.0) - (float) Math.PI);
        float yawSin = (float) Math.sin(-rotation.getYaw() * (float) (Math.PI / 180.0) - (float) Math.PI);
        float pitchCos = (float) (-Math.cos(-rotation.getPitch() * (float) (Math.PI / 180.0)));
        float pitchSin = (float) Math.sin(-rotation.getPitch() * (float) (Math.PI / 180.0));
        return new Vec3(yawSin * pitchCos, pitchSin, yawCos * pitchCos);
    }

    /** Brute-forces the hitbox points until a ray lands, like Naven's aura rotation search. */
    public static Data getRotationDataToEntity(Entity target) {
        Vec3 eyePos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                .add(0.0, mc.player.getEyeHeight(), 0.0);
        AABB targetBox = target.getBoundingBox();
        double spacing = 0.1;
        Set<Vec3> points = new LinkedHashSet<>();
        points.add(targetBox.getCenter());
        points.add(getClosestPoint(eyePos, targetBox));

        for (double x = targetBox.minX; x <= targetBox.maxX; x += spacing) {
            for (double y = targetBox.minY; y <= targetBox.maxY; y += spacing) {
                points.add(new Vec3(x, y, targetBox.minZ));
                points.add(new Vec3(x, y, targetBox.maxZ));
            }
        }
        for (double x = targetBox.minX; x <= targetBox.maxX; x += spacing) {
            for (double z = targetBox.minZ; z <= targetBox.maxZ; z += spacing) {
                points.add(new Vec3(x, targetBox.minY, z));
                points.add(new Vec3(x, targetBox.maxY, z));
            }
        }
        for (double y = targetBox.minY; y <= targetBox.maxY; y += spacing) {
            for (double z = targetBox.minZ; z <= targetBox.maxZ; z += spacing) {
                points.add(new Vec3(targetBox.minX, y, z));
                points.add(new Vec3(targetBox.maxX, y, z));
            }
        }

        for (Vec3 point : points) {
            Rotation bruteRotations = getRotations(eyePos, point);
            HitResult bruteHitResult = rayTrace(bruteRotations);
            if (checkHitResult(eyePos, bruteHitResult, target)) {
                Vec3 location = bruteHitResult.getLocation();
                Vector2f last = RotationManager.rotations();
                float lastYaw = last == null ? mc.player.getYRot() : last.x;
                float lastPitch = last == null ? mc.player.getXRot() : last.y;
                return new Data(eyePos, location, location.distanceTo(eyePos),
                        getFixedRotation(bruteRotations.getYaw(), bruteRotations.getPitch(), lastYaw, lastPitch));
            }
        }
        return new Data(eyePos, eyePos, 1000.0, null);
    }

    private static boolean checkHitResult(Vec3 eyePos, HitResult result, Entity target) {
        if (result != null && result.getType() == HitResult.Type.ENTITY
                && ((EntityHitResult) result).getEntity() == target) {
            Vec3 intercept = result.getLocation();
            return isVecInside(target.getBoundingBox(), eyePos) || intercept.distanceTo(eyePos) <= 3.0;
        }
        return false;
    }

    private static boolean isVecInside(AABB box, Vec3 vec) {
        return vec.x > box.minX && vec.x < box.maxX && vec.y > box.minY && vec.y < box.maxY
                && vec.z > box.minZ && vec.z < box.maxZ;
    }

    private static HitResult rayTrace(Rotation rotations) {
        double d0 = mc.player.blockInteractionRange();
        HitResult hitResult = mc.player.level().clip(new ClipContext(
                mc.player.getEyePosition(1.0F),
                mc.player.getEyePosition(1.0F).add(getVectorForRotation(rotations).scale(d0)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        Vec3 vec3 = mc.player.getEyePosition(1.0F);
        boolean flag = false;
        double d1 = d0;
        if (d0 > 3.0) flag = true;
        d1 *= d1;
        if (hitResult != null) d1 = hitResult.getLocation().distanceToSqr(vec3);

        Vec3 vec31 = getLook(rotations.getYaw(), rotations.getPitch());
        Vec3 vec32 = vec3.add(vec31.x * d0, vec31.y * d0, vec31.z * d0);
        AABB aabb = mc.player.getBoundingBox().expandTowards(vec31.scale(d0)).inflate(1.0, 1.0, 1.0);
        EntityHitResult entityhitresult = ProjectileUtil.getEntityHitResult(mc.player, vec3, vec32, aabb,
                entity -> !entity.isSpectator() && entity.isPickable(), d1);
        if (entityhitresult != null) {
            Vec3 vec33 = entityhitresult.getLocation();
            double d2 = vec3.distanceToSqr(vec33);
            if (flag && d2 > 9.0) {
                hitResult = BlockHitResult.miss(vec33, Direction.getApproximateNearest(vec31.x, vec31.y, vec31.z),
                        BlockPos.containing(vec33));
            } else if (d2 < d1 || hitResult == null) {
                hitResult = entityhitresult;
            }
        }
        return hitResult;
    }

    /** Rotates the strafe input so movement keeps following the server-side yaw. */
    public static Vector2f fixMovement(float forward, float strafe, float serverYaw, float playerYaw) {
        double diff = Mth.wrapDegrees(serverYaw - playerYaw);
        float sin = (float) Math.sin(Math.toRadians(diff));
        float cos = (float) Math.cos(Math.toRadians(diff));
        return new Vector2f(strafe * cos - forward * sin, strafe * sin + forward * cos);
    }

    public static final class Data {
        private final Vec3 eye;
        private final Vec3 hitVec;
        private final double distance;
        private final Vector2f rotation;

        public Data(Vec3 eye, Vec3 hitVec, double distance, Vector2f rotation) {
            this.eye = eye;
            this.hitVec = hitVec;
            this.distance = distance;
            this.rotation = rotation;
        }

        public Vec3 getEye() {
            return eye;
        }

        public Vec3 getHitVec() {
            return hitVec;
        }

        public double getDistance() {
            return distance;
        }

        public Vector2f getRotation() {
            return rotation;
        }
    }
}
