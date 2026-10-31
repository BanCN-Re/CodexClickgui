package wtf.carnation.wallpaper;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.joml.Matrix3x2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_WARNING;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA;
import static org.bytedeco.ffmpeg.global.avutil.av_log_set_level;

/**
 * Plays a Wallpaper Engine video wallpaper (the {@code .mp4} in the wallpaper folder) behind the
 * title screen. FFmpeg decodes on a background thread at native resolution (or configured max);
 * the render thread only copies the newest RGBA frame into a {@link DynamicTexture} and draws one
 * cover quad, so the real video animates without any per-scene rendering work.
 */
public final class VideoWallpaper {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carnation/Wallpaper");
    private static final String DEFAULT_FOLDER = "3511978938";

    private record FrameData(byte[] rgba, int width, int height) {}

    private static final AtomicReference<FrameData> latest = new AtomicReference<>();
    private static final ConcurrentLinkedQueue<byte[]> pool = new ConcurrentLinkedQueue<>();

    private static volatile boolean started;
    private static volatile boolean failed;
    private static volatile boolean decoding;
    private static Thread decoder;

    private static Path video;
    private static DynamicTexture texture;
    private static NativeImage image;
    private static GpuSampler sampler;
    private static long uploadedSequence = -1L;
    private static long sequence;

    private VideoWallpaper() {
    }

    /** Title-screen entry. Returns false while no frame is ready or when there is no video. */
    public static boolean renderInto(GuiGraphicsExtractor g) {
        if (failed) return false;
        if (!started) start();
        if (failed) return false;

        uploadLatest();
        if (texture == null || image == null) return false;

        float guiWidth = g.guiWidth();
        float guiHeight = g.guiHeight();
        float sourceAspect = image.getWidth() / (float) image.getHeight();
        float targetAspect = guiWidth / Math.max(1.0f, guiHeight);
        float u0 = 0.0f, v0 = 0.0f, u1 = 1.0f, v1 = 1.0f;
        if (sourceAspect > targetAspect) {
            float visible = targetAspect / sourceAspect;
            u0 = (1.0f - visible) * 0.5f;
            u1 = u0 + visible;
        } else {
            float visible = sourceAspect / targetAspect;
            v0 = (1.0f - visible) * 0.5f;
            v1 = v0 + visible;
        }

        RenderPipeline pipeline = WallpaperPipelines.QUAD_TRANSLUCENT;
        g.guiRenderState.addGuiElement(new WallpaperQuads.TexQuad(
                pipeline, new Matrix3x2f(),
                TextureSetup.singleTexture(texture.getTextureView(), sampler),
                0.0f, 0.0f, guiWidth, guiHeight,
                u0, v0, u1, v1, g.scissorStack.peek()));
        return true;
    }

    private static synchronized void start() {
        if (started) return;
        started = true;
        try {
            video = resolveVideo();
            if (video == null) {
                LOGGER.info("No wallpaper video found in {}", DEFAULT_FOLDER);
                failed = true;
                return;
            }
            LOGGER.info("Wallpaper video: {}", video);
            decoding = true;
            decoder = new Thread(VideoWallpaper::decodeLoop, "Carnation-Wallpaper-Video");
            decoder.setDaemon(true);
            decoder.start();
        } catch (Exception exception) {
            LOGGER.warn("Could not start the wallpaper video", exception);
            failed = true;
        }
    }

