package lol.mixin;

import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import wtf.carnation.graphics.SystemFonts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;

/** Lets font definitions use operating-system fonts through {@link SystemFonts}. */
@Mixin(TrueTypeGlyphProviderDefinition.class)
public class MixinTrueTypeGlyphProviderDefinition {
    @Shadow
    @Final
    private Identifier location;

    @Redirect(method = "load", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/ResourceManager;open(Lnet/minecraft/resources/Identifier;)Ljava/io/InputStream;"))
    private InputStream carnation$openSystemFont(ResourceManager manager, Identifier id) throws IOException {
        return SystemFonts.file(location) != null ? InputStream.nullInputStream() : manager.open(id);
    }

    @Redirect(method = "load", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/TextureUtil;readResource(Ljava/io/InputStream;)Ljava/nio/ByteBuffer;"))
    private ByteBuffer carnation$readSystemFont(InputStream stream) throws IOException {
        Path file = SystemFonts.file(location);
        return file != null ? SystemFonts.buffer(file) : TextureUtil.readResource(stream);
    }

    @Redirect(method = "load", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/system/MemoryUtil;memFree(Ljava/nio/ByteBuffer;)V"))
    private void carnation$keepSharedFont(ByteBuffer buffer) {
        SystemFonts.free(buffer);
    }
}
