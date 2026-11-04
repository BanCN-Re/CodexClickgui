package wtf.carnation.module.impl.render.projectiles.datas;

import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;

import java.awt.Color;
import java.util.Collections;
import java.util.HashSet;

/** Port of Naven's {@code EntityPotionData}. */
public class EntityPotionData extends BasicProjectileData {
    public EntityPotionData() {
        super(new HashSet<>(Collections.singletonList(AbstractThrownPotion.class)), new Color(255, 66, 249));
    }

    @Override
    public float getGravity() {
        return 0.05F;
    }
}
