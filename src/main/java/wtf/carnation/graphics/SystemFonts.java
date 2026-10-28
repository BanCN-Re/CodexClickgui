package wtf.carnation.graphics;

import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import wtf.carnation.Client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serves the operating system's own fonts (Segoe UI and Microsoft YaHei on Windows, which is what
 * the Codex app renders with) to Minecraft's TrueType provider without copying them into the mod.
 *
 * <p>Font definitions name them as {@code carnation:system/<file>}. Each file is read once into
 * native memory and every provider (one per size and scale) opens its FreeType face on that same
 * buffer, so a 20 MB CJK font is resident once instead of once per size. Shared buffers live for
 * the rest of the session; providers must not free them. When a file is missing (another OS),
 * loading that provider fails and the font falls through to the bundled Inter/MiSans providers.
 */
public final class SystemFonts {
    private static final String PREFIX = "system/";
    private static final Map<String, ByteBuffer> LOADED = new ConcurrentHashMap<>();
    private static final Set<ByteBuffer> SHARED = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private SystemFonts() {
    }

    /** The system font file a definition refers to, or null when it is a normal resource. */
    public static Path file(Identifier location) {
        if (!location.getNamespace().equals("carnation") || !location.getPath().startsWith(PREFIX)) return null;
        String name = location.getPath().substring(PREFIX.length());
        if (name.contains("/") || name.contains("\\") || name.contains("..")) return null;
        String windir = System.getenv("WINDIR");
        if (windir == null) return null;
        Path path = Path.of(windir, "Fonts", name);
        return Files.isRegularFile(path) ? path : null;
    }

    /** The shared native buffer holding the file. */
    public static ByteBuffer buffer(Path file) throws IOException {
        try {
            return LOADED.computeIfAbsent(file.toString(), key -> {
                try {
                    byte[] bytes = Files.readAllBytes(file);
                    ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
                    buffer.put(bytes).flip();
                    SHARED.add(buffer);
                    Client.logger.info("Loaded system font {} ({} KB)", file, bytes.length / 1024);
                    return buffer;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw e;
        }
    }

    /** Frees a provider's font memory unless it is one of the shared system font buffers. */
    public static void free(ByteBuffer buffer) {
        if (buffer != null && !SHARED.contains(buffer)) MemoryUtil.memFree(buffer);
    }
}
