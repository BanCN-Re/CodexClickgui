package wtf.carnation.module.impl.combat;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.EntityHitResult;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;

public class AutoClicker extends Module {
    private final NumberValue cps = new NumberValue("CPS", 10, 5, 20, 1);
    private final BoolValue itemCheck = new BoolValue("Item Check", true);
    private float counter;

    public AutoClicker() {
        super("AutoClicker", Category.COMBAT, "按住攻击键时自动以设定 CPS 点击");
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null) return;
        Item item = mc.player.getMainHandItem().getItem();
        boolean weapon = item.builtInRegistryHolder().is(ItemTags.SWORDS)
                || item.builtInRegistryHolder().is(ItemTags.AXES);
        if (!mc.options.keyAttack.isDown()) {
            counter = 0;
            return;
        }
        if (itemCheck.get() && !weapon) {
            counter = 0;
            return;
        }
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Entity target) {
            counter += cps.getFloat() / 20.0F;
            if (counter >= 1.0F / cps.getFloat()) {
                counter--;
                if (mc.gameMode != null) {
                    mc.gameMode.attack(mc.player, target);
                    mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                }
            }
        }
    }
}
