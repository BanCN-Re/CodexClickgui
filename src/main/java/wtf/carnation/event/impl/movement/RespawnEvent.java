package wtf.carnation.event.impl.movement;

import wtf.carnation.event.api.Event;

/** Fired when the local player respawns. */
public record RespawnEvent() implements Event {
}
