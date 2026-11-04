package wtf.carnation.utils;

/** Port of Naven's {@code tech.naven.utils.SmoothAnimationTimer}. */
public final class SmoothAnimationTimer {
    public float target;
    public float speed = 0.4F;
    public float value;

    public SmoothAnimationTimer(float target) {
        this.target = target;
        this.value = target;
    }

    public SmoothAnimationTimer(float target, float value) {
        this.target = target;
        this.value = value;
    }

    public SmoothAnimationTimer(float target, float value, float speed) {
        this.target = target;
        this.value = value;
        this.speed = speed;
    }

    public void update(boolean increment) {
        float finalState = increment ? this.target : 0.0F;
        float add = Math.max(10.0F, Math.abs(this.value - finalState) * 40.0F) * this.speed / 1000.0F * 16.7F;
        if (this.value < finalState) {
            this.value = Math.min(this.value + add, finalState);
        } else {
            this.value = Math.max(this.value - add, finalState);
        }
    }

    public boolean isAnimationDone(boolean increment) {
        return increment ? this.value == this.target : this.value == 0.0F;
    }
}
