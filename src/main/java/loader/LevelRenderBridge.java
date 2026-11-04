package loader;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import wtf.carnation.Client;
import wtf.carnation.event.impl.render.Render3DEvent;

/**
 * Bridges Fabric's level render stages into the client's own event bus.
 *
 * <p>Registered from {@link CarnationClient} instead of a mixin: 26.3's
 * {@code LevelRenderer.renderLevel} tail no longer owns a usable pose stack.</p>
 *
 * <p>The stage matters. {@code AFTER_TRANSLUCENT_FEATURES} fires <em>after</em>
 * {@code FeatureRenderDispatcher.prepareFrame} has already frozen the frame's node list, so geometry
 * submitted there is silently dropped. {@code COLLECT_SUBMITS} runs at the end of
 * {@code LevelRenderer.submitFeatures} — after the level pose stack is created but before the frame
 * is prepared — which is the window world-space modules need.</p>
 */
public final class LevelRenderBridge implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Client client = Client.instance;
            if (client == null) return;
            var state = context.levelState();
            var camera = state == null ? null : state.cameraRenderState;
            var poseStack = context.poseStack();
            var collector = context.submitNodeCollector();
            if (camera == null || camera.pos == null || poseStack == null || collector == null) return;
            client.getEventManager().call(new Render3DEvent(poseStack, collector, camera.pos,
                    state.worldPartialTicks));
        });
    }
}
