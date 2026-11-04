package wtf.carnation.event.impl.combat;

import wtf.carnation.event.api.CancellableEvent;

/**
 * Port of Naven's {@code EventClick}: fired from {@code Minecraft.startAttack}, i.e. once for every
 * real attack input the vanilla game consumes. KillAura drives its damage from this event rather
 * than from a held key, which is exactly how the original behaves.
 */
public final class EventClick extends CancellableEvent {
}
