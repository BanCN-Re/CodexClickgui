package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
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

public class ChestESP extends Module {
    private final NumberValue range = new NumberValue("Range", 32, 8, 64, 4);
    private final List<BlockPos> containers = new ArrayList<>();
    private int ticks;

    public ChestESP() {
        super("ChestESP", Category.RENDER, "标记附近的箱子、桶与容器");
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (++ticks % 20 != 0) return;
        containers.clear();
        int radius = range.getInt() >> 4;
        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                var chunk = mc.level.getChunkSource().getChunk(cx, cz,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                if (!(chunk instanceof LevelChunk levelChunk)) continue;
                for (BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
                    if (isContainer(blockEntity)) containers.add(blockEntity.getBlockPos());
                }
            }
        }
    }

    private static boolean isContainer(BlockEntity blockEntity) {
        String name = blockEntity.getClass().getSimpleName();
        return name.contains("Chest") || name.contains("Barrel") || name.contains("ShulkerBox")
                || name.contains("Hopper") || name.contains("Dispenser") || name.contains("Dropper")
                || name.contains("Furnace") || name.contains("Brewing");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || containers.isEmpty()) return;
        GuiGraphicsExtractor g = event.graphics();
        EspRender.begin(g);
        for (BlockPos pos : containers) {
            Vec3 min = new Vec3(pos.getX(), pos.getY(), pos.getZ());
            EspRender.boxAt(g, min, min.add(1.0, 1.0, 1.0), 0xFFE7C46E, false);
            double distance = mc.player.position().distanceTo(Vec3.atCenterOf(pos));
            EspRender.labelAt(g, Vec3.atCenterOf(pos).add(0, 0.9, 0),
                    String.format("%.0fm", distance), 0xFFE7C46E);
        }
        EspRender.end(g);
    }
}
