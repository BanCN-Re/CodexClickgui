package wtf.carnation.module.impl.combat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.RespawnEvent;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.EspRender;
import wtf.carnation.utils.RotationManager;
import wtf.carnation.utils.RotationUtils;
import wtf.carnation.utils.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Automatically attacks nearby entities. */
public class KillAura extends Module {
    private static final int TARGET_COLOR = 0xFFC83232;
    private static final int OTHER_COLOR = 0xFF32C864;

    public static Entity target;
    public static List<Entity> targets = new ArrayList<>();

    private final BoolValue targetHud = new BoolValue("Target HUD", true);
    private final BoolValue targetEsp = new BoolValue("Target ESP", true);
    private final BoolValue attackPlayer = new BoolValue("Attack Player", true);
    private final BoolValue attackInvisible = new BoolValue("Attack Invisible", false);
    private final BoolValue attackAnimals = new BoolValue("Attack Animals", false);
    private final BoolValue attackMobs = new BoolValue("Attack Mobs", false);
    private final BoolValue multi = new BoolValue("Multi Attack", false);
    private final BoolValue infinitySwitch = new BoolValue("Infinity Switch", false);
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

    private int index;
    private int attackTimes;
    private float attacks;

    public KillAura() {
        super("KillAura", Category.COMBAT, "自动攻击范围内的目标");
    }

    @Override
    protected void onEnable() {
        target = null;
        targets.clear();
        this.index = 0;
        this.attacks = 0;
        this.attackTimes = 0;
    }

    @Override
    protected void onDisable() {
        target = null;
        targets.clear();
        RotationManager.clearRotations();
    }

    @EventTarget
    public void onRespawn(RespawnEvent event) {
        target = null;
        targets.clear();
        RotationManager.clearRotations();
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null || mc.level == null) return;
        if (mc.gui.screen() != null) {
            target = null;
            targets.clear();
            RotationManager.clearRotations();
            return;
        }

        this.targets = getTargets();
        setSuffix(this.multi.get() ? "Multi" : (this.switchSize.get() > 1 ? "Switch" : "Single"));
        if (targets.isEmpty()) {
            target = null;
            RotationManager.clearRotations();
            return;
        }

        if (this.index > targets.size() - 1) this.index = 0;
        if (targets.size() > 1 && this.attackTimes >= this.switchAttackTimes.get()) {
            this.attackTimes = 0;
            for (int i = 0; i < targets.size(); i++) {
                this.index++;
                if (this.index > targets.size() - 1) this.index = 0;
                if (mc.player.distanceTo(targets.get(this.index)) < 3.0) break;
            }
        }
        if (this.index > targets.size() - 1 || this.switchSize.get() <= 1) this.index = 0;

