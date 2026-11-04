package wtf.carnation.event.impl.combat;

import net.minecraft.world.entity.Entity;
import wtf.carnation.event.api.Event;

/**
 * Port of Naven's {@code EventRayTrace}: fired from {@code Entity#getViewVector} for the local
 * player. Every pick, place and item-use raycast in the game goes through that method, so a module
 * can substitute its aim there without ever touching {@code player.yRot}/{@code xRot} — which is
 * what "silent" means: the server receives the aim, the camera does not move.
 */
public final class EventRayTrace implements Event {
    private final Entity entity;
    private float yaw;
    private float pitch;
    private boolean modified;

    public EventRayTrace(Entity entity, float yaw, float pitch) {
        this.entity = entity;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Entity getEntity() {
        return this.entity;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
        this.modified = true;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
        this.modified = true;
    }

    public boolean isModified() {
        return this.modified;
    }
}
