package wtf.carnation.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.TitleScreen;
import wtf.carnation.Client;
import wtf.carnation.codex.CodexChat;
import wtf.carnation.module.impl.misc.Test;
import wtf.carnation.ui.codex.CodexScreen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opens the Codex ClickGUI in a real client, walks through its main states and saves a
 * screenshot of each, so the look can be checked against the Codex reference without a person
 * at the keyboard. Run with {@code gradlew runClientGameTest}.
 */
public class CodexScreenshotTest implements FabricClientGameTest {
    /** Window size at which the 1551x1115 design is drawn at exactly 1:1. */
    private static final int WIDTH = 1640, HEIGHT = 1180;
    /** Where CodexScreen puts the design's origin in that window: round((window - design) / 2). */
    private static final int ORIGIN_X = Math.round((WIDTH - 1551) / 2f), ORIGIN_Y = Math.round((HEIGHT - 1115) / 2f);

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        context.getInput().resizeWindow(WIDTH, HEIGHT);
        context.waitTicks(5);

        // A throwaway account file so the composer's model picker is live (no network is used).
        try {
            Files.createDirectories(Path.of("Carnation"));
            Files.writeString(Path.of("Carnation", "codex-account.json"), "{\"type\":\"carnation-codex-account\","
                    + "\"access_token\":\"ui-preview-token\",\"email\":\"preview@carnation.ui\","
                    + "\"plan\":\"plus\",\"expires_at\":0,\"model\":\"gpt-6.1-sol\",\"effort\":\"high\"}",
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Could not seed the preview account", e);
        }

        context.setScreen(CodexScreen::new);
        context.waitTicks(25);
        shot(context, "01_home");

        // Hover a card, then open the 移动 category from the sidebar.
        move(context, 640, 690);
        context.waitTicks(10);
        shot(context, "02_card_hover");

        click(context, 200, 275);
        context.waitTicks(20);
        shot(context, "03_category");

        // Open Sprint's settings pane.
        click(context, 640, 690);
        context.waitTicks(25);
        shot(context, "04_pane");

        // Back to everything, open the Test module's pane and type a command with completions.
        click(context, 200, 139);
        context.waitTicks(10);
        click(context, 960, 690);
        context.waitTicks(25);
        context.getInput().typeChars(".t");
        context.waitTicks(12);
        shot(context, "05_command");

        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.getInput().typeChars("spr");
        context.waitTicks(15);
        shot(context, "06_search");

        // The 文件 menu.
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        click(context, 170, 23);
        context.waitTicks(10);
        shot(context, "07_menu");

        context.setScreen(() -> null);

        // The chat view: seed a finished conversation, then open it from the sidebar's 对话 list.
        // The assistant row carries a legacy meta stamp on purpose: it must not render anymore.
        // The thinking + update_plan rows exercise the collapsed summary header and the plan card.
        try {
            Path sessions = Path.of("Carnation", "Work", "sessions");
            Files.createDirectories(sessions);
            Files.writeString(sessions.resolve("session-ui-preview.jsonl"), String.join("\n",
                    "{\"type\":\"message\",\"role\":\"user\",\"text\":\"帮我看看客户端的启动流程\"}",
                    "{\"type\":\"thinking\",\"text\":\"用户想了解启动流程。先从 Client 类入手，梳理初始化顺序：事件与模块注册 → 配置恢复 → 渲染管线。\",\"seconds\":\"6\"}",
                    "{\"type\":\"call\",\"name\":\"update_plan\",\"arguments\":\"{\\\"plan\\\":[{\\\"step\\\":\\\"通读 Client 启动链\\\",\\\"status\\\":\\\"completed\\\"},{\\\"step\\\":\\\"梳理模块加载顺序\\\",\\\"status\\\":\\\"in_progress\\\"},{\\\"step\\\":\\\"汇总输出结论\\\",\\\"status\\\":\\\"pending\\\"}]}\",\"call_id\":\"call_plan1\",\"result\":\"计划已更新（1/3 完成，1 进行中）\"}",
                    "{\"type\":\"message\",\"role\":\"assistant\",\"text\":\"启动从 Client 的客户端初始化开始：先注册事件与模块，再加载配置，最后打开渲染管线。\\n\\n需要的话我可以继续拆每一步。\",\"meta\":\"GPT-6 Luna · 7.4s · 21482→182 tokens\"}",
                    ""), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Could not seed the chat preview session", e);
        }
        context.setScreen(CodexScreen::new);
        context.waitTicks(25);
        click(context, 140, 477); // the 对话 section header expands the session list
        context.waitTicks(15);
        click(context, 160, 560); // the first session row opens it as the main view
        context.waitTicks(30);
        shot(context, "10_chat");

        // Expand the thinking block: its collapsed header sits right below the user bubble.
        click(context, 520, 199);
        context.waitTicks(15);
        shot(context, "13_thinking_expanded");

        // A small game window must still show the whole app, so shrink and check.
        context.getInput().resizeWindow(900, 520);
        context.waitTicks(20);
        shot(context, "11_small_window");
        context.getInput().resizeWindow(WIDTH, HEIGHT);
        context.waitTicks(10);

        // The model picker: the composer's model label opens the list + effort slider.
        click(context, 1240, 1062);
        context.waitTicks(20);
        shot(context, "12_model_popup");
        context.setScreen(() -> null);

        // The archive: seed a second conversation, archive it through the real API, then
        // expand the sidebar's archive section and restore the row from there.
        try {
            Files.writeString(Path.of("Carnation", "Work", "sessions", "session-archive-demo.jsonl"),
                    "{\"type\":\"message\",\"role\":\"user\",\"text\":\"历史任务记录\"}\n",
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Could not seed the archive demo session", e);
        }
        Path demo = CodexChat.sessions().stream()
                .filter(s -> s.file().getFileName().toString().contains("archive-demo"))
                .findFirst().orElseThrow(() -> new RuntimeException("archive demo session not listed"))
                .file();
        String archived = CodexChat.archiveSession(demo);
        if (!archived.startsWith("已归档")) throw new RuntimeException("archive failed: " + archived);
        if (CodexChat.archivedSessions().isEmpty()) {
            throw new RuntimeException("archived list is empty after archiving");
        }
        context.setScreen(CodexScreen::new);
        context.waitTicks(25);
        click(context, 140, 477); // toggle 对话 — then force it open, the flag is static and shared
        forceSessionsExpanded();
        context.waitTicks(15);
        click(context, 160, 636); // the 已归档 row expands the archive list
        context.waitTicks(15);
        shot(context, "16_archived");
        click(context, 160, 674); // the newest archived row restores and opens it
        context.waitTicks(25);
        shot(context, "17_unarchived");
        context.setScreen(() -> null);

        // The no-account state: log out, then the empty chat must show the import call-to-action.
        CodexChat.logout();
        context.setScreen(CodexScreen::new);
        context.waitTicks(25);
        click(context, 140, 477); // toggle 对话 — then force it open, the flag is static and shared
        forceSessionsExpanded();
        context.waitTicks(15);
        click(context, 160, 560); // 开始新对话 → the empty chat with the import CTA
        context.waitTicks(20);
        shot(context, "14_no_account_chat");
        click(context, 775, 577); // the centered 导入账号 button opens the account popup
        context.waitTicks(15);
        shot(context, "15_account_popup");
        context.setScreen(() -> null);

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(mc -> {
                Test test = Client.instance.getModuleManager().getModule(Test.class);
                if (!test.isEnabled()) test.toggle();
            });
            context.waitTicks(20);
            shot(context, "08_hud");
            context.setScreen(CodexScreen::new);
            context.waitTicks(25);
            shot(context, "09_ingame_gui");
            context.setScreen(() -> null);
        }
    }

    private static void shot(ClientGameTestContext context, String name) {
        System.out.println("[CodexScreenshotTest] " + context.takeScreenshot(TestScreenshotOptions.of(name)));
    }

    /** recentOpen is a static field shared across screen instances; force it open before row clicks. */
    private static void forceSessionsExpanded() {
        try {
            java.lang.reflect.Field field = CodexScreen.class.getDeclaredField("recentOpen");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not force the 对话 section open", e);
        }
    }

    /** Moves the cursor to a point in the 1551x1115 design space. */
    private static void move(ClientGameTestContext context, float x, float y) {
        context.getInput().setCursorPos(ORIGIN_X + x, ORIGIN_Y + y);
    }

    private static void click(ClientGameTestContext context, float x, float y) {
        move(context, x, y);
        context.waitTick();
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTick();
    }
}
