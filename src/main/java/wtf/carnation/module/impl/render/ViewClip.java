package wtf.carnation.module.impl.render;

import net.minecraft.client.CameraType;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.SmoothAnimationTimer;

/** Port of Naven's {@code ViewClip}. */
public class ViewClip extends Module {
    public final NumberValue scale = new NumberValue("Scale", 1.0, 0.5, 2.0, 0.01);
    public final BoolValue animation = new BoolValue("Animation", true);
    public final NumberValue animationSpeed = new NumberValue("Animation Speed", 0.3, 0.01, 0.5, 0.01);
    public final SmoothAnimationTimer personViewAnimation = new SmoothAnimationTimer(100.0F);
    CameraType lastPersonView;

    public ViewClip() {
        super("ViewClip", Category.RENDER, "Allows you to see through blocks");
    }

    @EventTarget
    public void onRender(Render2DEvent e) {
        if (this.lastPersonView != mc.options.getCameraType()) {
            this.lastPersonView = mc.options.getCameraType();
            if (this.lastPersonView == CameraType.FIRST_PERSON
                    || this.lastPersonView == CameraType.THIRD_PERSON_BACK) {
                this.personViewAnimation.value = 0.0F;
            }
        }
        this.personViewAnimation.speed = this.animationSpeed.getFloat();
        this.personViewAnimation.update(true);
    }
}
