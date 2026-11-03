package wtf.carnation.event.impl.movement;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.api.Event;

/** Fired from {@code Entity.makeStuckInBlock}; replaces the vanilla speed multiplier. */
public final class StuckInBlockEvent implements Event {
    private final BlockState state;
    private Vec3 multiplier;

    public StuckInBlockEvent(BlockState state, Vec3 multiplier) {
        this.state = state;
        this.multiplier = multiplier;
    }

    public BlockState getState() {
        return state;
    }

    public Vec3 getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(Vec3 multiplier) {
        this.multiplier = multiplier;
    }
}
