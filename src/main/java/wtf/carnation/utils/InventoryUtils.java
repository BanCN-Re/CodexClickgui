package wtf.carnation.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.StemBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Port of Naven's {@code tech.naven.utils.InventoryUtils}. */
public final class InventoryUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    private InventoryUtils() {
    }

    public static boolean isSword(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ItemTags.SWORDS);
    }

    public static boolean isPickaxe(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ItemTags.PICKAXES);
    }

    public static boolean isAxe(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ItemTags.AXES);
    }

    public static boolean isShovel(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ItemTags.SHOVELS);
    }

    public static EquipmentSlot getEquipmentSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable == null ? null : equippable.slot();
    }

    public static boolean isArmor(ItemStack stack) {
        EquipmentSlot slot = getEquipmentSlot(stack);
        return slot != null && slot.isArmor() && getArmorValue(stack) > 0.0;
    }

    public static double getArmorValue(ItemStack stack) {
        EquipmentSlot slot = getEquipmentSlot(stack);
        if (slot == null || !slot.isArmor()) return 0.0;
        return getAttributeValue(stack, Attributes.ARMOR, slot);
    }

    public static double getAttributeValue(ItemStack stack, Holder<Attribute> attribute, EquipmentSlot slot) {
        if (stack == null || stack.isEmpty() || slot == null) return 0.0;
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY);
        double total = 0.0;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.slot().test(slot) && entry.attribute().is(attribute)
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                total += entry.modifier().amount();
            }
        }
        return total;
    }

    public static boolean isPlantableSeed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            return block instanceof CropBlock || block instanceof StemBlock || block instanceof NetherWartBlock;
        }
        return false;
    }

    public static Holder<Enchantment> getEnchantment(ResourceKey<Enchantment> enchantment) {
        if (mc.level == null) return null;
        return mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(enchantment).orElse(null);
    }

    public static int getEnchantmentLevel(ResourceKey<Enchantment> enchantment, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        Holder<Enchantment> holder = getEnchantment(enchantment);
        return holder == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }

    public static boolean shouldDisableFeatures() {
        return getAllItems().stream().anyMatch(item -> {
            if (item.isEmpty()) return false;
            String name = item.getDisplayName().getString();
            return name.contains("长按点击") || name.contains("点击使用") || name.contains("离开游戏")
                    || name.contains("选择一个队伍") || name.contains("再来一局");
        });
    }

    public static boolean isGoldenHead(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SkullBlock;
    }

    public static boolean isSharpnessAxe(ItemStack stack) {
        if (stack.isEmpty() || !isAxe(stack)) return false;
        int level = getEnchantmentLevel(Enchantments.SHARPNESS, stack);
        return level >= 8 && level < 50;
    }

    public static boolean isGodAxe(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() == Items.GOLDEN_AXE && getEnchantmentLevel(Enchantments.SHARPNESS, stack) > 100;
    }

    public static boolean isEnchantedGApple(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
    }

    public static boolean isEndCrystal(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.END_CRYSTAL;
    }

    public static boolean isKBBall(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.SLIME_BALL
                && getEnchantmentLevel(Enchantments.KNOCKBACK, stack) > 1;
    }

    public static boolean isKBStick(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.STICK
                && getEnchantmentLevel(Enchantments.KNOCKBACK, stack) > 1;
    }

    public static int findEmptyInventory() {
        for (int i = 9; i < Inventory.INVENTORY_SIZE; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    public static int findEmptySlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    public static int getPunchLevel(ItemStack stack) {
        return getEnchantmentLevel(Enchantments.PUNCH, stack);
    }

    public static int getPowerLevel(ItemStack stack) {
        return getEnchantmentLevel(Enchantments.POWER, stack);
    }

    public static List<ItemStack> getAllItems() {
        List<ItemStack> list = new ArrayList<>(40);
        if (mc.player == null) return list;
        Inventory inventory = mc.player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) list.add(inventory.getItem(i));
        list.add(mc.player.getItemBySlot(EquipmentSlot.FEET));
        list.add(mc.player.getItemBySlot(EquipmentSlot.LEGS));
        list.add(mc.player.getItemBySlot(EquipmentSlot.CHEST));
        list.add(mc.player.getItemBySlot(EquipmentSlot.HEAD));
        return list;
    }

    public static float getBestArmorScore(EquipmentSlot slot) {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && isArmor(item) && getEquipmentSlot(item) == slot)
                .map(InventoryUtils::getProtection)
                .max(Float::compareTo).orElse(0.0F);
    }

    public static float getCurrentArmorScore(EquipmentSlot slot) {
        if (slot != null && slot.isArmor() && mc.player != null) {
            return getProtection(mc.player.getItemBySlot(slot));
        }
        return 0.0F;
    }

    public static float getBestSwordDamage() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && isSword(item))
                .map(InventoryUtils::getSwordDamage)
                .max(Float::compareTo).orElse(0.0F);
    }

    public static ItemStack getBestSword() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && isSword(item))
                .max(Comparator.comparingInt(s -> (int) (getSwordDamage(s) * 100.0F)))
                .orElse(null);
    }

    public static int getItemStackSlot(ItemStack stack) {
        if (stack == null) return -1;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (mc.player.getInventory().getItem(i) == stack) return i;
        }
        return -1;
    }

    public static boolean isItemValid(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (stack.getItem() instanceof PlayerHeadItem) return false;
        String name = stack.getDisplayName().getString();
        return !name.contains("Click") && !name.contains("Right") && !name.contains("点击")
                && !name.contains("Teleport") && !name.contains("使用") && !name.contains("传送")
                && !name.contains("再来");
    }

    public static int getItemSlot(Item item) {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == item) return i;
        }
        return -1;
    }

    public static ItemStack getBestProjectile() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && (item.getItem() == Items.EGG || item.getItem() == Items.SNOWBALL)
                        && isItemValid(item))
                .max(Comparator.comparingInt(ItemStack::getCount)).orElse(null);
    }

    public static ItemStack getFishingRod() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof FishingRodItem && isItemValid(item))
                .findAny().orElse(null);
    }

    public static int getBlockCountInInventory() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BlockItem
                        && ScaffoldStack.isValidStack(item) && isItemValid(item))
                .mapToInt(ItemStack::getCount).sum();
    }

    public static ItemStack getWorstProjectile() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && (item.getItem() == Items.EGG || item.getItem() == Items.SNOWBALL))
                .min(Comparator.comparingInt(ItemStack::getCount)).orElse(null);
    }

    public static ItemStack getWorstArrow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof ArrowItem && isItemValid(item))
                .min(Comparator.comparingInt(ItemStack::getCount)).orElse(null);
    }

    public static ItemStack getWorstBlock() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BlockItem
                        && ScaffoldStack.isValidStack(item) && isItemValid(item))
                .min(Comparator.comparingInt(ItemStack::getCount)).orElse(null);
    }

    public static ItemStack getBestBlock() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BlockItem
                        && ScaffoldStack.isValidStack(item) && isItemValid(item))
                .max(Comparator.comparingInt(ItemStack::getCount)).orElse(null);
    }

    public static float getBestPickaxeScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && isPickaxe(item) && isItemValid(item))
                .map(InventoryUtils::getToolScore).max(Float::compareTo).orElse(0.0F);
    }

    public static ItemStack getBestPickaxe() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && isPickaxe(item) && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getToolScore(s) * 100.0F))).orElse(null);
    }

    public static float getBestAxeScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && isAxe(item) && !isSharpnessAxe(item) && isItemValid(item))
                .map(InventoryUtils::getToolScore).max(Float::compareTo).orElse(0.0F);
    }

    public static ItemStack getBestAxe() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && isAxe(item) && !isSharpnessAxe(item) && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getToolScore(s) * 100.0F))).orElse(null);
    }

    public static ItemStack getBestShapeAxe() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && isAxe(item) && isSharpnessAxe(item)
                        && isItemValid(item) && !isGodAxe(item))
                .max(Comparator.comparingInt(s -> (int) (getAxeDamage(s) * 100.0F))).orElse(null);
    }

    public static float getBestShovelScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && isShovel(item) && isItemValid(item))
                .map(InventoryUtils::getToolScore).max(Float::compareTo).orElse(0.0F);
    }

    public static ItemStack getBestShovel() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && isShovel(item) && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getToolScore(s) * 100.0F))).orElse(null);
    }

    public static float getBestCrossbowScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof CrossbowItem && isItemValid(item))
                .map(InventoryUtils::getCrossbowScore).max(Float::compareTo).orElse(0.0F);
    }

    public static ItemStack getBestCrossbow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof CrossbowItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getCrossbowScore(s) * 100.0F))).orElse(null);
    }

    public static float getBestPunchBowScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .map(InventoryUtils::getPunchBowScore).max(Float::compareTo).orElse(0.0F);
    }

    public static ItemStack getBestPunchBow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getPunchBowScore(s) * 100.0F))).orElse(null);
    }

    public static float getBestPowerBowScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .map(InventoryUtils::getPowerBowScore).max(Float::compareTo).orElse(0.0F);
    }

    public static ItemStack getBestPowerBow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getPowerBowScore(s) * 100.0F))).orElse(null);
    }

    public static boolean isPunchBow(ItemStack stack) {
        return getPunchBowScore(stack) > 10.0F && isItemValid(stack);
    }

    public static boolean isPowerBow(ItemStack stack) {
        return getPowerBowScore(stack) > 10.0F && isItemValid(stack);
    }

    public static boolean hasItem(Item item) {
        return getAllItems().stream().anyMatch(stack -> !stack.isEmpty() && stack.getItem() == item);
    }

    public static int getItemCount(Item item) {
        return getAllItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.getItem() == item)
                .mapToInt(ItemStack::getCount).sum();
    }

    public static float getPunchBowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BowItem)) return 0.0F;
        float value = 10.0F;
        value += getEnchantmentLevel(Enchantments.PUNCH, stack);
        value += getEnchantmentLevel(Enchantments.INFINITY, stack);
        value += getEnchantmentLevel(Enchantments.FLAME, stack);
        value += getEnchantmentLevel(Enchantments.POWER, stack) / 10.0F;
        return value + (float) stack.getDamageValue() / stack.getMaxDamage();
    }

    public static float getPowerBowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BowItem)) return 0.0F;
        float value = 10.0F;
        value += getEnchantmentLevel(Enchantments.PUNCH, stack) / 10.0F;
        value += getEnchantmentLevel(Enchantments.INFINITY, stack);
        value += getEnchantmentLevel(Enchantments.FLAME, stack);
        value += getEnchantmentLevel(Enchantments.POWER, stack);
        return value + (float) stack.getDamageValue() / stack.getMaxDamage();
    }

    public static float getToolScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || isGodItem(stack) || isSharpnessAxe(stack)) return 0.0F;
        float value;
        if (isPickaxe(stack)) {
            value = stack.getDestroySpeed(Blocks.STONE.defaultBlockState());
        } else if (isAxe(stack)) {
            value = stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState());
        } else if (isShovel(stack)) {
            value = stack.getDestroySpeed(Blocks.DIRT.defaultBlockState());
        } else {
            return 0.0F;
        }
        int efficiency = getEnchantmentLevel(Enchantments.EFFICIENCY, stack);
        if (efficiency > 0) value += efficiency * 0.0075F;
        return value;
    }

    public static float getAxeDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0F;
        float value = 0.0F;
        if (isAxe(stack) && isSharpnessAxe(stack)) {
            Item axe = stack.getItem();
            if (axe == Items.WOODEN_AXE) value += 4.0F;
            else if (axe == Items.STONE_AXE) value += 5.0F;
            else if (axe == Items.IRON_AXE) value += 6.0F;
            else if (axe == Items.GOLDEN_AXE) value += 4.0F;
            else if (axe == Items.DIAMOND_AXE) value += 7.0F;
        }
        int sharpness = getEnchantmentLevel(Enchantments.SHARPNESS, stack);
        if (sharpness > 0) value += 0.5F * sharpness + 0.5F;
        return value;
    }

    public static float getSwordDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0F;
        float value = 0.0F;
        if (isSword(stack)) value += (float) getAttributeValue(stack, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND) + 1.0F;
        int sharpness = getEnchantmentLevel(Enchantments.SHARPNESS, stack);
        if (sharpness > 0) value += 0.5F * sharpness + 0.5F;
        return value;
    }

    public static float getProtection(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0F;
        int value = 0;
        if (isArmor(stack)) {
            value += (int) (getArmorValue(stack) * 100.0);
            value += (int) (getAttributeValue(stack, Attributes.ARMOR_TOUGHNESS, getEquipmentSlot(stack)) * 10.0);
        }
        value += getEnchantmentLevel(Enchantments.PROTECTION, stack);
        return value;
    }

    public static float getCrossbowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof CrossbowItem)) return 0.0F;
        int value = 0;
        value += getEnchantmentLevel(Enchantments.QUICK_CHARGE, stack);
        value += getEnchantmentLevel(Enchantments.MULTISHOT, stack);
        value += getEnchantmentLevel(Enchantments.PIERCING, stack);
        return value;
    }

    public static boolean isGodItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (isAxe(stack) && stack.getItem() == Items.GOLDEN_AXE
                && getEnchantmentLevel(Enchantments.SHARPNESS, stack) > 100) {
            return true;
        }
        if (stack.getItem() == Items.SLIME_BALL && getEnchantmentLevel(Enchantments.KNOCKBACK, stack) > 1) {
            return true;
        }
        return stack.getItem() == Items.TOTEM_OF_UNDYING || stack.getItem() == Items.END_CRYSTAL;
    }

    public static boolean isCommonItemUseful(ItemStack stack) {
        if (stack.isEmpty()) return true;
        Item item = stack.getItem();
        if (item instanceof BlockItem block) {
            if (block.getBlock() == Blocks.ENCHANTING_TABLE || block.getBlock() == Blocks.COBWEB) return false;
        } else {
            if (item == Items.BOOK || item instanceof ExperienceBottleItem || item instanceof FireworkRocketItem) {
                return false;
            }
            if (item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS
                    || item == Items.MELON_SEEDS || item == Items.PUMPKIN_SEEDS) {
                return false;
            }
            if (item == Items.FLINT_AND_STEEL) return false;
        }
        return true;
    }

    /** Small bridge so this class does not import the Scaffold module. */
    private static final class ScaffoldStack {
        static boolean isValidStack(ItemStack stack) {
            return wtf.carnation.module.impl.movement.Scaffold.isValidStack(stack);
        }
    }
}
