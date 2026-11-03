package wtf.carnation.module.impl.movement;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;

public class NoSlow extends Module {
    private final ModeValue mode = new ModeValue("Mode",
            new String[]{"Always", "1:1 Pattern", "1:3 Pattern", "3:2 Pattern"}, "Always");
    private final BoolValue food = new BoolValue("Food", true);
    private final BoolValue bow = new BoolValue("Bow", true);
    private final BoolValue crossbow = new BoolValue("Crossbow", true);

    public NoSlow() {
        super("NoSlow", Category.MOVEMENT, "使用物品时不再减速");
    }

    /** Called from {@code LocalPlayer#itemUseSpeedMultiplier}. */
    public boolean shouldCancelSlowdown() {
        if (mc.player == null || !mc.player.isUsingItem()) return false;
        ItemStack stack = mc.player.getUseItem();
        if (!food.get() && stack.has(DataComponents.FOOD)) return false;
        if (!bow.get() && stack.is(Items.BOW)) return false;
        if (!crossbow.get() && stack.is(Items.CROSSBOW)) return false;

        int remaining = mc.player.getUseItemRemainingTicks();
        return switch (mode.get()) {
            case "1:1 Pattern" -> remaining % 2 == 0;
            case "1:3 Pattern" -> remaining % 3 == 0;
            case "3:2 Pattern" -> remaining % 3 != 0;
            default -> true;
        };
    }
}
