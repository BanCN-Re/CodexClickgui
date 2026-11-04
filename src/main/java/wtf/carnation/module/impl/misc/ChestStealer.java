package wtf.carnation.module.impl.misc;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import wtf.carnation.event.EventPriority;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.movement.Scaffold;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.InventoryUtils;
import wtf.carnation.utils.TimeHelper;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Port of Naven's {@code ChestStealer}. */
public class ChestStealer extends Module {
    private static final TimeHelper timer = new TimeHelper();
    private final NumberValue delay = new NumberValue("Delay (ms)", 110.0, 0.0, 500.0, 10.0);
    private final BoolValue pickEnderChest = new BoolValue("Ender Chest", false);
    private Screen lastTickScreen;

    public ChestStealer() {
        super("ChestStealer", Category.MISC, "Automatically steals items from chests");
    }

    public static boolean isWorking() {
        return !timer.delay(150.0);
    }

    @EventTarget
    @EventPriority(1)
    public void onMotion(MotionEvent e) {
        if (!e.isPre() || mc.player == null) return;
        Screen currentScreen = mc.gui.screen();
        if (currentScreen instanceof ContainerScreen container) {
            ChestMenu menu = (ChestMenu) container.getMenu();
            if (currentScreen != this.lastTickScreen) {
                timer.reset();
            } else {
                String chestTitle = container.getTitle().getString();
                String chest = Component.translatable("container.chest").getString();
                String largeChest = Component.translatable("container.chestDouble").getString();
                String enderChest = Component.translatable("container.enderchest").getString();
                if (chestTitle.equals(chest) || chestTitle.equals(largeChest)
                        || chestTitle.equals("Chest")
                        || this.pickEnderChest.get() && chestTitle.equals(enderChest)) {
                    if (this.isChestEmpty(menu) && timer.delay(this.delay.get())) {
                        mc.player.closeContainer();
                    } else {
                        List<Integer> slots = IntStream.range(0, menu.getRowCount() * 9)
                                .boxed().collect(Collectors.toList());
                        Collections.shuffle(slots);
                        for (Integer slotId : slots) {
                            ItemStack stack = menu.getSlot(slotId).getItem();
                            if (isItemUseful(stack) && this.isBestItemInChest(menu, stack)
                                    && timer.delay(this.delay.get())) {
                                mc.gameMode.handleContainerInput(menu.containerId, slotId, 0,
                                        ContainerInput.QUICK_MOVE, mc.player);
                                timer.reset();
                                break;
                            }
                        }
                    }
                }
            }
        }
        this.lastTickScreen = currentScreen;
    }

    private boolean isBestItemInChest(ChestMenu menu, ItemStack stack) {
        if (InventoryUtils.isGodItem(stack) || InventoryUtils.isSharpnessAxe(stack)) return true;
        for (int i = 0; i < menu.getRowCount() * 9; i++) {
            ItemStack checkStack = menu.getSlot(i).getItem();
            if (InventoryUtils.isArmor(stack) && InventoryUtils.isArmor(checkStack)) {
                EquipmentSlot slot = InventoryUtils.getEquipmentSlot(stack);
                EquipmentSlot checkSlot = InventoryUtils.getEquipmentSlot(checkStack);
                if (slot == checkSlot && InventoryUtils.getProtection(checkStack) > InventoryUtils.getProtection(stack)) {
                    return false;
                }
            } else if (InventoryUtils.isSword(stack) && InventoryUtils.isSword(checkStack)) {
                if (InventoryUtils.getSwordDamage(checkStack) > InventoryUtils.getSwordDamage(stack)) return false;
            } else if (InventoryUtils.isPickaxe(stack) && InventoryUtils.isPickaxe(checkStack)) {
                if (InventoryUtils.getToolScore(checkStack) > InventoryUtils.getToolScore(stack)) return false;
            } else if (InventoryUtils.isAxe(stack) && InventoryUtils.isAxe(checkStack)) {
                if (InventoryUtils.getToolScore(checkStack) > InventoryUtils.getToolScore(stack)) return false;
            } else if (InventoryUtils.isShovel(stack) && InventoryUtils.isShovel(checkStack)
                    && InventoryUtils.getToolScore(checkStack) > InventoryUtils.getToolScore(stack)) {
                return false;
            }
        }
        return true;
    }

