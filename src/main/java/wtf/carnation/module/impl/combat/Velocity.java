package wtf.carnation.module.impl.combat;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.RespawnEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;

/** Reduces or cancels the knockback the server sends. */
public class Velocity extends Module {
    private final ModeValue mode = new ModeValue("Mode",
            new String[]{"Cancel", "Jump", "Horizontal"}, "Cancel");
    private final NumberValue horizontal = new NumberValue("Horizontal", 100, 0, 100, 5);
    private final BoolValue onlyWhenGround = new BoolValue("Only On Ground", false);

    private boolean jumpReset;
    private boolean handlingVelocity;

    public Velocity() {
        super("Velocity", Category.COMBAT, "取消或削弱服务器下发的击退");
    }

    @Override
    protected void onEnable() {
        this.handlingVelocity = false;
        this.jumpReset = false;
    }

    @Override
    protected void onDisable() {
        this.handlingVelocity = false;
        this.jumpReset = false;
    }

    @EventTarget
    public void onRespawn(RespawnEvent event) {
        this.handlingVelocity = false;
        this.jumpReset = false;
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null) return;
        if (this.jumpReset) {
            // Jumping right after the knockback neutralises it.
            mc.player.jumpFromGround();
            this.jumpReset = false;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.isSend() || mc.player == null) return;
        if (!(event.getPacket() instanceof ClientboundSetEntityMotionPacket motion)) return;
        if (motion.id() != mc.player.getId()) return;
        if (this.onlyWhenGround.get() && !mc.player.onGround()) return;

        switch (this.mode.get()) {
            case "Cancel" -> {
                event.setCancelled(true);
                this.handlingVelocity = true;
            }
            case "Jump" -> {
                event.setCancelled(true);
                this.jumpReset = true;
                this.handlingVelocity = true;
            }
            case "Horizontal" -> {
                double factor = this.horizontal.get() / 100.0;
                var current = mc.player.getDeltaMovement();
                event.setPacket(new ClientboundSetEntityMotionPacket(motion.id(),
                        new net.minecraft.world.phys.Vec3(
                                current.x * factor, current.y, current.z * factor)));
                this.handlingVelocity = true;
            }
            default -> {
            }
        }
    }

}
