package wtf.carnation.module.impl.render;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.RespawnEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.utils.BlockUtils;
import wtf.carnation.utils.ChunkUtils;
import wtf.carnation.utils.EspRender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/** Port of Naven's {@code ChestESP}; the 3D box is drawn through {@link EspRender}. */
public class ChestESP extends Module {
    private static final int CHEST_COLOR = 0xFF00CC00;
    private static final int OPENED_COLOR = 0xFFCC0000;
    private final List<BlockPos> openedChests = new CopyOnWriteArrayList<>();
    private final List<AABB> renderBoundingBoxes = new CopyOnWriteArrayList<>();

    public ChestESP() {
        super("ChestESP", Category.RENDER, "Highlights chests");
    }

    @EventTarget
    public void onRespawn(RespawnEvent e) {
        this.openedChests.clear();
    }

    @EventTarget
    public void onPacket(PacketEvent e) {
        if (e.isSend() || !(e.getPacket() instanceof ClientboundBlockEventPacket packet)) return;
        if ((packet.getBlock() == Blocks.CHEST || packet.getBlock() == Blocks.TRAPPED_CHEST)
                && packet.getB0() == 1 && packet.getB1() == 1) {
            this.openedChests.add(packet.getPos());
        }
    }

    @EventTarget
    public void onTick(MotionEvent e) {
        if (!e.isPre() || mc.level == null) return;
        ArrayList<BlockEntity> blockEntities = ChunkUtils.getLoadedBlockEntities()
                .collect(Collectors.toCollection(ArrayList::new));
        this.renderBoundingBoxes.clear();
        for (BlockEntity blockEntity : blockEntities) {
            if (blockEntity instanceof ChestBlockEntity chest) {
                AABB box = this.getChestBox(chest);
                if (box != null) this.renderBoundingBoxes.add(box);
            }
        }
    }

    private AABB getChestBox(ChestBlockEntity chest) {
        BlockState state = chest.getBlockState();
        if (!state.hasProperty(ChestBlock.TYPE)) return null;
        ChestType chestType = state.getValue(ChestBlock.TYPE);
        if (chestType == ChestType.LEFT) return null;
        BlockPos pos = chest.getBlockPos();
        AABB box = BlockUtils.getBoundingBox(pos);
        if (chestType != ChestType.SINGLE) {
            BlockPos pos2 = pos.relative(ChestBlock.getConnectedDirection(state));
            if (BlockUtils.canBeClicked(pos2)) box = box.minmax(BlockUtils.getBoundingBox(pos2));
        }
        return box;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.renderBoundingBoxes.isEmpty()) return;
        for (AABB box : this.renderBoundingBoxes) {
            BlockPos pos = BlockPos.containing(box.minX, box.minY, box.minZ);
            int color = this.openedChests.contains(pos) ? OPENED_COLOR : CHEST_COLOR;
            EspRender.boxAt(event.graphics(), new Vec3(box.minX, box.minY, box.minZ),
                    new Vec3(box.maxX, box.maxY, box.maxZ), color, false);
        }
    }
}
