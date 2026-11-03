package wtf.carnation.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MoveInputEvent;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.network.PacketEvent;

import java.lang.reflect.Field;

/**
 * Holds the rotation the server should see and applies it to the player and the outbound
 * move packets, the way the original rotation manager does.
 */
public final class RotationManager {

    public static final RotationManager INSTANCE = new RotationManager();

    private static Vector2f rotations;
    private static boolean active;

    private RotationManager() {
    }

    public static void setRotations(Vector2f value) {
        rotations = value;
    }

    public static void setRotations(float yaw, float pitch) {
        rotations = new Vector2f(yaw, pitch);
    }

    public static void clearRotations() {
        rotations = null;
        active = false;
    }

    public static Vector2f rotations() {
        return rotations;
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || rotations == null || !active) return;
        mc.player.setYRot(rotations.x);
        mc.player.setXRot(rotations.y);
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || rotations == null || !active) return;
        Vector2f fixed = RotationUtils.fixMovement(event.getForward(), event.getStrafe(),
                rotations.x, mc.player.getYRot());
        event.setForward(fixed.y);
        event.setStrafe(fixed.x);
    }

    /** Rewrites the rotation in the position packets so the server sees the same angles. */
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isSend() || rotations == null || !active) return;
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket.PosRot packet)) return;
        double x = read(packet, "x");
        double y = read(packet, "y");
        double z = read(packet, "z");
        boolean onGround = readBool(packet, "onGround");
        boolean collision = readBool(packet, "horizontalCollision");
        event.setPacket(new ServerboundMovePlayerPacket.PosRot(
                x, y, z, rotations.x, rotations.y, onGround, collision));
    }

    private static double read(Object target, String name) {
        Object value = field(target, name);
        return value instanceof Number n ? n.doubleValue() : 0;
    }

    private static boolean readBool(Object target, String name) {
        Object value = field(target, name);
        return value instanceof Boolean b && b;
    }

    private static Object field(Object target, String name) {
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
}
