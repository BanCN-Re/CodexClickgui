package wtf.carnation.script;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundChangeGameModePacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSpectatorActionPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import wtf.carnation.Client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * 26.3 packet ↔ Lua table bridge.
 *
 * <p>Field names follow the 1.8.9 protocol wherever the packet has a direct 1.8.9
 * equivalent, so scripts written for the Moon/LuaJ runtime keep working. Numeric ids
 * 0x00–0x0E, 0x13, 0x14, 0x18 and 0x19 are identical to the 1.8.9 client ids; ids
 * from 0x40 upward are Carnation-specific additions for 26.3-only packets.</p>
 *
 * <p>Everything is rebuilt rather than reflectively written: 26.3 marks every packet
 * field final, so {@code Field.set} is not usable. Reads use reflection; writes go
 * through the packet constructors.</p>
 */
public final class LuaPackets {

    /** Outbound id → canonical name. */
    private static final Map<Integer, String> OUT_NAMES = new LinkedHashMap<>();
    private static final Map<String, Integer> OUT_IDS = new LinkedHashMap<>();
    /** Inbound id → canonical name. */
    private static final Map<Integer, String> IN_NAMES = new LinkedHashMap<>();
    private static final Map<String, Integer> IN_IDS = new LinkedHashMap<>();

    private static void out(String name, int id) {
        OUT_IDS.put(name, id);
        OUT_NAMES.put(id, name);
    }

    private static void in(String name, int id) {
        IN_IDS.put(name, id);
        IN_NAMES.put(id, name);
    }

    static {
        // ---- serverbound: 1.8.9-compatible block ----
        out("keep_alive", 0x00);
        out("chat", 0x01);
        out("interact", 0x02);
        out("move_player", 0x03);
        out("move_player_pos", 0x04);
        out("move_player_rot", 0x05);
        out("move_player_pos_rot", 0x06);
        out("player_action", 0x07);
        out("use_item_on", 0x08);
        out("set_carried_item", 0x09);
        out("punch", 0x0A);
        out("player_command", 0x0B);
        out("player_input", 0x0C);
        out("container_close", 0x0D);
        out("container_click", 0x0E);
        out("player_abilities", 0x13);
        out("command_suggestion", 0x14);
        out("client_information", 0x15);
        out("client_command", 0x16);
        out("custom_payload", 0x17);
        out("spectator_action", 0x18);
        out("resource_pack", 0x19);
        // ---- 26.3 additions ----
        out("attack", 0x1A);
        out("move_vehicle", 0x1B);
        out("change_game_mode", 0x1C);
        out("pick_item_from_block", 0x1D);
        out("pick_item_from_entity", 0x1E);
        out("player_loaded", 0x1F);
        out("teleport_to_entity", 0x20);
        out("use_item", 0x21);
        out("rename_item", 0x23);
        out("select_trade", 0x24);
        out("chat_command", 0x26);
        out("container_button_click", 0x27);

        // ---- clientbound: 1.8.9-compatible block ----
        in("keep_alive", 0x00);
        in("system_chat", 0x02);
        in("set_health", 0x06);
        in("player_position", 0x08);
        in("set_entity_motion", 0x12);
        in("move_entity", 0x14);
        in("move_entity_pos", 0x15);
        in("move_entity_rot", 0x16);
        in("move_entity_pos_rot", 0x17);
        in("teleport_entity", 0x18);
        in("rotate_head", 0x19);
        in("entity_event", 0x1A);
        in("set_entity_data", 0x1C);
        in("set_equipment", 0x20);
        in("remove_entities", 0x23);
        in("explode", 0x27);
        in("animate", 0x2A);
        in("block_update", 0x2E);
        in("container_set_slot", 0x2F);
        in("container_set_content", 0x30);
        in("container_set_data", 0x31);
        in("block_changed_ack", 0x32);
        in("player_chat", 0x38);
        in("resource_pack_push", 0x48);
        // ---- 26.3 additions ----
        in("entity_position_sync", 0x50);
        in("set_experience", 0x51);
        in("set_held_slot", 0x52);
        in("player_abilities", 0x53);
        in("game_event", 0x54);
        in("sound", 0x55);
        in("level_particles", 0x56);
        in("open_screen", 0x57);
        in("login", 0x58);
        in("respawn", 0x59);
        in("command_suggestions", 0x5A);
        in("player_info_update", 0x5B);
        in("player_info_remove", 0x5C);
        in("set_passengers", 0x5D);
        in("hurt_animation", 0x5E);
        in("cooldown", 0x5F);
        in("set_time", 0x60);
        in("damage_event", 0x61);
        in("add_entity", 0x62);
        in("set_entity_link", 0x63);
        in("block_destruction", 0x64);
        in("disguised_chat", 0x65);
    }

    private LuaPackets() {
    }

    /** One intercepted packet plus the Lua table handed to scripts. */
    public record Payload(Packet<?> packet, boolean outbound, int id, String name, LuaTable table) {
    }

    // ------------------------------------------------------------------ identity

    public static String nameOf(Packet<?> packet, boolean outbound) {
        if (packet instanceof ServerboundMovePlayerPacket p) {
            if (p instanceof ServerboundMovePlayerPacket.PosRot) return "move_player_pos_rot";
            if (p instanceof ServerboundMovePlayerPacket.Pos) return "move_player_pos";
            if (p instanceof ServerboundMovePlayerPacket.Rot) return "move_player_rot";
            return "move_player";
        }
        if (packet instanceof ClientboundMoveEntityPacket p) {
            if (p instanceof ClientboundMoveEntityPacket.PosRot) return "move_entity_pos_rot";
            if (p instanceof ClientboundMoveEntityPacket.Pos) return "move_entity_pos";
            if (p instanceof ClientboundMoveEntityPacket.Rot) return "move_entity_rot";
            return "move_entity";
        }
        if (packet instanceof ServerboundKeepAlivePacket) return "keep_alive";
        if (packet instanceof ClientboundKeepAlivePacket) return "keep_alive";
        if (packet instanceof ServerboundChatPacket) return "chat";
        if (packet instanceof ServerboundChatCommandPacket) return "chat_command";
        if (packet instanceof ServerboundInteractPacket) return "interact";
        if (packet instanceof ServerboundPlayerActionPacket) return "player_action";
        if (packet instanceof ServerboundUseItemOnPacket) return "use_item_on";
        if (packet instanceof ServerboundUseItemPacket) return "use_item";
        if (packet instanceof ServerboundSetCarriedItemPacket) return "set_carried_item";
        if (packet instanceof ServerboundPunchPacket) return "punch";
        if (packet instanceof ServerboundAttackPacket) return "attack";
        if (packet instanceof ServerboundPlayerCommandPacket) return "player_command";
        if (packet instanceof ServerboundPlayerInputPacket) return "player_input";
        if (packet instanceof ServerboundContainerClosePacket) return "container_close";
        if (packet instanceof ServerboundContainerClickPacket) return "container_click";
        if (packet instanceof ServerboundContainerButtonClickPacket) return "container_button_click";
        if (packet instanceof ServerboundPlayerAbilitiesPacket) return "player_abilities";
        if (packet instanceof ServerboundCommandSuggestionPacket) return "command_suggestion";
        if (packet instanceof ServerboundSpectatorActionPacket) return "spectator_action";
        if (packet instanceof ServerboundResourcePackPacket) return "resource_pack";
        if (packet instanceof ServerboundChangeGameModePacket) return "change_game_mode";
        if (packet instanceof ServerboundMoveVehiclePacket) return "move_vehicle";
        if (packet instanceof ServerboundClientCommandPacket) return "client_command";
        if (packet instanceof ServerboundPlayerLoadedPacket) return "player_loaded";
        if (packet instanceof ServerboundTeleportToEntityPacket) return "teleport_to_entity";
        if (packet instanceof ServerboundPickItemFromBlockPacket) return "pick_item_from_block";
        if (packet instanceof ServerboundPickItemFromEntityPacket) return "pick_item_from_entity";
        if (packet instanceof ServerboundRenameItemPacket) return "rename_item";
        if (packet instanceof ServerboundSelectTradePacket) return "select_trade";
        if (packet instanceof ServerboundClientInformationPacket) return "client_information";

        if (packet instanceof ClientboundSystemChatPacket) return "system_chat";
        if (packet instanceof ClientboundPlayerChatPacket) return "player_chat";
        if (packet instanceof ClientboundDisguisedChatPacket) return "disguised_chat";
        if (packet instanceof ClientboundPlayerPositionPacket) return "player_position";
        if (packet instanceof ClientboundTeleportEntityPacket) return "teleport_entity";
        if (packet instanceof ClientboundEntityPositionSyncPacket) return "entity_position_sync";
        if (packet instanceof ClientboundSetEntityMotionPacket) return "set_entity_motion";
        if (packet instanceof ClientboundExplodePacket) return "explode";
        if (packet instanceof ClientboundSetHealthPacket) return "set_health";
        if (packet instanceof ClientboundSetExperiencePacket) return "set_experience";
        if (packet instanceof ClientboundSetHeldSlotPacket) return "set_held_slot";
        if (packet instanceof ClientboundPlayerAbilitiesPacket) return "player_abilities";
        if (packet instanceof ClientboundContainerSetSlotPacket) return "container_set_slot";
        if (packet instanceof ClientboundContainerSetContentPacket) return "container_set_content";
        if (packet instanceof ClientboundContainerSetDataPacket) return "container_set_data";
        if (packet instanceof ClientboundBlockUpdatePacket) return "block_update";
        if (packet instanceof ClientboundBlockChangedAckPacket) return "block_changed_ack";
        if (packet instanceof ClientboundBlockDestructionPacket) return "block_destruction";
        if (packet instanceof ClientboundSectionBlocksUpdatePacket) return "section_blocks_update";
        if (packet instanceof ClientboundAnimatePacket) return "animate";
        if (packet instanceof ClientboundAddEntityPacket) return "add_entity";
        if (packet instanceof ClientboundRemoveEntitiesPacket) return "remove_entities";
        if (packet instanceof ClientboundSetEntityDataPacket) return "set_entity_data";
        if (packet instanceof ClientboundSetEquipmentPacket) return "set_equipment";
        if (packet instanceof ClientboundUpdateAttributesPacket) return "update_attributes";
        if (packet instanceof ClientboundGameEventPacket) return "game_event";
        if (packet instanceof ClientboundSoundPacket) return "sound";
        if (packet instanceof ClientboundLevelParticlesPacket) return "level_particles";
        if (packet instanceof ClientboundOpenScreenPacket) return "open_screen";
        if (packet instanceof ClientboundLoginPacket) return "login";
        if (packet instanceof ClientboundRespawnPacket) return "respawn";
        if (packet instanceof ClientboundCommandSuggestionsPacket) return "command_suggestions";
        if (packet instanceof ClientboundPlayerInfoUpdatePacket) return "player_info_update";
        if (packet instanceof ClientboundPlayerInfoRemovePacket) return "player_info_remove";
        if (packet instanceof ClientboundSetPassengersPacket) return "set_passengers";
        if (packet instanceof ClientboundEntityEventPacket) return "entity_event";
        if (packet instanceof ClientboundRotateHeadPacket) return "rotate_head";
        if (packet instanceof ClientboundHurtAnimationPacket) return "hurt_animation";
        if (packet instanceof ClientboundCooldownPacket) return "cooldown";
        if (packet instanceof ClientboundSetTimePacket) return "set_time";
        if (packet instanceof ClientboundDamageEventPacket) return "damage_event";
        if (packet instanceof ClientboundResourcePackPushPacket) return "resource_pack_push";
        if (packet instanceof ClientboundSetEntityLinkPacket) return "set_entity_link";

        return snake(packet.getClass().getSimpleName());
    }

