package wtf.carnation.script;

import net.minecraft.network.protocol.Packet;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Single funnel between {@code Connection} and the Lua modules.
 *
 * <p>Inbound dispatch happens on the Netty thread, so a script that edits a packet
 * field sees the change before {@code channelRead0} processes it.
 * Every module callback is serialized on its own monitor (see {@link LuaModule}), which keeps
 * LuaJ globals safe when the client thread runs {@code onUpdate} at the same time.</p>
 *
 * <p>The decision for one packet is cached per thread and keyed by object identity: Mixin's
 * {@code @ModifyVariable} and {@code @Inject} both call {@link #intercept}, but only the first
 * call dispatches to Lua and only the second consumes the cancel flag.</p>
 */
public final class LuaPacketDispatcher {

    public static final LuaPacketDispatcher INSTANCE = new LuaPacketDispatcher();

    private final List<LuaModule> listeners = new CopyOnWriteArrayList<>();
    private final ThreadLocal<Decision> last = new ThreadLocal<>();
    private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);
    private final ThreadLocal<Boolean> muted = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private record Decision(Packet<?> original, Packet<?> replacement, boolean cancel) {
    }

    private LuaPacketDispatcher() {
    }

    public void register(LuaModule module) {
        if (module != null && !listeners.contains(module)) listeners.add(module);
    }

    public void unregister(LuaModule module) {
        listeners.remove(module);
    }

    /** {@code api:send_packet_no_event} sets this so its own packet is not re-dispatched. */
    public boolean muted() {
        return Boolean.TRUE.equals(muted.get());
    }

    public void mute(boolean value) {
        muted.set(value);
    }

    public Packet<?> intercept(Packet<?> packet, boolean outbound) {
        if (packet == null) return null;
        Decision cached = last.get();
        if (cached != null && (cached.original() == packet || cached.replacement() == packet)) {
            return cached.replacement();
        }
        if (listeners.isEmpty()) {
            last.set(new Decision(packet, packet, false));
            return packet;
        }
        boolean anyEnabled = false;
        String packetName = LuaPackets.nameOf(packet, outbound);
        for (LuaModule module : listeners) {
            if (module.wantsPacket(packetName, outbound)) {
                anyEnabled = true;
                break;
            }
        }
        if (!anyEnabled) {
            last.set(new Decision(packet, packet, false));
            return packet;
        }
        // A script that sends a packet from inside on_packet_send must not recurse forever.
        int level = depth.get();
        if (level > 0) return packet;
        depth.set(level + 1);
        try {
            LuaPackets.Payload payload = LuaPackets.read(packet, outbound);
            boolean cancel = false;
            String callback = outbound ? "onPacketSend" : "onPacketReceive";
            for (LuaModule module : listeners) {
                if (!module.isEnabled()) continue;
                if (module.dispatchPacket(callback, payload.table())) cancel = true;
            }
            Packet<?> replacement;
            try {
                replacement = LuaPackets.apply(payload);
            } catch (Throwable failure) {
                ClientLog.warn("Lua packet rewrite failed for " + payload.name(), failure);
                replacement = packet;
            }
            last.set(new Decision(packet, replacement, cancel));
            return replacement;
        } finally {
            depth.set(level);
        }
    }

    public boolean cancelled(Packet<?> packet) {
        if (packet == null) return false;
        Decision cached = last.get();
        return cached != null
                && (cached.original() == packet || cached.replacement() == packet)
                && cached.cancel();
    }

    /** Small indirection so the dispatcher does not depend on the Client singleton. */
    private static final class ClientLog {
        private static void warn(String message, Throwable failure) {
            if (wtf.carnation.Client.instance != null) {
                wtf.carnation.Client.logger.warn(message, failure);
            }
        }
    }
}
