package wtf.carnation.module.impl.combat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.Client;
import net.minecraft.world.phys.AABB;
import wtf.carnation.event.impl.render.Render3DEvent;
import wtf.carnation.utils.Render3DUtils;
import wtf.carnation.module.impl.render.HUD;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.combat.EventClick;
import wtf.carnation.event.impl.movement.RespawnEvent;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.misc.Teams;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.FriendManager;
import wtf.carnation.utils.InventoryUtils;
import wtf.carnation.utils.NetworkUtils;
import wtf.carnation.utils.RotationManager;
import wtf.carnation.utils.RotationUtils;
import wtf.carnation.utils.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Port of Naven's {@code tech.naven.modules.impl.combat.Aura} (display name "KillAura").
 *
 * <p>Target selection, switching, rotation data and the per-tick attack accumulator are the
 * original's. Damage is driven from {@link EventClick} (fired by {@code Minecraft.startAttack}),
 * and the target boxes are world-space geometry submitted through {@link Render3DEvent}, both as
 * in the source. The target HUD uses Naven's {@code HUD.bodyColor} card with a 3 px header stripe;
 * 26.3 has no shader/blur pass, so the plate is plain instead of blurred.</p>
 */
public class Aura extends Module {
    private static final float[] targetColorRed = new float[]{0.78431374F, 0.0F, 0.0F, 0.23529412F};
    private static final float[] targetColorGreen = new float[]{0.0F, 0.78431374F, 0.0F, 0.23529412F};

    public static Entity target;
    public static Entity aimingTarget;
    public static List<Entity> targets = new ArrayList<>();
    public static Vector2f rotation;

    private final BoolValue targetHud = new BoolValue("Target HUD", true);
    private final BoolValue targetEsp = new BoolValue("Target ESP", true);
    private final BoolValue attackPlayer = new BoolValue("Attack Player", true);
    private final BoolValue attackInvisible = new BoolValue("Attack Invisible", false);
    private final BoolValue attackAnimals = new BoolValue("Attack Animals", false);
    private final BoolValue attackMobs = new BoolValue("Attack Mobs", false);
    private final BoolValue multi = new BoolValue("Multi Attack", false);
    private final BoolValue infSwitch = new BoolValue("Infinity Switch", false);
    private final BoolValue preferBaby = new BoolValue("Prefer Baby", false);
    private final BoolValue moreParticles = new BoolValue("More Particles", false);
    private final BoolValue keepSprint = new BoolValue("Keep Sprint", true);
    private final NumberValue aimRange = new NumberValue("Aim Range", 5.0, 1.0, 6.0, 0.1);
    private final NumberValue aps = new NumberValue("Attack Per Second", 10.0, 1.0, 20.0, 1.0);
    private final NumberValue switchSize = new NumberValue("Switch Size", 1.0, 1.0, 5.0, 1.0);
    private final NumberValue switchAttackTimes = new NumberValue("Switch Delay (Attack Times)", 1.0, 1.0, 10.0, 1.0);
    private final NumberValue fov = new NumberValue("FoV", 360.0, 10.0, 360.0, 1.0);
    private final NumberValue hurtTime = new NumberValue("Hurt Time", 10.0, 0.0, 10.0, 1.0);
    private final ModeValue priority = new ModeValue("Priority", new String[]{"Health", "FoV", "Range"}, "Health");

    private RotationUtils.Data lastRotationData;
    private RotationUtils.Data rotationData;
    private int attackTimes = 0;
    private float attacks = 0.0F;
    private int index;
    private boolean sprintCancelled;

    public Aura() {
        super("KillAura", Category.COMBAT, "Automatically attacks entities");
    }

    @Override
    protected void onEnable() {
        rotation = null;
        this.index = 0;
        this.sprintCancelled = false;
        target = null;
        aimingTarget = null;
        targets.clear();
    }

    @Override
    protected void onDisable() {
        this.sprintCancelled = false;
        target = null;
        aimingTarget = null;
        rotation = null;
        targets.clear();
        RotationManager.clearRotations();
    }

    @EventTarget
    public void onRespawn(RespawnEvent e) {
        this.sprintCancelled = false;
        target = null;
        aimingTarget = null;
    }

    /** Naven's keep-sprint helpers, kept for the movement modules. */
    public void markSprintReleased() {
        this.sprintCancelled = true;
    }

