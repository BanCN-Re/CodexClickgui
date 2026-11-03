package mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import wtf.carnation.Client;
import wtf.carnation.event.impl.movement.StuckInBlockEvent;

/** Stuck-in-block speed hook. The method is declared on Entity, not LivingEntity. */
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
}
