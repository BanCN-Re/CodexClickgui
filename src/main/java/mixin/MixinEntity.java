package mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.carnation.Client;
import wtf.carnation.event.impl.combat.EventRayTrace;
import wtf.carnation.event.impl.movement.StuckInBlockEvent;

/** Stuck-in-block speed hook, plus the silent-aim raycast override. */
@Mixin(Entity.class)
public class MixinEntity {

    @ModifyVariable(method = "makeStuckInBlock", at = @At("HEAD"), argsOnly = true, index = 2)
    private Vec3 carnation$stuckMultiplier(Vec3 multiplier) {
        if (Client.instance == null) return multiplier;
        if (!((Object) this instanceof LocalPlayer)) return multiplier;
        BlockState state = ((Entity) (Object) this).getBlockStateOn();
        StuckInBlockEvent event = new StuckInBlockEvent(state, multiplier);
        Client.instance.getEventManager().call(event);
        return event.getMultiplier();
    }

    /**
     * Naven's silent rotation lives here: {@code getViewVector} is what {@code Entity#pick},
     * block placement and item use all call, so overriding it aims those without moving the
     * player's own yaw/pitch (the camera).
     */
    @Inject(method = "getViewVector", at = @At("RETURN"), cancellable = true)
    private void carnation$viewVector(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if (Client.instance == null) return;
        Entity self = (Entity) (Object) this;
        if (self != Minecraft.getInstance().player) return;
        EventRayTrace event = new EventRayTrace(self, self.getViewYRot(partialTicks),
                self.getViewXRot(partialTicks));
        Client.instance.getEventManager().call(event);
        if (event.isModified()) {
            cir.setReturnValue(Entity.calculateViewVector(event.getPitch(), event.getYaw()));
        }
    }
}
