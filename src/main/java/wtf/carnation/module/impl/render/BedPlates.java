package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.UpdateEvent;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.EspRender;

import java.util.ArrayList;
import java.util.List;

public class BedPlates extends Module {
    private final NumberValue range = new NumberValue("Range", 12, 4, 32, 2);
    private final List<BlockPos> beds = new ArrayList<>();
    private int ticks;

    public BedPlates() {
        super("BedPlates", Category.RENDER, "标记附近的床并显示距离");
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (++ticks % 20 != 0) return;
        beds.clear();
        int radius = range.getInt();
        BlockPos origin = mc.player.blockPosition();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (mc.level.getBlockState(pos).getBlock() instanceof BedBlock) {
                        beds.add(pos.immutable());
                    }
                }
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || beds.isEmpty()) return;
        GuiGraphicsExtractor g = event.graphics();
        EspRender.begin(g);
        for (BlockPos pos : beds) {
            Vec3 min = new Vec3(pos.getX(), pos.getY(), pos.getZ());
            Vec3 max = min.add(1.0, 0.6, 1.0);
            EspRender.boxAt(g, min, max, 0xFFE76E6E, false);
            double distance = mc.player.position().distanceTo(Vec3.atCenterOf(pos));
            EspRender.labelAt(g, Vec3.atCenterOf(pos).add(0, 0.8, 0),
                    String.format("Bed %.1fm", distance), 0xFFE76E6E);
        }
        EspRender.end(g);
    }
}
