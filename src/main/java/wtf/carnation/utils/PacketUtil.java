package wtf.carnation.utils;

import net.minecraft.network.protocol.Packet;
import org.luaj.vm2.LuaValue;
import wtf.carnation.script.LuaPackets;

/** Small packet helpers reused by the movement modules. */
public final class PacketUtil {

    private PacketUtil() {
    }

    /** Rebuilds a serverbound move packet with a different on-ground flag. */
    public static Packet<?> withOnGround(Packet<?> packet, boolean onGround) {
        LuaPackets.Payload payload = LuaPackets.read(packet, true);
        LuaValue current = payload.table().get("on_ground");
        if (!current.isnil() && current.toboolean() == onGround) return packet;
        payload.table().set("on_ground", LuaValue.valueOf(onGround));
        return LuaPackets.apply(payload);
    }
}
