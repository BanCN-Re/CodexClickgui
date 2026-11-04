package wtf.carnation.event.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.api.Event;

/**
 * Fired once per frame for modules that draw in world space.
 *
 * <p>The 26.3 equivalent of Naven's {@code EventRender}. Note the coordinate convention: 26.3
 * submits level geometry with an <em>identity</em> pose stack and camera-relative vertex positions
 * ({@code LevelRenderer.submitEntities} only translates by {@code entityPos - cameraPos}; the view
 * rotation is applied later as a uniform, not baked into the pose). So callers subtract
 * {@link #camera} from world coordinates and must not assume the pose carries a rotation.</p>
 *
 * @param poseStack the level pose stack, identity at the point this event fires
 * @param collector the frame's submit node collector
 * @param camera    interpolated camera position in world space
 * @param partialTicks the frame's partial tick, for entity interpolation
 */
public record Render3DEvent(PoseStack poseStack, SubmitNodeCollector collector,
                            Vec3 camera, float partialTicks) implements Event {
}
