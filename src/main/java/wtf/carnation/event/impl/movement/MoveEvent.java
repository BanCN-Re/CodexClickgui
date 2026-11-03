package wtf.carnation.event.impl.movement;

import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.api.Event;

/**
 * Fired at the head of {@code LivingEntity.travel}. A module may replace the
 * velocity; when it does, vanilla travel is skipped for that tick.
 */
public final class MoveEvent implements Event {
    private Vec3 vector;
    private boolean modified;

    public MoveEvent(Vec3 vector) {
        this.vector = vector;
    }

    public Vec3 getVector() {
        return vector;
    }

    public double getX() {
        return vector.x;
    }

    public double getY() {
        return vector.y;
    }

    public double getZ() {
        return vector.z;
    }

    public void setVector(Vec3 vector) {
        this.vector = vector;
        this.modified = true;
    }

    public void set(double x, double y, double z) {
        setVector(new Vec3(x, y, z));
    }

    public void setX(double x) {
        setVector(new Vec3(x, vector.y, vector.z));
    }

    public void setY(double y) {
        setVector(new Vec3(vector.x, y, vector.z));
    }

    public void setZ(double z) {
        setVector(new Vec3(vector.x, vector.y, z));
    }

    public boolean isModified() {
        return modified;
    }
}
