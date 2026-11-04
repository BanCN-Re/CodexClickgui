package wtf.carnation.event.impl.movement;

import wtf.carnation.event.api.Event;

/** Fired when the game is about to apply the item-use slowdown, like Naven's EventSlowdown. */
public final class EventSlowdown implements Event {
    private boolean slowdown;

    public EventSlowdown(boolean slowdown) {
        this.slowdown = slowdown;
    }

    public boolean isSlowdown() {
        return slowdown;
    }

    public void setSlowdown(boolean slowdown) {
        this.slowdown = slowdown;
    }
}
