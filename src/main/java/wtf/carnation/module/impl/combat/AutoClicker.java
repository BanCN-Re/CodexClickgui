package wtf.carnation.module.impl.combat;

import mixin.KeyMappingAccessor;
import mixin.MinecraftAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.HitResult;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;

/**
 * Port of Naven's {@code tech.naven.modules.impl.combat.AutoClicker}.
 *
 * <p>Counts the CPS on the pre-motion event and forwards a real attack key press, clearing
 * {@code missTime} first so the vanilla click cooldown never throttles it.</p>
 */
public class AutoClicker extends Module {
    private final NumberValue cps = new NumberValue("CPS", 10.0, 5.0, 20.0, 1.0);
    private final BoolValue itemCheck = new BoolValue("Item Check", true);
    private float counter = 0.0F;

    public AutoClicker() {
        super("AutoClicker", Category.COMBAT, "Automatically clicks for you");
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (!e.isPre() || mc.player == null) return;
        boolean holdingWeapon = mc.player.getMainHandItem().is(ItemTags.SWORDS)
                || mc.player.getMainHandItem().is(ItemTags.AXES);
        boolean noBlockInFront = mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK;
        if (mc.options.keyAttack.isDown() && (holdingWeapon || !this.itemCheck.get()) && noBlockInFront) {
            this.counter = this.counter + this.cps.getFloat() / 20.0F;
            if (this.counter >= 1.0F / this.cps.getFloat()) {
                ((MinecraftAccessor) mc).carnation$setMissTime(0);
                KeyMapping.click(((KeyMappingAccessor) mc.options.keyAttack).carnation$getKey());
                this.counter--;
            }
        } else {
            this.counter = 0.0F;
        }
    }
}
