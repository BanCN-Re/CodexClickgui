package wtf.carnation.module.impl.misc;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.PearlPhysicsUtil;
import wtf.carnation.utils.ProjectionUtils;
import wtf.carnation.utils.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Port of Naven's {@code PearlPrediction}. */
public class PearlPrediction extends Module {
    public static Vector2f rotations;
    public static boolean isThrowing;

    private final NumberValue scale = new NumberValue("Scale", 0.35, 0.1, 1.0, 0.01);
    private final Map<Integer, PredictedPearlInfo> predictedPearls = new HashMap<>();
    private final Set<UUID> selfPearls = new HashSet<>();
    private static final long SELF_PEARL_WINDOW_MS = 800L;
    private long pendingSelfPearlTime = 0L;
    private Vec3 pendingSelfPearlPos = null;
    private long lastHeldPearlTime = 0L;
    private Vec3 lastHeldPearlPos = null;

    public PearlPrediction() {
        super("PearlPrediction", Category.MISC, "Renders prediction and can automatically counter thrown ender pearls.");
    }

    @Override
    protected void onEnable() {
        this.reset();
    }

    @Override
    protected void onDisable() {
        this.predictedPearls.values().forEach(PredictedPearlInfo::resetAnimation);
        this.predictedPearls.clear();
        this.reset();
    }

