package mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import wtf.carnation.Client;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.render.MotionBlur;
import wtf.carnation.module.impl.render.PostProcess;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the client's post effects to the frame's effect list.
 *
 * <p>The list passed to {@code preparePostEffects} is immutable, so the argument is replaced
 * with a mutable copy instead of being appended to.</p>
 */
@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @ModifyVariable(method = "preparePostEffects", at = @At("HEAD"), argsOnly = true, index = 1)
    private List<Identifier> carnation$preparePostEffects(List<Identifier> effects) {
        if (Client.instance == null) return effects;

        List<Identifier> result = null;
        Module post = Client.instance.getModuleManager().getModuleByName("PostProcess");
        if (post instanceof PostProcess process && process.isEnabled()) {
            result = new ArrayList<>(effects);
            result.add(process.effectId());
        }
        Module blur = Client.instance.getModuleManager().getModuleByName("MotionBlur");
        if (blur instanceof MotionBlur motionBlur && motionBlur.isEnabled() && motionBlur.active()) {
            if (result == null) result = new ArrayList<>(effects);
            result.add(motionBlur.effectId());
        }
        return result == null ? effects : result;
    }
}
