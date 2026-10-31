package wtf.carnation.wallpaper;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;

/** A textured quad submitted to the vanilla GUI render state: the wallpaper frame. */
final class WallpaperQuads {
    private WallpaperQuads() {
    }

    record TexQuad(RenderPipeline pipeline, Matrix3x2fc pose, TextureSetup textures,
                   float x0, float y0, float x1, float y1,
                   float u0, float v0, float u1, float v1,
                   ScreenRectangle scissorArea) implements GuiElementRenderState {

        @Override
        public void buildVertices(VertexConsumer consumer) {
            vertex(consumer, x0, y0, u0, v0);
            vertex(consumer, x1, y0, u1, v0);
            vertex(consumer, x1, y1, u1, v1);
            vertex(consumer, x0, y0, u0, v0);
            vertex(consumer, x1, y1, u1, v1);
            vertex(consumer, x0, y1, u0, v1);
        }

        private void vertex(VertexConsumer consumer, float x, float y, float u, float v) {
            float px = pose.m00() * x + pose.m10() * y + pose.m20();
            float py = pose.m01() * x + pose.m11() * y + pose.m21();
            consumer.addVertex(px, py, 0.0f).setColor(0xFFFFFFFF).setUv(u, v);
        }

        @Override
        public TextureSetup textureSetup() {
            return textures;
        }

        @Override
        public ScreenRectangle bounds() {
            return rect(x0, y0, x1, y1);
        }
    }

    private static ScreenRectangle rect(float x0, float y0, float x1, float y1) {
        int left = (int) Math.floor(Math.min(x0, x1)) - 1;
        int top = (int) Math.floor(Math.min(y0, y1)) - 1;
        int width = (int) Math.ceil(Math.max(x0, x1)) + 1 - left;
        int height = (int) Math.ceil(Math.max(y0, y1)) + 1 - top;
        return new ScreenRectangle(left, top, width, height);
    }
}
