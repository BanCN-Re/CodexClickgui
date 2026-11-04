package wtf.carnation.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Port of Naven's {@code tech.naven.utils.BlockUtils}. */
public final class BlockUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    private BlockUtils() {
    }

    public static AABB getBoundingBox(BlockPos pos) {
        return getOutlineShape(pos).bounds().move(pos);
    }

    private static VoxelShape getOutlineShape(BlockPos pos) {
        return getState(pos).getShape(mc.level, pos);
    }

    public static BlockState getState(BlockPos pos) {
        return mc.level.getBlockState(pos);
    }

    public static boolean canBeClicked(BlockPos pos) {
        return getOutlineShape(pos) != Shapes.empty();
    }

    public static boolean isAirBlock(BlockPos pos) {
        if (mc.level != null && mc.player != null) {
            Block block = mc.level.getBlockState(pos).getBlock();
            return block instanceof AirBlock;
        }
        return false;
    }
}
