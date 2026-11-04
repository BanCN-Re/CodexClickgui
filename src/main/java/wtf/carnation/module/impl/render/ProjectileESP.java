package wtf.carnation.module.impl.render;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render3DEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.render.projectiles.ProjectileData;
import wtf.carnation.module.impl.render.projectiles.datas.BasicProjectileData;
import wtf.carnation.module.impl.render.projectiles.datas.EntityArrowData;
import wtf.carnation.module.impl.render.projectiles.datas.EntityPotionData;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.utils.RayTraceUtil;
import wtf.carnation.utils.RayTraceUtils;
import wtf.carnation.utils.Render3DUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Port of Naven's {@code Projectile}: a predicted trajectory for the held throwable plus a traced
 * path for every projectile already in flight.
 *
 * <p>Naven runs this from {@code EventRender}, which 26.3 supplies as {@link Render3DEvent}; the
 * geometry goes through {@link Render3DUtils} and the end-of-path marker keeps the original's
 * per-face box shrink and colour rules.</p>
 */
public class ProjectileESP extends Module {
    private static final int MAX_PATH_STEPS = 1000;
    /** Naven stops a flight path once it falls out of the world. */
    private static final double VOID_Y = -128.0;
    private static final double DRAG = 0.99;
    private static final double WATER_DRAG = 0.8;
    private static final double THROW_MOTION_FACTOR = 0.4;
    private static final double BOW_MOTION_FACTOR = 1.0;

    private final ProjectileData arrowsData = new EntityArrowData();
    private final ProjectileData potionsData = new EntityPotionData();
    private final ProjectileData pearlData = new BasicProjectileData(
            new HashSet<>(Collections.singletonList(ThrownEnderpearl.class)), new Color(173, 12, 255));
    private final ProjectileData eggData = new BasicProjectileData(
            new HashSet<>(Collections.singletonList(ThrownEgg.class)), new Color(255, 238, 154));
    private final ProjectileData snowballData = new BasicProjectileData(
            new HashSet<>(Collections.singletonList(Snowball.class)), new Color(255, 255, 255));

    public final BoolValue showArrows = new BoolValue("Show Arrows", true);
    public final BoolValue showPearls = new BoolValue("Show Pearls", true);
    public final BoolValue showPotions = new BoolValue("Show Potions", false);
    public final BoolValue showEggs = new BoolValue("Show Eggs", false);
    public final BoolValue showSnowballs = new BoolValue("Show Snowballs", false);

