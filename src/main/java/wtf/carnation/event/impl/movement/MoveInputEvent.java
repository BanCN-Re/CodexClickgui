package wtf.carnation.event.impl.movement;

import wtf.carnation.event.api.Event;

/** Fired after the client rebuilds its movement input for the tick. */
public final class MoveInputEvent implements Event {
    private float forward;
    private float strafe;
    private boolean jump;
    private boolean sneak;
    private float sneakMultiplier;

    public MoveInputEvent(float forward, float strafe, boolean jump, boolean sneak, float sneakMultiplier) {
        this.forward = forward;
        this.strafe = strafe;
        this.jump = jump;
        this.sneak = sneak;
        this.sneakMultiplier = sneakMultiplier;
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

    public boolean isJump() {
        return jump;
    }

    public void setJump(boolean jump) {
        this.jump = jump;
    }

    public boolean isSneak() {
        return sneak;
    }

    public void setSneak(boolean sneak) {
        this.sneak = sneak;
    }

    public float getSneakMultiplier() {
        return sneakMultiplier;
    }

    public void setSneakMultiplier(float sneakMultiplier) {
        this.sneakMultiplier = sneakMultiplier;
    }
}
