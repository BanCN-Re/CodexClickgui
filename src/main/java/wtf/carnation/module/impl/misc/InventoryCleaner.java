package wtf.carnation.module.impl.misc;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.EventSprint;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.combat.Aura;
import wtf.carnation.module.impl.movement.Scaffold;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.ChatUtil;
import wtf.carnation.utils.InventoryUtils;
import wtf.carnation.utils.MoveUtil;
import wtf.carnation.utils.TimeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Port of Naven's {@code InventoryCleaner} (display name "InventoryManager"). */
public class InventoryCleaner extends Module {
    private static final TimeHelper timer = new TimeHelper();
    private int sprintStopCooldown = 0;
    private boolean lastSprintState = false;
    private int actionCooldown = 999;

    private final NumberValue delay = new NumberValue("Delay (ms)", 110.0, 0.0, 500.0, 10.0);
    private final ModeValue offhandItems = new ModeValue("Offhand Items",
            new String[]{"None", "Golden Apple", "Projectile", "Fishing Rod", "Block"}, "None");
    private final BoolValue autoArmor = new BoolValue("Auto Armor", true);
    private final BoolValue inventoryOnly = new BoolValue("Inventory Only", false);
    private final BoolValue pauseOnKillAura = new BoolValue("Pause On KillAura", true);
    private final BoolValue switchSword = new BoolValue("Switch Sword", true);
    private final NumberValue swordSlot = new NumberValue("Sword Slot", 1.0, 1.0, 9.0, 1.0);
    private final BoolValue switchBlock = new BoolValue("Switch Block", true);
    private final NumberValue blockSlot = new NumberValue("Block Slot", 2.0, 1.0, 9.0, 1.0);
    private final NumberValue maxBlockSize = new NumberValue("Max Block Size", 256.0, 64.0, 512.0, 64.0);
    private final BoolValue switchPickaxe = new BoolValue("Switch Pickaxe", true);
    private final NumberValue pickaxeSlot = new NumberValue("Pickaxe Slot", 3.0, 1.0, 9.0, 1.0);
    private final BoolValue switchAxe = new BoolValue("Switch Axe", true);
    private final NumberValue axeSlot = new NumberValue("Axe Slot", 4.0, 1.0, 9.0, 1.0);
    private final BoolValue switchBow = new BoolValue("Switch Bow or Crossbow", true);
    private final NumberValue bowSlot = new NumberValue("Bow Slot", 5.0, 1.0, 9.0, 1.0);
    private final ModeValue preferBow = new ModeValue("Bow Priority",
            new String[]{"Crossbow", "Power Bow", "Punch Bow"}, "Crossbow");
    private final NumberValue maxArrowSize = new NumberValue("Max Arrow Size", 256.0, 64.0, 512.0, 64.0);
    private final BoolValue switchWaterBucket = new BoolValue("Switch Water Bucket", true);
    private final NumberValue waterBucketSlot = new NumberValue("Water Bucket Slot", 6.0, 1.0, 9.0, 1.0);
    private final BoolValue switchEnderPearl = new BoolValue("Switch Ender Pearl", true);
    private final NumberValue enderPearlSlot = new NumberValue("Ender Pearl Slot", 7.0, 1.0, 9.0, 1.0);
    private final BoolValue switchFireball = new BoolValue("Switch Fireball", true);
    private final NumberValue fireballSlot = new NumberValue("Fireball Slot", 8.0, 1.0, 9.0, 1.0);
    private final BoolValue switchGoldenApple = new BoolValue("Switch Golden Apple", true);
    private final NumberValue goldenAppleSlot = new NumberValue("Golden Apple Slot", 9.0, 1.0, 9.0, 1.0);
    private final BoolValue throwItems = new BoolValue("Throw Items", true);
    private final NumberValue waterBucketCount = new NumberValue("Keep Water Buckets", 1.0, 0.0, 5.0, 1.0);
    private final NumberValue lavaBucketCount = new NumberValue("Keep Lava Buckets", 1.0, 0.0, 5.0, 1.0);
    private final BoolValue keepProjectile = new BoolValue("Keep Eggs & Snowballs", true);
    private final BoolValue switchProjectile = new BoolValue("Switch Eggs & Snowballs", false);
    private final NumberValue projectileSlot = new NumberValue("Eggs & Snowballs Slot", 9.0, 1.0, 9.0, 1.0);
    private final NumberValue maxProjectileSize = new NumberValue("Max Eggs & Snowballs Size", 64.0, 16.0, 256.0, 16.0);
    private final BoolValue switchRod = new BoolValue("Switch Rod", false);
    private final NumberValue rodSlot = new NumberValue("Rod Slot", 9.0, 1.0, 9.0, 1.0);

    int noMoveTicks = 0;
    private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private boolean clickOffHand = false;
    private boolean inventoryOpen = false;