    public boolean isSprintReleased() {
        return this.sprintCancelled;
    }

    public boolean shouldStopSprint() {
        if (!this.isEnabled() || !this.keepSprint.get() || this.sprintCancelled) return false;
        if (mc.player == null || target == null || mc.player.isUsingItem()) return false;
        return this.canAttackTarget() && mc.player != null && !mc.player.onGround();
    }

    private boolean canAttackTarget() {
        return target != null && mc.player != null
                && target.getBoundingBox().distanceToSqr(mc.player.position()) <= 12.25
                && mc.player.hasLineOfSight(target);
    }


    @EventTarget
    public void onMotion(RunTickEvent event) {
        if (!event.isPre() || mc.player == null) return;
        if (!mc.player.onGround() || mc.player.isInWater() || mc.player.isInLava()
                || mc.player.isPassenger() || mc.player.isFallFlying() || mc.player.getAbilities().flying) {
            this.sprintCancelled = false;
        }

        Module stuck = Client.instance.getModuleManager().getModuleByName("Stuck");
        if (mc.gui.screen() instanceof AbstractContainerScreen
                || (stuck != null && stuck.isEnabled())
                || InventoryUtils.shouldDisableFeatures()) {
            target = null;
            aimingTarget = null;
            this.rotationData = null;
            rotation = null;
            this.lastRotationData = null;
            targets.clear();
            return;
        }

        boolean isSwitch = this.switchSize.get() > 1.0F;
        this.setSuffix(this.multi.get() ? "Multi" : (isSwitch ? "Switch" : "Single"));
        this.updateAttackTargets();
        aimingTarget = this.shouldPreAim();
        this.lastRotationData = this.rotationData;
        this.rotationData = null;
        if (aimingTarget != null) {
            this.rotationData = RotationUtils.getRotationDataToEntity(aimingTarget);
            rotation = this.rotationData.getRotation();
        }

        if (targets.isEmpty()) {
            target = null;
            return;
        }

        if (this.index > targets.size() - 1) this.index = 0;

        if (targets.size() > 1 && ((float) this.attackTimes >= this.switchAttackTimes.get()
                || this.rotationData != null && this.rotationData.getDistance() > 3.0)) {
            this.attackTimes = 0;
            for (int i = 0; i < targets.size(); i++) {
                this.index++;
                if (this.index > targets.size() - 1) this.index = 0;
                RotationUtils.Data data = RotationUtils.getRotationDataToEntity(targets.get(this.index));
                if (data.getDistance() < 3.0) break;
            }
        }

        if (this.index > targets.size() - 1 || !isSwitch) this.index = 0;

        target = targets.get(this.index);
        this.attacks = this.attacks + this.aps.getFloat() / 20.0F;
    }

