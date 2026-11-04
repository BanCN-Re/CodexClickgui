package mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.carnation.Client;
import wtf.carnation.event.api.Phase;
import wtf.carnation.event.impl.combat.EventAttack;

/** Naven's MixinMultiPlayerGameMode: attack pre/post events for Aura, Criticals and AutoClicker. */
@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void carnation$attackPre(Player player, Entity target, CallbackInfo ci) {
        if (Client.instance == null) return;
        if (Client.instance.getEventManager().call(new EventAttack(target, Phase.PRE)).isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void carnation$attackPost(Player player, Entity target, CallbackInfo ci) {
        if (Client.instance == null) return;
        Client.instance.getEventManager().call(new EventAttack(target, Phase.POST));
    }
}
