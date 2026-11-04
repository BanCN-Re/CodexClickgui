package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.misc.ChestStealer;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.InventoryUtils;
import wtf.carnation.utils.MathUtils;
import wtf.carnation.utils.ProjectionUtils;
import wtf.carnation.utils.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of Naven's {@code ItemTags}.
 *
 * <p>Dropped items within one block of each other are folded into a single label that counts by
 * item name; god items print red. Naven draws the label through its Harmony font in the BLUR/SHADOW
 * mask pass — 26.3 has no GUI shader hook, so the background is a plain translucent rectangle and
 * the text goes through {@link UiFont}. The {@code Scale} slider drives the pose instead of the
 * font's own scale argument.</p>
 */
public class ItemTags extends Module {
    /** Items within this squared distance of a group's centre join that group. */
    private static final double GROUP_RADIUS_SQ = 1.0;
    private static final int BACKGROUND = new Color(0, 0, 0, 40).getRGB();
    private static final int LINE_HEIGHT = 9;

    private final List<TagGroup> tagGroups = new ArrayList<>();

    public final NumberValue scale = new NumberValue("Scale", 0.35, 0.1, 0.5, 0.01);
    public final BoolValue allItems = new BoolValue("All Items", false);
    public final BoolValue godItems = new BoolValue("God Items", true, this::notAllItems);
    public final BoolValue diamond = new BoolValue("Diamond", true, this::notAllItems);
    public final BoolValue gold = new BoolValue("Gold", true, this::notAllItems);
    public final BoolValue iron = new BoolValue("Iron", true, this::notAllItems);
    public final BoolValue enderPearl = new BoolValue("Ender Pearl", true, this::notAllItems);
    public final BoolValue goldenApple = new BoolValue("Golden Apple", true, this::notAllItems);
    public final BoolValue usefulItem = new BoolValue("Useful Item", true, this::notAllItems);

    public ItemTags() {
        super("ItemTags", Category.RENDER, "Show item tags.");
    }

    private boolean notAllItems() {
        return !this.allItems.get();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;
        this.updatePositions(event.partialTicks());
        if (this.tagGroups.isEmpty()) return;

        UiFont.useGuiScale();
        GuiGraphicsExtractor graphics = event.graphics();
        float scale = this.scale.getFloat();

        for (TagGroup group : this.tagGroups) {
            List<TagLine> lines = group.buildLines();
            if (lines.isEmpty()) continue;

            float maxWidth = 0.0F;
            for (TagLine line : lines) maxWidth = Math.max(maxWidth, UiFont.Z_SMALL.width(line.text));
            float boxWidth = maxWidth + 8.0F;
            float boxHeight = lines.size() * LINE_HEIGHT + 4.0F;

            graphics.pose().pushMatrix();
            graphics.pose().translate(group.screenPos.x, group.screenPos.y);
            graphics.pose().scale(scale, scale);
            Render2D.rect(graphics, -boxWidth / 2.0F, -boxHeight, boxWidth, boxHeight, BACKGROUND);
            float textY = -boxHeight + 2.0F;
            for (TagLine line : lines) {
                float lineWidth = UiFont.Z_SMALL.width(line.text);
                UiFont.Z_SMALL.draw(graphics, line.text, -lineWidth / 2.0F, textY, line.color);
                textY += LINE_HEIGHT;
            }
            graphics.pose().popMatrix();
        }
    }

    private void updatePositions(float partialTicks) {
        this.tagGroups.clear();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;
            ItemStack stack = itemEntity.getItem();
            if (!this.isValidItem(stack)) continue;

            double x = MathUtils.interpolate(partialTicks, entity.xo, entity.getX());
            double y = MathUtils.interpolate(partialTicks, entity.yo, entity.getY()) + entity.getBbHeight() + 0.5;
            double z = MathUtils.interpolate(partialTicks, entity.zo, entity.getZ());
            Vec3 worldPos = new Vec3(x, y, z);

            TagGroup group = null;
            for (TagGroup candidate : this.tagGroups) {
                if (candidate.center.distanceToSqr(worldPos) <= GROUP_RADIUS_SQ) {
                    group = candidate;
                    break;
                }
            }
            if (group == null) {
                group = new TagGroup(worldPos);
                this.tagGroups.add(group);
            } else {
                group.addCenterSample(worldPos);
            }
            group.addItem(stack);
        }

