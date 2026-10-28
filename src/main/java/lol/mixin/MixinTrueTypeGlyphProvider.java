package lol.mixin;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import wtf.carnation.graphics.SystemFonts;

import java.nio.ByteBuffer;

/** Keeps shared system font memory alive when one of the providers using it closes. */
@Mixin(TrueTypeGlyphProvider.class)
public class MixinTrueTypeGlyphProvider {
    @Redirect(method = "close", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/system/MemoryUtil;memFree(Ljava/nio/ByteBuffer;)V"))
    private void carnation$keepSharedFont(ByteBuffer buffer) {
        SystemFonts.free(buffer);
    }
}
