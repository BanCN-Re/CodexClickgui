package wtf.carnation.graphics;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;

/**
 * A triangle list submitted to the GUI render state. {@code positions} holds x/y pairs and
 * {@code colors} one ARGB value per vertex; every three vertices form a triangle.
 */
public record ShapeRenderState(
        RenderPipeline pipeline,
        Matrix3x2fc pose,
        float[] positions,
        int[] colors,
        int vertexCount,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState {

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (int i = 0; i < vertexCount; i++) {
            consumer.addVertexWith2DPose(pose, positions[i * 2], positions[i * 2 + 1]).setColor(colors[i]);
        }
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}
