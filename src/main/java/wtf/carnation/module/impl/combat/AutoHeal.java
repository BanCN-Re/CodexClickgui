package wtf.carnation.module.impl.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.InventoryUtils;
import wtf.carnation.utils.TimeHelper;

/** Port of Naven's {@code tech.naven.modules.impl.combat.AutoHeal}. */
public class AutoHeal extends Module {
    private final TimeHelper timer = new TimeHelper();
    private final BoolValue speedCheck = new BoolValue("Speed Check", true);
    private final BoolValue regenCheck = new BoolValue("Regen Check", true);
    private final NumberValue delay = new NumberValue("Delay", 500.0, 300.0, 1000.0, 1.0);
    private final NumberValue health = new NumberValue("Health Percent", 0.5, 0.0, 1.0, 0.05);
    private final ModeValue mode = new ModeValue("Mode", new String[]{"Soup", "Head"}, "Soup");

    private boolean switchBack = false;
    private boolean useItem = false;
    private boolean throwItem = false;

    public AutoHeal() {
        super("AutoHeal", Category.COMBAT, "Automatically heals you when you're low on health.");
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (!e.isPre() || mc.player == null) return;

        if (this.useItem) {
            mc.getConnection().send(new ServerboundUseItemPacket(
                    InteractionHand.MAIN_HAND, 0, mc.player.getYRot(), mc.player.getXRot()));
            this.useItem = false;
            return;
        }

        if (this.throwItem) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.DROP_ITEM, BlockPos.ZERO, Direction.DOWN));
            this.throwItem = false;
            return;
        }

        if (this.switchBack) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(mc.player.getInventory().getSelectedSlot()));
            this.switchBack = false;
            return;
        }

        if (!this.timer.delay(this.delay.get())) return;
        if (mc.player.hasEffect(MobEffects.SPEED) && this.speedCheck.get()) return;
        if (mc.player.hasEffect(MobEffects.REGENERATION) && this.regenCheck.get()) return;

        if (mc.player.getHealth() / mc.player.getMaxHealth() < this.health.get()) {
            if (this.mode.is("Soup")) {
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (stack.getItem() == Items.MUSHROOM_STEW) {
                        this.switchUseItem(i, true);
                        this.switchBack = true;
                        break;
                    }
                }
            } else if (this.mode.is("Head")) {
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (InventoryUtils.isGoldenHead(stack)) {
                        this.switchUseItem(i, false);
                        this.switchBack = true;
                        break;
                    }
                }
            }
        }
    }

    private void switchUseItem(int slot, boolean throwItem) {
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        this.throwItem = throwItem;
        this.useItem = true;
    }
}
