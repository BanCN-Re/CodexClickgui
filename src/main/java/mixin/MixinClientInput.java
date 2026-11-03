package mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.Client;
import wtf.carnation.event.impl.movement.MoveInputEvent;

/** Rebuilds the movement input after vanilla, so modules can edit forward/strafe/jump/sneak. */
@Mixin(ClientInput.class)
public class MixinClientInput {

    @Shadow
    public Input keyPresses;

    @Shadow
    protected Vec2 moveVector;

    @Inject(method = "tick", at = @At("TAIL"))
    private void carnation$inputTick(CallbackInfo ci) {
        if (Client.instance == null) return;
        float forward = this.moveVector.y;
        float strafe = this.moveVector.x;
        MoveInputEvent event = new MoveInputEvent(forward, strafe,
                this.keyPresses.jump(), this.keyPresses.shift(), 0.3F);
        Client.instance.getEventManager().call(event);

        float newForward = event.getForward();
        float newStrafe = event.getStrafe();
        boolean jump = event.isJump();
        boolean sneak = event.isSneak();
        this.moveVector = new Vec2(newStrafe, newForward);
        this.keyPresses = new Input(
                newForward > 0.0F, newForward < 0.0F,
                newStrafe > 0.0F, newStrafe < 0.0F,
                jump, sneak, this.keyPresses.sprint());
    }
}
