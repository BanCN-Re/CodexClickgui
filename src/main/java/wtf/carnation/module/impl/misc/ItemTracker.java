package wtf.carnation.module.impl.misc;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.utils.EntityWatcher;
import wtf.carnation.utils.MathUtils;
import wtf.carnation.utils.ProjectionUtils;
import wtf.carnation.utils.SharedESPData;
import wtf.carnation.utils.Vector2f;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Port of Naven's {@code ItemTracker} (display name "EffectTags").
 *
 * <p>Positions are refreshed from the 2D event (this client has no 3D render event) and text is
 * drawn with {@link UiFont}.</p>
 */
public class ItemTracker extends Module {
    private final BoolValue debug = new BoolValue("Debug", false);
    private final BoolValue shared = new BoolValue("Shared", true);
    private final List<TargetInfo> entityPositions = new CopyOnWriteArrayList<>();

    public ItemTracker() {
        super("EffectTags", Category.MISC, "Show the player's effect tags.");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;
        float partialTicks = event.partialTicks();
        this.updatePositions(partialTicks);

        GuiGraphicsExtractor graphics = event.graphics();
        UiFont.useGuiScale();
        for (TargetInfo info : this.entityPositions) {
            double y = 0.0;
            for (String tag : info.description) {
                String text = Component.translatable(tag).getString();
                UiFont.Z_SMALL.draw(graphics, text, info.position.x + 10.0F, (float) (info.position.y + y), 0xFFFF5555);
                y += 10.0;
            }
            if (this.debug.get() && info.player != null) {
                AbstractClientPlayer player = info.player;
                LinkedHashSet<String> debugInfos = new LinkedHashSet<>();
                debugInfos.add("X: " + player.getX());
                debugInfos.add("Y: " + player.getY());
                debugInfos.add("Z: " + player.getZ());
                debugInfos.add("Ticks: " + player.tickCount);
                for (String debugInfo : debugInfos) {
                    UiFont.Z_SMALL.draw(graphics, debugInfo, info.position.x + 10.0F,
                            (float) (info.position.y + y), 0xFFFF5555);
                    y += 10.0;
                }
            }
        }
    }

    private static boolean isValidScreenPos(Vector2f pos) {
        if (pos == null) return false;
        float x = pos.getX();
        float y = pos.getY();
        return !Float.isNaN(x) && !Float.isInfinite(x) && !Float.isNaN(y) && !Float.isInfinite(y)
                && Math.abs(x) < 1.0E6F && Math.abs(y) < 1.0E6F;
    }

    private void updatePositions(float renderPartialTicks) {
        this.entityPositions.clear();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !(entity instanceof AbstractClientPlayer player)) continue;
            double x = MathUtils.interpolate(renderPartialTicks, player.xo, player.getX());
            double y = MathUtils.interpolate(renderPartialTicks, player.yo, player.getY()) + player.getBbHeight();
            double z = MathUtils.interpolate(renderPartialTicks, player.zo, player.getZ());
            Vector2f vector = ProjectionUtils.project(x, y, z, renderPartialTicks);
            if (!isValidScreenPos(vector)) continue;
            this.entityPositions.add(new TargetInfo(player, vector,
                    EntityWatcher.getEntityTags(player)));
        }

        if (this.shared.get()) {
            Map<String, SharedESPData> dataMap = EntityWatcher.getSharedESPData();
            for (SharedESPData value : dataMap.values()) {
                double x = value.getPosX();
                double y = value.getPosY() + mc.player.getBbHeight();
                double z = value.getPosZ();
                Vector2f vector = ProjectionUtils.project(x, y, z, renderPartialTicks);
                if (!isValidScreenPos(vector)) continue;
                this.entityPositions.add(new TargetInfo(null, vector, Set.of(value.getTags())));
            }
        }
    }

    private record TargetInfo(AbstractClientPlayer player, Vector2f position, Set<String> description) {
    }
}