    public InventoryCleaner() {
        super("InventoryManager", Category.MISC, "Automatically manage your inventory");
    }

    private static InventoryCleaner instance() {
        return Client.instance.getModuleManager().getModule(InventoryCleaner.class);
    }

    public static int getMaxBlockSize() {
        InventoryCleaner module = instance();
        return module == null ? 256 : module.maxBlockSize.getInt();
    }

    public static boolean shouldKeepProjectile() {
        InventoryCleaner module = instance();
        return module != null && module.keepProjectile.get();
    }

    public static int getMaxProjectileSize() {
        InventoryCleaner module = instance();
        return module == null ? 64 : module.maxProjectileSize.getInt();
    }

    public static int getMaxArrowSize() {
        InventoryCleaner module = instance();
        return module == null ? 256 : module.maxArrowSize.getInt();
    }

    public static int getWaterBucketCount() {
        InventoryCleaner module = instance();
        return module == null ? 1 : module.waterBucketCount.getInt();
    }

    public static int getLavaBucketCount() {
        InventoryCleaner module = instance();
        return module == null ? 1 : module.lavaBucketCount.getInt();
    }

    public boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (InventoryUtils.isGodItem(stack)) return true;
        if (stack.getDisplayName().getString().contains("点击使用")) return true;
        if (InventoryUtils.isArmor(stack)) {
            EquipmentSlot armorSlot = InventoryUtils.getEquipmentSlot(stack);
            float protection = InventoryUtils.getProtection(stack);
            if (InventoryUtils.getCurrentArmorScore(armorSlot) >= protection) return false;
            return !(protection < InventoryUtils.getBestArmorScore(armorSlot));
        }
        if (InventoryUtils.isSword(stack)) return InventoryUtils.getBestSword() == stack;
        if (InventoryUtils.isPickaxe(stack)) return InventoryUtils.getBestPickaxe() == stack;
        if (InventoryUtils.isAxe(stack) && !InventoryUtils.isSharpnessAxe(stack)) return InventoryUtils.getBestAxe() == stack;
        if (InventoryUtils.isShovel(stack)) return InventoryUtils.getBestShovel() == stack;
        if (stack.getItem() instanceof CrossbowItem) return InventoryUtils.getBestCrossbow() == stack;
        if (stack.getItem() instanceof BowItem && InventoryUtils.isPunchBow(stack)) return InventoryUtils.getBestPunchBow() == stack;
        if (stack.getItem() instanceof BowItem && InventoryUtils.isPowerBow(stack)) return InventoryUtils.getBestPowerBow() == stack;
        if (stack.getItem() instanceof BowItem && InventoryUtils.getItemCount(Items.BOW) > 1) return false;
        if (stack.getItem() == Items.WATER_BUCKET && InventoryUtils.getItemCount(Items.WATER_BUCKET) > getWaterBucketCount()) return false;
        if (stack.getItem() == Items.LAVA_BUCKET && InventoryUtils.getItemCount(Items.LAVA_BUCKET) > getLavaBucketCount()) return false;
        if (stack.getItem() instanceof FishingRodItem && InventoryUtils.getItemCount(Items.FISHING_ROD) > 1) return false;
        if ((stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.EGG) && !shouldKeepProjectile()) return false;
        return !InventoryUtils.isPlantableSeed(stack) && InventoryUtils.isCommonItemUseful(stack);
    }

    @EventTarget
    public void onPacket(PacketEvent e) {
        if (!e.isSend() || mc.player == null) return;
        if (e.getPacket() instanceof ServerboundContainerClosePacket) this.inventoryOpen = false;
        if (this.inventoryOpen && !this.inventoryOnly.get()) {
            if (e.getPacket() instanceof ServerboundMovePlayerPacket) {
                if (MoveUtil.isMoving() && mc.getConnection() != null) {
                    mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
                }
            } else if (e.getPacket() instanceof ServerboundUseItemOnPacket
                    || e.getPacket() instanceof ServerboundUseItemPacket
                    || e.getPacket() instanceof ServerboundInteractPacket
                    || e.getPacket() instanceof ServerboundPlayerActionPacket) {
                if (mc.getConnection() != null) {
                    mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
                }
            }
        }
    }

    private boolean checkConfig() {
        List<int[]> pairs = new ArrayList<>();
        if (!this.keepProjectile.get()) this.switchProjectile.set(false);
        addPair(pairs, this.switchSword, this.swordSlot);
        addPair(pairs, this.switchPickaxe, this.pickaxeSlot);
        addPair(pairs, this.switchAxe, this.axeSlot);
        addPair(pairs, this.switchBow, this.bowSlot);
        addPair(pairs, this.switchWaterBucket, this.waterBucketSlot);
        addPair(pairs, this.switchEnderPearl, this.enderPearlSlot);
        addPair(pairs, this.switchFireball, this.fireballSlot);
        if (!this.offhandItems.is("Golden Apple")) addPair(pairs, this.switchGoldenApple, this.goldenAppleSlot);
        if (!this.offhandItems.is("Projectile")) addPair(pairs, this.switchProjectile, this.projectileSlot);
        if (!this.offhandItems.is("Fishing Rod")) addPair(pairs, this.switchRod, this.rodSlot);
        if (!this.offhandItems.is("Block")) addPair(pairs, this.switchBlock, this.blockSlot);

        Set<Integer> used = new HashSet<>();
        for (int[] pair : pairs) {
            if (pair[0] == 1) {
                if (!used.add(pair[1])) return false;
            }
        }
        return true;
    }

    private static void addPair(List<int[]> pairs, BoolValue toggle, NumberValue slot) {
        if (!toggle.get()) return;
        pairs.add(new int[]{1, slot.getInt() - 1});
    }

    @EventTarget
    public void onSprint(EventSprint event) {
        if (this.inventoryOnly.get() || mc.player == null) return;
        boolean nowSprinting = mc.player.isSprinting();
        if (this.lastSprintState && !nowSprinting) this.sprintStopCooldown = Math.max(this.sprintStopCooldown, 2);
        this.lastSprintState = nowSprinting;
        if (this.isPerformingSilentAction()) this.suppressSprintForInventory();
    }

    public void suppressSprintForInventory() {
        if (mc.player == null) return;
        mc.options.keySprint.setDown(false);
        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
            this.sprintStopCooldown = Math.max(this.sprintStopCooldown, 2);
        }
    }

    public boolean isPerformingSilentAction() {
        return this.isEnabled() && !this.inventoryOnly.get() && !this.shouldPauseForAction()
                && (this.hasPendingActions() || this.sprintStopCooldown > 0 || this.inventoryOpen);
    }

    public boolean shouldPauseForAction() {
        if (mc.player == null) return true;
        if (InventoryUtils.shouldDisableFeatures()) return true;
        Module chestStealer = Client.instance.getModuleManager().getModuleByName("ChestStealer");
        if (chestStealer != null && chestStealer.isEnabled()
                && ChestStealer.isWorking()) return true;
        Module scaffold = Client.instance.getModuleManager().getModuleByName("Scaffold");
        if (scaffold != null && scaffold.isEnabled()) return true;
        if (mc.player.isUsingItem()) return true;
        if (this.pauseOnKillAura.get() && Aura.target != null) return true;
        return this.inventoryOnly.get() && !(mc.gui.screen() instanceof InventoryScreen);
    }

    public boolean hasPendingActions() {
        if (mc.player == null || mc.level == null) return false;
        if (this.actionCooldown < 5) return true;

        if (this.autoArmor.get()) {
            for (EquipmentSlot armorSlot : ARMOR_SLOTS) {
                ItemStack equipped = mc.player.getItemBySlot(armorSlot);
                if (InventoryUtils.isArmor(equipped) && !equipped.isEmpty()
                        && InventoryUtils.getBestArmorScore(InventoryUtils.getEquipmentSlot(equipped))
                        > InventoryUtils.getProtection(equipped)) {
                    return true;
                }
            }
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                ItemStack candidate = mc.player.getInventory().getItem(i);
                if (!candidate.isEmpty() && InventoryUtils.isArmor(candidate)) {
                    EquipmentSlot slot = InventoryUtils.getEquipmentSlot(candidate);
                    float score = InventoryUtils.getProtection(candidate);
                    if (InventoryUtils.getBestArmorScore(slot) == score
                            && InventoryUtils.getCurrentArmorScore(slot) < score) {
                        return true;
                    }
                }
            }
        }

        if (this.clickOffHand) return true;

        if (this.offhandItems.is("Golden Apple")) {
            ItemStack offHand = mc.player.getItemBySlot(EquipmentSlot.OFFHAND);
            int slot = InventoryUtils.getItemSlot(Items.GOLDEN_APPLE);
            if (slot != -1) {
                if (offHand.getItem() == Items.GOLDEN_APPLE) {
                    ItemStack goldenApple = mc.player.getInventory().getItem(slot);
                    if (offHand.getCount() + goldenApple.getCount() <= 64) return true;
                } else {
                    return true;
                }
            }
        } else if (this.offhandItems.is("Projectile")) {
            ItemStack offHand = mc.player.getItemBySlot(EquipmentSlot.OFFHAND);
            ItemStack bestProjectile = InventoryUtils.getBestProjectile();
            if (bestProjectile != null && InventoryUtils.getItemStackSlot(bestProjectile) != -1) {
                if (offHand.getItem() != Items.EGG && offHand.getItem() != Items.SNOWBALL) return true;
                if (offHand.getCount() < bestProjectile.getCount()) return true;
            }
        } else if (this.offhandItems.is("Fishing Rod")) {
            ItemStack offHand = mc.player.getItemBySlot(EquipmentSlot.OFFHAND);
            if (InventoryUtils.getItemSlot(Items.FISHING_ROD) != -1 && offHand.getItem() != Items.FISHING_ROD) {
                return true;
            }
        } else if (this.offhandItems.is("Block")) {
            ItemStack offHand = mc.player.getItemBySlot(EquipmentSlot.OFFHAND);
            ItemStack bestBlock = InventoryUtils.getBestBlock();
            if (bestBlock != null && InventoryUtils.getItemStackSlot(bestBlock) != -1) {
                if (!Scaffold.isValidStack(offHand) || offHand.getCount() < bestBlock.getCount()) return true;
            }
        }

        if (this.switchGoldenApple.get() && !this.offhandItems.is("Golden Apple")
                && this.needsItemSwap(this.goldenAppleSlot.getInt() - 1, Items.GOLDEN_APPLE)) {
            return true;
        }

        if (this.switchBlock.get()) {
            int blockSlot = this.blockSlot.getInt() - 1;
            ItemStack currentBlock = mc.player.getInventory().getItem(blockSlot);
            ItemStack bestBlock = InventoryUtils.getBestBlock();
            if (bestBlock != null
                    && (bestBlock.getCount() > currentBlock.getCount() || !Scaffold.isValidStack(currentBlock))
                    && !this.offhandItems.is("Block")
                    && InventoryUtils.isItemValid(currentBlock)
                    && InventoryUtils.getItemStackSlot(bestBlock) != -1) {
                return true;
            }
            if ((float) InventoryUtils.getBlockCountInInventory() > this.maxBlockSize.get()) {
                ItemStack worstBlock = InventoryUtils.getWorstBlock();
                if (InventoryUtils.isItemValid(worstBlock) && InventoryUtils.getItemStackSlot(worstBlock) != -1) {
                    return true;
                }
            }
        }

        if (this.switchSword.get()) {
            int slot = this.swordSlot.getInt() - 1;
            ItemStack currentSword = mc.player.getInventory().getItem(slot);
            ItemStack bestSword = InventoryUtils.getBestSword();
            ItemStack bestShapeAxe = InventoryUtils.getBestShapeAxe();
            if (InventoryUtils.getAxeDamage(bestShapeAxe) > InventoryUtils.getSwordDamage(bestSword)) {
                bestSword = bestShapeAxe;
            }
            if (bestSword != null && InventoryUtils.isItemValid(currentSword)
                    && InventoryUtils.getItemStackSlot(bestSword) != -1) {
                float currentDamage = InventoryUtils.isSword(currentSword)
                        ? InventoryUtils.getSwordDamage(currentSword)
                        : InventoryUtils.getAxeDamage(currentSword);
                float bestDamage = InventoryUtils.isSword(bestSword)
                        ? InventoryUtils.getSwordDamage(bestSword)
                        : InventoryUtils.getAxeDamage(bestSword);
                if (bestDamage > currentDamage) return true;
            }
        }

        if (this.switchPickaxe.get()) {
            int slot = this.pickaxeSlot.getInt() - 1;
            ItemStack bestPickaxe = InventoryUtils.getBestPickaxe();
            ItemStack currentPickaxe = mc.player.getInventory().getItem(slot);
            if (bestPickaxe != null && InventoryUtils.isPickaxe(bestPickaxe)
                    && InventoryUtils.isItemValid(currentPickaxe)
                    && InventoryUtils.getItemStackSlot(bestPickaxe) != -1
                    && (InventoryUtils.getToolScore(bestPickaxe) > InventoryUtils.getToolScore(currentPickaxe)
                    || !InventoryUtils.isPickaxe(currentPickaxe))) {
                return true;
            }
        }

        if (this.switchAxe.get()) {
            int slot = this.axeSlot.getInt() - 1;
            ItemStack bestAxe = InventoryUtils.getBestAxe();
            ItemStack currentAxe = mc.player.getInventory().getItem(slot);
            if (bestAxe != null && InventoryUtils.isAxe(bestAxe) && InventoryUtils.isItemValid(currentAxe)
                    && InventoryUtils.getItemStackSlot(bestAxe) != -1
                    && (InventoryUtils.getToolScore(bestAxe) > InventoryUtils.getToolScore(currentAxe)
                    || !InventoryUtils.isAxe(currentAxe))) {
                return true;
            }
        }

        if (this.switchRod.get() && !this.offhandItems.is("Fishing Rod")) {
            int slot = this.rodSlot.getInt() - 1;
            ItemStack bestRod = InventoryUtils.getFishingRod();
            ItemStack currentRod = mc.player.getInventory().getItem(slot);
            if (bestRod != null && InventoryUtils.isItemValid(currentRod)
                    && !(currentRod.getItem() instanceof FishingRodItem)
                    && InventoryUtils.getItemStackSlot(bestRod) != -1) {
                return true;
            }
        }

        if (this.switchBow.get()) {
            int slot = this.bowSlot.getInt() - 1;
            ItemStack currentBow = mc.player.getInventory().getItem(slot);
            ItemStack bestBow;
            float bestBowScore;
            float currentBowScore;
            if (this.preferBow.is("Crossbow")) {
                bestBow = InventoryUtils.getBestCrossbow();
                bestBowScore = InventoryUtils.getCrossbowScore(bestBow);
                currentBowScore = InventoryUtils.getCrossbowScore(currentBow);
            } else if (this.preferBow.is("Power Bow")) {
                bestBow = InventoryUtils.getBestPowerBow();
                bestBowScore = InventoryUtils.getPowerBowScore(bestBow);
                currentBowScore = InventoryUtils.getPowerBowScore(currentBow);
            } else {
                bestBow = InventoryUtils.getBestPunchBow();
                bestBowScore = InventoryUtils.getPunchBowScore(bestBow);
                currentBowScore = InventoryUtils.getPunchBowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InventoryUtils.getBestCrossbow();
                bestBowScore = InventoryUtils.getCrossbowScore(bestBow);
                currentBowScore = InventoryUtils.getCrossbowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InventoryUtils.getBestPowerBow();
                bestBowScore = InventoryUtils.getPowerBowScore(bestBow);
                currentBowScore = InventoryUtils.getPowerBowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InventoryUtils.getBestPunchBow();
                bestBowScore = InventoryUtils.getPunchBowScore(bestBow);
                currentBowScore = InventoryUtils.getPunchBowScore(currentBow);
            }
            if (bestBow != null && bestBowScore > currentBowScore
                    && InventoryUtils.isItemValid(currentBow)
                    && InventoryUtils.getItemStackSlot(bestBow) != -1) {
                return true;
            }
            if ((float) InventoryUtils.getItemCount(Items.ARROW) > this.maxArrowSize.get()) {
                ItemStack worstArrow = InventoryUtils.getWorstArrow();
                if (InventoryUtils.isItemValid(worstArrow)
                        && InventoryUtils.getItemStackSlot(worstArrow) != -1) {
                    return true;
                }
            }
        }

        if (this.switchEnderPearl.get() && this.needsItemSwap(this.enderPearlSlot.getInt() - 1, Items.ENDER_PEARL)) return true;
        if (this.switchWaterBucket.get() && this.needsItemSwap(this.waterBucketSlot.getInt() - 1, Items.WATER_BUCKET)) return true;
        if (this.switchFireball.get() && this.needsItemSwap(this.fireballSlot.getInt() - 1, Items.FIRE_CHARGE)) return true;

        if (this.keepProjectile.get()) {
            if ((float) (InventoryUtils.getItemCount(Items.EGG) + InventoryUtils.getItemCount(Items.SNOWBALL))
                    > this.maxProjectileSize.get()) {
                ItemStack worstProjectile = InventoryUtils.getWorstProjectile();
                if (InventoryUtils.isItemValid(worstProjectile)
                        && InventoryUtils.getItemStackSlot(worstProjectile) != -1) {
                    return true;
                }
            }
            if (this.switchProjectile.get() && !this.offhandItems.is("Projectile")) {
                int slot = this.projectileSlot.getInt() - 1;
                if (InventoryUtils.getItemCount(Items.EGG) > 0) {
                    if (this.needsItemSwap(slot, Items.EGG)) return true;
                } else if (InventoryUtils.getItemCount(Items.SNOWBALL) > 0
                        && this.needsItemSwap(slot, Items.SNOWBALL)) {
                    return true;
                }
            }
        }

        if (this.throwItems.get()) {
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (!stack.isEmpty() && !this.isItemUseful(stack)
                        && InventoryUtils.isItemValid(stack)
                        && InventoryUtils.getItemStackSlot(stack) != -1) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean needsItemSwap(int targetSlot, Item item) {
        ItemStack current = mc.player.getInventory().getItem(targetSlot);
        if (!InventoryUtils.isItemValid(current)) return false;
        int source = InventoryUtils.getItemSlot(item);
        if (source == -1) return false;
        ItemStack sourceStack = mc.player.getInventory().getItem(source);
        return current.getItem() != item || current.getCount() < sourceStack.getCount();
    }

    private void click(int slotId, int button, ContainerInput clickType) {
        if (mc.gameMode == null || mc.player == null) return;
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slotId, button, clickType, mc.player);
    }

    public static void appendSilentClose(int containerId) {
        if (mc.player == null || mc.gui.screen() != null || mc.getConnection() == null) return;
        InventoryCleaner inventory = instance();
        if (inventory == null || !inventory.isEnabled() || inventory.inventoryOnly.get()) return;
        mc.getConnection().send(new ServerboundContainerClosePacket(containerId));
        inventory.inventoryOpen = false;
        inventory.actionCooldown = 0;
    }

    @EventTarget
    public void onMotionEnd(MotionEvent e) {
        if (e.isPre() || mc.player == null) return;
        if (this.inventoryOpen) {
            this.inventoryOpen = false;
            this.actionCooldown = 0;
        } else {
            this.actionCooldown++;
        }
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (!e.isPre() || mc.player == null) return;

        if (!this.checkConfig()) {
            ChatUtil.addChatMessage("Duplicate slot config in Inventory Manager! Please check your config!");
            this.toggle();
            return;
        }

        if (InventoryUtils.shouldDisableFeatures()) return;
        if (MoveUtil.isMoving()) this.noMoveTicks = 0;
        else this.noMoveTicks++;

        if (this.shouldPauseForAction()) {
            this.clickOffHand = false;
            return;
        }

        if (mc.gui.screen() instanceof AbstractContainerScreen<?> container
                && container.getMenu().containerId != mc.player.inventoryMenu.containerId) {
            return;
        }

        if (!this.inventoryOnly.get()) {
            if (mc.player.isSprinting()) {
                if (this.hasPendingActions()) this.suppressSprintForInventory();
                return;
            }
            if (this.sprintStopCooldown > 0) {
                this.sprintStopCooldown--;
                return;
            }
        }

        if (this.autoArmor.get()) {
            for (int i = 0; i < ARMOR_SLOTS.length; i++) {
                ItemStack stack = mc.player.getItemBySlot(ARMOR_SLOTS[i]);
                if (InventoryUtils.isArmor(stack)) {
                    EquipmentSlot armorSlot = InventoryUtils.getEquipmentSlot(stack);
                    if (!stack.isEmpty() && timer.delay(this.delay.get())
                            && InventoryUtils.getBestArmorScore(armorSlot) > InventoryUtils.getProtection(stack)) {
                        this.click(4 + (4 - i), 1, ContainerInput.THROW);
                        this.inventoryOpen = true;
                        timer.reset();
                    }
                }
            }
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (!stack.isEmpty() && InventoryUtils.isArmor(stack)) {
                    EquipmentSlot armorSlot = InventoryUtils.getEquipmentSlot(stack);
                    float score = InventoryUtils.getProtection(stack);
                    boolean isBest = InventoryUtils.getBestArmorScore(armorSlot) == score;
                    boolean isBetter = InventoryUtils.getCurrentArmorScore(armorSlot) < score;
                    if (isBest && isBetter && timer.delay(this.delay.get())) {
                        if (i < 9) this.click(i + 36, 0, ContainerInput.QUICK_MOVE);
                        else this.click(i, 0, ContainerInput.QUICK_MOVE);
                        this.inventoryOpen = true;
                        timer.reset();
                    }
                }
            }
        }

        if (this.clickOffHand && timer.delay(this.delay.get())) {
            this.click(45, 0, ContainerInput.PICKUP);
            this.inventoryOpen = true;
            this.clickOffHand = false;
            timer.reset();
        }

        if (this.offhandItems.is("Golden Apple")) {
            ItemStack offHand = mc.player.getItemBySlot(EquipmentSlot.OFFHAND);
            int slot = InventoryUtils.getItemSlot(Items.GOLDEN_APPLE);
            if (slot != -1 && timer.delay(this.delay.get())) {
                if (offHand.getItem() == Items.GOLDEN_APPLE) {
                    ItemStack goldenAppleStack = mc.player.getInventory().getItem(slot);
                    if (offHand.getCount() + goldenAppleStack.getCount() <= 64) {
                        if (slot < 9) this.click(slot + 36, 0, ContainerInput.PICKUP);
                        else this.click(slot, 0, ContainerInput.PICKUP);
                        this.inventoryOpen = true;
                        this.clickOffHand = true;
                        timer.reset();
                    }
                } else {
                    this.swapOffHand(slot);
                }
            }
        } else if (this.offhandItems.is("Projectile")) {
            ItemStack offHand = mc.player.getItemBySlot(EquipmentSlot.OFFHAND);
            ItemStack bestProjectile = InventoryUtils.getBestProjectile();
            if (bestProjectile != null) {
                int slot = InventoryUtils.getItemStackSlot(bestProjectile);
                boolean shouldSwap = offHand.getItem() != Items.EGG && offHand.getItem() != Items.SNOWBALL
                        || offHand.getCount() < bestProjectile.getCount();
                if (shouldSwap && slot != -1 && timer.delay(this.delay.get())) this.swapOffHand(slot);
            }
        } else if (this.offhandItems.is("Fishing Rod")) {
            ItemStack offHand = mc.player.getItemBySlot(EquipmentSlot.OFFHAND);
            int slot = InventoryUtils.getItemSlot(Items.FISHING_ROD);
            if (slot != -1 && timer.delay(this.delay.get()) && offHand.getItem() != Items.FISHING_ROD) {
                this.swapOffHand(slot);
            }
        } else if (this.offhandItems.is("Block")) {
            ItemStack offHand = mc.player.getItemBySlot(EquipmentSlot.OFFHAND);
            ItemStack bestBlock = InventoryUtils.getBestBlock();
            if (bestBlock != null) {
                int slot = InventoryUtils.getItemStackSlot(bestBlock);
                boolean shouldSwap = Scaffold.isValidStack(offHand)
                        ? offHand.getCount() < bestBlock.getCount() : true;
                if (shouldSwap && slot != -1 && timer.delay(this.delay.get())) this.swapOffHand(slot);
            }
        }

        if (this.switchGoldenApple.get() && !this.offhandItems.is("Golden Apple")) {
            this.swapItem(this.goldenAppleSlot.getInt() - 1, Items.GOLDEN_APPLE);
        }

        if (this.switchBlock.get()) {
            int blockSlot = this.blockSlot.getInt() - 1;
            ItemStack currentBlock = mc.player.getInventory().getItem(blockSlot);
            ItemStack bestBlock = InventoryUtils.getBestBlock();
            if (bestBlock != null
                    && (bestBlock.getCount() > currentBlock.getCount() || !Scaffold.isValidStack(currentBlock))
                    && !this.offhandItems.is("Block")) {
                this.swapItem(blockSlot, bestBlock);
            }
            if ((float) InventoryUtils.getBlockCountInInventory() > this.maxBlockSize.get()) {
                this.throwItem(InventoryUtils.getWorstBlock());
            }
        }

        if (this.switchSword.get()) {
            int slot = this.swordSlot.getInt() - 1;
            ItemStack currentSword = mc.player.getInventory().getItem(slot);
            ItemStack bestSword = InventoryUtils.getBestSword();
            ItemStack bestShapeAxe = InventoryUtils.getBestShapeAxe();
            if (InventoryUtils.getAxeDamage(bestShapeAxe) > InventoryUtils.getSwordDamage(bestSword)) {
                bestSword = bestShapeAxe;
            }
            if (bestSword != null) {
                float currentDamage = InventoryUtils.isSword(currentSword)
                        ? InventoryUtils.getSwordDamage(currentSword)
                        : InventoryUtils.getAxeDamage(currentSword);
                float bestDamage = InventoryUtils.isSword(bestSword)
                        ? InventoryUtils.getSwordDamage(bestSword)
                        : InventoryUtils.getAxeDamage(bestSword);
                if (bestDamage > currentDamage) this.swapItem(slot, bestSword);
            }
        }

        if (this.switchPickaxe.get()) {
            int slot = this.pickaxeSlot.getInt() - 1;
            ItemStack bestPickaxe = InventoryUtils.getBestPickaxe();
            ItemStack currentPickaxe = mc.player.getInventory().getItem(slot);
            if (bestPickaxe != null && InventoryUtils.isPickaxe(bestPickaxe)
                    && (InventoryUtils.getToolScore(bestPickaxe) > InventoryUtils.getToolScore(currentPickaxe)
                    || !InventoryUtils.isPickaxe(currentPickaxe))) {
                this.swapItem(slot, bestPickaxe);
            }
        }

        if (this.switchAxe.get()) {
            int slot = this.axeSlot.getInt() - 1;
            ItemStack bestAxe = InventoryUtils.getBestAxe();
            ItemStack currentAxe = mc.player.getInventory().getItem(slot);
            if (bestAxe != null && InventoryUtils.isAxe(bestAxe)
                    && (InventoryUtils.getToolScore(bestAxe) > InventoryUtils.getToolScore(currentAxe)
                    || !InventoryUtils.isAxe(currentAxe))) {
                this.swapItem(slot, bestAxe);
            }
        }

        if (this.switchRod.get() && !this.offhandItems.is("Fishing Rod")) {
            int slot = this.rodSlot.getInt() - 1;
            ItemStack bestRod = InventoryUtils.getFishingRod();
            ItemStack currentRod = mc.player.getInventory().getItem(slot);
            if (!(currentRod.getItem() instanceof FishingRodItem)) this.swapItem(slot, bestRod);
        }

        if (this.switchBow.get()) {
            int slot = this.bowSlot.getInt() - 1;
            ItemStack currentBow = mc.player.getInventory().getItem(slot);
            ItemStack bestBow;
            float bestBowScore;
            float currentBowScore;
            if (this.preferBow.is("Crossbow")) {
                bestBow = InventoryUtils.getBestCrossbow();
                bestBowScore = InventoryUtils.getCrossbowScore(bestBow);
                currentBowScore = InventoryUtils.getCrossbowScore(currentBow);
            } else if (this.preferBow.is("Power Bow")) {
                bestBow = InventoryUtils.getBestPowerBow();
                bestBowScore = InventoryUtils.getPowerBowScore(bestBow);
                currentBowScore = InventoryUtils.getPowerBowScore(currentBow);
            } else {
                bestBow = InventoryUtils.getBestPunchBow();
                bestBowScore = InventoryUtils.getPunchBowScore(bestBow);
                currentBowScore = InventoryUtils.getPunchBowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InventoryUtils.getBestCrossbow();
                bestBowScore = InventoryUtils.getCrossbowScore(bestBow);
                currentBowScore = InventoryUtils.getCrossbowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InventoryUtils.getBestPowerBow();
                bestBowScore = InventoryUtils.getPowerBowScore(bestBow);
                currentBowScore = InventoryUtils.getPowerBowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InventoryUtils.getBestPunchBow();
                bestBowScore = InventoryUtils.getPunchBowScore(bestBow);
                currentBowScore = InventoryUtils.getPunchBowScore(currentBow);
            }
            if (bestBow != null && bestBowScore > currentBowScore) this.swapItem(slot, bestBow);
            if ((float) InventoryUtils.getItemCount(Items.ARROW) > this.maxArrowSize.get()) {
                this.throwItem(InventoryUtils.getWorstArrow());
            }
        }

        if (this.switchEnderPearl.get()) this.swapItem(this.enderPearlSlot.getInt() - 1, Items.ENDER_PEARL);
        if (this.switchWaterBucket.get()) this.swapItem(this.waterBucketSlot.getInt() - 1, Items.WATER_BUCKET);
        if (this.switchFireball.get()) this.swapItem(this.fireballSlot.getInt() - 1, Items.FIRE_CHARGE);

        if (this.keepProjectile.get()) {
            if ((float) (InventoryUtils.getItemCount(Items.EGG) + InventoryUtils.getItemCount(Items.SNOWBALL))
                    > this.maxProjectileSize.get()) {
                this.throwItem(InventoryUtils.getWorstProjectile());
            }
            if (this.switchProjectile.get() && !this.offhandItems.is("Projectile")) {
                int slot = this.projectileSlot.getInt() - 1;
                if (InventoryUtils.getItemCount(Items.EGG) > 0) this.swapItem(slot, Items.EGG);
                else if (InventoryUtils.getItemCount(Items.SNOWBALL) > 0) this.swapItem(slot, Items.SNOWBALL);
            }
        }

        if (this.throwItems.get()) {
            List<Integer> slots = IntStream.range(0, Inventory.INVENTORY_SIZE).boxed().collect(Collectors.toList());
            Collections.shuffle(slots);
            for (Integer slot : slots) {
                ItemStack stack = mc.player.getInventory().getItem(slot);
                if (!stack.isEmpty() && !this.isItemUseful(stack)) this.throwItem(stack);
            }
        }
    }

    private void swapOffHand(int slot) {
        if (slot < 9) this.click(slot + 36, 40, ContainerInput.SWAP);
        else this.click(slot, 40, ContainerInput.SWAP);
        this.inventoryOpen = true;
        timer.reset();
    }

    private void throwItem(ItemStack item) {
        if (InventoryUtils.isItemValid(item) && timer.delay(this.delay.get())) {
            int itemSlot = InventoryUtils.getItemStackSlot(item);
            if (itemSlot != -1) {
                if (itemSlot < 9) this.click(itemSlot + 36, 1, ContainerInput.THROW);
                else this.click(itemSlot, 1, ContainerInput.THROW);
                this.inventoryOpen = true;
                timer.reset();
            }
        }
    }

    private void swapItem(int targetSlot, ItemStack bestItem) {
        if (bestItem == null) return;
        ItemStack currentSlot = mc.player.getInventory().getItem(targetSlot);
        if (InventoryUtils.isItemValid(currentSlot) && bestItem != currentSlot
                && timer.delay(this.delay.get())) {
            int bestItemSlot = InventoryUtils.getItemStackSlot(bestItem);
            if (bestItemSlot != -1) {
                if (bestItemSlot < 9) this.click(bestItemSlot + 36, targetSlot, ContainerInput.SWAP);
                else this.click(bestItemSlot, targetSlot, ContainerInput.SWAP);
                this.inventoryOpen = true;
                timer.reset();
            }
        }
    }

    private void swapItem(int targetSlot, Item item) {
        ItemStack currentSlot = mc.player.getInventory().getItem(targetSlot);
        if (InventoryUtils.isItemValid(currentSlot) && timer.delay(this.delay.get())) {
            int bestItemSlot = InventoryUtils.getItemSlot(item);
            if (bestItemSlot != -1) {
                ItemStack bestItemStack = mc.player.getInventory().getItem(bestItemSlot);
                if (currentSlot.getItem() != item
                        || currentSlot.getCount() < bestItemStack.getCount()) {
                    if (bestItemSlot < 9) this.click(bestItemSlot + 36, targetSlot, ContainerInput.SWAP);
                    else this.click(bestItemSlot, targetSlot, ContainerInput.SWAP);
                    this.inventoryOpen = true;
                    timer.reset();
                }
            }
        }
    }
}
