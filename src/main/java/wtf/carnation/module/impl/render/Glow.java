package wtf.carnation.module.impl.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import wtf.carnation.Client;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;

/** Port of Naven's {@code Glow}; the glow gate is called from MixinMinecraft. */
public class Glow extends Module {
    private final BoolValue players = new BoolValue("Player", true);
    private final BoolValue items = new BoolValue("Items", false);
    private final BoolValue mobs = new BoolValue("Mobs", false);
    private final BoolValue animals = new BoolValue("Animals", false);
    private final BoolValue arrows = new BoolValue("Arrows", false);

    public Glow() {
        super("Glow", Category.RENDER, "Glow effect for entities");
    }

    public static boolean shouldGlow(Entity entity) {
        Glow module = Client.instance.getModuleManager().getModule(Glow.class);
        if (module == null || !module.isEnabled()) return false;
        if (entity instanceof Player && module.players.get()) return true;
        if (entity instanceof ItemEntity && module.items.get()) return true;
        if (entity instanceof Mob && module.mobs.get()) return true;
        if (entity instanceof Animal && module.animals.get()) return true;
        return entity instanceof Arrow && module.arrows.get();
    }
}
