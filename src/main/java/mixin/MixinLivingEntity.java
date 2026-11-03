package mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.carnation.Client;
import wtf.carnation.event.impl.movement.MoveEvent;
import wtf.carnation.event.impl.movement.StrafeEvent;
import wtf.carnation.module.Module;

/** Movement hooks: strafe vector and velocity replacement. */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

    @Shadow
    public float xxa;

    @Shadow
    public float zza;

    @Unique
    private boolean carnation$movementHook;

    protected MixinLivingEntity(net.minecraft.world.entity.EntityType<?> type, net.minecraft.world.level.Level level) {
        super(type, level);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void carnation$travel(Vec3 input, CallbackInfo ci) {
        if (Client.instance == null) return;
        if (!((Object) this instanceof LocalPlayer player)) return;
        if (this.carnation$movementHook) return;
        this.carnation$movementHook = true;
        try {
            StrafeEvent strafe = new StrafeEvent(this.xxa, this.zza, 0.0F);
            Client.instance.getEventManager().call(strafe);
            this.xxa = strafe.getForward();
            this.zza = strafe.getStrafe();

            MoveEvent move = new MoveEvent(player.getDeltaMovement());
            Client.instance.getEventManager().call(move);
            if (move.isModified()) {
                player.setDeltaMovement(move.getVector());
                ci.cancel();
            }
        } finally {
            this.carnation$movementHook = false;
        }
    }

    /** AntiBlindness / AntiNausea: the client never sees these two effects. */
    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void carnation$hasEffect(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        if (Client.instance == null) return;
        if (!((Object) this instanceof LocalPlayer)) return;
        if (effect == MobEffects.BLINDNESS && enabled("AntiBlindness")) {
            cir.setReturnValue(false);
        } else if (effect == MobEffects.NAUSEA && enabled("AntiNausea")) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private static boolean enabled(String name) {
        Module module = Client.instance.getModuleManager().getModuleByName(name);
        return module != null && module.isEnabled();
    }
}
