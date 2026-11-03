package wtf.carnation.utils;

/** A two-float vector, used for yaw/pitch pairs. */
public final class Vector2f {
    public float x;
    public float y;

    public Vector2f(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public void set(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vector2f copy() {
        return new Vector2f(x, y);
    }
}
