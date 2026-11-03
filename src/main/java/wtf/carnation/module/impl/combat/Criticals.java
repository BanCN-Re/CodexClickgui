package wtf.carnation.module.impl.combat;

import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.shapes.VoxelShape;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;

/** Cancels sprint while falling so the server registers a critical hit. */
public class Criticals extends Module {
    private final BoolValue skipTicks = new BoolValue("SkipTicks", true);

    private Entity lastTarget;

    public Criticals() {
        super("Criticals", Category.COMBAT, "下落时取消疾跑以打出暴击");
    }

    @EventTarget
    public void onAttack(PacketEvent event) {
        if (!event.isSend() || mc.player == null) return;
        if (!(event.getPacket() instanceof ServerboundAttackPacket)) return;
        Entity target = mc.level == null ? null : mc.level.getEntity(((ServerboundAttackPacket) event.getPacket()).entityId());
        if (target == null) return;
        this.lastTarget = target;
        if (cantCrit(target)) {
            this.lastTarget = null;
            return;
        }
        if (mc.player.getDeltaMovement().y < 0.0 && !mc.player.onGround()
                && mc.player.distanceTo(target) <= 3.0F) {
            forceCancelSprint();
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null || mc.level == null) return;
        Entity target = this.lastTarget;
        if (target == null) return;
        if (hasHeadBlock() || cantCrit(target)) {
            this.lastTarget = null;
            return;
        }
        if (mc.player.getDeltaMovement().y < 0.0 && !mc.player.onGround()
                && mc.player.distanceTo(target) <= 3.0F) {
            forceCancelSprint();
        }
    }

    private boolean cantCrit(Entity target) {
        if (mc.player == null) return true;
        if (!(target instanceof LivingEntity living)) return true;
        return mc.player.onClimbable() || mc.player.isInWater() || mc.player.isInLava()
                || mc.player.isPassenger() || hasHeadBlock()
                || living.getHealth() <= 0.0F;
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
}
