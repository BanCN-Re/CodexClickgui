package wtf.carnation.event.impl.network;

import net.minecraft.network.protocol.Packet;
import wtf.carnation.event.api.CancellableEvent;

/** Fired for every outbound and inbound packet; the packet may be replaced or dropped. */
public final class PacketEvent extends CancellableEvent {
    public enum Type {
        SEND,
        RECEIVE
    }

    private final Type type;
    private Packet<?> packet;

    public PacketEvent(Type type, Packet<?> packet) {
        this.type = type;
        this.packet = packet;
    }

    public Type getType() {
        return type;
    }

    public boolean isSend() {
        return type == Type.SEND;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public void setPacket(Packet<?> packet) {
        this.packet = packet;
    }
}
