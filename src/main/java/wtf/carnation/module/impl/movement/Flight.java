package wtf.carnation.module.impl.movement;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.Packet;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.PacketHook;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.RespawnEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Keeps the knockback packet and delays the pong replies, then starts fall flying.
 * Ported from the original keepalive-desync implementation; packet ids and the
 * fall-flying command are the 26.3 equivalents.
 */
public class Flight extends Module {
    private final Queue<Packet<?>> delayed = new ConcurrentLinkedQueue<>();
    private boolean intercepting;
    private boolean shouldStartFallFlying;
    private int ticksUntilFallFlying;

    public Flight() {
        super("Flight", Category.MOVEMENT, "拦截自身击退并延迟 pong，随后进入滑翔");
    }

    @Override
    protected void onEnable() {
        reset(false);
    }

    @Override
    protected void onDisable() {
        flush();
        reset(false);
    }

    @EventTarget
    public void onRespawn(RespawnEvent event) {
        if (isEnabled()) setState(false);
        reset(true);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null) return;
        if (!shouldStartFallFlying) return;
        if (++ticksUntilFallFlying < 8) return;
        PacketHook.sendSilently(new ServerboundPlayerCommandPacket(
                mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        shouldStartFallFlying = false;
        ticksUntilFallFlying = 0;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;
        Packet<?> packet = event.getPacket();

        if (event.isSend()) {
            if (intercepting && packet instanceof ServerboundPongPacket) {
                event.setCancelled(true);
                if (delayed.isEmpty()) {
                    shouldStartFallFlying = true;
                    ticksUntilFallFlying = 0;
                }
                delayed.add(packet);
                if (delayed.size() > 200) flushAndStop();
                return;
            }
            if (packet instanceof ServerboundPlayerCommandPacket) {
                if (intercepting && !delayed.isEmpty()) flushAndStop();
            }
            return;
        }

        if (packet instanceof ClientboundPlayerPositionPacket) {
            if (intercepting && !delayed.isEmpty()) flushAndStop();
            return;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == mc.player.getId()) {
            if (intercepting || !delayed.isEmpty()) return;
            intercepting = true;
            shouldStartFallFlying = false;
            ticksUntilFallFlying = 0;
            event.setCancelled(true);
        }
    }

    private void flushAndStop() {
        flush();
        intercepting = false;
        shouldStartFallFlying = false;
        ticksUntilFallFlying = 0;
    }

    private void flush() {
        while (!delayed.isEmpty()) {
            PacketHook.sendSilently(delayed.poll());
        }
    }

    private void reset(boolean clearQueue) {
        intercepting = false;
        shouldStartFallFlying = false;
        ticksUntilFallFlying = 0;
        if (clearQueue) delayed.clear();
    }
}
