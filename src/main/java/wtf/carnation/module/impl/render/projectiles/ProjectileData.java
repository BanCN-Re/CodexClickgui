package wtf.carnation.module.impl.render.projectiles;

import net.minecraft.world.entity.Entity;

import java.awt.Color;

/** Port of Naven's {@code tech.naven.modules.impl.render.projectiles.ProjectileData}. */
public interface ProjectileData {
    Color getColor(Object value);

    default float getData1() {
        return 0.125F;
    }

    boolean isTargetEntity(Entity entity);

    default float getData2() {
        return 0.25F;
    }

    default float getGravity() {
        return 0.03F;
    }
}