    public static int idOf(Packet<?> packet, boolean outbound) {
        return (outbound ? OUT_IDS : IN_IDS).getOrDefault(nameOf(packet, outbound), -1);
    }

    public static String nameOfId(int id, boolean outbound) {
        return (outbound ? OUT_NAMES : IN_NAMES).get(id);
    }

    public static int idOfName(String name, boolean outbound) {
        if (name == null) return -1;
        Integer direct = (outbound ? OUT_IDS : IN_IDS).get(name);
        if (direct != null) return direct;
        String normalized = snake(name);
        Integer alias = (outbound ? OUT_IDS : IN_IDS).get(normalized);
        return alias == null ? -1 : alias;
    }

    public static Map<Integer, String> outboundNames() {
        return Map.copyOf(OUT_NAMES);
    }

    public static Map<Integer, String> inboundNames() {
        return Map.copyOf(IN_NAMES);
    }

    // ------------------------------------------------------------------ read

    public static Payload read(Packet<?> packet, boolean outbound) {
        String name = nameOf(packet, outbound);
        int id = (outbound ? OUT_IDS : IN_IDS).getOrDefault(name, -1);
        LuaTable table = new LuaTable();
        table.set("packet_id", LuaValue.valueOf(id));
        table.set("id", LuaValue.valueOf(id));
        table.set("packet", LuaValue.valueOf(name));
        table.set("name", LuaValue.valueOf(name));
        table.set("packet_class", LuaValue.valueOf(packet.getClass().getSimpleName()));
        table.set("direction", LuaValue.valueOf(outbound ? "send" : "receive"));
        table.set("cancel", LuaValue.FALSE);
        if (outbound) {
            readOutbound(table, packet);
        } else {
            readInbound(table, packet);
        }
        generic(table, packet);
        return new Payload(packet, outbound, id, name, table);
    }

