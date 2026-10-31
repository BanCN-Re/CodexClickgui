package wtf.carnation.wallpaper;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The single pipeline the title-screen wallpaper needs: a textured quad drawn through the vanilla
 * GUI stack (position + uv + colour, translucent blending, clamp sampler).
 */
public final class WallpaperPipelines {
    public static final RenderPipeline QUAD_TRANSLUCENT = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("carnation", "pipeline/wallpaper_quad"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();

    private WallpaperPipelines() {
    }
}
