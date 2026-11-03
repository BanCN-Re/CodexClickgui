package wtf.carnation.event.impl.movement;

import wtf.carnation.event.api.Event;

/** Fired before vanilla applies the player's strafe input. */
public final class StrafeEvent implements Event {
    private float forward;
    private float strafe;
    private float friction;

    public StrafeEvent(float forward, float strafe, float friction) {
        this.forward = forward;
        this.strafe = strafe;
        this.friction = friction;
    }

    public float getForward() {
        return forward;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    public float getStrafe() {
        return strafe;
    }

    public void setStrafe(float strafe) {
        this.strafe = strafe;
    }

    public float getFriction() {
        return friction;
    }

    public void setFriction(float friction) {
        this.friction = friction;
    }
}
