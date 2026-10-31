package wtf.carnation.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.TitleScreen;
import wtf.carnation.ui.codex.ApiSettingsScreen;
import wtf.carnation.ui.codex.CodexScreen;

import java.lang.reflect.Field;

/**
 * Clicks the three controls in the account panel's title bar: the API login pill, its gear and the
 * close cross. They used to be covered by the panel-wide background hit, so nothing happened at
 * all until the geometric hit-test learned about them.
 */
public class AccountPanelGameTest implements FabricClientGameTest {
    private static final int WIDTH = 1640, HEIGHT = 1180;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        context.getInput().resizeWindow(WIDTH, HEIGHT);
        context.waitTicks(5);
        context.setScreen(CodexScreen::new);
        context.waitTicks(20);
        CodexScreen screen = context.computeOnClient(mc -> (CodexScreen) mc.gui.screen());

        openPanel(context, screen);
        clickDesign(context, screen, panelX(screen) + panelW() - 42, panelY(screen) + 42);
        context.waitTicks(4);
        check(!accountOpen(screen), "close cross did not close the account panel");
        System.out.println("[AccountPanelGameTest] close cross passed");

        openPanel(context, screen);
        clickDesign(context, screen, panelX(screen) + panelW() - 84, panelY(screen) + 42);
        context.waitTicks(10);
        check(context.computeOnClient(mc -> mc.gui.screen()) instanceof ApiSettingsScreen,
                "gear did not open the API settings, screen is "
                        + context.computeOnClient(mc -> String.valueOf(mc.gui.screen())));
        System.out.println("[AccountPanelGameTest] gear passed");
        context.takeScreenshot(TestScreenshotOptions.of("account_gear_api"));

        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(10);
        check(context.computeOnClient(mc -> mc.gui.screen()) == screen,
                "Escape did not return from the API settings to the Codex window");

        openPanel(context, screen);
        clickDesign(context, screen, panelX(screen) + panelW() - 195, panelY(screen) + 42);
        context.waitTicks(10);
        check(context.computeOnClient(mc -> mc.gui.screen()) instanceof ApiSettingsScreen,
                "login pill did not open the API settings, screen is "
                        + context.computeOnClient(mc -> String.valueOf(mc.gui.screen())));
        System.out.println("[AccountPanelGameTest] login pill passed");

        context.setScreen(TitleScreen::new);
        context.waitTicks(3);
        System.out.println("[AccountPanelGameTest] account panel title bar passed");
    }

    private static void openPanel(ClientGameTestContext context, CodexScreen screen) {
        try {
            Field field = CodexScreen.class.getDeclaredField("accountOpen");
            field.setAccessible(true);
            context.runOnClient(mc -> field.setBoolean(screen, true));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not open the account panel", e);
        }
        context.waitTicks(20);
    }

    private static boolean accountOpen(CodexScreen screen) {
        try {
            Field field = CodexScreen.class.getDeclaredField("accountOpen");
            field.setAccessible(true);
            return field.getBoolean(screen);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not read accountOpen", e);
        }
    }

    private static float panelX(CodexScreen screen) {
        return read(screen, "accountPanelX");
    }

    private static float panelY(CodexScreen screen) {
        return read(screen, "accountPanelY");
    }

    private static float panelW() {
        try {
            Field field = CodexScreen.class.getDeclaredField("ACCOUNT_PANEL_W");
            field.setAccessible(true);
            return field.getFloat(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not read the panel width", e);
        }
    }

    private static float read(CodexScreen screen, String name) {
        try {
            Field field = CodexScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getFloat(screen);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not read " + name, e);
        }
    }

    /** Clicks a design-space point, through the same origin and scale the screen renders with. */
    private static void clickDesign(ClientGameTestContext context, CodexScreen screen, float x, float y) {
        float scale = scale(screen);
        int originX = origin(screen, "originX"), originY = origin(screen, "originY");
        // The test input API takes framebuffer pixels; MouseHandler folds the GUI scale in.
        context.getInput().setCursorPos(originX + x * scale, originY + y * scale);
        context.waitTicks(2);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    private static float scale(CodexScreen screen) {
        try {
            Field field = CodexScreen.class.getDeclaredField("s");
            field.setAccessible(true);
            return field.getFloat(screen);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not read the render scale", e);
        }
    }

    private static int origin(CodexScreen screen, String name) {
        try {
            Field field = CodexScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(screen);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not read " + name, e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
