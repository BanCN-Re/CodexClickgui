package wtf.carnation.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Port of Naven's {@code tech.naven.utils.rotation.RayTraceUtil}, keeping the entity-aware
 * cast used by AutoThrow.
 */
public final class RayTraceUtil {
    private static final Minecraft mc = Minecraft.getInstance();

    private RayTraceUtil() {
    }

    public static HitResult rayTraceBlocks(Vec3 from, Vec3 to, boolean stopOnLiquid,
                                           boolean ignoreBlockWithoutBoundingBox,
                                           boolean returnLastUncollidableBlock, Entity entity) {
        ClipContext.Block block = ignoreBlockWithoutBoundingBox ? ClipContext.Block.COLLIDER
                : returnLastUncollidableBlock ? ClipContext.Block.VISUAL : ClipContext.Block.OUTLINE;
        ClipContext.Fluid fluid = stopOnLiquid ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE;
        return mc.level.clip(new ClipContext(from, to, block, fluid, entity));
    }

    public static HitResult rayCast(Rotation rotation, double range, float expand, Entity filterEntity,
                                    Entity targetEntity, boolean throughWalls) {
        if (filterEntity == null || mc.level == null) return null;
        float partialTicks = 1.0F;
        Vec3 eyePosition = filterEntity.getEyePosition(partialTicks);
        Vec3 lookVector = RotationUtils.getVectorForRotation(rotation);
        Vec3 targetVec = eyePosition.add(lookVector.x * range, lookVector.y * range, lookVector.z * range);
        BlockHitResult blockHit = null;
        double blockDistance = range;
        if (!throughWalls) {
            blockHit = mc.level.clip(new ClipContext(eyePosition, targetVec,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, filterEntity));
            blockDistance = blockHit.getType() == HitResult.Type.BLOCK
                    ? eyePosition.distanceTo(blockHit.getLocation()) : range;
        }

        double expandedRange = Math.min(range, blockDistance) + (double) expand;
        AABB searchBox = new AABB(
                eyePosition.x - expandedRange, eyePosition.y - expandedRange, eyePosition.z - expandedRange,
                eyePosition.x + expandedRange, eyePosition.y + expandedRange, eyePosition.z + expandedRange);
        List<Entity> entities = mc.level.getEntitiesOfClass(Entity.class, searchBox,
                ex -> ex != filterEntity && (targetEntity == null || ex == targetEntity)
                        && EntitySelector.NO_SPECTATORS.test(ex) && ex.isPickable());
        Entity pointedEntity = null;
        Vec3 hitVec = null;
        double closestDistance = Math.min(range, blockDistance);
        closestDistance *= closestDistance;

        for (Entity e : entities) {
            AABB entityBox = e.getBoundingBox().inflate((double) expand);
            Optional<Vec3> intercept = entityBox.clip(eyePosition, targetVec);
            if (intercept.isEmpty()) continue;
            Vec3 interceptPoint = intercept.get();
            double distSq = eyePosition.distanceToSqr(interceptPoint);
            if (distSq >= closestDistance) continue;

            boolean canHit = true;
            if (!throughWalls) {
                Vec3 entityCenter = entityBox.getCenter();
                BlockHitResult wallCheck = mc.level.clip(new ClipContext(eyePosition, entityCenter,
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, filterEntity));
                if (wallCheck.getType() == HitResult.Type.BLOCK
                        && eyePosition.distanceToSqr(wallCheck.getLocation()) <= distSq) {
                    canHit = false;
                }
            }
            if (canHit) {
                closestDistance = distSq;
                pointedEntity = e;
                hitVec = interceptPoint;
            }
        }

        if (pointedEntity != null) return new EntityHitResult(pointedEntity, hitVec);
        return !throughWalls && blockHit != null ? blockHit
                : mc.level.clip(new ClipContext(eyePosition, targetVec,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, filterEntity));
    }
}