        target = targets.get(this.index);
        Vector2f rotations = RotationUtils.getRotations(target);
        RotationManager.setRotations(rotations);
        RotationManager.setActive(true);
        this.attacks += this.aps.get() / 20.0F;
        while (this.attacks >= 1.0F) {
            this.attacks -= 1.0F;
            doAttack();
        }
    }

    private void doAttack() {
        if (target == null || targets.isEmpty() || mc.player == null || mc.gameMode == null) return;
        if (mc.player.getUseItem().isEmpty()) {
            if (this.multi.get()) {
                int attacked = 0;
                for (Entity entity : targets) {
                    if (mc.player.distanceTo(entity) < 3.0) {
                        attackEntity(entity);
                        if (++attacked >= 2) break;
                    }
                }
            } else {
                attackEntity(target);
            }
        }
    }

    public void attackEntity(Entity entity) {
        this.attackTimes++;
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        Vector2f rotations = RotationManager.rotations();
        if (rotations != null) {
            mc.player.setYRot(rotations.x);
            mc.player.setXRot(rotations.y);
        }
        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        if (this.moreParticles.get()) {
            mc.player.crit(entity);
        }
        mc.player.setYRot(currentYaw);
        mc.player.setXRot(currentPitch);
        if (this.keepSprint.get() && !mc.player.onGround() && mc.player.getDeltaMovement().y < 0) {
            mc.options.keySprint.setDown(false);
            mc.player.setSprinting(false);
        }
    }

    public boolean isValidTarget(Entity entity) {
        if (entity == mc.player || entity == null) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        if (living.isDeadOrDying() || living.getHealth() <= 0.0F) return false;
        if (entity instanceof ArmorStand) return false;
        if (entity.isInvisible() && !this.attackInvisible.get()) return false;
        if (entity instanceof Player player) {
            if (!this.attackPlayer.get()) return false;
            if (player.isSpectator()) return false;
            if (mc.player.isAlliedTo(player)) return false;
        }
        if (entity instanceof Mob && !this.attackMobs.get()) return false;
        if (entity instanceof Animal && !this.attackAnimals.get()) return false;
        return true;
    }

    private boolean isValidAttack(Entity entity) {
        if (!isValidTarget(entity) || mc.player == null) return false;
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 closest = RotationUtils.getClosestPoint(eye, entity.getBoundingBox());
        if (closest.distanceTo(eye) > this.aimRange.get()) return false;
        return RotationUtils.inFoV(entity, this.fov.getFloat() / 2.0F);
    }

    private List<Entity> getTargets() {
        List<Entity> possible = new ArrayList<>();
        for (LivingEntity living : mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(this.aimRange.get() + 2.0))) {
            if (isValidAttack(living)) possible.add(living);
        }
        if (priority.is("Range")) {
            possible.sort(Comparator.comparingDouble(mc.player::distanceTo));
        } else if (priority.is("FoV")) {
            possible.sort(Comparator.comparingDouble(entity -> {
                Vector2f rotations = RotationUtils.getRotations(entity);
                return Math.abs(rotations.x - mc.player.getYRot());
            }));
        } else if (priority.is("Health")) {
            possible.sort(Comparator.comparingDouble(entity ->
                    entity instanceof LivingEntity living ? living.getHealth() : 0.0));
        }
        if (this.preferBaby.get() && possible.stream()
                .anyMatch(entity -> entity instanceof LivingEntity living && living.isBaby())) {
            possible.removeIf(entity -> !(entity instanceof LivingEntity living) || !living.isBaby());
        }
        possible.sort(Comparator.comparing(entity -> entity instanceof EndCrystal ? 0 : 1));
        if (this.infinitySwitch.get()) return possible;
        return possible.subList(0, (int) Math.min(possible.size(), this.switchSize.get()));
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.targetEsp.get()) {
            for (Entity entity : targets) {
                if (!(entity instanceof LivingEntity living)) continue;
                EspRender.box(event.graphics(), living, entity == target ? TARGET_COLOR : OTHER_COLOR, false);
            }
        }
        if (!this.targetHud.get() || !(target instanceof LivingEntity living)) return;
        GuiGraphicsExtractor g = event.graphics();
        float scale = UiFont.useGuiScale();
        int guiScale = mc.getWindow().getGuiScale();
        float screenW = mc.getWindow().getWidth();
        float screenH = mc.getWindow().getHeight();
        String name = living.getName().getString() + (living.isBaby() ? " (Baby)" : "");
        String hp = "HP: " + Math.round(living.getHealth());
        float width = Math.max(UiFont.Z_SMALL.width(name) + 12, 60);
        float x = screenW / 2.0F + 10;
        float y = screenH / 2.0F + 10;

        g.pose().pushMatrix();
        g.pose().scale(scale / guiScale, scale / guiScale);
        Render2D.roundedRect(g, x, y, width, 30, 5, 0xD8121218);
        float ratio = living.getMaxHealth() > 0 ? living.getHealth() / living.getMaxHealth() : 0;
        Render2D.rect(g, x, y, width * Math.max(0, Math.min(1, ratio)), 3, 0xFF7C5CFF);
        UiFont.Z_SMALL.draw(g, name, x + 5, y + 9, 0xFFFFFFFF);
        UiFont.Z_SMALL.draw(g, hp, x + 5, y + 20, 0xFFFFFFFF);
        g.pose().popMatrix();
    }
}
