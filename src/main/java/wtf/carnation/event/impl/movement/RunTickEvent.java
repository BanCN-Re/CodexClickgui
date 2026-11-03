package wtf.carnation.event.impl.movement;

import wtf.carnation.event.api.Event;
import wtf.carnation.event.api.Phase;

/** Fired before and after every client tick. */
public record RunTickEvent(Phase phase) implements Event {
    public boolean isPre() {
        return phase == Phase.PRE;
    }
}