    private static void decodeLoop() {
        av_log_set_level(AV_LOG_WARNING);
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(video.toFile())) {
            grabber.setPixelFormat(AV_PIX_FMT_RGBA);
            grabber.setAudioChannels(0);
            grabber.start();

            int rawWidth = grabber.getImageWidth();
            int rawHeight = grabber.getImageHeight();
            int targetWidth = rawWidth;
            int targetHeight = rawHeight;

            int overrideWidth = Integer.getInteger("carnation.wallpaper.width", 0);
            int overrideHeight = Integer.getInteger("carnation.wallpaper.height", 0);
            int maxHeight = Integer.getInteger("carnation.wallpaper.maxHeight", 1440);

            if (overrideWidth > 0 && overrideHeight > 0) {
                targetWidth = overrideWidth;
                targetHeight = overrideHeight;
            } else if (maxHeight > 0 && rawHeight > maxHeight) {
                targetHeight = maxHeight;
                targetWidth = (int) Math.round((double) rawWidth * maxHeight / rawHeight);
                targetWidth = (targetWidth + 1) & ~1;
                targetHeight = (targetHeight + 1) & ~1;
            }

            if (targetWidth != rawWidth || targetHeight != rawHeight) {
                grabber.setImageWidth(targetWidth);
                grabber.setImageHeight(targetHeight);
            }

            double fps = grabber.getVideoFrameRate();
            if (fps <= 0.0 || Double.isNaN(fps)) fps = grabber.getFrameRate();
            if (fps <= 0.0 || Double.isNaN(fps)) fps = 60.0;

            int overrideFps = Integer.getInteger("carnation.wallpaper.fps", 0);
            if (overrideFps > 0) fps = overrideFps;

            long frameNanos = (long) (1_000_000_000.0 / fps);
            LOGGER.info("Wallpaper video decoding: {}x{} @ {:.1f} fps (source: {}x{})",
                    targetWidth, targetHeight, fps, rawWidth, rawHeight);

            long nextFrame = System.nanoTime();
            while (decoding) {
                Frame frame = grabber.grabImage();
                if (frame == null) {
                    // Reopen instead of seek: every container loops reliably this way.
                    grabber.restart();
                    nextFrame = System.nanoTime();
                    continue;
                }
                byte[] rgba = copyFrame(frame);
                if (rgba != null) {
                    FrameData next = new FrameData(rgba, frame.imageWidth, frame.imageHeight);
                    FrameData previous = latest.getAndSet(next);
                    if (previous != null && pool.size() < 4) {
                        pool.offer(previous.rgba());
                    }
                }
                nextFrame += frameNanos;
                long remaining = nextFrame - System.nanoTime();
                if (remaining > 0L) {
                    Thread.sleep(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                } else {
                    nextFrame = System.nanoTime();
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable throwable) {
            failed = true;
            LOGGER.warn("Wallpaper video decoder stopped; the vanilla background stays active", throwable);
        }
    }

    private static byte[] copyFrame(Frame frame) {
        if (frame == null || frame.image == null || frame.image.length == 0) return null;
        if (!(frame.image[0] instanceof ByteBuffer source)) return null;
        int width = frame.imageWidth;
        int height = frame.imageHeight;
        int channels = frame.imageChannels;
        int stride = frame.imageStride;
        if (width <= 0 || height <= 0 || (channels != 3 && channels != 4) || stride <= 0) return null;

        byte[] rgba = acquire(width * height * 4);
        ByteBuffer pixels = source.duplicate();
        if (channels == 4) {
            int rowBytes = width * 4;
            if (stride == rowBytes) {
                pixels.position(0);
                pixels.get(rgba, 0, width * height * 4);
            } else {
                for (int row = 0; row < height; row++) {
                    pixels.position(row * stride);
                    pixels.get(rgba, row * rowBytes, rowBytes);
                }
            }
        } else {
            for (int row = 0; row < height; row++) {
                int sourceRow = row * stride;
                for (int column = 0; column < width; column++) {
                    int from = sourceRow + column * 3;
                    int to = (row * width + column) * 4;
                    rgba[to] = pixels.get(from + 2);
                    rgba[to + 1] = pixels.get(from + 1);
                    rgba[to + 2] = pixels.get(from);
                    rgba[to + 3] = (byte) 0xFF;
                }
            }
        }
        return rgba;
    }

    private static byte[] acquire(int size) {
        byte[] buffer;
        while ((buffer = pool.poll()) != null) {
            if (buffer.length == size) return buffer;
        }
        return new byte[size];
    }

    private static void uploadLatest() {
        FrameData frame = latest.getAndSet(null);
        if (frame == null) return;
        int width = frame.width();
        int height = frame.height();
        if (image == null || image.getWidth() != width || image.getHeight() != height) {
            if (texture != null) {
                texture.close();
                texture = null;
            }
            image = new NativeImage(width, height, false);
            texture = new DynamicTexture(
                    (java.util.function.Supplier<String>) () -> "carnation_wallpaper_video", image);
            sampler = RenderSystem.getSamplerCache().getSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, false);
        }
        try {
            ByteBuffer pixels = image.getPixelBytes();
            pixels.clear();
            byte[] data = frame.rgba();
            if (pixels.remaining() >= data.length) {
                pixels.put(data);
                texture.upload();
                uploadedSequence = ++sequence;
            }
        } catch (Throwable throwable) {
            LOGGER.warn("Could not upload a wallpaper video frame", throwable);
            failed = true;
        } finally {
            if (pool.size() < 4) {
                pool.offer(frame.rgba());
            }
        }
    }

    private static Path resolveVideo() throws IOException {
        String property = System.getProperty("carnation.wallpaper.video");
        if (property != null && !property.isBlank()) {
            Path path = Path.of(property);
            if (Files.isRegularFile(path)) return path;
        }
        Path folder = resolveFolder();
        if (folder == null) return null;

        Path project = folder.resolve("project.json");
        if (Files.isRegularFile(project)) {
            JsonObject json = JsonParser.parseString(Files.readString(project, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.has("file") && json.get("file").isJsonPrimitive()) {
                Path candidate = folder.resolve(json.get("file").getAsString());
                if (Files.isRegularFile(candidate)) return candidate;
            }
        }
        try (Stream<Path> stream = Files.list(folder)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".mp4"))
                    .sorted(Comparator.comparing(Path::toString))
                    .findFirst().orElse(null);
        }
    }

    private static Path resolveFolder() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(DEFAULT_FOLDER);
            if (Files.isDirectory(candidate)) return candidate;
            dir = dir.getParent();
        }
        return null;
    }

    /** Stops decoding and releases the GPU texture before the render device shuts down. */
    public static synchronized void shutdown() {
        decoding = false;
        if (decoder != null) {
            decoder.interrupt();
            try {
                decoder.join(3000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            decoder = null;
        }
        if (texture != null) {
            texture.close();
            texture = null;
        }
        image = null;
        latest.set(null);
        pool.clear();
    }
}