    @EventTarget
    public void onTick(RunTickEvent event) {
        if (!event.isPre() || mc.player == null || mc.level == null) return;
        this.updateLastHeldPearl();
        this.updatePearlLogicStates();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || this.predictedPearls.isEmpty()) return;
        UiFont.useGuiScale();
        for (PredictedPearlInfo info : this.predictedPearls.values()) {
            info.calculateAnimation(event.partialTicks());
            if (info.finalWidth > 0.1F && info.finalHeight > 0.1F) this.renderLandingMark(event.graphics(), info);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isSend() || mc.player == null) return;
        if (event.getPacket() instanceof ServerboundUseItemPacket packet) {
            InteractionHand hand = packet.hand();
            if (hand != null) this.recordSelfPearlThrow(hand);
        }
    }

    private void updateLastHeldPearl() {
        if (mc.player.getMainHandItem().is(Items.ENDER_PEARL) || mc.player.getOffhandItem().is(Items.ENDER_PEARL)) {
            this.lastHeldPearlTime = System.currentTimeMillis();
            this.lastHeldPearlPos = mc.player.position();
        }
    }

    private void recordSelfPearlThrow(InteractionHand hand) {
        if (!mc.player.getItemInHand(hand).is(Items.ENDER_PEARL)) return;
        this.pendingSelfPearlTime = System.currentTimeMillis();
        this.pendingSelfPearlPos = mc.player.position();
    }

    private boolean isSelfPearl(ThrownEnderpearl pearl) {
        if (Objects.equals(pearl.getOwner(), mc.player)) return true;
        if (this.selfPearls.contains(pearl.getUUID())) return true;

        long now = System.currentTimeMillis();
        if (this.pendingSelfPearlTime == 0L || this.pendingSelfPearlPos == null) {
            if (this.lastHeldPearlTime == 0L || this.lastHeldPearlPos == null) return false;
            if (now - this.lastHeldPearlTime > SELF_PEARL_WINDOW_MS) {
                this.lastHeldPearlTime = 0L;
                this.lastHeldPearlPos = null;
                return false;
            }
            if (pearl.position().distanceTo(this.lastHeldPearlPos) > 4.0) return false;
            this.selfPearls.add(pearl.getUUID());
            return true;
        }
        if (now - this.pendingSelfPearlTime > SELF_PEARL_WINDOW_MS) {
            this.pendingSelfPearlTime = 0L;
            this.pendingSelfPearlPos = null;
            return false;
        }
        if (pearl.position().distanceTo(this.pendingSelfPearlPos) > 4.0) return false;
        this.selfPearls.add(pearl.getUUID());
        return true;
    }

    private void updatePearlLogicStates() {
        Set<Integer> visible = new HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ThrownEnderpearl pearl)) continue;
            int pearlId = pearl.getId();
            visible.add(pearlId);
            PredictedPearlInfo info = this.predictedPearls.get(pearlId);
            if (info == null) {
                this.isSelfPearl(pearl);
                this.predictedPearls.put(pearlId, new PredictedPearlInfo(pearl));
            } else {
                info.refreshCountdown(pearl);
            }
        }

        List<Integer> toRemove = new ArrayList<>();
        for (Integer pearlId : this.predictedPearls.keySet()) {
            if (!visible.contains(pearlId)) toRemove.add(pearlId);
        }
        for (Integer pearlId : toRemove) {
            PredictedPearlInfo info = this.predictedPearls.remove(pearlId);
            if (info != null) this.selfPearls.remove(info.pearlUuid);
        }
        if (this.selfPearls.size() > 100) this.selfPearls.clear();
    }

    private void reset() {
        rotations = null;
        isThrowing = false;
        this.pendingSelfPearlTime = 0L;
        this.pendingSelfPearlPos = null;
        this.lastHeldPearlTime = 0L;
        this.lastHeldPearlPos = null;
        if (this.selfPearls.size() > 50) this.selfPearls.clear();
    }

    private void renderLandingMark(GuiGraphicsExtractor graphics, PredictedPearlInfo info) {
        Render2D.rect(graphics, info.finalX, info.finalY, info.finalWidth, info.finalHeight, 0x37000000);

        float textY = info.finalY + 2.0F;
        float rowHeight = UiFont.Z_SMALL.size() + 2.0F;
        for (String line : info.lines) {
            float textXOffset = (info.finalWidth - UiFont.Z_SMALL.width(line)) / 2.0F;
            UiFont.Z_SMALL.draw(graphics, line, info.finalX + textXOffset, textY, 0xFFFFFFFF);
            textY += rowHeight;
        }
    }

    private final class PredictedPearlInfo {
        private final UUID pearlUuid;
        private final Vec3 landingPos;
        private final int launchTicks;
        private final int startTickCount;
        private final String ownerName;
        private int ticksToLand;
        private Vector2f projectedPos;
        private final List<String> lines = new ArrayList<>();
        private float totalTextHeight;
        private float finalWidth;
        private float finalHeight;
        private float finalX;
        private float finalY;

        private PredictedPearlInfo(ThrownEnderpearl pearl) {
            this.pearlUuid = pearl.getUUID();
            this.startTickCount = pearl.tickCount;

            Object[] data = PearlPhysicsUtil.predictPearlLandingWithTicks(pearl, mc.level);
            this.landingPos = (Vec3) data[0];
            this.launchTicks = (Integer) data[1];
            this.ticksToLand = this.launchTicks;

            Entity owner = pearl.getOwner();
            this.ownerName = owner != null ? owner.getName().getString()
                    : (mc.player != null && selfPearls.contains(this.pearlUuid)
                    ? mc.player.getName().getString() : "Unknown");
        }

        private void refreshCountdown(ThrownEnderpearl pearl) {
            int elapsed = Math.max(0, pearl.tickCount - this.startTickCount);
            this.ticksToLand = Math.max(0, this.launchTicks - elapsed);
        }

        private void calculateAnimation(float partialTick) {
            if (this.landingPos == null) {
                this.finalWidth = 0.0F;
                this.finalHeight = 0.0F;
                return;
            }
            this.projectedPos = ProjectionUtils.project(this.landingPos.x, this.landingPos.y,
                    this.landingPos.z, partialTick);
            if (this.projectedPos == null || this.projectedPos.x == Float.MAX_VALUE
                    || this.projectedPos.y == Float.MAX_VALUE) {
                this.finalWidth = 0.0F;
                this.finalHeight = 0.0F;
                return;
            }

            this.lines.clear();
            this.lines.add("Thrown by: " + this.ownerName);
            this.lines.add(String.format("Lands in: %.1f s", this.ticksToLand / 20.0));
            this.lines.add(String.format("Distance: %.1f m",
                    mc.player == null ? 0.0 : mc.player.position().distanceTo(this.landingPos)));

            float textHeight = UiFont.Z_SMALL.size() + 2.0F;
            this.totalTextHeight = textHeight * (this.lines.size() - 1) + textHeight;

            float width = 0.0F;
            for (String line : this.lines) width = Math.max(width, UiFont.Z_SMALL.width(line));

            this.finalWidth = width + 8.0F;
            this.finalHeight = this.totalTextHeight + 4.0F;
            this.finalX = this.projectedPos.x - this.finalWidth / 2.0F;
            this.finalY = this.projectedPos.y - this.finalHeight / 2.0F;
        }

        private void resetAnimation() {
            this.finalWidth = 0.0F;
            this.finalHeight = 0.0F;
        }
    }
}
