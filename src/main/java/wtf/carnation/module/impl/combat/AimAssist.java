package wtf.carnation.module.impl.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.Rotation;
import wtf.carnation.utils.RotationManager;
import wtf.carnation.utils.RotationUtils;
import wtf.carnation.utils.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Aims at the closest valid entity, optionally silently. */
public class AimAssist extends Module {
    private final BoolValue attackPlayer = new BoolValue("Attack Player", true);
    private final BoolValue attackInvisible = new BoolValue("Attack Invisible", false);
    private final BoolValue attackAnimals = new BoolValue("Attack Animals", false);
    private final BoolValue attackMobs = new BoolValue("Attack Mobs", false);
    private final BoolValue clickOnly = new BoolValue("Click Only", true);
    private final BoolValue silent = new BoolValue("Silent Aim", true);
    private final NumberValue rotateSpeed = new NumberValue("Rotation Speed", 10.0, 1.0, 90.0, 1.0);
    private final NumberValue aimRange = new NumberValue("Aim Range", 5.0, 1.0, 6.0, 0.1);
    private final NumberValue fov = new NumberValue("FoV", 360.0, 10.0, 360.0, 1.0);
    private final ModeValue priority = new ModeValue("Priority",
            new String[]{"Health", "FoV", "Range", "None"}, "Health");

    private final Vector2f targetRotation = new Vector2f(0.0F, 0.0F);
    private boolean working;

    public AimAssist() {
        super("AimAssist", Category.COMBAT, "自动瞄准最近的合法目标");
    }

    @EventTarget
    public void onRunTick(RunTickEvent event) {
        if (!event.isPre() || mc.player == null || mc.level == null) return;
        if (clickOnly.get() && !mc.options.keyAttack.isDown()) {
            this.working = false;
            return;
        }
        Entity target = getTarget();
        float targetYaw;
        float targetPitch;
        if (target != null) {
            Vector2f rotations = RotationUtils.getRotations(target);
            targetYaw = rotations.x;
            targetPitch = rotations.y;
            this.working = true;
        } else {
            targetYaw = mc.player.getYRot();
            targetPitch = mc.player.getXRot();
            this.working = false;
        }

        if (this.working) {
            this.targetRotation.x = Rotation.updateRotation(this.targetRotation.x, targetYaw, rotateSpeed.getFloat());
            this.targetRotation.y = targetPitch;
            if (silent.get()) {
                RotationManager.setRotations(this.targetRotation.x, this.targetRotation.y);
                RotationManager.setActive(true);
            } else {
                mc.player.setYRot(this.targetRotation.x);
                mc.player.setXRot(this.targetRotation.y);
            }
        }
    }

    public boolean isValidTarget(Entity entity) {
        if (entity == mc.player || entity == null) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        if (living.isDeadOrDying() || living.getHealth() <= 0.0F) return false;
        if (entity instanceof ArmorStand) return false;
        if (entity.isInvisible() && !attackInvisible.get()) return false;
        if (entity instanceof Player player) {
            if (!attackPlayer.get()) return false;
            if (player.isSpectator()) return false;
            if (mc.player.isAlliedTo(player)) return false;
        }
        if (entity instanceof Mob && !attackMobs.get()) return false;
        if (entity instanceof Animal && !attackAnimals.get()) return false;
        return true;
    }

    private boolean isValidAttack(Entity entity) {
        if (!isValidTarget(entity) || mc.player == null) return false;
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 closest = RotationUtils.getClosestPoint(eye, entity.getBoundingBox());
        if (closest.distanceTo(eye) > aimRange.get()) return false;
        return RotationUtils.inFoV(entity, fov.getFloat() / 2.0F);
    }

    private Entity getTarget() {
        List<Entity> targets = new ArrayList<>();
        for (LivingEntity living : mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(aimRange.get() + 2.0))) {
            if (isValidAttack(living)) targets.add(living);
        }
        if (targets.isEmpty()) return null;
        if (priority.is("Range")) {
            targets.sort(Comparator.comparingDouble(mc.player::distanceTo));
        } else if (priority.is("FoV")) {
            targets.sort(Comparator.comparingDouble(entity -> {
                Vector2f rotations = RotationUtils.getRotations(entity);
                return Math.abs(Rotation.updateRotation(0, rotations.x, 180) - rotations.x);
            }));
        } else if (priority.is("Health")) {
            targets.sort(Comparator.comparingDouble(entity ->
                    entity instanceof LivingEntity living ? living.getHealth() : 0.0));
        }
        return targets.get(0);
    }
}