    public ProjectileESP() {
        // Kept as "Projectile" so existing binds and configs still resolve; the class name avoids
        // the clash with net.minecraft.world.entity.projectile.Projectile.
        super("Projectile", Category.RENDER, "Renders projectiles");
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        Vec3 camera = event.camera();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof net.minecraft.world.entity.projectile.Projectile)) continue;
            ProjectileData data = this.dataFor(entity);
            if (data == null) continue;
            this.renderFlightPath(event, entity, data, camera);
        }

        Path predicted = this.predictPath(event.partialTicks());
        if (predicted == null || predicted.path.size() < 2) return;

        Render3DUtils.lineStrip(event.poseStack(), event.collector(), predicted.path, camera,
                1.0F, 1.0F, 1.0F, 1.0F);
        this.drawEndMarker(event, predicted, camera);
    }

    /** Traces a projectile already in flight: same drag, gravity and hit rules as the server. */
    private void renderFlightPath(Render3DEvent event, Entity entity, ProjectileData data, Vec3 camera) {
        if (entity.onGround()) return;
        if (entity.getX() == entity.xo && entity.getZ() == entity.zo) return;

        Color color = data.getColor(entity);
        if (color == null) color = Color.WHITE;

        List<Vec3> path = new ArrayList<>();
        double posX = entity.getX();
        double posY = entity.getY();
        double posZ = entity.getZ();
        double motionX = entity.getDeltaMovement().x;
        double motionY = entity.getDeltaMovement().y;
        double motionZ = entity.getDeltaMovement().z;
        path.add(new Vec3(posX, posY, posZ));

        while (true) {
            float half = data.getData1();
            float height = data.getData2();
            AABB box = new AABB(posX - half, posY, posZ - half, posX + half, posY + height, posZ + half);
            Vec3 from = new Vec3(posX, posY, posZ);
            Vec3 to = new Vec3(posX + motionX, posY + motionY, posZ + motionZ);

            HitResult hit = RayTraceUtil.rayTraceBlocks(from, to, false,
                    entity instanceof Arrow, false, entity);
            if (hit.getType() != HitResult.Type.MISS) {
                to = new Vec3(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
            }

            List<Entity> candidates = mc.level.getEntities(mc.player,
                    box.contract(motionX, motionY, motionZ).expandTowards(1.0, 1.0, 1.0));
            double nearest = 0.0;
            for (Entity candidate : candidates) {
                if (!(candidate instanceof LivingEntity)) continue;
                if (candidate instanceof Enderman) continue;
                if (!candidate.isPickable() || candidate.equals(mc.player)) continue;
                AABB candidateBox = candidate.getBoundingBox().expandTowards(0.3, 0.3, 0.3);
                EntityHitResult intercept = RayTraceUtils.calculateIntercept(candidateBox, from, to);
                if (intercept == null) continue;
                double distance = from.distanceTo(intercept.getLocation());
                if (distance < nearest || nearest == 0.0) {
                    nearest = distance;
                    hit = intercept;
                }
            }

            posX += motionX;
            posY += motionY;
            posZ += motionZ;
            if (hit.getType() != HitResult.Type.MISS) {
                posX = hit.getLocation().x;
                posY = hit.getLocation().y;
                posZ = hit.getLocation().z;
                path.add(new Vec3(posX, posY, posZ));
                break;
            }
            if (posY < VOID_Y) break;

            double drag = entity.isInWater() ? WATER_DRAG : DRAG;
            motionX *= drag;
            motionZ *= drag;
            motionY = motionY * drag - data.getGravity();
            path.add(new Vec3(posX + motionX, posY + motionY, posZ + motionZ));
        }

        Render3DUtils.lineStrip(event.poseStack(), event.collector(), path, camera,
                color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, 1.0F);
    }

    /** The trajectory of whatever throwable the player is holding, stepped until it hits something. */
    private Path predictPath(float partialTicks) {
        Player player = mc.player;
        if (player == null || mc.level == null) return null;

        ItemStack stack = player.getMainHandItem();
        Item item = stack.getItem();
        if (stack.isEmpty() || !isThrowable(item)) return null;

        double arrowX = player.xo + (player.getX() - player.xo) * partialTicks;
        double arrowY = player.yo + (player.getY() - player.yo) * partialTicks + player.getEyeHeight() - 0.1;
        double arrowZ = player.zo + (player.getZ() - player.zo) * partialTicks;
        double motionFactor = item instanceof ProjectileWeaponItem ? BOW_MOTION_FACTOR : THROW_MOTION_FACTOR;

        double yaw = Math.toRadians(Mth.lerp(partialTicks, player.yRotO, player.getYRot()));
        double pitch = Math.toRadians(Mth.lerp(partialTicks, player.xRotO, player.getXRot()));

        double motionX = -Math.sin(yaw) * Math.cos(pitch) * motionFactor;
        double motionY = -Math.sin(pitch) * motionFactor;
        double motionZ = Math.cos(yaw) * Math.cos(pitch) * motionFactor;
        double magnitude = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        if (magnitude < 1.0e-6) return null;
        motionX /= magnitude;
        motionY /= magnitude;
        motionZ /= magnitude;

        if (item instanceof ProjectileWeaponItem) {
            // The bow's charge curve, copied from the vanilla power lookup.
            float power = (72000 - player.getUseItemRemainingTicks()) / 20.0F;
            power = (power * power + power * 2.0F) / 3.0F;
            if (power > 1.0F || power <= 0.1F) power = 1.0F;
            power *= 3.0F;
            motionX *= power;
            motionY *= power;
            motionZ *= power;
        } else {
            motionX *= 1.5;
            motionY *= 1.5;
            motionZ *= 1.5;
        }

        double gravity = gravityOf(item);
        List<Vec3> path = new ArrayList<>();

        for (int step = 0; step < MAX_PATH_STEPS; step++) {
            Vec3 from = new Vec3(arrowX, arrowY, arrowZ);
            Vec3 to = new Vec3(arrowX + motionX, arrowY + motionY, arrowZ + motionZ);
            path.add(from);

            BlockHitResult blockHit = mc.level.clip(new ClipContext(from, to,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (blockHit.getType() != HitResult.Type.MISS) {
                path.add(blockHit.getLocation());
                return new Path(path, blockHit);
            }

            // A throwaway arrow supplies the hitbox the entity sweep needs.
            Arrow probe = new Arrow(mc.level, arrowX, arrowY, arrowZ, ItemStack.EMPTY, ItemStack.EMPTY);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(mc.level, probe, from, to,
                    probe.getBoundingBox().expandTowards(motionX, motionY, motionZ).inflate(1.0),
                    candidate -> candidate != player && candidate instanceof LivingEntity);
            if (entityHit != null && entityHit.getType() == HitResult.Type.ENTITY) {
                path.add(entityHit.getLocation());
                return new Path(path, entityHit);
            }

            arrowX += motionX;
            arrowY += motionY;
            arrowZ += motionZ;
            motionX *= DRAG;
            motionY *= DRAG;
            motionZ *= DRAG;
            motionY -= gravity;
        }
        return new Path(path, null);
    }

    /** The box at the end of the path: a face panel on a block hit, the entity's box on a hit. */
    private void drawEndMarker(Render3DEvent event, Path path, Vec3 camera) {
        Vec3 end = path.path.get(path.path.size() - 1);
        AABB box = new AABB(0.15, 0.15, 0.15, 0.35, 0.35, 0.35);
        float[] color = {1.0F, 1.0F, 1.0F};
        HitResult result = path.result;

        if (result != null && result.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) result;
            Direction face = blockHit.getDirection();
            if (face == Direction.SOUTH) {
                box = new AABB(0.0, 0.0, 0.0, 0.5, 0.5, 0.1);
            } else if (face == Direction.NORTH) {
                box = new AABB(0.0, 0.0, 0.4, 0.5, 0.5, 0.5);
            } else if (face == Direction.EAST) {
                box = new AABB(0.0, 0.0, 0.0, 0.1, 0.5, 0.5);
            } else if (face == Direction.WEST) {
                box = new AABB(0.4, 0.0, 0.0, 0.5, 0.5, 0.5);
            } else if (face == Direction.UP) {
                color = new float[]{0.0F, 1.0F, 0.0F};
                box = new AABB(0.0, 0.0, 0.0, 0.5, 0.1, 0.5);
            } else if (face == Direction.DOWN) {
                box = new AABB(0.0, 0.4, 0.0, 0.5, 0.5, 0.5);
            }
        } else if (result != null && result.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) result;
            color = new float[]{1.0F, 0.0F, 0.0F};
            Entity target = entityHit.getEntity();
            // Naven's preview is the target's box rewound by one tick, so the marker sits where the
            // hit landed rather than where the entity has drifted since.
            double motionX = target.getX() - target.xo;
            double motionY = target.getY() - target.yo;
            double motionZ = target.getZ() - target.zo;
            AABB preview = target.getBoundingBox()
                    .move(-camera.x, -camera.y, -camera.z)
                    .move(-motionX, -motionY, -motionZ)
                    .move((double) event.partialTicks() * motionX,
                            (double) event.partialTicks() * motionY,
                            (double) event.partialTicks() * motionZ)
                    .inflate(0.1);
            Render3DUtils.filledBox(event.poseStack(), event.collector(), preview,
                    color[0], color[1], color[2], 0.5F);
        }

        AABB marker = box.move(end.x - camera.x - 0.25, end.y - camera.y - 0.25, end.z - camera.z - 0.25);
        Render3DUtils.filledBox(event.poseStack(), event.collector(), marker,
                color[0], color[1], color[2], 0.25F);
        Render3DUtils.outlinedBox(event.poseStack(), event.collector(), marker,
                color[0], color[1], color[2], 0.75F);
    }

    private ProjectileData dataFor(Entity entity) {
        for (ProjectileData data : this.activeData()) {
            if (data.isTargetEntity(entity)) return data;
        }
        return null;
    }

    private List<ProjectileData> activeData() {
        List<ProjectileData> data = new ArrayList<>();
        if (this.showArrows.get()) data.add(this.arrowsData);
        if (this.showPotions.get()) data.add(this.potionsData);
        if (this.showPearls.get()) data.add(this.pearlData);
        if (this.showEggs.get()) data.add(this.eggData);
        if (this.showSnowballs.get()) data.add(this.snowballData);
        return data;
    }

    private static double gravityOf(Item item) {
        if (item instanceof BowItem || item instanceof CrossbowItem) return 0.05;
        if (item instanceof PotionItem) return 0.4;
        if (item instanceof FishingRodItem) return 0.15;
        if (item instanceof TridentItem) return 0.015;
        return 0.03;
    }

    private static boolean isThrowable(Item item) {
        return item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof SnowballItem
                || item instanceof EggItem
                || item instanceof EnderpearlItem
                || item instanceof SplashPotionItem
                || item instanceof LingeringPotionItem
                || item instanceof FishingRodItem
                || item instanceof TridentItem;
    }

    private record Path(List<Vec3> path, HitResult result) {
    }
}
