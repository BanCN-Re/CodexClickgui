package wtf.carnation.event.impl.input;

import wtf.carnation.event.api.Event;

/** Fired for every mouse button press/release, with SDL button codes (left 1, middle 2, right 3, side 4/5). */
public final class MouseClickEvent implements Event {
    private final int key;
    private final boolean state;

    public MouseClickEvent(int key, boolean state) {
        this.key = key;
        this.state = state;
    }

    public int getKey() {
        return key;
    }

    public boolean isState() {
        return state;
    }
}
