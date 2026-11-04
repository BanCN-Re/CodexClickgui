package wtf.carnation.module.impl.render.projectiles.datas;

import net.minecraft.world.entity.Entity;
import wtf.carnation.module.impl.render.projectiles.ProjectileData;

import java.awt.Color;
import java.util.Set;

/** Port of Naven's {@code BasicProjectileData}. */
public class BasicProjectileData implements ProjectileData {
    private final Color color;
    private final Set<Class<?>> entityClass;

    public BasicProjectileData(Set<Class<?>> entityClass) {
        this(entityClass, new Color(255, 255, 255));
    }

    public BasicProjectileData(Set<Class<?>> entityClass, Color color) {
        this.entityClass = entityClass;
        this.color = color;
    }

    @Override
    public Color getColor(Object value) {
        return this.color;
    }

    @Override
    public boolean isTargetEntity(Entity entity) {
        for (Class<?> clazz : this.entityClass) {
            if (clazz.isInstance(entity)) return true;
        }
        return false;
    }
}
