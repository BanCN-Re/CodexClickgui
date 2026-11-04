package wtf.carnation.event.impl.movement;

import wtf.carnation.event.api.Event;

/** Fired once per local-player tick before the sprint state is resolved, like Naven's EventSprint. */
public final class EventSprint implements Event {
    private boolean sprint;

    public EventSprint() {
        this(true);
    }

    public EventSprint(boolean sprint) {
        this.sprint = sprint;
    }

    public boolean isSprint() {
        return sprint;
    }

    public void setSprint(boolean sprint) {
        this.sprint = sprint;
    }
}
