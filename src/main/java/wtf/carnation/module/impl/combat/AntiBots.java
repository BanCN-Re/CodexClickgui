package wtf.carnation.module.impl.combat;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.RespawnEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.ChatUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Detects fake staff and bedwars bots so they are not treated as real players. */
public class AntiBots extends Module {
    private static final Map<UUID, String> uuidDisplayNames = new ConcurrentHashMap<>();
    private static final Map<Integer, String> entityIdDisplayNames = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> uuids = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> respawnTime = new ConcurrentHashMap<>();
    private static final Set<Integer> ids = new HashSet<>();

    private final NumberValue respawnTimeValue = new NumberValue("Respawn Time", 2500, 0, 10000, 100);

    public AntiBots() {
        super("AntiBots", Category.COMBAT, "识别假人staff与起床战争机器人，避免误伤");
    }

    /** True while the entity is a bedwars bot that respawned recently. */
    public static boolean isBedWarsBot(Entity entity) {
        Module module = Client.instance.getModuleManager().getModuleByName("AntiBots");
        if (!(module instanceof AntiBots bots) || bots.respawnTimeValue.get() < 1) return false;
        Long time = respawnTime.get(entity.getUUID());
        return time != null && System.currentTimeMillis() - time < bots.respawnTimeValue.get();
    }

    public static boolean isBot(Entity entity) {
        return ids.contains(entity.getId());
    }

    @EventTarget
    public void onRespawn(RespawnEvent event) {
        uuidDisplayNames.clear();
        entityIdDisplayNames.clear();
        ids.clear();
        uuids.clear();
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre()) return;
        uuids.entrySet().removeIf(entry -> {
            if (System.currentTimeMillis() - entry.getValue() <= 500L) return false;
            ChatUtil.addChatMessage("Fake Staff Detected! (" + uuidDisplayNames.get(entry.getKey()) + ")");
            return true;
        });
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.isSend()) return;

        if (event.getPacket() instanceof ClientboundPlayerInfoUpdatePacket packet) {
            if (!packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) return;
            for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.entries()) {
                UUID id = entry.profile().id();
                respawnTime.put(id, System.currentTimeMillis());
                if (entry.displayName() == null || !entry.displayName().getSiblings().isEmpty()
                        || entry.gameMode() != GameType.SURVIVAL) continue;
                uuids.put(id, System.currentTimeMillis());
                uuidDisplayNames.put(id, entry.displayName().getString());
            }
            return;
        }

        if (event.getPacket() instanceof ClientboundAddEntityPacket packet) {
            if (!EntityTypes.PLAYER.equals(packet.getType()) || !uuids.containsKey(packet.getUUID())) return;
            String displayName = uuidDisplayNames.get(packet.getUUID());
            ChatUtil.addChatMessage("Bot Detected! (" + displayName + ")");
            entityIdDisplayNames.put(packet.getId(), displayName);
            uuids.remove(packet.getUUID());
            ids.add(packet.getId());
            return;
        }

        if (event.getPacket() instanceof ClientboundRemoveEntitiesPacket packet) {
            for (int entityId : packet.entityIds()) {
                if (!ids.contains(entityId)) continue;
                ChatUtil.addChatMessage("Bot Removed! (" + entityIdDisplayNames.get(entityId) + ")");
                ids.remove(entityId);
            }
            return;
        }

        if (event.getPacket() instanceof ClientboundAnimatePacket packet) {
            Entity entity = mc.level == null ? null : mc.level.getEntity(packet.getId());
            if (entity != null && packet.getAction() == 0) {
                respawnTime.remove(entity.getUUID());
            }
        }
    }
}
