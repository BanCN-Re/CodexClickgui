package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.input.MouseClickEvent;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.RespawnEvent;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.misc.Teams;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.BlinkingPlayer;
import wtf.carnation.utils.ChatUtil;
import wtf.carnation.utils.EntityWatcher;
import wtf.carnation.utils.FriendManager;
import wtf.carnation.utils.InventoryUtils;
import wtf.carnation.utils.MathUtils;
import wtf.carnation.utils.ProjectionUtils;
import wtf.carnation.utils.RotationUtils;
import wtf.carnation.utils.SharedESPData;
import wtf.carnation.utils.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Port of Naven's {@code NameTags}.
 *
 * <p>Every visible player gets a plate holding distance, name and health; the plate's background is
 * filled to the missing-health fraction, the way Naven's two-pass {@code RenderUtils.fill} does it.
 * Aiming at a player for ten ticks with Middle Click Friend on toggles the friend list. Text goes
 * through {@link UiFont} in the "Modern" style — the Naven style needs coloured {@code §} runs that
 * the baked TrueType atlas does not carry, so it is not reproduced.</p>
 */
public class NameTags extends Module {
    /** Ticks of continuous aim before middle click acts on a player. */
    private static final int AIM_TICKS = 10;
    /** Reach used for the aim test, in blocks. */
    private static final double AIM_REACH = 150.0;
    private static final int PLATE_BACKGROUND = 0x19000000;
    private static final int PLATE_MISSING_HEALTH = 0x37000000;
    private static final int DISTANCE_COLOR = 0xFFA0A0A0;

    public final BoolValue middleClickFriend = new BoolValue("Middle Click Friend", true);
    public final BoolValue showCompassPosition = new BoolValue("Compass Position", true);
    public final BoolValue compassOnly = new BoolValue("Compass Only", true, this::compassEnabled);
    public final BoolValue noPlayerOnly = new BoolValue("No Player Only", true, this::compassEnabled);
    public final BoolValue shared = new BoolValue("Shared ESP", true);
    public final BoolValue showPlayerEquipment = new BoolValue("Show Player Equipment", false);
    public final NumberValue scale = new NumberValue("Scale", 0.35, 0.1, 0.5, 0.01);

    /** Base font size the Scale slider is relative to. */
    private static final float BASE_SCALE = 0.35F;

    private final Map<Entity, Vector2f> entityPositions = new ConcurrentHashMap<>();
    private final List<SharedTag> sharedPositions = new CopyOnWriteArrayList<>();
    private final Map<Player, Integer> aimTicks = new ConcurrentHashMap<>();

    private BlockPos spawnPosition;
    private Vector2f compassPosition;
    private Player aimingPlayer;

    public NameTags() {
        super("NameTags", Category.RENDER, "Renders name tags");
    }

    private boolean compassEnabled() {
        return this.showCompassPosition.get();
    }

    @Override
    protected void onDisable() {
        this.entityPositions.clear();
        this.sharedPositions.clear();
        this.aimTicks.clear();
        this.aimingPlayer = null;
        this.spawnPosition = null;
        this.compassPosition = null;
    }

