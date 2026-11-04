package wtf.carnation.module.impl.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.misc.Teams;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.FriendManager;
import wtf.carnation.utils.RotationManager;
import wtf.carnation.utils.RotationUtils;
import wtf.carnation.utils.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Port of Naven's {@code tech.naven.modules.impl.combat.AimAssist}. */
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

    public final Vector2f targetRotation = new Vector2f(0.0F, 0.0F);
    public boolean working = false;
    public boolean slientaim = silent.get();

    /** Live value: the arbitration reads this every tick, the snapshot above goes stale. */
    public boolean silentAim() {
        return this.silent.get();
    }

    public AimAssist() {
        super("AimAssist", Category.COMBAT, "Automatically aims at the nearest entity");
    }

    @EventTarget
    public void onMotion(RunTickEvent e) {
        if (!e.isPre() || mc.player == null) return;
        if (this.clickOnly.get() && !mc.options.keyAttack.isDown()) {
            this.working = false;
            return;
        }

        Entity target = this.getTarget();
        float targetYaw;
        float targetPitch;
        if (target != null) {
            Vector2f rotations = RotationUtils.getRotations(target);
            targetYaw = mc.player.getYRot() + RotationUtils.getAngleDifference(rotations.x, mc.player.getYRot());
            targetPitch = rotations.y;
            this.working = true;
        } else {
            targetYaw = mc.player.getYRot();
            targetPitch = mc.player.getXRot();
            if (this.targetRotation.x % 360.0F == targetYaw % 360.0F) {
                this.working = false;
            }
        }

        if (this.working) {
            this.targetRotation.x = RotationUtils.rotateToYaw(this.rotateSpeed.getFloat(),
                    this.targetRotation.x, targetYaw);
            this.targetRotation.y = targetPitch;
            if (this.silent.get()) {
                RotationManager.setRotations(this.targetRotation.x, this.targetRotation.y);
                RotationManager.setActive(true);
            } else {
                mc.player.setYRot(this.targetRotation.x);
                mc.player.setXRot(this.targetRotation.y);
            }
        } else {
            this.targetRotation.x = targetYaw;
            this.targetRotation.y = targetPitch;
        }
    }

    public boolean isValidTarget(Entity entity) {
        if (entity == mc.player) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        if (Teams.isSameTeam(living)) return false;
        if (FriendManager.isFriend(living)) return false;
        if (living.isDeadOrDying() || living.getHealth() <= 0.0F) return false;
        if (entity instanceof ArmorStand) return false;
        if (entity.isInvisible() && !this.attackInvisible.get()) return false;
        if (entity instanceof Player && !this.attackPlayer.get()) return false;
        AntiBots antiBots = Client.instance.getModuleManager().getModule(AntiBots.class);
        if (antiBots != null && antiBots.isEnabled()
                && (AntiBots.isBot(entity) || AntiBots.isBedWarsBot(entity))) {
            return false;
        }
        if ((entity instanceof Mob || entity instanceof Slime || entity instanceof Bat
                || entity instanceof AbstractGolem) && !this.attackMobs.get()) {
            return false;
        }
        if ((entity instanceof Animal || entity instanceof Squid) && !this.attackAnimals.get()) {
            return false;
        }
        if (entity instanceof Villager && !this.attackAnimals.get()) return false;
        return !(entity instanceof Player player) || !player.isSpectator();
    }

    public boolean isValidAttack(Entity entity) {
        if (!this.isValidTarget(entity)) return false;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 closestPoint = RotationUtils.getClosestPoint(eye, entity.getBoundingBox());
        if (closestPoint.distanceTo(eye) > this.aimRange.get()) return false;
        return RotationUtils.inFoV(entity, this.fov.getFloat() / 2.0F);
    }

    private Entity getTarget() {
        List<Entity> possibleTargets = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (this.isValidAttack(entity)) possibleTargets.add(entity);
        }

        if (this.priority.is("Range")) {
            possibleTargets.sort(Comparator.comparingDouble(entity -> entity.distanceTo(mc.player)));
        } else if (this.priority.is("FoV")) {
            Vector2f current = RotationManager.rotations();
            float yaw = current == null ? mc.player.getYRot() : current.x;
            possibleTargets.sort(Comparator.comparingDouble(entity ->
                    RotationUtils.getDistanceBetweenAngles(yaw, RotationUtils.getRotations(entity).x)));
        } else if (this.priority.is("Health")) {
            possibleTargets.sort(Comparator.comparingDouble(entity ->
                    entity instanceof LivingEntity living ? living.getHealth() : 0.0));
        }

        return possibleTargets.isEmpty() ? null : possibleTargets.get(0);
    }
}
