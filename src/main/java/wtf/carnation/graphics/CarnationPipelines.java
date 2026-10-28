package wtf.carnation.graphics;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Client render pipelines, built on renderpearl (the 26.x Blaze3D API).
 */
public final class CarnationPipelines {
    /**
     * The vanilla GUI snippet (position + colour, translucent blending, core/gui shader) with a
     * triangle list instead of quads, so arbitrary shapes such as rounded rectangles and circles
     * can be submitted straight into the GUI render state. Culling is off because the shape
     * builder emits triangles in either winding.
     */
    public static final RenderPipeline GUI_TRIANGLES = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("carnation", "pipeline/gui_triangles"))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private CarnationPipelines() {
    }
}