    @EventTarget
    public void onRespawn(RespawnEvent event) {
        this.entityPositions.clear();
        this.sharedPositions.clear();
        this.aimTicks.clear();
        this.aimingPlayer = null;
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.level == null || mc.player == null) return;
        this.updateAimingPlayer();
        this.updateSpawnPosition();
    }

    private void updateAimingPlayer() {
        if (!this.middleClickFriend.get()) {
            this.aimingPlayer = null;
            this.aimTicks.clear();
            return;
        }

        for (Player player : mc.level.players()) {
            if (player == mc.player || player instanceof BlinkingPlayer) continue;
            if (isAiming(player, mc.player.getYRot(), mc.player.getXRot())) {
                int ticks = this.aimTicks.getOrDefault(player, 0) + 1;
                this.aimTicks.put(player, ticks);
                if (ticks >= AIM_TICKS) this.aimingPlayer = player;
            } else {
                int ticks = this.aimTicks.getOrDefault(player, 0);
                if (ticks > 0) this.aimTicks.put(player, ticks - 1);
            }
        }

        if (this.aimingPlayer != null && this.aimTicks.getOrDefault(this.aimingPlayer, 0) <= 0) {
            this.aimingPlayer = null;
        }
    }

    private void updateSpawnPosition() {
        this.spawnPosition = null;
        if (!this.showCompassPosition.get()) return;
        if (this.compassOnly.get() && !InventoryUtils.hasItem(Items.COMPASS)) return;
        if (this.noPlayerOnly.get() && this.hasOtherPlayer()) return;
        // 26.3 dropped DimensionType.natural(); the overworld is the only dimension whose
        // respawn anchor is a bed the compass can point at.
        if (mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            this.spawnPosition = mc.level.getRespawnData().pos();
        }
    }

    private boolean hasOtherPlayer() {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity != mc.player && !(entity instanceof BlinkingPlayer) && entity instanceof Player) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAiming(Entity target, float yaw, float pitch) {
        Vec3 eye = new Vec3(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(), mc.player.getZ());
        HitResult hit = RotationUtils.getIntercept(target.getBoundingBox(), new Vector2f(yaw, pitch),
                eye, AIM_REACH);
        if (hit == null) return false;
        return hit.getType() == HitResult.Type.ENTITY && hit.getLocation().distanceTo(eye) < AIM_REACH;
    }

    @EventTarget
    public void onMouse(MouseClickEvent event) {
        // SDL button codes: 2 is the middle button.
        if (event.getKey() != 2 || event.isState()) return;
        if (!this.middleClickFriend.get() || this.aimingPlayer == null) return;

        String name = this.aimingPlayer.getName().getString();
        if (FriendManager.isFriend(this.aimingPlayer)) {
            FriendManager.removeFriend(this.aimingPlayer);
            ChatUtil.addChatMessage("Removed " + name + " from friends!");
        } else {
            FriendManager.addFriend(this.aimingPlayer);
            ChatUtil.addChatMessage("Added " + name + " as friends!");
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;
        float partialTicks = event.partialTicks();
        this.updatePositions(partialTicks);
        this.updateCompassPosition(partialTicks);

        UiFont.useGuiScale();
        GuiGraphicsExtractor graphics = event.graphics();
        this.drawCompass(graphics);
        this.drawPlayers(graphics);
        if (this.shared.get()) this.drawShared(graphics);
    }

    private void updatePositions(float partialTicks) {
        this.entityPositions.clear();
        this.sharedPositions.clear();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Player player) || player == mc.player) continue;
            if (!player.isAlive()) continue;
            double x = MathUtils.interpolate(partialTicks, player.xo, player.getX());
            double y = MathUtils.interpolate(partialTicks, player.yo, player.getY()) + player.getBbHeight() + 0.5;
            double z = MathUtils.interpolate(partialTicks, player.zo, player.getZ());
            Vector2f point = ProjectionUtils.project(x, y, z, partialTicks);
            if (!isValidScreenPos(point)) continue;
            point.setY(point.getY() - 2.0F);
            this.entityPositions.put(player, point);
        }

        if (!this.shared.get()) return;
        for (SharedESPData data : EntityWatcher.getSharedESPData().values()) {
            double x = data.getPosX();
            double y = data.getPosY() + mc.player.getBbHeight() + 0.5;
            double z = data.getPosZ();
            Vector2f point = ProjectionUtils.project(x, y, z, partialTicks);
            if (!isValidScreenPos(point)) continue;
            point.setY(point.getY() - 2.0F);
            this.sharedPositions.add(new SharedTag(data.getDisplayName(), data.getHealth(),
                    data.getMaxHealth(), data.getAbsorption(), new Vec3(x, y, z), point));
        }
    }

    private void updateCompassPosition(float partialTicks) {
        this.compassPosition = null;
        if (this.spawnPosition == null) return;
        Vector2f point = ProjectionUtils.project(
                this.spawnPosition.getX() + 0.5,
                this.spawnPosition.getY() + 1.75,
                this.spawnPosition.getZ() + 0.5,
                partialTicks);
        if (isValidScreenPos(point)) this.compassPosition = point;
    }

    private void drawCompass(GuiGraphicsExtractor graphics) {
        if (this.compassPosition == null || this.spawnPosition == null) return;

        double distance = Math.sqrt(mc.player.distanceToSqr(
                this.spawnPosition.getX() + 0.5,
                this.spawnPosition.getY() + 1.75,
                this.spawnPosition.getZ() + 0.5));
        // The label shrinks as the player walks away and is fully gone at eighty blocks.
        float shrink = Math.max(80.0F - (float) distance, 0.0F) / 80.0F;
        if (shrink <= 0.0F) return;

        String text = "Compass";
        float width = UiFont.Z_SMALL.width(text);
        float poseScale = this.scale.getFloat() / BASE_SCALE * shrink;

        graphics.pose().pushMatrix();
        graphics.pose().translate(this.compassPosition.x, this.compassPosition.y);
        graphics.pose().scale(poseScale, poseScale);
        Render2D.rect(graphics, -width / 2.0F - 2.0F, -3.0F, width + 4.0F,
                UiFont.Z_SMALL.size() + 4.0F, PLATE_BACKGROUND);
        UiFont.Z_SMALL.draw(graphics, text, -width / 2.0F, -1.0F, 0xFFFFFFFF);
        graphics.pose().popMatrix();
    }

    private void drawPlayers(GuiGraphicsExtractor graphics) {
        for (Map.Entry<Entity, Vector2f> entry : this.entityPositions.entrySet()) {
            if (!(entry.getKey() instanceof Player player)) continue;
            Vector2f position = entry.getValue();

            String name = player.getName().getString();
            if (mc.player != null) name = name.replace(mc.player.getName().getString(), "Hidden");

            String distance = String.format(Locale.ROOT, "[%.1fm] ", mc.player.distanceTo(player));
            String prefix = (Teams.isSameTeam(player) ? "Team " : "") + name;
            String health = Math.round(player.getHealth())
                    + (player.getAbsorptionAmount() > 0.0F
                    ? "+" + Math.round(player.getAbsorptionAmount()) : "")
                    + "HP";

            drawPlate(graphics, position,
                    distance, prefix, health,
                    player.getHealth(), player.getMaxHealth(),
                    this.labelFor(player),
                    player);
        }
    }

    /** The extra line above the plate: "Friend" in green, "Aiming" in red. */
    private String labelFor(Player player) {
        if (this.aimingPlayer != player) return null;
        return FriendManager.isFriend(player) ? "Friend" : "Aiming";
    }

    private void drawShared(GuiGraphicsExtractor graphics) {
        for (SharedTag data : this.sharedPositions) {
            double distance = mc.player.position().distanceTo(data.position);
            String distanceText = "[" + String.format(Locale.ROOT, "%.1fm] ", distance);
            String health = Math.round(data.health)
                    + (data.absorption > 0.0 ? "+" + Math.round(data.absorption) : "")
                    + "HP";
            drawPlate(graphics, data.render, distanceText, data.displayName + " Shared", health,
                    (float) data.health, (float) data.maxHealth, null, null);
        }
    }

    /**
     * One plate: background filled to the missing-health fraction, optional label line above,
     * then distance / name / health on one line.
     */
    private void drawPlate(GuiGraphicsExtractor graphics, Vector2f position,
                           String distanceText, String prefix, String healthText,
                           float health, float maxHealth, String label, Player equipmentOwner) {
        float fontWidthDistance = UiFont.Z_SMALL.width(distanceText);
        float fontWidthPrefix = UiFont.Z_SMALL.width(prefix);
        float fontWidthHealth = UiFont.Z_SMALL.width(" " + healthText);
        float mainWidth = fontWidthDistance + fontWidthPrefix + fontWidthHealth;
        float labelWidth = label == null ? 0.0F : UiFont.Z_SMALL.width(label);
        float plateWidth = Math.max(mainWidth, labelWidth);

        float max = maxHealth <= 0.0F ? 20.0F : maxHealth;
        float missing = Mth.clamp(1.0F - health / max, 0.0F, 1.0F);
        float lineHeight = UiFont.Z_SMALL.size();
        float poseScale = this.scale.getFloat() / BASE_SCALE;

        float top = -3.0F;
        if (label != null) top -= lineHeight + 2.0F;
        float bottom = UiFont.Z_SMALL.size() + 1.0F;

        graphics.pose().pushMatrix();
        graphics.pose().translate(position.x, position.y);
        graphics.pose().scale(poseScale, poseScale);

        float left = -plateWidth / 2.0F - 2.0F;
        float right = plateWidth / 2.0F + 2.0F;
        Render2D.rect(graphics, left, top, right - left, bottom - top, PLATE_BACKGROUND);
        // The second Naven fill only covers the part of the bar still missing health.
        Render2D.rect(graphics, left, top, (right - left) * missing, bottom - top, PLATE_MISSING_HEALTH);

        if (label != null && labelWidth > 0.0F) {
            int color = "Friend".equals(label) ? 0xFF55FF55 : 0xFFFF5555;
            UiFont.Z_SMALL.draw(graphics, label, -labelWidth / 2.0F, top + 2.0F, color);
        }

        float x = -mainWidth / 2.0F;
        float textY = -1.0F;
        UiFont.Z_SMALL.draw(graphics, distanceText, x, textY, DISTANCE_COLOR);
        x += fontWidthDistance;
        UiFont.Z_SMALL.draw(graphics, prefix, x, textY, 0xFFFFFFFF);
        x += fontWidthPrefix;
        UiFont.Z_SMALL.draw(graphics, " " + healthText, x, textY, healthGradient(health, max));

        graphics.pose().popMatrix();

        if (this.showPlayerEquipment.get() && equipmentOwner != null) {
            this.drawEquipment(graphics, equipmentOwner, position, top * poseScale, poseScale);
        }
    }

    private void drawEquipment(GuiGraphicsExtractor graphics, Player player, Vector2f position,
                               float plateTop, float poseScale) {
        List<ItemStack> items = new ArrayList<>();
        addIfPresent(items, player.getMainHandItem());
        addIfPresent(items, player.getItemBySlot(EquipmentSlot.HEAD));
        addIfPresent(items, player.getItemBySlot(EquipmentSlot.CHEST));
        addIfPresent(items, player.getItemBySlot(EquipmentSlot.LEGS));
        addIfPresent(items, player.getItemBySlot(EquipmentSlot.FEET));
        addIfPresent(items, player.getOffhandItem());
        if (items.isEmpty()) return;

        final float size = 16.0F;
        final float spacing = 2.0F;
        float rowWidth = items.size() * size + (items.size() - 1) * spacing;
        float startX = position.x - rowWidth / 2.0F;
        // Plates are anchored on the projected head position; the row sits above the plate.
        float y = position.y + plateTop - size - 3.0F;

        float x = startX;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                int drawX = Mth.floor(x);
                int drawY = Mth.floor(y);
                graphics.item(stack, drawX, drawY);
                graphics.itemDecorations(mc.font, stack, drawX, drawY);
            }
            x += size + spacing;
        }
    }

    private static void addIfPresent(List<ItemStack> items, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) items.add(stack);
    }

    /** Red at empty, yellow at half, green at full. */
    private static int healthGradient(float health, float max) {
        float t = Mth.clamp(health / (max <= 0.0F ? 20.0F : max), 0.0F, 1.0F);
        int red;
        int green;
        if (t >= 0.5F) {
            float u = (t - 0.5F) / 0.5F;
            red = Math.round(255.0F * (1.0F - u));
            green = 255;
        } else {
            float u = t / 0.5F;
            red = 255;
            green = Math.round(255.0F * u);
        }
        return 0xFF000000 | (Mth.clamp(red, 0, 255) << 16) | (Mth.clamp(green, 0, 255) << 8);
    }

    private static boolean isValidScreenPos(Vector2f pos) {
        if (pos == null) return false;
        float x = pos.getX();
        float y = pos.getY();
        if (Float.isNaN(x) || Float.isInfinite(x) || Float.isNaN(y) || Float.isInfinite(y)) return false;
        return Math.abs(x) < 1.0E6F && Math.abs(y) < 1.0E6F;
    }

    private record SharedTag(String displayName, double health, double maxHealth, double absorption,
                             Vec3 position, Vector2f render) {
    }
}
