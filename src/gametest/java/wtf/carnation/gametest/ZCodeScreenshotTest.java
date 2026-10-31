package wtf.carnation.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.TitleScreen;
import wtf.carnation.codex.CodexChat;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.ui.codex.CodexScreen;
import wtf.carnation.ui.codex.ZCodeScreen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opens the ZCode ClickGUI and the reasoning slider in a real client and screenshots each state,
 * for comparison with the ZCode reference. Run with {@code gradlew runClientGameTest}.
 *
 * <p>The window is never resized (the Vulkan title wallpaper loses the device on a swapchain
 * rebuild), so clicks are mapped from design pixels with the same fit the screens use.
 */
public class ZCodeScreenshotTest implements FabricClientGameTest {
    /** The composer's bottom row and the slider card above it, in ZCode design pixels. */
    private static final float ROW_Y = 485.6f, CARD_X = 990.8f, CARD_Y = 362.4f;

    private float scale, originX, originY;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        context.waitTicks(20);
        try {
            Files.createDirectories(Path.of("Carnation"));
            Files.writeString(Path.of("Carnation", "codex-account.json"), "{\"type\":\"carnation-codex-account\","
                    + "\"access_token\":\"ui-preview-token\",\"email\":\"preview@carnation.ui\","
                    + "\"plan\":\"plus\",\"expires_at\":0,\"model\":\"gpt-6.1-sol\",\"effort\":\"high\"}",
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Could not seed the preview account", e);
        }
        context.runOnClient(mc -> CodexChat.ensureLoaded());

        // The Codex window's composer with the new trigger, then its slider card.
        context.setScreen(CodexScreen::new);
        context.waitTicks(25);
        layout(context, 1551, 1115);
        click(context, 1240, 1062);
        context.waitTicks(15);
        shot(context, "z02_codex_slider");
        context.setScreen(() -> null);
        context.waitTicks(5);

        context.setScreen(ZCodeScreen::new);
        context.waitTicks(25);
        layout(context, 1568, 802);
        shot(context, "z01_home");
        click(context, 1170, ROW_Y);
        context.waitTicks(15);
        shot(context, "z03_slider");

        // Drag to Ultra: the warning flashes, then the purple fill settles.
        click(context, CARD_X + 250, CARD_Y + 78);
        context.waitTicks(6);
        shot(context, "z04_ultra_warning");
        context.waitTicks(45);
        shot(context, "z05_ultra");

        click(context, CARD_X + 21, CARD_Y + 21);
        context.waitTicks(20);
        shot(context, "z06_ultra_fast");

        click(context, CARD_X + 136, CARD_Y + 25);
        context.waitTicks(20);
        shot(context, "z07_models");

        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(10);
        context.getInput().typeChars("spr");
        context.waitTicks(15);
        shot(context, "z08_search");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.setScreen(() -> null);
        context.runOnClient(mc -> CodexChat.setEffort(CodexChat.DEFAULT_EFFORT));
    }

    /** The same placement CodexScreen.extractRenderState computes for a design of w x h. */
    private void layout(ClientGameTestContext context, float w, float h) {
        context.runOnClient(mc -> {
            int ww = mc.getWindow().getWidth(), wh = mc.getWindow().getHeight();
            float fit = Math.min(ww * 0.95f / w, wh * 0.95f / h);
            scale = Math.min(UiFont.useScale(fit), fit);
            originX = Math.round((ww - w * scale) / 2);
            originY = Math.round((wh - h * scale) / 2);
            System.out.println("[ZCodeScreenshotTest] window " + ww + "x" + wh + " scale " + scale);
        });
    }

    private static void shot(ClientGameTestContext context, String name) {
        System.out.println("[ZCodeScreenshotTest] " + context.takeScreenshot(TestScreenshotOptions.of(name)));
    }

    private void click(ClientGameTestContext context, float x, float y) {
        context.getInput().setCursorPos(originX + x * scale, originY + y * scale);
        context.waitTick();
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTick();
    }
}
