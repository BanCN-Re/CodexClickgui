package wtf.carnation.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.RespawnEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Port of Naven's {@code EntityWatcher}; notifications go through the client chat instead. */
public final class EntityWatcher {
    public static final EntityWatcher INSTANCE = new EntityWatcher();

    private static final Minecraft mc = Minecraft.getInstance();
    private static final Map<Entity, Set<String>> tags = new ConcurrentHashMap<>();
    private static final Map<String, SharedESPData> sharedESPData = new ConcurrentHashMap<>();

    private EntityWatcher() {
    }

    public static Set<String> getEntityTags(AbstractClientPlayer player) {
        List<MobEffect> effects = new ArrayList<>();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            MobEffect effect = instance.getEffect().value();
            if (effect != MobEffects.ABSORPTION.value()) effects.add(effect);
        }

        Set<String> currentPlayerTags = new LinkedHashSet<>();
        if (tags.containsKey(player)) currentPlayerTags.addAll(tags.get(player));
        Set<String> collected = effects.stream()
                .map(effect -> "effect.minecraft." + BuiltInRegistries.MOB_EFFECT.getKey(effect).getPath())
                .collect(Collectors.toSet());
        currentPlayerTags.addAll(collected);
        return currentPlayerTags;
    }

    @EventTarget
    public void onRespawn(RespawnEvent e) {
        tags.clear();
        sharedESPData.clear();
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (!e.isPre() || mc.level == null) return;
        sharedESPData.forEach((ignored, data) -> {
            if (System.currentTimeMillis() - data.getUpdateTime() > 500L) sharedESPData.remove(ignored);
        });

        for (AbstractClientPlayer player : new ArrayList<>(mc.level.players())) {
            if (player == mc.player) continue;
            tags.computeIfAbsent(player, ignored -> new HashSet<>());
            Set<String> playerTags = tags.get(player);

            check(player, playerTags, "God Axe",
                    InventoryUtils.isGodAxe(player.getMainHandItem()) || InventoryUtils.isGodAxe(player.getOffhandItem()));
            check(player, playerTags, "Enchanted Golden Apple",
                    InventoryUtils.isEnchantedGApple(player.getMainHandItem())
                            || InventoryUtils.isEnchantedGApple(player.getOffhandItem()));
            check(player, playerTags, "End Crystal",
                    InventoryUtils.isEndCrystal(player.getMainHandItem())
                            || InventoryUtils.isEndCrystal(player.getOffhandItem()));
            check(player, playerTags, "KB Ball",
                    InventoryUtils.isKBBall(player.getMainHandItem()) || InventoryUtils.isKBBall(player.getOffhandItem()));
            check(player, playerTags, "KB Stick",
                    InventoryUtils.isKBStick(player.getMainHandItem()) || InventoryUtils.isKBStick(player.getOffhandItem()));
            check(player, playerTags, "Punch Bow",
                    InventoryUtils.getPunchLevel(player.getMainHandItem()) > 2
                            || InventoryUtils.getPunchLevel(player.getOffhandItem()) > 2);
            check(player, playerTags, "Power Bow",
                    InventoryUtils.getPowerLevel(player.getMainHandItem()) > 3
                            || InventoryUtils.getPowerLevel(player.getOffhandItem()) > 3);
        }
    }

    private static void check(AbstractClientPlayer player, Set<String> playerTags, String tag, boolean present) {
        if (present && !playerTags.contains(tag)) {
            ChatUtil.addChatMessage(player.getName().getString() + " is holding " + tag + "!");
            playerTags.add(tag);
        }
    }

    public static Map<String, SharedESPData> getSharedESPData() {
        return sharedESPData;
    }
}
