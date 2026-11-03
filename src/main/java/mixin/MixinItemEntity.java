package mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.carnation.Client;
import wtf.carnation.module.Module;

/** ItemPhysics: stops dropped items from spinning. */
@Mixin(ItemEntity.class)
public class MixinItemEntity {

    @Inject(method = "getSpin", at = @At("HEAD"), cancellable = true)
    private static void carnation$spin(float partialTicks, float ageInTicks, CallbackInfoReturnable<Float> cir) {
        if (Client.instance == null) return;
        Module module = Client.instance.getModuleManager().getModuleByName("ItemPhysics");
        if (module != null && module.isEnabled()) {
            cir.setReturnValue(0.0F);
        }
    }
}