    /**
     * Naven drives damage from {@code EventClick} (fired by {@code Minecraft.startAttack}), not
     * from a held key: every consumed attack input drains the accumulated budget. Holding the
     * button is not an attack input by itself.
     */
    @EventTarget
    public void onClick(EventClick event) {
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.getUseItem().isEmpty() || mc.gui.screen() != null || NetworkUtils.isServerLag()) {
            return;
        }
        Module blink = Client.instance.getModuleManager().getModuleByName("Blink");
        if (blink != null && blink.isEnabled()) return;
        while (this.attacks >= 1.0F) {
            this.doAttack();
            this.attacks--;
        }
    }

    public Entity shouldPreAim() {
        Entity current = target;
        if (current == null) {
            List<Entity> aimTargets = this.getTargets();
            if (!aimTargets.isEmpty()) current = aimTargets.get(0);
        }
        return current;
    }

    public void doAttack() {
        if (targets.isEmpty() || mc.player == null || mc.gameMode == null) return;
        HitResult hitResult = mc.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY
                && AntiBots.isBot(((EntityHitResult) hitResult).getEntity())) {
            return;
        }

        if (this.multi.get()) {
            int attacked = 0;
            for (Entity entity : targets) {
                Vector2f aim = RotationManager.rotations();
                if (aim == null) aim = new Vector2f(mc.player.getYRot(), mc.player.getXRot());
                if (RotationUtils.getDistance(entity, mc.player.getEyePosition(), aim) < 3.0) {
                    this.attackEntity(entity);
                    if (++attacked >= 2) break;
                }
            }
        } else if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            this.attackEntity(((EntityHitResult) hitResult).getEntity());
        }
    }

    public void updateAttackTargets() {
        targets = this.getTargets();
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
        if (entity instanceof LivingEntity living && (float) living.hurtTime > this.hurtTime.get()) return false;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 closestPoint = RotationUtils.getClosestPoint(eye, entity.getBoundingBox());
        return closestPoint.distanceTo(eye) <= this.aimRange.get()
                && RotationUtils.inFoV(entity, this.fov.getFloat() / 2.0F);
    }

    public void attackEntity(Entity entity) {
        if (mc.player == null || mc.gameMode == null) return;
        this.attackTimes++;
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        Vector2f aim = RotationManager.rotations();
        if (aim != null) {
            mc.player.setYRot(aim.x);
            mc.player.setXRot(aim.y);
        }
        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        if (this.moreParticles.get()) {
            mc.player.magicCrit(entity);
            mc.player.crit(entity);
        }
        mc.player.setYRot(currentYaw);
        mc.player.setXRot(currentPitch);
    }

    private List<Entity> getTargets() {
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

        if (this.preferBaby.get() && possibleTargets.stream()
                .anyMatch(entity -> entity instanceof LivingEntity living && living.isBaby())) {
            possibleTargets.removeIf(entity -> !(entity instanceof LivingEntity living) || !living.isBaby());
        }

        possibleTargets.sort(Comparator.comparing(entity -> entity instanceof EndCrystal ? 0 : 1));
        return this.infSwitch.get()
                ? possibleTargets
                : possibleTargets.subList(0, (int) Math.min(possibleTargets.size(), this.switchSize.get()));
    }

    /**
     * Naven's target boxes are world-space geometry ({@code EventRender}). 26.3 exposes that as
     * {@link Render3DEvent}, whose vertices must already be camera-relative.
     */
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.targetEsp.get() || targets.isEmpty()) return;
        Vec3 camera = event.camera();
        float partial = event.partialTicks();
        for (Entity entity : targets) {
            if (!(entity instanceof LivingEntity)) continue;
            float[] color = entity == target ? targetColorRed : targetColorGreen;
            double motionX = entity.getX() - entity.xo;
            double motionY = entity.getY() - entity.yo;
            double motionZ = entity.getZ() - entity.zo;
            AABB box = entity.getBoundingBox()
                    .move(-motionX, -motionY, -motionZ)
                    .move(motionX * partial, motionY * partial, motionZ * partial)
                    .move(-camera.x, -camera.y, -camera.z);
            Render3DUtils.filledBox(event.poseStack(), event.collector(), box,
                    color[0], color[1], color[2], 0.25F);
            Render3DUtils.outlinedBox(event.poseStack(), event.collector(), box,
                    color[0], color[1], color[2], 0.85F);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.targetHud.get() || !(target instanceof LivingEntity living)) return;
        GuiGraphicsExtractor g = event.graphics();
        UiFont.useGuiScale();

        float x = mc.getWindow().getGuiScaledWidth() / 2.0F + 10.0F;
        float y = mc.getWindow().getGuiScaledHeight() / 2.0F + 10.0F;
        String name = living.getName().getString() + (living.isBaby() ? " (Baby)" : "");
        float width = Math.max(UiFont.Z_SMALL.width(name) + 10.0F, 60.0F);

        Render2D.roundedRect(g, x, y, width, 30.0F, 5.0F, HUD.BODY_COLOR);
        float ratio = living.getMaxHealth() > 0.0F ? living.getHealth() / living.getMaxHealth() : 0.0F;
        Render2D.rect(g, x, y, width * Math.max(0.0F, Math.min(1.0F, ratio)), 3.0F, HUD.HEADER_COLOR);
        UiFont.Z_SMALL.drawInBox(g, name, x + 5.0F, y + 2.0F, 14.0F, 0xFFFFFFFF);
        String hp = "HP: " + Math.round(living.getHealth())
                + (living.getAbsorptionAmount() > 0.0F ? "+" + Math.round(living.getAbsorptionAmount()) : "");
        UiFont.Z_SMALL.drawInBox(g, hp, x + 5.0F, y + 15.0F, 13.0F, 0xFFFFFFFF);
    }
}
