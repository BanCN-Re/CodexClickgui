package wtf.carnation.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MoveInputEvent;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import wtf.carnation.event.impl.combat.EventRayTrace;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.ModuleManager;
import wtf.carnation.module.impl.combat.AimAssist;
import wtf.carnation.module.impl.combat.AttackCrystal;
import wtf.carnation.module.impl.combat.Aura;
import wtf.carnation.module.impl.combat.AutoThrow;
import wtf.carnation.module.impl.misc.Helper;
import wtf.carnation.module.impl.movement.AutoMLG;
import wtf.carnation.module.impl.movement.LongJump;
import wtf.carnation.module.impl.movement.Scaffold;


/**
 * Holds the rotation the server should see and applies it to the player and the outbound
 * move packets, the way the original rotation manager does.
 */
public final class RotationManager {

    public static final RotationManager INSTANCE = new RotationManager();

    private static Vector2f rotations;
    private static Vector2f lastRotations;
    private static boolean active;

    private RotationManager() {
    }

    public static void setRotations(Vector2f value) {
        rotations = value;
    }

    public static void setRotations(float yaw, float pitch) {
        setRotations(new Vector2f(yaw, pitch));
    }

    public static Vector2f lastRotations() {
        return lastRotations;
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

    /**
     * Bookkeeping only. The player's own yaw/pitch are never written here: that would drag the
     * camera around and make every "silent" aim module visible. The aim reaches the server through
     * the packet rewrite below and reaches the crosshair through {@link EventRayTrace}.
     */
    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre()) return;
        lastRotations = rotations;
    }

    /** Silent aim: substitute the rotation for the game's own view-vector raycast. */
    @EventTarget
    public void onRayTrace(EventRayTrace event) {
        if (!active || rotations == null) return;
        if (event.getEntity() != Minecraft.getInstance().player) return;
        event.setYaw(rotations.x);
        event.setPitch(rotations.y);
    }

    /**
     * Per-tick arbitration, ported from Naven's {@code updateGlobalYaw}: whichever module owns the
     * aim this tick publishes its rotation here. Without it the combat modules compute a rotation
     * that nothing ever applies, so the crosshair never lands on the target and the attack packet
     * never leaves.
     */
    @EventTarget
    public void onRunTick(RunTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isPre() || mc.player == null || Client.instance == null) return;
        ModuleManager modules = Client.instance.getModuleManager();

        Aura aura = modules.getModule(Aura.class);
        AttackCrystal crystal = modules.getModule(AttackCrystal.class);
        AutoMLG autoMlg = modules.getModule(AutoMLG.class);
        LongJump longJump = modules.getModule(LongJump.class);
        Scaffold scaffold = modules.getModule(Scaffold.class);
        Helper helper = modules.getModule(Helper.class);
        AutoThrow autoThrow = modules.getModule(AutoThrow.class);
        AimAssist aimAssist = modules.getModule(AimAssist.class);

        active = true;
        if (autoMlg != null && autoMlg.isEnabled() && autoMlg.rotation) {
            setRotations(new Vector2f(mc.player.getYRot(), 90.0F));
        } else if (longJump != null && longJump.isEnabled() && LongJump.rotation != null) {
            setRotations(LongJump.rotation.toVector());
        } else if (crystal != null && crystal.isEnabled() && AttackCrystal.rotations != null) {
            setRotations(AttackCrystal.rotations);
        } else if (scaffold != null && scaffold.isEnabled() && scaffold.rots != null) {
            setRotations(scaffold.rots);
        } else if (helper != null && helper.isEnabled() && helper.rotation != null) {
            setRotations(helper.rotation);
        } else if (autoThrow != null && autoThrow.isEnabled() && autoThrow.rotation != null) {
            setRotations(autoThrow.rotation);
        } else if (aura != null && aura.isEnabled() && Aura.target != null && Aura.rotation != null) {
            setRotations(Aura.rotation);
        } else if (aimAssist == null || !aimAssist.isEnabled() || !aimAssist.working) {
            active = false;
        } else if (aimAssist.silentAim()) {
            setRotations(aimAssist.targetRotation);
        }
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

    /** Rewrites the rotation in every outbound packet that carries one. */
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isSend() || rotations == null || !active) return;
        if (event.getPacket() instanceof ServerboundMovePlayerPacket.PosRot posRot) {
            event.setPacket(new ServerboundMovePlayerPacket.PosRot(
                    posRot.getX(0.0), posRot.getY(0.0), posRot.getZ(0.0),
                    rotations.x, rotations.y, posRot.isOnGround(), posRot.horizontalCollision()));
        } else if (event.getPacket() instanceof ServerboundMovePlayerPacket.Rot rot) {
            event.setPacket(new ServerboundMovePlayerPacket.Rot(
                    rotations.x, rotations.y, rot.isOnGround(), rot.horizontalCollision()));
        } else if (event.getPacket() instanceof ServerboundUseItemPacket useItem) {
            event.setPacket(new ServerboundUseItemPacket(useItem.hand(), useItem.sequence(),
                    rotations.x, rotations.y));
        }
    }
}
