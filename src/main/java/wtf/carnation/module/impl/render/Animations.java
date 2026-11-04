package wtf.carnation.module.impl.render;

import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.combat.Aura;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.InventoryUtils;

/**
 * Port of Naven's {@code Animations}.
 *
 * <p>Blocking-state machine and tuning values are the original's; the first-person arm pose is
 * applied by the client's hand-render hook when that hook is present.</p>
 */
public class Animations extends Module {
    public final NumberValue positionX = new NumberValue("Position X", 0.0, -1.0, 1.0, 0.01);
    public final NumberValue positionY = new NumberValue("Position Y", 0.0, -1.0, 1.0, 0.01);
    public final NumberValue positionZ = new NumberValue("Position Z", 0.0, -1.0, 1.0, 0.01);
    public final BoolValue customEquippedProgress = new BoolValue("Custom Equipped Progress", false);
    public final NumberValue equippedProgress = new NumberValue("Equipped Progress", 1.0, 0.0, 1.0, 0.01);
    public final BoolValue onlykillaura = new BoolValue("Only KillAura", false);

    public boolean isBlocking;
    public float blockProgress;
    public float oBlockProgress;

    public Animations() {
        super("Animations", Category.RENDER, "Old versions hitting animations with multiple styles");
    }

    @Override
    protected void onEnable() {
        this.isBlocking = false;
        this.blockProgress = 0.0F;
        this.oBlockProgress = 0.0F;
    }

    @EventTarget
    public void onTick(RunTickEvent event) {
        if (!event.isPre() || mc.player == null) return;
        this.updateBlockingState();
        this.updateBlockProgress();
    }

    private void updateBlockingState() {
        if (mc.player == null) return;
        boolean shouldBlock = mc.options.keyUse.isDown();
        if (this.getAuraTarget() != null) shouldBlock = true;
        if (this.onlykillaura.get()) shouldBlock = this.getAuraTarget() != null;
        this.isBlocking = shouldBlock;
    }

    private void updateBlockProgress() {
        this.oBlockProgress = this.blockProgress;
        float target = this.shouldShowBlockAnimation() ? 1.0F : 0.0F;
        this.blockProgress += Mth.clamp(target - this.blockProgress, -0.4F, 0.4F);
    }

    public boolean shouldShowBlockAnimation() {
        if (!this.isEnabled() || mc.player == null) return false;
        if (!InventoryUtils.isSword(mc.player.getMainHandItem())) return false;

        boolean isOffhandUsing = false;
        if (mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
            if (!mc.player.getOffhandItem().is(Items.SHIELD)) isOffhandUsing = true;
        }

        boolean isKillauraBlocking = this.getAuraTarget() != null;
        if (this.onlykillaura.get()) return isKillauraBlocking;
        if (isKillauraBlocking) return true;
        if (isOffhandUsing) return false;
        return mc.options.keyUse.isDown() || this.isBlocking;
    }

    private LivingEntity getAuraTarget() {
        Aura aura = Client.instance.getModuleManager().getModule(Aura.class);
        if (aura == null || !aura.isEnabled()) return null;
        return Aura.target instanceof LivingEntity living ? living : null;
    }
}