    private static void readOutbound(LuaTable t, Packet<?> packet) {
        if (packet instanceof ServerboundMovePlayerPacket p) {
            t.set("x", LuaValue.valueOf(gd(p, "x", 0)));
            t.set("y", LuaValue.valueOf(gd(p, "y", 0)));
            t.set("z", LuaValue.valueOf(gd(p, "z", 0)));
            t.set("yaw", LuaValue.valueOf(gf(p, "yRot", 0)));
            t.set("pitch", LuaValue.valueOf(gf(p, "xRot", 0)));
            boolean ground = gb(p, "onGround", false);
            t.set("ground", LuaValue.valueOf(ground));
            t.set("on_ground", LuaValue.valueOf(ground));
            t.set("horizontal_collision", LuaValue.valueOf(gb(p, "horizontalCollision", false)));
            t.set("has_pos", LuaValue.valueOf(gb(p, "hasPos", false)));
            t.set("has_rot", LuaValue.valueOf(gb(p, "hasRot", false)));
            return;
        }
        if (packet instanceof ServerboundPlayerActionPacket p) {
            enumField(t, p, "action", "action");
            t.set("status", LuaValue.valueOf(gi(p, "action", 0)));
            t.set("x", LuaValue.valueOf(gposX(p, "pos")));
            t.set("y", LuaValue.valueOf(gposY(p, "pos")));
            t.set("z", LuaValue.valueOf(gposZ(p, "pos")));
            enumField(t, p, "direction", "facing");
            t.set("sequence", LuaValue.valueOf(gi(p, "sequence", 0)));
            return;
        }
        if (packet instanceof ServerboundUseItemOnPacket p) {
            enumField(t, p, "hand", "hand");
            t.set("sequence", LuaValue.valueOf(gi(p, "sequence", 0)));
            Object hit = get(p, "hitResult");
            if (hit instanceof BlockHitResult bhr) {
                Vec3 loc = bhr.getLocation();
                t.set("x", LuaValue.valueOf(loc.x));
                t.set("y", LuaValue.valueOf(loc.y));
                t.set("z", LuaValue.valueOf(loc.z));
                t.set("offset_x", LuaValue.valueOf(loc.x - bhr.getBlockPos().getX()));
                t.set("offset_y", LuaValue.valueOf(loc.y - bhr.getBlockPos().getY()));
                t.set("offset_z", LuaValue.valueOf(loc.z - bhr.getBlockPos().getZ()));
                t.set("facing", LuaValue.valueOf(bhr.getDirection().ordinal()));
                t.set("block_x", LuaValue.valueOf(bhr.getBlockPos().getX()));
                t.set("block_y", LuaValue.valueOf(bhr.getBlockPos().getY()));
                t.set("block_z", LuaValue.valueOf(bhr.getBlockPos().getZ()));
                t.set("inside", LuaValue.valueOf(bhr.isInside()));
            }
            return;
        }
        if (packet instanceof ServerboundUseItemPacket p) {
            enumField(t, p, "hand", "hand");
            t.set("sequence", LuaValue.valueOf(gi(p, "sequence", 0)));
            t.set("yaw", LuaValue.valueOf(gf(p, "yRot", 0)));
            t.set("pitch", LuaValue.valueOf(gf(p, "xRot", 0)));
            return;
        }
        if (packet instanceof ServerboundInteractPacket p) {
            t.set("target", LuaValue.valueOf(gi(p, "entityId", -1)));
            t.set("entity_id", LuaValue.valueOf(gi(p, "entityId", -1)));
            enumField(t, p, "hand", "hand");
            Object loc = get(p, "location");
            if (loc instanceof Vec3 vec) {
                t.set("hit_x", LuaValue.valueOf(vec.x));
                t.set("hit_y", LuaValue.valueOf(vec.y));
                t.set("hit_z", LuaValue.valueOf(vec.z));
            }
            t.set("using_secondary_action", LuaValue.valueOf(gb(p, "usingSecondaryAction", false)));
            return;
        }
        if (packet instanceof ServerboundAttackPacket p) {
            t.set("target", LuaValue.valueOf(gi(p, "entityId", -1)));
            t.set("entity_id", LuaValue.valueOf(gi(p, "entityId", -1)));
            return;
        }
        if (packet instanceof ServerboundSetCarriedItemPacket p) {
            t.set("slot", LuaValue.valueOf(gi(p, "slot", 0)));
            return;
        }
        if (packet instanceof ServerboundPlayerCommandPacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "id", -1)));
            enumField(t, p, "action", "action");
            t.set("aux_data", LuaValue.valueOf(gi(p, "data", 0)));
            t.set("data", LuaValue.valueOf(gi(p, "data", 0)));
            return;
        }
        if (packet instanceof ServerboundPlayerInputPacket p) {
            Object input = get(p, "input");
            if (input instanceof Input in) {
                t.set("forward", LuaValue.valueOf(in.forward()));
                t.set("backward", LuaValue.valueOf(in.backward()));
                t.set("left", LuaValue.valueOf(in.left()));
                t.set("right", LuaValue.valueOf(in.right()));
                t.set("jumping", LuaValue.valueOf(in.jump()));
                t.set("jump", LuaValue.valueOf(in.jump()));
                t.set("sneaking", LuaValue.valueOf(in.shift()));
                t.set("sprint", LuaValue.valueOf(in.sprint()));
            }
            return;
        }
        if (packet instanceof ServerboundContainerClosePacket p) {
            t.set("window_id", LuaValue.valueOf(gi(p, "containerId", 0)));
            t.set("container_id", LuaValue.valueOf(gi(p, "containerId", 0)));
            return;
        }
        if (packet instanceof ServerboundContainerClickPacket p) {
            t.set("window_id", LuaValue.valueOf(gi(p, "containerId", 0)));
            t.set("container_id", LuaValue.valueOf(gi(p, "containerId", 0)));
            t.set("state_id", LuaValue.valueOf(gi(p, "stateId", 0)));
            t.set("slot_id", LuaValue.valueOf(gi(p, "slotNum", 0)));
            t.set("slot", LuaValue.valueOf(gi(p, "slotNum", 0)));
            t.set("mouse_button", LuaValue.valueOf(gi(p, "buttonNum", 0)));
            t.set("button", LuaValue.valueOf(gi(p, "buttonNum", 0)));
            enumField(t, p, "containerInput", "mode");
            return;
        }
        if (packet instanceof ServerboundContainerButtonClickPacket p) {
            t.set("window_id", LuaValue.valueOf(gi(p, "containerId", 0)));
            t.set("button_id", LuaValue.valueOf(gi(p, "buttonId", 0)));
            return;
        }
        if (packet instanceof ServerboundPlayerAbilitiesPacket p) {
            t.set("flying", LuaValue.valueOf(gb(p, "isFlying", false)));
            return;
        }
        if (packet instanceof ServerboundCommandSuggestionPacket p) {
            t.set("id", LuaValue.valueOf(gi(p, "id", 0)));
            t.set("text", LuaValue.valueOf(gs(p, "command", "")));
            return;
        }
        if (packet instanceof ServerboundSpectatorActionPacket p) {
            Object value = get(p, "spectateEntityId");
            if (value instanceof OptionalInt oi && oi.isPresent()) {
                t.set("target", LuaValue.valueOf(oi.getAsInt()));
                t.set("entity_id", LuaValue.valueOf(oi.getAsInt()));
            }
            return;
        }
        if (packet instanceof ServerboundResourcePackPacket p) {
            t.set("id", LuaValue.valueOf(String.valueOf(get(p, "id"))));
            enumField(t, p, "action", "status");
            return;
        }
        if (packet instanceof ServerboundChangeGameModePacket p) {
            enumField(t, p, "mode", "mode");
            return;
        }
        if (packet instanceof ServerboundMoveVehiclePacket p) {
            Object par = get(p, "movingTo");
            if (par instanceof PositionAndRotation rot) {
                Object pos = get(rot, "position");
                if (pos instanceof Vec3 vec) {
                    t.set("x", LuaValue.valueOf(vec.x));
                    t.set("y", LuaValue.valueOf(vec.y));
                    t.set("z", LuaValue.valueOf(vec.z));
                }
                t.set("yaw", LuaValue.valueOf(gf(rot, "yRot", 0)));
                t.set("pitch", LuaValue.valueOf(gf(rot, "xRot", 0)));
            }
            t.set("on_ground", LuaValue.valueOf(gb(p, "onGround", false)));
            return;
        }
        if (packet instanceof ServerboundClientCommandPacket p) {
            enumField(t, p, "action", "action");
            return;
        }
        if (packet instanceof ServerboundTeleportToEntityPacket p) {
            t.set("uuid", LuaValue.valueOf(String.valueOf(get(p, "uuid"))));
            return;
        }
        if (packet instanceof ServerboundPickItemFromBlockPacket p) {
            t.set("x", LuaValue.valueOf(gposX(p, "pos")));
            t.set("y", LuaValue.valueOf(gposY(p, "pos")));
            t.set("z", LuaValue.valueOf(gposZ(p, "pos")));
            t.set("include_data", LuaValue.valueOf(gb(p, "includeData", false)));
            return;
        }
        if (packet instanceof ServerboundPickItemFromEntityPacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "id", -1)));
            t.set("include_data", LuaValue.valueOf(gb(p, "includeData", false)));
            return;
        }
        if (packet instanceof ServerboundRenameItemPacket p) {
            t.set("name", LuaValue.valueOf(gs(p, "name", "")));
            return;
        }
        if (packet instanceof ServerboundSelectTradePacket p) {
            t.set("item", LuaValue.valueOf(gi(p, "item", 0)));
            return;
        }
        if (packet instanceof ServerboundChatCommandPacket p) {
            t.set("command", LuaValue.valueOf(gs(p, "command", "")));
            return;
        }
        if (packet instanceof ServerboundChatPacket p) {
            t.set("message", LuaValue.valueOf(gs(p, "message", "")));
            t.set("salt", LuaValue.valueOf(gl(p, "salt", 0)));
            return;
        }
        if (packet instanceof ServerboundKeepAlivePacket p) {
            t.set("id", LuaValue.valueOf(gl(p, "id", 0)));
            return;
        }
    }

    private static void readInbound(LuaTable t, Packet<?> packet) {
        if (packet instanceof ClientboundKeepAlivePacket p) {
            t.set("id", LuaValue.valueOf(gl(p, "id", 0)));
            return;
        }
        if (packet instanceof ClientboundSystemChatPacket p) {
            t.set("message", LuaValue.valueOf(component(get(p, "content"))));
            t.set("overlay", LuaValue.valueOf(gb(p, "overlay", false)));
            return;
        }
        if (packet instanceof ClientboundPlayerChatPacket p) {
            t.set("global_index", LuaValue.valueOf(gi(p, "globalIndex", 0)));
            t.set("sender", LuaValue.valueOf(String.valueOf(get(p, "sender"))));
            t.set("index", LuaValue.valueOf(gi(p, "index", 0)));
            Object body = get(p, "body");
            if (body != null) t.set("message", LuaValue.valueOf(gs(body, "content", "")));
            return;
        }
        if (packet instanceof ClientboundDisguisedChatPacket p) {
            t.set("message", LuaValue.valueOf(component(get(p, "message"))));
            return;
        }
        if (packet instanceof ClientboundPlayerPositionPacket p) {
            t.set("id", LuaValue.valueOf(gi(p, "id", 0)));
            t.set("teleport_id", LuaValue.valueOf(gi(p, "id", 0)));
            Object change = get(p, "change");
            if (change instanceof PositionMoveRotation rot) {
                writeMove(t, rot);
            }
            t.set("relative_count", LuaValue.valueOf(sizeOf(get(p, "relatives"))));
            return;
        }
        if (packet instanceof ClientboundTeleportEntityPacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "id", -1)));
            Object change = get(p, "change");
            if (change instanceof PositionMoveRotation rot) {
                writeMove(t, rot);
            }
            t.set("relative_count", LuaValue.valueOf(sizeOf(get(p, "relatives"))));
            t.set("on_ground", LuaValue.valueOf(gb(p, "onGround", false)));
            return;
        }
        if (packet instanceof ClientboundEntityPositionSyncPacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "id", -1)));
            t.set("yaw", LuaValue.valueOf(gf(p, "yRot", 0)));
            t.set("pitch", LuaValue.valueOf(gf(p, "xRot", 0)));
            t.set("on_ground", LuaValue.valueOf(gb(p, "onGround", false)));
            return;
        }
        if (packet instanceof ClientboundMoveEntityPacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "entityId", -1)));
            int yaw = gi(p, "yRot", 0);
            int pitch = gi(p, "xRot", 0);
            t.set("yaw", LuaValue.valueOf(yaw * 360.0 / 256.0));
            t.set("pitch", LuaValue.valueOf(pitch * 360.0 / 256.0));
            t.set("yaw_byte", LuaValue.valueOf(yaw));
            t.set("pitch_byte", LuaValue.valueOf(pitch));
            boolean ground = gb(p, "onGround", false);
            t.set("ground", LuaValue.valueOf(ground));
            t.set("on_ground", LuaValue.valueOf(ground));
            t.set("rotating", LuaValue.valueOf(gb(p, "hasRot", false)));
            t.set("has_pos", LuaValue.valueOf(gb(p, "hasPos", false)));
            return;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "id", -1)));
            t.set("id", LuaValue.valueOf(gi(p, "id", -1)));
            Object movement = get(p, "movement");
            if (movement instanceof Vec3 vec) {
                t.set("motion_x", LuaValue.valueOf(vec.x));
                t.set("motion_y", LuaValue.valueOf(vec.y));
                t.set("motion_z", LuaValue.valueOf(vec.z));
            }
            return;
        }
        if (packet instanceof ClientboundExplodePacket p) {
            Object center = get(p, "center");
            if (center instanceof Vec3 vec) {
                t.set("x", LuaValue.valueOf(vec.x));
                t.set("y", LuaValue.valueOf(vec.y));
                t.set("z", LuaValue.valueOf(vec.z));
            }
            t.set("strength", LuaValue.valueOf(gf(p, "radius", 0)));
            t.set("radius", LuaValue.valueOf(gf(p, "radius", 0)));
            t.set("block_count", LuaValue.valueOf(gi(p, "blockCount", 0)));
            Object knockback = get(p, "playerKnockback");
            if (knockback instanceof Optional<?> optional && optional.isPresent()
                    && optional.get() instanceof Vec3 vec) {
                t.set("motion_x", LuaValue.valueOf(vec.x));
                t.set("motion_y", LuaValue.valueOf(vec.y));
                t.set("motion_z", LuaValue.valueOf(vec.z));
            }
            return;
        }
        if (packet instanceof ClientboundSetHealthPacket p) {
            t.set("health", LuaValue.valueOf(gf(p, "health", 0)));
            t.set("food", LuaValue.valueOf(gi(p, "food", 0)));
            t.set("saturation", LuaValue.valueOf(gf(p, "saturation", 0)));
            return;
        }
        if (packet instanceof ClientboundSetExperiencePacket p) {
            t.set("experience_progress", LuaValue.valueOf(gf(p, "experienceProgress", 0)));
            t.set("total_experience", LuaValue.valueOf(gi(p, "totalExperience", 0)));
            t.set("experience_level", LuaValue.valueOf(gi(p, "experienceLevel", 0)));
            return;
        }
        if (packet instanceof ClientboundSetHeldSlotPacket p) {
            t.set("slot", LuaValue.valueOf(gi(p, "slot", 0)));
            return;
        }
        if (packet instanceof ClientboundPlayerAbilitiesPacket p) {
            t.set("invulnerable", LuaValue.valueOf(gb(p, "invulnerable", false)));
            t.set("flying", LuaValue.valueOf(gb(p, "isFlying", false)));
            t.set("allow_flying", LuaValue.valueOf(gb(p, "canFly", false)));
            t.set("creative_mode", LuaValue.valueOf(gb(p, "instabuild", false)));
            t.set("flying_speed", LuaValue.valueOf(gf(p, "flyingSpeed", 0)));
            t.set("walking_speed", LuaValue.valueOf(gf(p, "walkingSpeed", 0)));
            return;
        }
        if (packet instanceof ClientboundContainerSetSlotPacket p) {
            t.set("window_id", LuaValue.valueOf(gi(p, "containerId", 0)));
            t.set("container_id", LuaValue.valueOf(gi(p, "containerId", 0)));
            t.set("state_id", LuaValue.valueOf(gi(p, "stateId", 0)));
            t.set("slot", LuaValue.valueOf(gi(p, "slot", 0)));
            writeStack(t, get(p, "itemStack"));
            return;
        }
        if (packet instanceof ClientboundContainerSetContentPacket p) {
            t.set("window_id", LuaValue.valueOf(gi(p, "containerId", 0)));
            t.set("state_id", LuaValue.valueOf(gi(p, "stateId", 0)));
            t.set("item_count", LuaValue.valueOf(sizeOf(get(p, "items"))));
            writeStack(t, get(p, "carriedItem"));
            return;
        }
        if (packet instanceof ClientboundContainerSetDataPacket p) {
            t.set("window_id", LuaValue.valueOf(gi(p, "containerId", 0)));
            t.set("property", LuaValue.valueOf(gi(p, "id", 0)));
            t.set("value", LuaValue.valueOf(gi(p, "value", 0)));
            return;
        }
        if (packet instanceof ClientboundBlockUpdatePacket p) {
            Object pos = get(p, "pos");
            if (pos instanceof BlockPos bp) {
                t.set("x", LuaValue.valueOf(bp.getX()));
                t.set("y", LuaValue.valueOf(bp.getY()));
                t.set("z", LuaValue.valueOf(bp.getZ()));
            }
            t.set("block", LuaValue.valueOf(String.valueOf(get(p, "blockState"))));
            return;
        }
        if (packet instanceof ClientboundBlockChangedAckPacket p) {
            t.set("sequence", LuaValue.valueOf(gi(p, "sequence", 0)));
            return;
        }
        if (packet instanceof ClientboundBlockDestructionPacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "id", -1)));
            t.set("progress", LuaValue.valueOf(gi(p, "progress", 0)));
            Object pos = get(p, "pos");
            if (pos instanceof BlockPos bp) {
                t.set("x", LuaValue.valueOf(bp.getX()));
                t.set("y", LuaValue.valueOf(bp.getY()));
                t.set("z", LuaValue.valueOf(bp.getZ()));
            }
            return;
        }
        if (packet instanceof ClientboundSectionBlocksUpdatePacket p) {
            t.set("section", LuaValue.valueOf(String.valueOf(get(p, "sectionPos"))));
            t.set("change_count", LuaValue.valueOf(sizeOf(get(p, "positions"))));
            return;
        }
        if (packet instanceof ClientboundAnimatePacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "id", -1)));
            t.set("action", LuaValue.valueOf(gi(p, "action", 0)));
            return;
        }
        if (packet instanceof ClientboundAddEntityPacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "id", -1)));
            t.set("uuid", LuaValue.valueOf(String.valueOf(get(p, "uuid"))));
            t.set("type", LuaValue.valueOf(String.valueOf(get(p, "type"))));
            t.set("x", LuaValue.valueOf(gd(p, "x", 0)));
            t.set("y", LuaValue.valueOf(gd(p, "y", 0)));
            t.set("z", LuaValue.valueOf(gd(p, "z", 0)));
            t.set("yaw", LuaValue.valueOf(gi(p, "yRot", 0) * 360.0 / 256.0));
            t.set("pitch", LuaValue.valueOf(gi(p, "xRot", 0) * 360.0 / 256.0));
            t.set("data", LuaValue.valueOf(gi(p, "data", 0)));
            return;
        }
        if (packet instanceof ClientboundRemoveEntitiesPacket p) {
            Object ids = get(p, "entityIds");
            LuaTable list = new LuaTable();
            int count = 0;
            if (ids instanceof Collection<?> collection) {
                for (Object value : collection) {
                    if (count >= 128) break;
                    list.set(++count, LuaValue.valueOf(String.valueOf(value)));
                }
            }
            t.set("entity_ids", list);
            t.set("count", LuaValue.valueOf(count));
            return;
        }
        if (packet instanceof ClientboundSetEntityDataPacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "id", -1)));
            t.set("entry_count", LuaValue.valueOf(sizeOf(get(p, "packedItems"))));
            return;
        }
        if (packet instanceof ClientboundSetEquipmentPacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "entity", -1)));
            t.set("slot_count", LuaValue.valueOf(sizeOf(get(p, "slots"))));
            return;
        }
        if (packet instanceof ClientboundUpdateAttributesPacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "entityId", -1)));
            t.set("attribute_count", LuaValue.valueOf(sizeOf(get(p, "attributes"))));
            return;
        }
        if (packet instanceof ClientboundGameEventPacket p) {
            enumField(t, p, "event", "event");
            t.set("param", LuaValue.valueOf(gf(p, "param", 0)));
            return;
        }
        if (packet instanceof ClientboundSoundPacket p) {
            t.set("x", LuaValue.valueOf(gi(p, "x", 0)));
            t.set("y", LuaValue.valueOf(gi(p, "y", 0)));
            t.set("z", LuaValue.valueOf(gi(p, "z", 0)));
            t.set("volume", LuaValue.valueOf(gf(p, "volume", 0)));
            t.set("pitch", LuaValue.valueOf(gf(p, "pitch", 0)));
            t.set("seed", LuaValue.valueOf(gl(p, "seed", 0)));
            return;
        }
        if (packet instanceof ClientboundLevelParticlesPacket p) {
            t.set("x", LuaValue.valueOf(gd(p, "x", 0)));
            t.set("y", LuaValue.valueOf(gd(p, "y", 0)));
            t.set("z", LuaValue.valueOf(gd(p, "z", 0)));
            t.set("count", LuaValue.valueOf(gi(p, "count", 0)));
            return;
        }
        if (packet instanceof ClientboundOpenScreenPacket p) {
            t.set("window_id", LuaValue.valueOf(gi(p, "containerId", 0)));
            t.set("type", LuaValue.valueOf(String.valueOf(get(p, "type"))));
            t.set("title", LuaValue.valueOf(component(get(p, "title"))));
            return;
        }
        if (packet instanceof ClientboundLoginPacket p) {
            t.set("player_id", LuaValue.valueOf(gi(p, "playerId", -1)));
            t.set("max_players", LuaValue.valueOf(gi(p, "maxPlayers", 0)));
            t.set("chunk_radius", LuaValue.valueOf(gi(p, "chunkRadius", 0)));
            t.set("simulation_distance", LuaValue.valueOf(gi(p, "simulationDistance", 0)));
            t.set("hardcore", LuaValue.valueOf(gb(p, "hardcore", false)));
            t.set("online_mode", LuaValue.valueOf(gb(p, "onlineMode", false)));
            t.set("secure_chat", LuaValue.valueOf(gb(p, "enforcesSecureChat", false)));
            return;
        }
        if (packet instanceof ClientboundRespawnPacket p) {
            t.set("data_to_keep", LuaValue.valueOf(gi(p, "dataToKeep", 0)));
            return;
        }
        if (packet instanceof ClientboundCommandSuggestionsPacket p) {
            t.set("id", LuaValue.valueOf(gi(p, "id", 0)));
            t.set("start", LuaValue.valueOf(gi(p, "start", 0)));
            t.set("length", LuaValue.valueOf(gi(p, "length", 0)));
            t.set("suggestion_count", LuaValue.valueOf(sizeOf(get(p, "suggestions"))));
            return;
        }
        if (packet instanceof ClientboundPlayerInfoUpdatePacket p) {
            t.set("entry_count", LuaValue.valueOf(sizeOf(get(p, "entries"))));
            return;
        }
        if (packet instanceof ClientboundPlayerInfoRemovePacket p) {
            t.set("entry_count", LuaValue.valueOf(sizeOf(get(p, "profileIds"))));
            return;
        }
        if (packet instanceof ClientboundSetPassengersPacket p) {
            t.set("vehicle", LuaValue.valueOf(gi(p, "vehicle", -1)));
            Object passengers = get(p, "passengers");
            if (passengers instanceof int[] values) {
                LuaTable list = new LuaTable();
                for (int index = 0; index < values.length && index < 128; index++) {
                    list.set(index + 1, LuaValue.valueOf(values[index]));
                }
                t.set("passengers", list);
                t.set("count", LuaValue.valueOf(values.length));
            }
            return;
        }
        if (packet instanceof ClientboundEntityEventPacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "entityId", -1)));
            t.set("event_id", LuaValue.valueOf(gi(p, "eventId", 0)));
            return;
        }
        if (packet instanceof ClientboundRotateHeadPacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "entityId", -1)));
            int head = gi(p, "yHeadRot", 0);
            t.set("head_yaw", LuaValue.valueOf(head * 360.0 / 256.0));
            t.set("head_yaw_byte", LuaValue.valueOf(head));
            return;
        }
        if (packet instanceof ClientboundHurtAnimationPacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "id", -1)));
            t.set("yaw", LuaValue.valueOf(gf(p, "yaw", 0)));
            return;
        }
        if (packet instanceof ClientboundCooldownPacket p) {
            t.set("group", LuaValue.valueOf(String.valueOf(get(p, "cooldownGroup"))));
            t.set("duration", LuaValue.valueOf(gi(p, "duration", 0)));
            return;
        }
        if (packet instanceof ClientboundSetTimePacket p) {
            t.set("game_time", LuaValue.valueOf(gl(p, "gameTime", 0)));
            t.set("clock_count", LuaValue.valueOf(sizeOf(get(p, "clockUpdates"))));
            return;
        }
        if (packet instanceof ClientboundDamageEventPacket p) {
            t.set("entity_id", LuaValue.valueOf(gi(p, "entityId", -1)));
            t.set("source_cause_id", LuaValue.valueOf(gi(p, "sourceCauseId", -1)));
            t.set("source_direct_id", LuaValue.valueOf(gi(p, "sourceDirectId", -1)));
            return;
        }
        if (packet instanceof ClientboundResourcePackPushPacket p) {
            t.set("id", LuaValue.valueOf(String.valueOf(get(p, "id"))));
            t.set("url", LuaValue.valueOf(gs(p, "url", "")));
            t.set("hash", LuaValue.valueOf(gs(p, "hash", "")));
            t.set("required", LuaValue.valueOf(gb(p, "required", false)));
            return;
        }
        if (packet instanceof ClientboundSetEntityLinkPacket p) {
            t.set("source_id", LuaValue.valueOf(gi(p, "sourceId", -1)));
            t.set("dest_id", LuaValue.valueOf(gi(p, "destId", -1)));
        }
    }

    private static void writeMove(LuaTable t, PositionMoveRotation change) {
        Vec3 position = change.position();
        t.set("x", LuaValue.valueOf(position.x));
        t.set("y", LuaValue.valueOf(position.y));
        t.set("z", LuaValue.valueOf(position.z));
        Vec3 delta = change.deltaMovement();
        t.set("motion_x", LuaValue.valueOf(delta.x));
        t.set("motion_y", LuaValue.valueOf(delta.y));
        t.set("motion_z", LuaValue.valueOf(delta.z));
        t.set("yaw", LuaValue.valueOf(change.yRot()));
        t.set("pitch", LuaValue.valueOf(change.xRot()));
    }

    private static void writeStack(LuaTable t, Object stack) {
        if (!(stack instanceof ItemStack item)) return;
        t.set("item_empty", LuaValue.valueOf(item.isEmpty()));
        t.set("item_count", LuaValue.valueOf(item.getCount()));
        t.set("item", LuaValue.valueOf(String.valueOf(item.getItem())));
    }

    /** Adds any field the curated mapping did not already expose. */
    private static void generic(LuaTable t, Packet<?> packet) {
        Class<?> type = packet.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                String key = snake(field.getName());
                if (!t.get(key).isnil()) continue;
                try {
                    field.setAccessible(true);
                    put(t, key, field.get(packet));
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }

    private static void put(LuaTable t, String key, Object value) {
        if (value == null) return;
        if (value instanceof Boolean b) t.set(key, LuaValue.valueOf(b));
        else if (value instanceof Byte b) t.set(key, LuaValue.valueOf(b.intValue()));
        else if (value instanceof Short s) t.set(key, LuaValue.valueOf(s.intValue()));
        else if (value instanceof Integer i) t.set(key, LuaValue.valueOf(i));
        else if (value instanceof Long l) t.set(key, LuaValue.valueOf(l));
        else if (value instanceof Float f) t.set(key, LuaValue.valueOf(f));
        else if (value instanceof Double d) t.set(key, LuaValue.valueOf(d));
        else if (value instanceof String s) t.set(key, LuaValue.valueOf(s));
        else if (value instanceof Enum<?> e) t.set(key, LuaValue.valueOf(e.name()));
        else if (value instanceof UUID u) t.set(key, LuaValue.valueOf(u.toString()));
        else if (value instanceof Vec3 v) {
            LuaTable vec = new LuaTable();
            vec.set("x", LuaValue.valueOf(v.x));
            vec.set("y", LuaValue.valueOf(v.y));
            vec.set("z", LuaValue.valueOf(v.z));
            t.set(key, vec);
        } else if (value instanceof BlockPos p) {
            LuaTable pos = new LuaTable();
            pos.set("x", LuaValue.valueOf(p.getX()));
            pos.set("y", LuaValue.valueOf(p.getY()));
            pos.set("z", LuaValue.valueOf(p.getZ()));
            t.set(key, pos);
        } else if (value instanceof Component c) {
            t.set(key, LuaValue.valueOf(c.getString()));
        }
    }

    private static void enumField(LuaTable t, Object owner, String field, String key) {
        Object value = get(owner, field);
        if (value instanceof Enum<?> e) {
            t.set(key, LuaValue.valueOf(e.ordinal()));
            t.set(key + "_name", LuaValue.valueOf(e.name()));
        }
    }

    private static String component(Object value) {
        return value instanceof Component c ? c.getString() : "";
    }

    private static int sizeOf(Object value) {
        if (value instanceof Collection<?> c) return c.size();
        if (value instanceof Map<?, ?> m) return m.size();
        if (value instanceof Object[] a) return a.length;
        if (value instanceof int[] a) return a.length;
        if (value instanceof short[] a) return a.length;
        return 0;
    }

    // ------------------------------------------------------------------ write

    public static Packet<?> apply(Payload payload) {
        LuaTable table = payload.table();
        return payload.outbound()
                ? applyOutbound(payload.packet(), table)
                : applyInbound(payload.packet(), table);
    }

    private static Packet<?> applyInbound(Packet<?> packet, LuaTable e) {
        if (packet instanceof ClientboundKeepAlivePacket p) {
            long id = gl(e, "id", gl(p, "id", 0));
            return id == gl(p, "id", 0) ? p : new ClientboundKeepAlivePacket(id);
        }
        if (packet instanceof ClientboundSetEntityMotionPacket p) {
            int id = gi(e, "entity_id", gi(p, "id", 0));
            Object movement = get(p, "movement");
            double dx = movement instanceof Vec3 v ? v.x : 0;
            double dy = movement instanceof Vec3 v ? v.y : 0;
            double dz = movement instanceof Vec3 v ? v.z : 0;
            double x = gd(e, "motion_x", dx);
            double y = gd(e, "motion_y", dy);
            double z = gd(e, "motion_z", dz);
            if (id == gi(p, "id", 0) && x == dx && y == dy && z == dz) return p;
            return new ClientboundSetEntityMotionPacket(id, new Vec3(x, y, z));
        }
        if (packet instanceof ClientboundPlayerPositionPacket p) {
            Object change = get(p, "change");
            if (!(change instanceof PositionMoveRotation rot)) return p;
            Vec3 pos = rot.position();
            Vec3 delta = rot.deltaMovement();
            float yRot = rot.yRot();
            float xRot = rot.xRot();
            double x = gd(e, "x", pos.x);
            double y = gd(e, "y", pos.y);
            double z = gd(e, "z", pos.z);
            float ny = (float) gd(e, "yaw", yRot);
            float nx = (float) gd(e, "pitch", xRot);
            int id = gi(e, "id", gi(p, "id", 0));
            if (id == gi(p, "id", 0) && x == pos.x && y == pos.y && z == pos.z && ny == yRot && nx == xRot) return p;
            @SuppressWarnings("rawtypes")
            Set relatives = (Set) get(p, "relatives");
            return new ClientboundPlayerPositionPacket(id,
                    new PositionMoveRotation(new Vec3(x, y, z), delta, ny, nx), relatives);
        }
        if (packet instanceof ClientboundTeleportEntityPacket p) {
            Object change = get(p, "change");
            if (!(change instanceof PositionMoveRotation rot)) return p;
            Vec3 pos = rot.position();
            Vec3 delta = rot.deltaMovement();
            float yRot = rot.yRot();
            float xRot = rot.xRot();
            double x = gd(e, "x", pos.x);
            double y = gd(e, "y", pos.y);
            double z = gd(e, "z", pos.z);
            float ny = (float) gd(e, "yaw", yRot);
            float nx = (float) gd(e, "pitch", xRot);
            boolean ground = gb(e, "on_ground", gb(p, "onGround", false));
            if (x == pos.x && y == pos.y && z == pos.z && ny == yRot && nx == xRot
                    && ground == gb(p, "onGround", false)) return p;
            @SuppressWarnings("rawtypes")
            Set relatives = (Set) get(p, "relatives");
            return new ClientboundTeleportEntityPacket(gi(p, "id", 0),
                    new PositionMoveRotation(new Vec3(x, y, z), delta, ny, nx), relatives, ground);
        }
        if (packet instanceof ClientboundSetHealthPacket p) {
            float health = (float) gd(e, "health", gf(p, "health", 0));
            int food = gi(e, "food", gi(p, "food", 0));
            float saturation = (float) gd(e, "saturation", gf(p, "saturation", 0));
            if (health == gf(p, "health", 0) && food == gi(p, "food", 0)
                    && saturation == gf(p, "saturation", 0)) return p;
            return new ClientboundSetHealthPacket(health, food, saturation);
        }
        if (packet instanceof ClientboundSetExperiencePacket p) {
            float progress = (float) gd(e, "experience_progress", gf(p, "experienceProgress", 0));
            int total = gi(e, "total_experience", gi(p, "totalExperience", 0));
            int level = gi(e, "experience_level", gi(p, "experienceLevel", 0));
            if (progress == gf(p, "experienceProgress", 0) && total == gi(p, "totalExperience", 0)
                    && level == gi(p, "experienceLevel", 0)) return p;
            return new ClientboundSetExperiencePacket(progress, total, level);
        }
        if (packet instanceof ClientboundSetHeldSlotPacket p) {
            int slot = gi(e, "slot", gi(p, "slot", 0));
            return slot == gi(p, "slot", 0) ? p : new ClientboundSetHeldSlotPacket(slot);
        }
        if (packet instanceof ClientboundContainerSetSlotPacket p) {
            int container = gi(e, "window_id", gi(p, "containerId", 0));
            int state = gi(e, "state_id", gi(p, "stateId", 0));
            int slot = gi(e, "slot", gi(p, "slot", 0));
            if (container == gi(p, "containerId", 0) && state == gi(p, "stateId", 0)
                    && slot == gi(p, "slot", 0)) return p;
            return new ClientboundContainerSetSlotPacket(container, state, slot,
                    (ItemStack) get(p, "itemStack"));
        }
        if (packet instanceof ClientboundContainerSetDataPacket p) {
            int container = gi(e, "window_id", gi(p, "containerId", 0));
            int property = gi(e, "property", gi(p, "id", 0));
            int value = gi(e, "value", gi(p, "value", 0));
            if (container == gi(p, "containerId", 0) && property == gi(p, "id", 0)
                    && value == gi(p, "value", 0)) return p;
            return new ClientboundContainerSetDataPacket(container, property, value);
        }
        if (packet instanceof ClientboundBlockChangedAckPacket p) {
            int sequence = gi(e, "sequence", gi(p, "sequence", 0));
            return sequence == gi(p, "sequence", 0) ? p : new ClientboundBlockChangedAckPacket(sequence);
        }
        if (packet instanceof ClientboundSetTimePacket p) {
            long time = gl(e, "game_time", gl(p, "gameTime", 0));
            if (time == gl(p, "gameTime", 0)) return p;
            @SuppressWarnings("rawtypes")
            Map clocks = (Map) get(p, "clockUpdates");
            return new ClientboundSetTimePacket(time, clocks);
        }
        if (packet instanceof ClientboundCooldownPacket p) {
            int duration = gi(e, "duration", gi(p, "duration", 0));
            if (duration == gi(p, "duration", 0)) return p;
            return new ClientboundCooldownPacket((Identifier) get(p, "cooldownGroup"), duration);
        }
        if (packet instanceof ClientboundHurtAnimationPacket p) {
            int id = gi(e, "entity_id", gi(p, "id", 0));
            float yaw = (float) gd(e, "yaw", gf(p, "yaw", 0));
            if (id == gi(p, "id", 0) && yaw == gf(p, "yaw", 0)) return p;
            return new ClientboundHurtAnimationPacket(id, yaw);
        }
        if (packet instanceof ClientboundBlockDestructionPacket p) {
            int id = gi(e, "entity_id", gi(p, "id", 0));
            int progress = gi(e, "progress", gi(p, "progress", 0));
            if (id == gi(p, "id", 0) && progress == gi(p, "progress", 0)) return p;
            return new ClientboundBlockDestructionPacket(id, (BlockPos) get(p, "pos"), progress);
        }
        if (packet instanceof ClientboundGameEventPacket p) {
            float param = (float) gd(e, "param", gf(p, "param", 0));
            if (param == gf(p, "param", 0)) return p;
            return new ClientboundGameEventPacket((ClientboundGameEventPacket.Type) get(p, "event"), param);
        }
        if (packet instanceof ClientboundExplodePacket p) {
            double mx = gd(e, "motion_x", 0);
            double my = gd(e, "motion_y", 0);
            double mz = gd(e, "motion_z", 0);
            Object knockback = get(p, "playerKnockback");
            Vec3 current = knockback instanceof Optional<?> o && o.orElse(null) instanceof Vec3 v ? v : null;
            if (current != null && current.x == mx && current.y == my && current.z == mz) return p;
            if (current == null && e.get("motion_x").isnil() && e.get("motion_y").isnil()
                    && e.get("motion_z").isnil()) return p;
            return new ClientboundExplodePacket((Vec3) get(p, "center"), gf(p, "radius", 0),
                    gi(p, "blockCount", 0), Optional.of(new Vec3(mx, my, mz)),
                    (ParticleOptions) get(p, "explosionParticle"),
                    (Holder) get(p, "explosionSound"),
                    (net.minecraft.util.random.WeightedList) get(p, "blockParticles"),
                    gb(p, "playSound", false));
        }
        return packet;
    }

    private static Packet<?> applyOutbound(Packet<?> packet, LuaTable e) {
        if (packet instanceof ServerboundMovePlayerPacket p) {
            double x = gd(e, "x", gd(p, "x", 0));
            double y = gd(e, "y", gd(p, "y", 0));
            double z = gd(e, "z", gd(p, "z", 0));
            float yaw = (float) gd(e, "yaw", gf(p, "yRot", 0));
            float pitch = (float) gd(e, "pitch", gf(p, "xRot", 0));
            boolean ground = gb(e, "on_ground", gb(e, "ground", gb(p, "onGround", false)));
            boolean collision = gb(e, "horizontal_collision", gb(p, "horizontalCollision", false));
            if (x == gd(p, "x", 0) && y == gd(p, "y", 0) && z == gd(p, "z", 0)
                    && yaw == gf(p, "yRot", 0) && pitch == gf(p, "xRot", 0)
                    && ground == gb(p, "onGround", false)
                    && collision == gb(p, "horizontalCollision", false)) return p;
            if (p instanceof ServerboundMovePlayerPacket.PosRot) {
                return new ServerboundMovePlayerPacket.PosRot(x, y, z, yaw, pitch, ground, collision);
            }
            if (p instanceof ServerboundMovePlayerPacket.Pos) {
                return new ServerboundMovePlayerPacket.Pos(x, y, z, ground, collision);
            }
            if (p instanceof ServerboundMovePlayerPacket.Rot) {
                return new ServerboundMovePlayerPacket.Rot(yaw, pitch, ground, collision);
            }
            return new ServerboundMovePlayerPacket.StatusOnly(ground, collision);
        }
        if (packet instanceof ServerboundPlayerActionPacket p) {
            int action = giEnum(e, "action", ServerboundPlayerActionPacket.Action.values(), gi(p, "action", 0));
            int direction = giEnum(e, "facing", Direction.values(), gi(p, "direction", 0));
            int sequence = gi(e, "sequence", gi(p, "sequence", 0));
            int x = gi(e, "x", gposX(p, "pos"));
            int y = gi(e, "y", gposY(p, "pos"));
            int z = gi(e, "z", gposZ(p, "pos"));
            if (action == gi(p, "action", 0) && direction == gi(p, "direction", 0)
                    && sequence == gi(p, "sequence", 0)
                    && x == gposX(p, "pos") && y == gposY(p, "pos") && z == gposZ(p, "pos")) return p;
            return new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.values()[clamp(action, ServerboundPlayerActionPacket.Action.values().length)],
                    new BlockPos(x, y, z), Direction.values()[clamp(direction, Direction.values().length)], sequence);
        }
        if (packet instanceof ServerboundSetCarriedItemPacket p) {
            int slot = gi(e, "slot", gi(p, "slot", 0));
            return slot == gi(p, "slot", 0) ? p : new ServerboundSetCarriedItemPacket(slot);
        }
        if (packet instanceof ServerboundPlayerCommandPacket p) {
            int action = giEnum(e, "action", ServerboundPlayerCommandPacket.Action.values(), gi(p, "action", 0));
            int data = gi(e, "aux_data", gi(e, "data", gi(p, "data", 0)));
            if (action == gi(p, "action", 0) && data == gi(p, "data", 0)) return p;
            var player = Minecraft.getInstance().player;
            if (player == null) return p;
            return new ServerboundPlayerCommandPacket(player,
                    ServerboundPlayerCommandPacket.Action.values()[clamp(action, ServerboundPlayerCommandPacket.Action.values().length)],
                    data);
        }
        if (packet instanceof ServerboundPlayerInputPacket p) {
            Object input = get(p, "input");
            Input base = input instanceof Input in ? in : Input.EMPTY;
            Input next = new Input(
                    gb(e, "forward", base.forward()), gb(e, "backward", base.backward()),
                    gb(e, "left", base.left()), gb(e, "right", base.right()),
                    gb(e, "jumping", gb(e, "jump", base.jump())),
                    gb(e, "sneaking", base.shift()), gb(e, "sprint", base.sprint()));
            return next.equals(base) ? p : new ServerboundPlayerInputPacket(next);
        }
        if (packet instanceof ServerboundContainerClosePacket p) {
            int container = gi(e, "window_id", gi(p, "containerId", 0));
            return container == gi(p, "containerId", 0) ? p : new ServerboundContainerClosePacket(container);
        }
        if (packet instanceof ServerboundContainerClickPacket p) {
            int container = gi(e, "window_id", gi(p, "containerId", 0));
            int state = gi(e, "state_id", gi(p, "stateId", 0));
            int slot = gi(e, "slot", gi(p, "slotNum", 0));
            int button = gi(e, "mouse_button", gi(p, "buttonNum", 0));
            int mode = giEnum(e, "mode", ContainerInput.values(), gi(p, "containerInput", 0));
            if (container == gi(p, "containerId", 0) && state == gi(p, "stateId", 0)
                    && slot == gi(p, "slotNum", 0) && button == gi(p, "buttonNum", 0)
                    && mode == gi(p, "containerInput", 0)) return p;
            Object carried = get(p, "carriedItem");
            return new ServerboundContainerClickPacket(container, state, (short) slot, (byte) button,
                    ContainerInput.values()[clamp(mode, ContainerInput.values().length)],
                    new Int2ObjectOpenHashMap<>(), carried instanceof HashedStack hs ? hs : HashedStack.EMPTY);
        }
        if (packet instanceof ServerboundContainerButtonClickPacket p) {
            int container = gi(e, "window_id", gi(p, "containerId", 0));
            int button = gi(e, "button_id", gi(p, "buttonId", 0));
            if (container == gi(p, "containerId", 0) && button == gi(p, "buttonId", 0)) return p;
            return new ServerboundContainerButtonClickPacket(container, button);
        }
        if (packet instanceof ServerboundPlayerAbilitiesPacket p) {
            boolean flying = gb(e, "flying", gb(p, "isFlying", false));
            if (flying == gb(p, "isFlying", false)) return p;
            Abilities abilities = new Abilities();
            abilities.flying = flying;
            return new ServerboundPlayerAbilitiesPacket(abilities);
        }
        if (packet instanceof ServerboundCommandSuggestionPacket p) {
            int id = gi(e, "id", gi(p, "id", 0));
            String text = gs(e, "text", gs(p, "command", ""));
            if (id == gi(p, "id", 0) && text.equals(gs(p, "command", ""))) return p;
            return new ServerboundCommandSuggestionPacket(id, text);
        }
        if (packet instanceof ServerboundSpectatorActionPacket p) {
            int target = gi(e, "target", -1);
            if (target < 0) return p;
            return new ServerboundSpectatorActionPacket(OptionalInt.of(target));
        }
        if (packet instanceof ServerboundResourcePackPacket p) {
            int status = giEnum(e, "status", ServerboundResourcePackPacket.Action.values(), gi(p, "action", 0));
            if (status == gi(p, "action", 0)) return p;
            return new ServerboundResourcePackPacket((UUID) get(p, "id"),
                    ServerboundResourcePackPacket.Action.values()[clamp(status, ServerboundResourcePackPacket.Action.values().length)]);
        }
        if (packet instanceof ServerboundClientCommandPacket p) {
            int action = giEnum(e, "action", ServerboundClientCommandPacket.Action.values(), gi(p, "action", 0));
            if (action == gi(p, "action", 0)) return p;
            return new ServerboundClientCommandPacket(
                    ServerboundClientCommandPacket.Action.values()[clamp(action, ServerboundClientCommandPacket.Action.values().length)]);
        }
        if (packet instanceof ServerboundInteractPacket p) {
            int target = gi(e, "target", gi(p, "entityId", -1));
            int hand = giEnum(e, "hand", InteractionHand.values(), gi(p, "hand", 0));
            boolean secondary = gb(e, "using_secondary_action", gb(p, "usingSecondaryAction", false));
            Object location = get(p, "location");
            double hx = gd(e, "hit_x", location instanceof Vec3 v ? v.x : 0);
            double hy = gd(e, "hit_y", location instanceof Vec3 v ? v.y : 0);
            double hz = gd(e, "hit_z", location instanceof Vec3 v ? v.z : 0);
            return new ServerboundInteractPacket(target,
                    InteractionHand.values()[clamp(hand, InteractionHand.values().length)],
                    new Vec3(hx, hy, hz), secondary);
        }
        if (packet instanceof ServerboundAttackPacket p) {
            int target = gi(e, "target", gi(p, "entityId", -1));
            return target == gi(p, "entityId", -1) ? p : new ServerboundAttackPacket(target);
        }
        if (packet instanceof ServerboundUseItemPacket p) {
            int hand = giEnum(e, "hand", InteractionHand.values(), gi(p, "hand", 0));
            int sequence = gi(e, "sequence", gi(p, "sequence", 0));
            float yaw = (float) gd(e, "yaw", gf(p, "yRot", 0));
            float pitch = (float) gd(e, "pitch", gf(p, "xRot", 0));
            if (hand == gi(p, "hand", 0) && sequence == gi(p, "sequence", 0)
                    && yaw == gf(p, "yRot", 0) && pitch == gf(p, "xRot", 0)) return p;
            return new ServerboundUseItemPacket(
                    InteractionHand.values()[clamp(hand, InteractionHand.values().length)], sequence, yaw, pitch);
        }
        if (packet instanceof ServerboundUseItemOnPacket p) {
            int sequence = gi(e, "sequence", gi(p, "sequence", 0));
            int hand = giEnum(e, "hand", InteractionHand.values(), gi(p, "hand", 0));
            Object hit = get(p, "hitResult");
            BlockHitResult base = hit instanceof BlockHitResult bhr ? bhr : null;
            if (base == null) return p;
            int x = gi(e, "x", base.getBlockPos().getX());
            int y = gi(e, "y", base.getBlockPos().getY());
            int z = gi(e, "z", base.getBlockPos().getZ());
            int facing = gi(e, "facing", base.getDirection().ordinal());
            BlockPos pos = new BlockPos(x, y, z);
            Vec3 location = new Vec3(x + 0.5, y + 0.5, z + 0.5);
            if (e.get("offset_x").isnumber() || e.get("offset_y").isnumber() || e.get("offset_z").isnumber()) {
                location = new Vec3(x + gd(e, "offset_x", 0.5), y + gd(e, "offset_y", 0.5), z + gd(e, "offset_z", 0.5));
            }
            if (sequence == gi(p, "sequence", 0) && hand == gi(p, "hand", 0)
                    && x == base.getBlockPos().getX() && y == base.getBlockPos().getY()
                    && z == base.getBlockPos().getZ() && facing == base.getDirection().ordinal()
                    && location.equals(base.getLocation())) return p;
            return new ServerboundUseItemOnPacket(
                    InteractionHand.values()[clamp(hand, InteractionHand.values().length)],
                    new BlockHitResult(location, Direction.values()[clamp(facing, Direction.values().length)], pos, false),
                    sequence);
        }
        if (packet instanceof ServerboundMoveVehiclePacket p) {
            Object par = get(p, "movingTo");
            Vec3 pos = par instanceof PositionAndRotation rot && rot.position() != null
                    ? rot.position() : Vec3.ZERO;
            float baseYaw = par instanceof PositionAndRotation rot ? rot.yRot() : 0;
            float basePitch = par instanceof PositionAndRotation rot ? rot.xRot() : 0;
            float yaw = (float) gd(e, "yaw", baseYaw);
            float pitch = (float) gd(e, "pitch", basePitch);
            double x = gd(e, "x", pos.x);
            double y = gd(e, "y", pos.y);
            double z = gd(e, "z", pos.z);
            boolean ground = gb(e, "on_ground", gb(p, "onGround", false));
            if (x == pos.x && y == pos.y && z == pos.z && yaw == baseYaw
                    && pitch == basePitch && ground == gb(p, "onGround", false)) return p;
            return new ServerboundMoveVehiclePacket(
                    PositionAndRotation.of(new Vec3(x, y, z), yaw, pitch), ground);
        }
        if (packet instanceof ServerboundChangeGameModePacket p) {
            int mode = giEnum(e, "mode", GameType.values(), gi(p, "mode", 0));
            if (mode == gi(p, "mode", 0)) return p;
            return new ServerboundChangeGameModePacket(
                    GameType.values()[clamp(mode, GameType.values().length)]);
        }
        if (packet instanceof ServerboundChatCommandPacket p) {
            String command = gs(e, "command", gs(p, "command", ""));
            return command.equals(gs(p, "command", "")) ? p : new ServerboundChatCommandPacket(command);
        }
        if (packet instanceof ServerboundChatPacket p) {
            String message = gs(e, "message", gs(p, "message", ""));
            if (message.equals(gs(p, "message", ""))) return p;
            Object signature = get(p, "signature");
            Object lastSeen = get(p, "lastSeenMessages");
            return new ServerboundChatPacket(message, (Instant) get(p, "timeStamp"), gl(p, "salt", 0),
                    (Optional) signature, (LastSeenMessages.Update) lastSeen);
        }
        return packet;
    }

    // ------------------------------------------------------------------ create

    public static Packet<?> create(int id, boolean outbound, LuaTable args) {
        if (!outbound) throw new LuaError("api:send_packet 只能发送服务端向封包");
        return switch (id) {
            case 0x00 -> new ServerboundKeepAlivePacket(gl(args, "id", 0));
            case 0x02 -> createInteract(args);
            case 0x03 -> new ServerboundMovePlayerPacket.StatusOnly(
                    gb(args, "on_ground", true), gb(args, "horizontal_collision", false));
            case 0x04 -> new ServerboundMovePlayerPacket.Pos(
                    gd(args, "x", 0), gd(args, "y", 0), gd(args, "z", 0),
                    gb(args, "on_ground", true), gb(args, "horizontal_collision", false));
            case 0x05 -> new ServerboundMovePlayerPacket.Rot(
                    (float) gd(args, "yaw", 0), (float) gd(args, "pitch", 0),
                    gb(args, "on_ground", true), gb(args, "horizontal_collision", false));
            case 0x06 -> new ServerboundMovePlayerPacket.PosRot(
                    gd(args, "x", 0), gd(args, "y", 0), gd(args, "z", 0),
                    (float) gd(args, "yaw", 0), (float) gd(args, "pitch", 0),
                    gb(args, "on_ground", true), gb(args, "horizontal_collision", false));
            case 0x07 -> new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.values()[
                            clamp(gi(args, "action", 0), ServerboundPlayerActionPacket.Action.values().length)],
                    new BlockPos(gi(args, "x", 0), gi(args, "y", 0), gi(args, "z", 0)),
                    Direction.values()[clamp(gi(args, "facing", 0), Direction.values().length)],
                    gi(args, "sequence", 0));
            case 0x08 -> new ServerboundUseItemOnPacket(
                    InteractionHand.values()[clamp(gi(args, "hand", 0), InteractionHand.values().length)],
                    new BlockHitResult(
                            new Vec3(gd(args, "x", 0) + gd(args, "offset_x", 0.5),
                                    gd(args, "y", 0) + gd(args, "offset_y", 0.5),
                                    gd(args, "z", 0) + gd(args, "offset_z", 0.5)),
                            Direction.values()[clamp(gi(args, "facing", 0), Direction.values().length)],
                            new BlockPos(gi(args, "x", 0), gi(args, "y", 0), gi(args, "z", 0)), false),
                    gi(args, "sequence", 0));
            case 0x09 -> new ServerboundSetCarriedItemPacket(gi(args, "slot", 0));
            case 0x0A -> new ServerboundPunchPacket();
            case 0x0B -> createPlayerCommand(args);
            case 0x0C -> new ServerboundPlayerInputPacket(new Input(
                    gb(args, "forward", false), gb(args, "backward", false),
                    gb(args, "left", false), gb(args, "right", false),
                    gb(args, "jumping", gb(args, "jump", false)),
                    gb(args, "sneaking", false), gb(args, "sprint", false)));
            case 0x0D -> new ServerboundContainerClosePacket(gi(args, "window_id", gi(args, "container_id", 0)));
            case 0x0E -> new ServerboundContainerClickPacket(
                    gi(args, "window_id", 0), gi(args, "state_id", 0),
                    (short) gi(args, "slot", 0), (byte) gi(args, "mouse_button", 0),
                    ContainerInput.values()[clamp(gi(args, "mode", 0), ContainerInput.values().length)],
                    new Int2ObjectOpenHashMap<>(), HashedStack.EMPTY);
            case 0x13 -> createAbilities(args);
            case 0x14 -> new ServerboundCommandSuggestionPacket(
                    gi(args, "id", 0), gs(args, "text", gs(args, "command", "")));
            case 0x18 -> new ServerboundSpectatorActionPacket(
                    args.get("target").isnil() ? OptionalInt.empty() : OptionalInt.of(gi(args, "target", -1)));
            case 0x19 -> new ServerboundResourcePackPacket(
                    UUID.fromString(gs(args, "uuid", gs(args, "id", "00000000-0000-0000-0000-000000000000"))),
                    ServerboundResourcePackPacket.Action.values()[
                            clamp(gi(args, "status", 0), ServerboundResourcePackPacket.Action.values().length)]);
            case 0x1A -> new ServerboundAttackPacket(gi(args, "target", gi(args, "entity_id", -1)));
            case 0x1B -> new ServerboundMoveVehiclePacket(
                    PositionAndRotation.of(new Vec3(gd(args, "x", 0), gd(args, "y", 0), gd(args, "z", 0)),
                            (float) gd(args, "yaw", 0), (float) gd(args, "pitch", 0)),
                    gb(args, "on_ground", true));
            case 0x1C -> new ServerboundClientCommandPacket(
                    ServerboundClientCommandPacket.Action.values()[
                            clamp(gi(args, "action", 0), ServerboundClientCommandPacket.Action.values().length)]);
            case 0x1D -> new ServerboundPickItemFromBlockPacket(
                    new BlockPos(gi(args, "x", 0), gi(args, "y", 0), gi(args, "z", 0)),
                    gb(args, "include_data", false));
            case 0x1E -> new ServerboundPickItemFromEntityPacket(
                    gi(args, "entity_id", gi(args, "id", -1)), gb(args, "include_data", false));
            case 0x1F -> new ServerboundPlayerLoadedPacket();
            case 0x20 -> new ServerboundTeleportToEntityPacket(UUID.fromString(gs(args, "uuid", "")));
            case 0x21 -> new ServerboundUseItemPacket(
                    InteractionHand.values()[clamp(gi(args, "hand", 0), InteractionHand.values().length)],
                    gi(args, "sequence", 0), (float) gd(args, "yaw", 0), (float) gd(args, "pitch", 0));
            case 0x23 -> new ServerboundRenameItemPacket(gs(args, "name", ""));
            case 0x24 -> new ServerboundSelectTradePacket(gi(args, "item", 0));
            case 0x26 -> new ServerboundChatCommandPacket(gs(args, "command", ""));
            case 0x27 -> new ServerboundContainerButtonClickPacket(
                    gi(args, "window_id", gi(args, "container_id", 0)), gi(args, "button_id", 0));
            default -> throw new LuaError("不支持的出站封包 id 0x" + Integer.toHexString(id)
                    + "；用 api:packet_list(\"send\") 查看全部可用 id");
        };
    }

    private static Packet<?> createInteract(LuaTable args) {
        int target = gi(args, "target", gi(args, "entity_id", -1));
        int action = gi(args, "action", 0);
        if (action == 1) return new ServerboundAttackPacket(target);
        InteractionHand hand = InteractionHand.values()[
                clamp(gi(args, "hand", 0), InteractionHand.values().length)];
        boolean at = action == 2;
        Vec3 location = at
                ? new Vec3(gd(args, "hit_x", 0), gd(args, "hit_y", 0), gd(args, "hit_z", 0))
                : Vec3.ZERO;
        return new ServerboundInteractPacket(target, hand, location, at);
    }

    private static Packet<?> createPlayerCommand(LuaTable args) {
        var player = Minecraft.getInstance().player;
        if (player == null) throw new LuaError("player_command 需要处于游戏世界中");
        return new ServerboundPlayerCommandPacket(player,
                ServerboundPlayerCommandPacket.Action.values()[
                        clamp(gi(args, "action", 0), ServerboundPlayerCommandPacket.Action.values().length)],
                gi(args, "aux_data", gi(args, "data", 0)));
    }

    private static Packet<?> createAbilities(LuaTable args) {
        Abilities abilities = new Abilities();
        abilities.flying = gb(args, "flying", false);
        return new ServerboundPlayerAbilitiesPacket(abilities);
    }

    public static Packet<?> create(String name, boolean outbound, LuaTable args) {
        int id = idOfName(name, outbound);
        if (id < 0) throw new LuaError("未知封包名: " + name);
        return create(id, outbound, args);
    }

    // ------------------------------------------------------------------ helpers

    public static String snake(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder out = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) out.append('_');
                out.append(Character.toLowerCase(c));
            } else if (c == '$') {
                out.append('_');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static int clamp(int value, int size) {
        if (size <= 0) return 0;
        return Math.max(0, Math.min(size - 1, value));
    }

    private static Object get(Object target, String name) {
        if (target == null) return null;
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static int gi(Object target, String name, int fallback) {
        Object value = get(target, name);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof Enum<?> e) return e.ordinal();
        if (value instanceof Boolean b) return b ? 1 : 0;
        return fallback;
    }

    private static long gl(Object target, String name, long fallback) {
        Object value = get(target, name);
        return value instanceof Number n ? n.longValue() : fallback;
    }

    private static float gf(Object target, String name, float fallback) {
        Object value = get(target, name);
        return value instanceof Number n ? n.floatValue() : fallback;
    }

    private static double gd(Object target, String name, double fallback) {
        Object value = get(target, name);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private static boolean gb(Object target, String name, boolean fallback) {
        Object value = get(target, name);
        return value instanceof Boolean b ? b : fallback;
    }

    private static String gs(Object target, String name, String fallback) {
        Object value = get(target, name);
        return value instanceof String s ? s : fallback;
    }

    private static int gposX(Object target, String name) {
        Object value = get(target, name);
        return value instanceof BlockPos p ? p.getX() : 0;
    }

    private static int gposY(Object target, String name) {
        Object value = get(target, name);
        return value instanceof BlockPos p ? p.getY() : 0;
    }

    private static int gposZ(Object target, String name) {
        Object value = get(target, name);
        return value instanceof BlockPos p ? p.getZ() : 0;
    }

    // ---- Lua table accessors ----

    private static double gd(LuaTable t, String key, double fallback) {
        LuaValue value = t.get(key);
        return value.isnil() ? fallback : value.todouble();
    }

    private static int gi(LuaTable t, String key, int fallback) {
        LuaValue value = t.get(key);
        return value.isnil() ? fallback : value.toint();
    }

    private static long gl(LuaTable t, String key, long fallback) {
        LuaValue value = t.get(key);
        return value.isnil() ? fallback : (long) value.todouble();
    }

    private static boolean gb(LuaTable t, String key, boolean fallback) {
        LuaValue value = t.get(key);
        return value.isnil() ? fallback : value.toboolean();
    }

    private static String gs(LuaTable t, String key, String fallback) {
        LuaValue value = t.get(key);
        return value.isnil() ? fallback : value.tojstring();
    }

    /** Accepts either an ordinal number or an enum constant name. */
    private static int giEnum(LuaTable t, String key, Enum<?>[] values, int fallback) {
        LuaValue value = t.get(key);
        if (value.isnil()) return fallback;
        if (value.isnumber()) return clamp(value.toint(), values.length);
        String name = value.tojstring();
        for (Enum<?> candidate : values) {
            if (candidate.name().equalsIgnoreCase(name)) return candidate.ordinal();
        }
        return fallback;
    }

    /** Human-readable summary used by {@code api:packet_list}. */
    public static List<String> describe(boolean outbound) {
        List<String> lines = new ArrayList<>();
        Map<Integer, String> names = outbound ? OUT_NAMES : IN_NAMES;
        names.forEach((id, name) -> lines.add(String.format("0x%02X %s", id, name)));
        return lines;
    }
}
