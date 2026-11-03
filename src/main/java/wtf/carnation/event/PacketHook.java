package wtf.carnation.event;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import wtf.carnation.Client;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.script.LuaPacketDispatcher;

/**
 * One packet switchboard shared by {@code Connection} and the modules.
 *
 * <p>Java listeners run first, then the Lua dispatcher. The result for a packet is cached
 * per thread by object identity because Mixin's {@code @ModifyVariable} and {@code @Inject}
 * both call this class for the same object.</p>
 */
public final class PacketHook {

    private static final ThreadLocal<Decision> LAST = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SILENT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private record Decision(Packet<?> original, Packet<?> result, boolean cancel) {
    }

    private PacketHook() {
    }

    public static Packet<?> dispatch(Packet<?> packet, boolean outbound) {
        if (packet == null) return null;
        Decision cached = LAST.get();
        if (cached != null && (cached.original() == packet || cached.result() == packet)) {
            return cached.result();
        }
        if (Boolean.TRUE.equals(SILENT.get()) || Client.instance == null) {
            LAST.set(new Decision(packet, packet, false));
            return packet;
        }
        Packet<?> working = packet;
        boolean cancel = false;
        try {
            PacketEvent event = new PacketEvent(outbound ? PacketEvent.Type.SEND : PacketEvent.Type.RECEIVE, working);
            Client.instance.getEventManager().call(event);
            cancel = event.isCancelled();
            working = event.getPacket();
        } catch (Throwable failure) {
            Client.logger.warn("Packet event failed", failure);
            working = packet;
        }
        if (!LuaPacketDispatcher.INSTANCE.muted()) {
            Packet<?> luaResult = LuaPacketDispatcher.INSTANCE.intercept(working, outbound);
            if (luaResult != working) working = luaResult;
            cancel |= LuaPacketDispatcher.INSTANCE.cancelled(working);
        }
        LAST.set(new Decision(packet, working, cancel));
        return working;
    }

    public static boolean cancelled(Packet<?> packet) {
        if (packet == null) return false;
        Decision cached = LAST.get();
        return cached != null
                && (cached.original() == packet || cached.result() == packet)
                && cached.cancel();
    }

    /** Sends a packet without firing {@link PacketEvent} or the Lua hooks. */
    public static void sendSilently(Packet<?> packet) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.connection == null) return;
        boolean previous = Boolean.TRUE.equals(SILENT.get());
        SILENT.set(Boolean.TRUE);
        try {
            player.connection.send(packet);
        } finally {
            SILENT.set(previous);
        }
    }
}
