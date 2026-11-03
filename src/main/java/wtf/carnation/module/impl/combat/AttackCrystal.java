package wtf.carnation.module.impl.combat;

import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.RotationManager;
import wtf.carnation.utils.RotationUtils;
import wtf.carnation.utils.Vector2f;

/** Automatically hits end crystals. */
public class AttackCrystal extends Module {
    private final BoolValue onlyOnGround = new BoolValue("Only On Ground", false);
    private final NumberValue range = new NumberValue("Range", 4.0, 1.0, 6.0, 0.5);
    private final BoolValue rotate = new BoolValue("Rotate", true);

    private EndCrystal target;

    public AttackCrystal() {
        super("CrystalAura", Category.COMBAT, "自动攻击末影水晶");
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null || mc.level == null) return;
        this.target = null;
        double best = range.get();
        for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class,
                mc.player.getBoundingBox().inflate(range.get()))) {
            double distance = mc.player.distanceTo(crystal);
            if (distance > best) continue;
            best = distance;
            this.target = crystal;
        }
        if (this.target == null) {
            RotationManager.clearRotations();
            return;
        }
        Vector2f rotations = RotationUtils.getRotations(this.target);
        if (rotate.get()) {
            RotationManager.setRotations(rotations);
            RotationManager.setActive(true);
        }
        if (onlyOnGround.get() && !mc.player.onGround()) return;
        if (!mc.options.keyAttack.isDown()) return;
        attack();
    }

    private void attack() {
        if (this.target == null || mc.player == null) return;
        if (mc.player.connection != null) {
            mc.player.connection.send(new ServerboundAttackPacket(this.target.getId()));
        }
        mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        this.target = null;
    }
}
