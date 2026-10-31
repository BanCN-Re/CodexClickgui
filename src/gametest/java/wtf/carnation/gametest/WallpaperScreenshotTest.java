package wtf.carnation.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Lands on the title screen with the Wallpaper Engine background active and saves two screenshots
 * a few seconds apart: the birds and the shake/water-wave shader must have moved between them.
 * The wallpaper log line ("Wallpaper scene loaded: ...") in the run log names the parsed layers.
 */
public class WallpaperScreenshotTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        // The first extractBackground call decodes and uploads every scene texture; give it time.
        // The birds spawn off-canvas and drift in over tens of seconds, so the late shot waits a
        // while to catch them plus several full shake/wave cycles.
        context.waitTicks(60);
        System.out.println("[WallpaperScreenshotTest] "
                + context.takeScreenshot(TestScreenshotOptions.of("wallpaper_title_early")));
        context.waitTicks(240);
        System.out.println("[WallpaperScreenshotTest] "
                + context.takeScreenshot(TestScreenshotOptions.of("wallpaper_title_late")));
    }
}
