package wtf.carnation.event.impl.combat;

import net.minecraft.world.entity.Entity;
import wtf.carnation.event.api.CancellableEvent;
import wtf.carnation.event.api.Phase;

/** Fired around {@code MultiPlayerGameMode.attack}, before and after the hit lands. */
public final class EventAttack extends CancellableEvent {
    private final Entity target;
    private final Phase phase;

    public EventAttack(Entity target, Phase phase) {
        this.target = target;
        this.phase = phase;
    }

    public Entity getTarget() {
        return target;
    }

    public boolean isPre() {
        return phase == Phase.PRE;
    }

    public Phase getPhase() {
        return phase;
    }
}
