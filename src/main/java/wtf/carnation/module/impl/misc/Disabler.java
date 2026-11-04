package wtf.carnation.module.impl.misc;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import sun.misc.Unsafe;
import wtf.carnation.Client;
import wtf.carnation.event.EventPriority;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.utils.ChatUtil;
import wtf.carnation.utils.MathUtils;

import java.lang.reflect.Field;
import java.util.Random;

/**
 * Port of Naven's {@code Disabler}.
 *
 * <p>Duplicate-rotation placement plus the ACA aim-step / perfect-rotation packet rewrites.
 * The final packet fields are patched through {@link Unsafe} exactly like the original.</p>
 */
public class Disabler extends Module {
    private final BoolValue logging = new BoolValue("Logging", false);
    private final BoolValue acaaimstep = new BoolValue("ACAAimStep", false);
    private final BoolValue acaperfectrotation = new BoolValue("ACAPerfectRotation", false);
    private final BoolValue grimDuplicateRotPlace = new BoolValue("GrimDuplicateRotPlace", false);

    private float playerYaw;
    private float deltaYaw;
    private float lastPlacedDeltaYaw;
    private boolean rotated = false;
    private float lastYaw = 0.0F;
    private float lastPitch = 0.0F;
    private final Random random = new Random();

    private static final double[] PERFECT_PATTERNS = new double[]{0.1, 0.25};
    private static final Unsafe unsafe;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    public Disabler() {
        super("Disabler", Category.MISC, "Disables some checks of the anti cheat.");
    }

    private void log(String message) {
        if (this.logging.get()) ChatUtil.addChatMessage(message);
    }

    private float normalizeYaw(float yaw) {
        while (yaw > 180.0F) yaw -= 360.0F;
        while (yaw < -180.0F) yaw += 360.0F;
        return yaw;
    }

    private boolean shouldModifyRotation(float currentYaw, float currentPitch) {
        if (this.lastYaw == 0.0F && this.lastPitch == 0.0F) return false;
        double yawDelta = Math.abs(this.normalizeYaw(currentYaw - this.lastYaw));
        double pitchDelta = Math.abs(currentPitch - this.lastPitch);
        return yawDelta < 1.0E-5 && pitchDelta > 1.0 || pitchDelta < 1.0E-5 && yawDelta > 1.0;
    }

    private float[] getModifiedRotation(float yaw, float pitch) {
        double yawDelta = Math.abs(this.normalizeYaw(yaw - this.lastYaw));
        double pitchDelta = Math.abs(pitch - this.lastPitch);
        float newYaw = yaw;
        float newPitch = pitch;
        if (yawDelta < 1.0E-5 && pitchDelta > 1.0) newYaw = this.lastYaw + (float) (this.random.nextGaussian() * 0.001);
        if (pitchDelta < 1.0E-5 && yawDelta > 1.0) newPitch = this.lastPitch + (float) (this.random.nextGaussian() * 0.001);
        return new float[]{newYaw, newPitch};
    }

    private float[] getAntiPerfectRotation(float yaw, float pitch) {
        if (this.lastYaw == 0.0F && this.lastPitch == 0.0F) return new float[]{yaw, pitch};
        double yawDelta = Math.abs(this.normalizeYaw(yaw - this.lastYaw));
        double pitchDelta = Math.abs(pitch - this.lastPitch);
        float newYaw = yaw;
        float newPitch = pitch;
        if (!this.isNoRotation(yawDelta) && this.isPerfectPattern(yawDelta)) {
            newYaw = yaw + (float) (this.random.nextGaussian() * 0.005);
        }
        if (!this.isNoRotation(pitchDelta) && this.isPerfectPattern(pitchDelta)) {
            newPitch = pitch + (float) (this.random.nextGaussian() * 0.005);
        }
        return new float[]{newYaw, newPitch};
    }

    private boolean isNoRotation(double rotation) {
        return Math.abs(rotation) <= 1.0E-10 || this.isIntegerMultiple(360.0, rotation);
    }

    private boolean isPerfectPattern(double rotation) {
        if (Double.isInfinite(rotation) || Double.isNaN(rotation)) return false;
        for (double pattern : PERFECT_PATTERNS) {
            if (this.isIntegerMultiple(pattern, rotation)) return true;
        }
        return false;
    }

    private boolean isIntegerMultiple(double reference, double value) {
        if (reference == 0.0) return Math.abs(value) <= 1.0E-10;
        double multiple = value / reference;
        return Math.abs(multiple - Math.round(multiple)) <= 1.0E-10;
    }

    public static float getPacketYRot(ServerboundMovePlayerPacket packet) {
        return packet.getYRot(0.0F);
    }

    public static float getPacketXRot(ServerboundMovePlayerPacket packet) {
        return packet.getXRot(0.0F);
    }

