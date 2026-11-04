package wtf.carnation.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;

/**
 * Continuous Rhine Lab score.
 *
 * <p>Vanilla's {@code MusicManager} only starts a track every 10-20 minutes, so the title would sit
 * in silence most of the time. This player runs its own looping {@link SoundInstance}: menu,
 * survival and creative each get one of the RhineLabUI tracks (replaced in {@code sounds.json}) and
 * the stream repeats until the situation changes.
 */
public final class RhineMusic {
    private static final String MENU = "music.menu";
    private static final String GAME = "music.game";
    private static final String CREATIVE = "music.creative";

    private static SoundInstance current;
    private static String playing;
    private static long startedAt;

    private RhineMusic() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) return;

        String want = minecraft.level == null ? MENU
                : minecraft.player != null && minecraft.player.isCreative() ? CREATIVE : GAME;
        long now = Util.getMillis();

        if (want.equals(playing) && current != null) {
            // Give a fresh stream a moment to become active before assuming it died.
            if (minecraft.getSoundManager().isActive(current) || now - startedAt < 2000) return;
        }

        if (current != null) minecraft.getSoundManager().stop(current);

        SimpleSoundInstance instance = new SimpleSoundInstance(
                Identifier.withDefaultNamespace(want),
                SoundSource.MUSIC, 1.0f, 1.0f,
                SoundInstance.createUnseededRandom(), true, 0,
                SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        minecraft.getSoundManager().play(instance);

        current = instance;
        playing = want;
        startedAt = now;
    }
}
