package wtf.carnation.module.impl.movement;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Holds the movement packets back so the server sees the player standing still. */
public class Blink extends Module {
    private final NumberValue maxTicks = new NumberValue("Max Ticks", 200, 10, 500, 10);
    private final NumberValue releaseSpeed = new NumberValue("Release Speed (Tick)", 10, 3, 20, 1);

    private final Queue<Packet<?>> packets = new ConcurrentLinkedQueue<>();
    private int ticks;

    public Blink() {
        super("Blink", Category.MOVEMENT, "延迟发送移动包，实现瞬移效果");
    }

    @Override
    protected void onDisable() {
        release();
        this.ticks = 0;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isSend() || mc.player == null) return;
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket)) return;
        if (this.ticks >= this.maxTicks.getInt()) return;
        event.setCancelled(true);
        this.packets.add(event.getPacket());
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null) return;
        this.ticks++;
        if (this.ticks % this.releaseSpeed.getInt() != 0) return;
        releaseOneTick();
    }

    private void releaseOneTick() {
        while (!packets.isEmpty()) {
            Packet<?> packet = packets.poll();
            if (packet == null) return;
            if (mc.player != null && mc.player.connection != null) {
                mc.player.connection.send(packet);
            }
            if (packet instanceof ServerboundMovePlayerPacket) return;
        }
    }

    private void release() {
        while (!packets.isEmpty()) {
            Packet<?> packet = packets.poll();
            if (packet == null) return;
            if (mc.player != null && mc.player.connection != null) {
                mc.player.connection.send(packet);
            }
        }
    }
}
