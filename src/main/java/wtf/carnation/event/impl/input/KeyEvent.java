package wtf.carnation.event.impl.input;

import wtf.carnation.event.api.Event;

/** A key press (SDL scancode, see InputConstants) (state = true) or release while no screen is open. */
public record KeyEvent(int key, boolean state) implements Event {
}
