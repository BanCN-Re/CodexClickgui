package wtf.carnation.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.network.PacketEvent;

/** Port of Naven's {@code tech.naven.utils.NetworkUtils} (lag detection only). */
public final class NetworkUtils {
    /** Registered in {@link wtf.carnation.Client}: without it no packet ever refreshes the timer. */
    public static final NetworkUtils INSTANCE = new NetworkUtils();

    private static final Minecraft mc = Minecraft.getInstance();
    private static final long[] lastPacket = {System.currentTimeMillis()};

    private NetworkUtils() {
    }

    public static boolean isServerLag() {
        return System.currentTimeMillis() - lastPacket[0] >= 500L;
    }

    /**
     * Any inbound traffic resets the timer. Naven's {@code Timer} is fed by every packet; watching
     * only pings/entity moves leaves 500 ms gaps in normal play, which makes {@link #isServerLag()}
     * flap and silently disables every module that gates on it (KillAura included).
     */
    @EventTarget
    public void onPacket(PacketEvent e) {
        if (e.isSend() || e.getPacket() == null) return;
        lastPacket[0] = System.currentTimeMillis();
    }
}