        for (TagGroup group : this.tagGroups) {
            Vector2f point = ProjectionUtils.project(group.center.x, group.center.y, group.center.z, partialTicks);
            if (!isValidScreenPos(point)) continue;
            point.setY(point.getY() - 2.0F);
            group.screenPos = point;
        }
        this.tagGroups.removeIf(group -> group.screenPos == null || group.items.isEmpty());
    }

    private static boolean isValidScreenPos(Vector2f pos) {
        if (pos == null) return false;
        float x = pos.getX();
        float y = pos.getY();
        return !Float.isNaN(x) && !Float.isInfinite(x) && !Float.isNaN(y) && !Float.isInfinite(y)
                && Math.abs(x) < 1.0E6F && Math.abs(y) < 1.0E6F;
    }

    private boolean isValidItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (this.allItems.get()) return true;

        if (this.godItems.get()) {
            if (InventoryUtils.isKBBall(stack)) return true;
            if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return true;
            if (InventoryUtils.isGodAxe(stack)) return true;
        }

        if (this.diamond.get() && stack.getItem() == Items.DIAMOND) return true;
        if (this.gold.get() && stack.getItem() == Items.GOLD_INGOT) return true;
        if (this.iron.get() && stack.getItem() == Items.IRON_INGOT) return true;
        if (this.enderPearl.get() && stack.getItem() == Items.ENDER_PEARL) return true;
        if (this.goldenApple.get() && stack.getItem() == Items.GOLDEN_APPLE) return true;

        if (this.usefulItem.get()) {
            // A lone block or a couple of projectiles is never worth labelling.
            if (stack.getItem() instanceof BlockItem && stack.getCount() < 8) return false;
            if ((stack.getItem() instanceof SnowballItem || stack.getItem() instanceof EggItem)
                    && stack.getCount() < 3) {
                return false;
            }
            if (ChestStealer.isItemUseful(stack)) return true;
        }
        return false;
    }

    private static boolean isGodItem(ItemStack stack) {
        if (InventoryUtils.isKBBall(stack)) return true;
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return true;
        return stack.getItem() instanceof EndCrystalItem || InventoryUtils.isGodAxe(stack);
    }

    /** Drops the brackets the server sometimes wraps display names in. */
    private static String stripBrackets(String text) {
        if (text == null) return "";
        String s = text.trim();
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '[' && last == ']') || (first == '【' && last == '】')
                    || (first == '（' && last == '）') || (first == '(' && last == ')')) {
                s = s.substring(1, s.length() - 1).trim();
            }
        }
        return s.replace("[", "").replace("]", "").replace("【", "").replace("】", "");
    }

    private record TagLine(String text, int color) {
    }

    private static final class LineData {
        private int count;
        private boolean godItem;

        private LineData(int count, boolean godItem) {
            this.count = count;
            this.godItem = godItem;
        }
    }

    private static final class TagGroup {
        private Vec3 center;
        private int centerSamples = 1;
        private Vector2f screenPos;
        private final Map<String, LineData> items = new HashMap<>();

        private TagGroup(Vec3 center) {
            this.center = center;
        }

        /** Running mean, so a pile of items centres between them instead of on the first one. */
        private void addCenterSample(Vec3 pos) {
            int next = this.centerSamples + 1;
            this.center = new Vec3(
                    (this.center.x * this.centerSamples + pos.x) / next,
                    (this.center.y * this.centerSamples + pos.y) / next,
                    (this.center.z * this.centerSamples + pos.z) / next);
            this.centerSamples = next;
        }

        private void addItem(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return;
            String name = stripBrackets(stack.getDisplayName().getString());
            LineData data = this.items.get(name);
            int count = stack.getCount();
            if (data == null) {
                this.items.put(name, new LineData(count, isGodItem(stack)));
            } else {
                data.count += count;
                data.godItem |= isGodItem(stack);
            }
        }

        private List<TagLine> buildLines() {
            List<TagLine> out = new ArrayList<>();
            if (this.items.isEmpty()) return out;
            List<Map.Entry<String, LineData>> entries = new ArrayList<>(this.items.entrySet());
            entries.sort(Comparator
                    .<Map.Entry<String, LineData>>comparingInt(entry -> -entry.getValue().count)
                    .thenComparing(Map.Entry::getKey));
            for (Map.Entry<String, LineData> entry : entries) {
                out.add(new TagLine(entry.getKey() + " * " + entry.getValue().count,
                        entry.getValue().godItem ? 0xFFFF5555 : 0xFFFFFFFF));
            }
            return out;
        }
    }
}
