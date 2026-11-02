package mixin;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.Client;
import wtf.carnation.script.LuaPacketDispatcher;

/**
 * Packet-level Lua hooks for 26.3.
 *
 * <p>{@code Connection.sendPacket} is the single funnel every outbound packet passes through,
 * and {@code channelRead0} is the single inbound entry point. Both are intercepted before the
 * vanilla handling runs, so {@code on_packet_receive} can rewrite or cancel a packet and
 * {@code on_packet_send} can replace or drop it.</p>
 */
@Mixin(Connection.class)
public class MixinConnection {

    private static final String SEND = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V";
    private static final String SEND_PACKET = "sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V";
    private static final String READ = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V";

    @ModifyVariable(method = SEND, at = @At("HEAD"), argsOnly = true, index = 1)
    private Packet<?> carnation$outboundSend(Packet<?> packet) {
        return outbound(packet);
    }

    @Inject(method = SEND, at = @At("HEAD"), cancellable = true)
    private void carnation$cancelSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (cancel(packet)) ci.cancel();
    }

    @ModifyVariable(method = SEND_PACKET, at = @At("HEAD"), argsOnly = true, index = 1)
    private Packet<?> carnation$outboundSendPacket(Packet<?> packet) {
        return outbound(packet);
    }

    @Inject(method = SEND_PACKET, at = @At("HEAD"), cancellable = true)
    private void carnation$cancelSendPacket(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (cancel(packet)) ci.cancel();
    }

    @ModifyVariable(method = READ, at = @At("HEAD"), argsOnly = true, index = 2)
    private Packet<?> carnation$inboundPacket(Packet<?> packet) {
        if (Client.instance == null || LuaPacketDispatcher.INSTANCE.muted()) return packet;
        if (!flow(packet, PacketFlow.CLIENTBOUND)) return packet;
        try {
            return LuaPacketDispatcher.INSTANCE.intercept(packet, false);
        } catch (Throwable failure) {
            Client.logger.warn("Lua inbound hook failed", failure);
            return packet;
        }
    }

    @Inject(method = READ, at = @At("HEAD"), cancellable = true)
    private void carnation$cancelInbound(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (Client.instance == null || LuaPacketDispatcher.INSTANCE.muted()) return;
        if (!flow(packet, PacketFlow.CLIENTBOUND)) return;
        try {
            LuaPacketDispatcher.INSTANCE.intercept(packet, false);
            if (LuaPacketDispatcher.INSTANCE.cancelled(packet)) ci.cancel();
        } catch (Throwable failure) {
            Client.logger.warn("Lua inbound hook failed", failure);
        }
    }

    private static Packet<?> outbound(Packet<?> packet) {
        if (Client.instance == null || LuaPacketDispatcher.INSTANCE.muted()) return packet;
        if (!flow(packet, PacketFlow.SERVERBOUND)) return packet;
        try {
            return LuaPacketDispatcher.INSTANCE.intercept(packet, true);
        } catch (Throwable failure) {
            Client.logger.warn("Lua outbound hook failed", failure);
            return packet;
        }
    }

    private static boolean cancel(Packet<?> packet) {
        if (Client.instance == null || LuaPacketDispatcher.INSTANCE.muted()) return false;
        if (!flow(packet, PacketFlow.SERVERBOUND)) return false;
        try {
            LuaPacketDispatcher.INSTANCE.intercept(packet, true);
            return LuaPacketDispatcher.INSTANCE.cancelled(packet);
        } catch (Throwable failure) {
            Client.logger.warn("Lua outbound hook failed", failure);
            return false;
        }
    }

    /** The integrated server shares the JVM, so direction must come from the packet type, not the hook. */
    private static boolean flow(Packet<?> packet, PacketFlow expected) {
        try {
            return packet.type() != null && packet.type().flow() == expected;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