    private boolean isChestEmpty(ChestMenu menu) {
        for (int i = 0; i < menu.getRowCount() * 9; i++) {
            ItemStack item = menu.getSlot(i).getItem();
            if (!item.isEmpty() && isItemUseful(item) && this.isBestItemInChest(menu, item)) return false;
        }
        return true;
    }

    public static boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (InventoryUtils.isGodItem(stack) || InventoryUtils.isSharpnessAxe(stack)) return true;
        if (InventoryUtils.isArmor(stack)) {
            EquipmentSlot slot = InventoryUtils.getEquipmentSlot(stack);
            float protection = InventoryUtils.getProtection(stack);
            return !(protection <= InventoryUtils.getBestArmorScore(slot));
        }
        if (InventoryUtils.isSword(stack)) {
            return !(InventoryUtils.getSwordDamage(stack) <= InventoryUtils.getBestSwordDamage());
        }
        if (InventoryUtils.isPickaxe(stack)) {
            return !(InventoryUtils.getToolScore(stack) <= InventoryUtils.getBestPickaxeScore());
        }
        if (InventoryUtils.isAxe(stack)) {
            return !(InventoryUtils.getToolScore(stack) <= InventoryUtils.getBestAxeScore());
        }
        if (InventoryUtils.isShovel(stack)) {
            return !(InventoryUtils.getToolScore(stack) <= InventoryUtils.getBestShovelScore());
        }
        if (stack.getItem() instanceof CrossbowItem) {
            return !(InventoryUtils.getCrossbowScore(stack) <= InventoryUtils.getBestCrossbowScore());
        }
        if (stack.getItem() instanceof BowItem && InventoryUtils.isPunchBow(stack)) {
            return !(InventoryUtils.getPunchBowScore(stack) <= InventoryUtils.getBestPunchBowScore());
        }
        if (stack.getItem() instanceof BowItem && InventoryUtils.isPowerBow(stack)) {
            return !(InventoryUtils.getPowerBowScore(stack) <= InventoryUtils.getBestPowerBowScore());
        }
        if (stack.getItem() == Items.COMPASS) return !InventoryUtils.hasItem(stack.getItem());
        if (stack.getItem() == Items.WATER_BUCKET
                && InventoryUtils.getItemCount(Items.WATER_BUCKET) >= InventoryCleaner.getWaterBucketCount()) {
            return false;
        }
        if (stack.getItem() == Items.LAVA_BUCKET
                && InventoryUtils.getItemCount(Items.LAVA_BUCKET) >= InventoryCleaner.getLavaBucketCount()) {
            return false;
        }
        if (stack.getItem() instanceof BlockItem && Scaffold.isValidStack(stack)
                && InventoryUtils.getBlockCountInInventory() + stack.getCount() >= InventoryCleaner.getMaxBlockSize()) {
            return false;
        }
        if (stack.getItem() == Items.ARROW
                && InventoryUtils.getItemCount(Items.ARROW) + stack.getCount() >= InventoryCleaner.getMaxArrowSize()) {
            return false;
        }
        if (stack.getItem() instanceof FishingRodItem && InventoryUtils.getItemCount(Items.FISHING_ROD) >= 1) {
            return false;
        }
        if ((stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.EGG)
                && (InventoryUtils.getItemCount(Items.SNOWBALL) + InventoryUtils.getItemCount(Items.EGG)
                + stack.getCount() < InventoryCleaner.getMaxProjectileSize()
                && InventoryCleaner.shouldKeepProjectile())) {
            return !InventoryUtils.isPlantableSeed(stack) && InventoryUtils.isCommonItemUseful(stack);
        }
        return false;
    }
}
