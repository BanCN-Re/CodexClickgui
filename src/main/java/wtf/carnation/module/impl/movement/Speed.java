package wtf.carnation.module.impl.movement;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MoveInputEvent;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.MoveUtil;

public class Speed extends Module {
    private final ModeValue mode = new ModeValue("Mode", new String[]{"Collision"}, "Collision");
    private final NumberValue hitboxCount = new NumberValue("Collision Hitbox Count", 10.0, 1.0, 20.0, 1.0);
    private final NumberValue boost = new NumberValue("Collision Boost Velocity", 0.08, 0.01, 0.5, 0.01);
    private final BoolValue autoJump = new BoolValue("Auto Jump", true);

    private double lastX;
    private double lastZ;
    private long lastTime;
    private double bps;

    public Speed() {
        super("Speed", Category.MOVEMENT, "借助身边实体的碰撞箱额外加速");
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            lastX = mc.player.getX();
            lastZ = mc.player.getZ();
        }
        lastTime = System.currentTimeMillis();
    }

    @EventTarget
    public void onRunTick(RunTickEvent event) {
        if (!event.isPre() || mc.player == null || mc.level == null) return;
        updateBps();
        setSuffix(String.format("%.2f", bps));

        if (!mode.is("Collision") || !MoveUtil.isMoving()) return;
        if (mc.player.isInWater() || mc.player.isInLava()) return;

        int collisions = 0;
        AABB playerBox = mc.player.getBoundingBox();
        double shrink = hitboxCount.get() / 10.0;
        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class,
                playerBox.inflate(2.0), e -> e != mc.player)) {
            if (playerBox.intersects(entity.getBoundingBox().deflate(shrink, 0.0, shrink))) {
                collisions++;
            }
        }
        if (collisions > 0) {
            double yaw = Math.toRadians(mc.player.getYRot());
            double x = -Math.sin(yaw);
            double z = Math.cos(yaw);
            double multiplier = boost.get() * collisions;
            mc.player.setDeltaMovement(
                    mc.player.getDeltaMovement().x + x * multiplier,
                    mc.player.getDeltaMovement().y,
                    mc.player.getDeltaMovement().z + z * multiplier);
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!autoJump.get() || mc.player == null) return;
        if (mc.player.onGround() && MoveUtil.isMoving()) {
            event.setJump(true);
        }
    }

    private void updateBps() {
        long now = System.currentTimeMillis();
        long delta = now - lastTime;
        if (delta <= 0) return;
        double dx = mc.player.getX() - lastX;
        double dz = mc.player.getZ() - lastZ;
        bps = Math.sqrt(dx * dx + dz * dz) / (delta / 1000.0);
        lastX = mc.player.getX();
        lastZ = mc.player.getZ();
        lastTime = now;
    }
}
