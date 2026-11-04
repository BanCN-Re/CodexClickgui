package wtf.carnation.module.impl.misc;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

import java.util.Optional;

/** Port of Naven's {@code AntiFireball}. */
public class AntiFireball extends Module {
    public AntiFireball() {
        super("AntiFireball", Category.MISC, "Prevents fireballs from damaging you");
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        Module longJump = Client.instance.getModuleManager().getModuleByName("LongJump");
        if (longJump != null && longJump.isEnabled()) return;
        if (!e.isPre() || mc.player == null || mc.level == null || mc.gameMode == null) return;

        Optional<Fireball> fireball = java.util.stream.StreamSupport
                .stream(mc.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> entity instanceof Fireball && mc.player.distanceTo(entity) < 6.0F)
                .map(entity -> (Fireball) entity)
                .findFirst();
        fireball.ifPresent(fireballEntity -> {
            mc.gameMode.attack(mc.player, fireballEntity);
            mc.player.swing(InteractionHand.MAIN_HAND,
                    net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        });
    }
}
