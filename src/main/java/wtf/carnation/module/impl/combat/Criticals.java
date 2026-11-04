package wtf.carnation.module.impl.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.shapes.VoxelShape;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.combat.EventAttack;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.MoveInputEvent;
import wtf.carnation.event.impl.movement.RespawnEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;

/**
 * Port of Naven's {@code tech.naven.modules.impl.combat.Criticals}.
 *
 * <p>Naven's tick-skip counter is not part of this client yet, so the SkipTicks option only
 * records intent and the sprint-cancel path (the actual crit enabler) is unchanged.</p>
 */
public class Criticals extends Module {
    private final BoolValue skipTicksOption = new BoolValue("SkipTicks", true);
    private boolean usedSkipTicks;

    public Criticals() {
        super("Criticals", Category.COMBAT, "Critical Hit Target");
    }

    @Override
    protected void onDisable() {
        this.reset();
    }

    @EventTarget
    public void onRespawn(RespawnEvent event) {
        this.reset();
    }

    @EventTarget
    public void onMoveInputSprintCancel(MoveInputEvent event) {
        if (!this.isEnabled() || mc.player == null || mc.level == null) return;
        Entity target = Aura.target;
        if (target == null) return;
        if (this.hasHeadBlock() || this.cantCrit(target)) {
            this.reset();
            return;
        }
        if (this.aura() == null || !this.aura().isEnabled()) {
            this.reset();
            return;
        }
        if (mc.player.getDeltaMovement().y < 0.0 && !mc.player.onGround()
                && mc.player.distanceTo(target) <= 3.0F) {
            this.forceCancelSprint();
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null) return;
        if (Aura.target == null) return;
        if (this.hasHeadBlock()) {
            this.reset();
            return;
        }
        if (this.cantCrit(Aura.target)) {
            this.reset();
            return;
        }
        Aura aura = this.aura();
        if (mc.player.getDeltaMovement().y < 0.0 && !mc.player.onGround()
                && aura != null && aura.isEnabled()
                && mc.player.distanceTo(Aura.target) <= 3.0F) {
            if (this.skipTicksOption.get() && !this.usedSkipTicks) {
                this.usedSkipTicks = true;
            }
        } else if (aura == null || !aura.isEnabled()) {
            this.reset();
        }
    }

    @EventTarget
    public void onAttack(EventAttack event) {
        if (!this.isEnabled() || mc.player == null || mc.level == null) return;
        if (Aura.target == null || event.getTarget() != Aura.target) return;
        if (this.hasHeadBlock()) {
            this.reset();
            return;
        }
        Aura aura = this.aura();
        if (aura != null && aura.isEnabled()
                && mc.player.getDeltaMovement().y < 0.0 && !mc.player.onGround()
                && mc.player.distanceTo(Aura.target) <= 3.0F) {
            this.forceCancelSprint();
        }
        if (this.cantCrit(Aura.target)) this.reset();
    }

    private Aura aura() {
        return wtf.carnation.Client.instance.getModuleManager().getModule(Aura.class);
    }

    private boolean cantCrit(Entity target) {
        if (mc.player == null) return true;
        if (!(target instanceof LivingEntity living)) return true;
        return mc.player.onClimbable() || mc.player.isInWater() || mc.player.isInLava()
                || mc.player.isPassenger() || this.hasHeadBlock()
                || living.hurtTime > 10 || living.getHealth() <= 0.0F;
    }

    private boolean hasHeadBlock() {
        if (mc.player == null || mc.level == null) return false;
        Iterable<VoxelShape> collisions = mc.level.getBlockCollisions(mc.player,
                mc.player.getBoundingBox().move(0.0, 0.5, 0.0));
        return collisions.iterator().hasNext();
    }

    private void forceCancelSprint() {
        if (mc.player == null) return;
        if (mc.player.isSprinting() || mc.options.keySprint.isDown()) {
            mc.options.keySprint.setDown(false);
            mc.player.setSprinting(false);
        }
    }

    private void reset() {
        if (this.usedSkipTicks) this.usedSkipTicks = false;
    }
}
