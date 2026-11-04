package wtf.carnation.module.impl.movement;

import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.EventSprint;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.combat.Aura;
import wtf.carnation.utils.InventoryUtils;

/** Port of Naven's {@code tech.naven.modules.impl.move.Sprint}. */
public class Sprint extends Module {
    public Sprint() {
        super("Sprint", Category.MOVEMENT, "Automatically sprints");
    }

    @EventTarget
    public void onSprint(EventSprint event) {
        if (mc.player == null || InventoryUtils.shouldDisableFeatures()) return;

        Module inventory = Client.instance.getModuleManager().getModuleByName("InventoryCleaner");
        if (inventory != null && inventory.isEnabled()) {
            // The silent-action guard lives in InventoryCleaner; that module is migrated later.
        }

        Aura aura = Client.instance.getModuleManager().getModule(Aura.class);
        Module stuck = Client.instance.getModuleManager().getModuleByName("Stuck");
        if ((stuck != null && stuck.isEnabled()) || (aura != null && aura.shouldStopSprint())) {
            mc.options.keySprint.setDown(false);
            mc.player.setSprinting(false);
            if (aura != null) aura.markSprintReleased();
            return;
        }

        if (aura != null && aura.isSprintReleased()) {
            mc.options.keySprint.setDown(false);
            mc.player.setSprinting(false);
            return;
        }

        mc.options.toggleSprint().set(false);
        mc.options.keySprint.setDown(true);
        if (mc.player.input.getMoveVector().y != 0.0F) {
            mc.player.setSprinting(true);
        }
    }

    @Override
    protected void onDisable() {
        mc.options.keySprint.setDown(false);
    }
}
