package wtf.carnation.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * The terminal cues from RhineLabUI's {@code src/audio.ts} (MIT), rendered offline by
 * {@code tools/build_rhine_cues.py}: brand, confirm, scan, welcome, text, array, open, the glass
 * tick and the typing click.
 */
public final class RhineSounds {
    private static final SoundEvent KEY = event("ui_tick");
    private static final SoundEvent GLASS = event("ui_glass");
    private static final SoundEvent BRAND = event("brand");
    private static final SoundEvent CONFIRM = event("confirm");
    private static final SoundEvent SCAN = event("scan");
    private static final SoundEvent WELCOME = event("welcome");
    private static final SoundEvent TEXT = event("text");
    private static final SoundEvent ARRAY = event("array");
    private static final SoundEvent TICKS = event("ui_ticks");
    private static final SoundEvent[] VOICE = {
            event("voice_1"), event("voice_2"), event("voice_3"),
            event("voice_4"), event("voice_5"), event("voice_6")
    };

    private RhineSounds() {
    }

    public static void key() {
        play(KEY, 0.45f);
    }

    public static void tick() {
        play(GLASS, 0.40f);
    }

    public static void brand() {
        play(BRAND, 0.85f);
    }

    public static void confirm() {
        play(CONFIRM, 0.85f);
    }

    public static void scan() {
        play(SCAN, 0.85f);
    }

    public static void welcome() {
        play(WELCOME, 0.9f);
    }

    public static void text() {
        play(TEXT, 0.75f);
    }

    public static void array() {
        play(ARRAY, 0.85f);
    }

    public static void ticksRun() {
        play(TICKS, 0.55f);
    }

    /** Plays one of the six English terminal lines (1..6). */
    public static void voice(int line) {
        if (line < 1 || line > 6) return;
        play(VOICE[line - 1], 0.95f);
    }

    private static SoundEvent event(String path) {
        return SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath("carnation", path));
    }

    private static void play(SoundEvent event, float volume) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getSoundManager() != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, volume, 1.0f));
            }
        } catch (Throwable ignored) {
            // Sound is decorative; the boot must survive a half-initialised sound engine.
        }
    }
}
