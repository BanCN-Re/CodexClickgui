package mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Naven's MinecraftAccessor.setMissTime, used by AutoClicker to bypass the click cooldown. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("missTime")
    void carnation$setMissTime(int value);

    @Accessor("rightClickDelay")
    void carnation$setRightClickDelay(int value);
}