    private static Field findField(Class<?> clazz, String... fieldNames) {
        if (clazz == null || fieldNames == null || fieldNames.length == 0) {
            throw new IllegalArgumentException("Class and fieldNames must not be null or empty");
        }
        Exception failed = null;
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            for (String fieldName : fieldNames) {
                if (fieldName == null) continue;
                try {
                    Field f = current.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    if ((f.getModifiers() & 16) != 0) {
                        unsafe.putInt(f, unsafe.arrayBaseOffset(boolean[].class), f.getModifiers() & -17);
                    }
                    return f;
                } catch (Exception exception) {
                    failed = exception;
                }
            }
        }
        throw new IllegalStateException(failed);
    }

    public static void setPacketYRot(ServerboundMovePlayerPacket packet, float yRot) {
        setFinalFloat(packet, "yRot", yRot);
    }

    public static void setPacketXRot(ServerboundMovePlayerPacket packet, float xRot) {
        setFinalFloat(packet, "xRot", xRot);
    }

    private static void setFinalFloat(ServerboundMovePlayerPacket packet, String fieldName, float value) {
        try {
            Field field = findField(packet.getClass(), fieldName);
            unsafe.putFloat(packet, unsafe.objectFieldOffset(field), value);
        } catch (Exception exception) {
            Client.logger.error("Failed to set {} field", fieldName, exception);
        }
    }

    @EventTarget
    @EventPriority(3)
    public void duplicateRotPlaceDisabler(PacketEvent e) {
        if (this.grimDuplicateRotPlace.get() && e.isSend() && !e.isCancelled() && mc.player != null) {
            if (e.getPacket() instanceof ServerboundMovePlayerPacket packet) {
                if (packet.hasRotation()) {
                    if (packet.getYRot(0.0F) < 360.0F && packet.getYRot(0.0F) > -360.0F) {
                        if (packet.hasPosition()) {
                            e.setPacket(new ServerboundMovePlayerPacket.PosRot(packet.getX(0.0), packet.getY(0.0),
                                    packet.getZ(0.0), packet.getYRot(0.0F) + 720.0F, packet.getXRot(0.0F),
                                    packet.isOnGround(), packet.horizontalCollision()));
                        } else {
                            e.setPacket(new ServerboundMovePlayerPacket.Rot(packet.getYRot(0.0F) + 720.0F,
                                    packet.getXRot(0.0F), packet.isOnGround(), packet.horizontalCollision()));
                        }
                    }
                    float lastPlayerYaw = this.playerYaw;
                    this.playerYaw = packet.getYRot(0.0F);
                    this.deltaYaw = Math.abs(this.playerYaw - lastPlayerYaw);
                    this.rotated = true;
                    if (this.deltaYaw > 2.0F) {
                        float xDiff = Math.abs(this.deltaYaw - this.lastPlacedDeltaYaw);
                        if (xDiff < 1.0E-4) {
                            this.log("Disabling DuplicateRotPlace!");
                            if (packet.hasPosition()) {
                                e.setPacket(new ServerboundMovePlayerPacket.PosRot(packet.getX(0.0), packet.getY(0.0),
                                        packet.getZ(0.0), packet.getYRot(0.0F) + 0.002F, packet.getXRot(0.0F),
                                        packet.isOnGround(), packet.horizontalCollision()));
                            } else {
                                e.setPacket(new ServerboundMovePlayerPacket.Rot(packet.getYRot(0.0F) + 0.002F,
                                        packet.getXRot(0.0F), packet.isOnGround(), packet.horizontalCollision()));
                            }
                        }
                    }
                }
            } else if (e.getPacket() instanceof ServerboundUseItemOnPacket && this.rotated) {
                this.lastPlacedDeltaYaw = this.deltaYaw;
                this.rotated = false;
            }
        }

        if ((this.acaaimstep.get() || this.acaperfectrotation.get())
                && e.getPacket() instanceof ServerboundMovePlayerPacket movePacket) {
            float currentYaw = getPacketYRot(movePacket);
            float currentPitch = getPacketXRot(movePacket);
            boolean modified = false;
            if (this.acaaimstep.get() && this.shouldModifyRotation(currentYaw, currentPitch)) {
                float[] rotation = this.getModifiedRotation(currentYaw, currentPitch);
                currentYaw = rotation[0];
                currentPitch = rotation[1];
                modified = true;
            }
            if (this.acaperfectrotation.get()) {
                float[] rotation = this.getAntiPerfectRotation(currentYaw, currentPitch);
                if (rotation[0] != currentYaw || rotation[1] != currentPitch) {
                    currentYaw = rotation[0];
                    currentPitch = rotation[1];
                    modified = true;
                    this.log("PerfectRotation: Modified rotation");
                }
            }
            if (modified) {
                setPacketYRot(movePacket, currentYaw);
                setPacketXRot(movePacket, MathUtils.clampPitch_To90(currentPitch));
            }
            this.lastYaw = getPacketYRot(movePacket);
            this.lastPitch = getPacketXRot(movePacket);
        }
    }
}
