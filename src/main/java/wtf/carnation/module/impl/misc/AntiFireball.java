package wtf.carnation.module.impl.misc;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

/** Hits fireballs that come close before they can damage the player. */
public class AntiFireball extends Module {
    public AntiFireball() {
        super("AntiFireball", Category.MISC, "自动打掉靠近的火球");
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null || mc.level == null || mc.gameMode == null) return;
        for (Fireball fireball : mc.level.getEntitiesOfClass(Fireball.class,
                mc.player.getBoundingBox().inflate(6.0))) {
            mc.gameMode.attack(mc.player, fireball);
            mc.player.swing(InteractionHand.MAIN_HAND,
                    net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            return;
        }
    }
}
