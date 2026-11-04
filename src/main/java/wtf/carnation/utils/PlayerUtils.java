package wtf.carnation.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Subset of Naven's {@code tech.naven.utils.PlayerUtils}. */
public final class PlayerUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    private PlayerUtils() {
    }

    public static boolean movementInput() {
        return mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
    }

    public static int getMoveSpeedEffectAmplifier() {
        if (mc.player == null) return 0;
        return mc.player.hasEffect(MobEffects.SPEED)
                ? mc.player.getEffect(MobEffects.SPEED).getAmplifier() + 1 : 0;
    }

    public static Vec3 getVectorForRotation(Vector2f rotation) {
        float yawCos = (float) Math.cos(-rotation.getX() * (float) (Math.PI / 180.0) - (float) Math.PI);
        float yawSin = (float) Math.sin(-rotation.getX() * (float) (Math.PI / 180.0) - (float) Math.PI);
        float pitchCos = (float) (-Math.cos(-rotation.getY() * (float) (Math.PI / 180.0)));
        float pitchSin = (float) Math.sin(-rotation.getY() * (float) (Math.PI / 180.0));
        return new Vec3(yawSin * pitchCos, pitchSin, yawCos * pitchCos);
    }

    public static HitResult pickCustom(double blockReachDistance, float yaw, float pitch) {
        if (mc.player == null || mc.level == null) return null;
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 direction = getVectorForRotation(new Vector2f(yaw, pitch));
        Vec3 end = eye.add(direction.x * blockReachDistance, direction.y * blockReachDistance, direction.z * blockReachDistance);
        return mc.level.clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
    }
}
