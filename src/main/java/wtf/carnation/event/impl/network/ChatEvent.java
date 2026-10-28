package wtf.carnation.event.impl.network;

import lombok.Getter;
import wtf.carnation.event.api.CancellableEvent;

/** An outgoing chat message; cancelling it keeps it off the server. */
@Getter
public class ChatEvent extends CancellableEvent {
    private final String message;

    public ChatEvent(String message) {
        this.message = message;
    }
}
