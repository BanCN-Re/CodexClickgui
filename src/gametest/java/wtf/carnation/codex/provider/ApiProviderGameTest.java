package wtf.carnation.codex.provider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import wtf.carnation.codex.CodexChat;
import wtf.carnation.ui.codex.ApiSettingsScreen;
import wtf.carnation.ui.codex.CodexScreen;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ApiProviderGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/v1/models", exchange -> respond(exchange,
                    "{\"data\":[{\"id\":\"relay-model\",\"display_name\":\"Relay Model\",\"context_length\":128000}]}"));
            server.createContext("/v1/chat/completions", exchange -> {
                exchange.getRequestBody().readAllBytes();
                respond(exchange, "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"hello from local relay\"}}]}");
            });
            server.start();
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";

            ApiProviderConfig config = new ApiProviderConfig();
            config.protocol(ProviderProtocol.OPENAI_CHAT);
            config.apiKey("test-key");
            config.baseUrl(baseUrl);
            config.selectedModel("relay-model");
            config.enabled(true);

            List<ApiModel> models = ApiProviderClient.fetchModels(config);
            check(models.size() == 1 && "relay-model".equals(models.getFirst().id()),
                    "Model fetch did not parse the relay response");
            check(models.getFirst().contextLength() == 128000, "Context length was not parsed");
            ApiProviderClient.Completion completion = ApiProviderClient.complete(config, "system",
                    List.of(ApiMessage.user("hello")), new com.google.gson.JsonArray());
            check(completion.text().contains("hello from local relay"), "Chat completion did not parse");

            config.models(models);
            config.save();
            CodexChat.reloadProvider();
            CodexChat.send("ping");
            for (int i = 0; i < 50 && CodexChat.state() == CodexChat.State.THINKING; i++) context.waitTicks(2);
            check(CodexChat.messages().stream().anyMatch(message ->
                            message.text().contains("hello from local relay")),
                    "Generic provider response did not reach the chat");

            context.setScreen(CodexScreen::new);
            context.waitTicks(5);
            Object codex = context.computeOnClient(mc -> mc.gui.screen());
            context.setScreen(() -> new ApiSettingsScreen((net.minecraft.client.gui.screens.Screen) codex));
            context.waitTicks(5);
            ApiSettingsScreen screen = context.computeOnClient(mc -> (ApiSettingsScreen) mc.gui.screen());
            context.getInput().setCursorPos(clickX(screen, 304), clickY(screen, 193));
            context.waitTicks(2);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            context.getInput().typeChars("old-value");
            context.waitTicks(2);
            // The gametest input API builds KeyEvent(scancode, scancode, 0), which never carries
            // modifiers or the SDL keycode a real keyboard sends, so dispatch the real shape here:
            // A = scancode 4 / keycode 97 / left control held.
            shortcut(context, InputConstants.KEY_A, 'a');
            context.waitTicks(2);
            context.getInput().typeChars("shortcut-value");
            context.waitTicks(3);
            ApiProviderConfig edited = (ApiProviderConfig) field(screen, "config");
            Object focusedField = field(screen, "field");
            check("shortcut-value".equals(edited.apiKey()),
                    "Ctrl+A replacement did not edit the API key; value='" + edited.apiKey()
                            + "' field=" + focusedField);
            System.out.println("[ApiProviderGameTest] " + context.takeScreenshot(
                    TestScreenshotOptions.of("api_settings")));

            // Protocol tabs are laid out from the label widths, so click the hit region itself.
            // The screen owns its own ApiProviderConfig loaded from disk, not the test's object.
            ApiProviderConfig live = (ApiProviderConfig) field(screen, "config");
            clickHit(context, screen, 3);
            context.waitTicks(2);
            check(live.protocol() == ProviderProtocol.ANTHROPIC_MESSAGES,
                    "Anthropic tab click did not switch the protocol: " + live.protocol());
            clickHit(context, screen, 1);
            context.waitTicks(2);
            check(live.protocol() == ProviderProtocol.OPENAI_CHAT,
                    "OpenAI tab click did not switch the protocol: " + live.protocol());
            live.apiKey("test-key");
            live.baseUrl(baseUrl);

            clickDesign(context, screen, 118, 260);
            for (int i = 0; i < 40 && live.models().isEmpty(); i++) context.waitTicks(2);
            check(live.models().size() == 1, "Fetch models button did not load the relay list: "
                    + live.models().size());

            clickDesign(context, screen, 250, 409);
            context.waitTicks(2);
            check("MODEL_ID".equals(String.valueOf(field(screen, "field"))),
                    "Model id cell click did not focus the field: " + field(screen, "field"));

            clickDesign(context, screen, 30, 700);
            context.waitTicks(2);
            check("NONE".equals(String.valueOf(field(screen, "field"))),
                    "Backdrop click did not clear the focus: " + field(screen, "field"));
            clickDesign(context, screen, 30, 700);
            context.waitTicks(2);

            clickDesign(context, screen, 849, 669);
            context.waitTicks(3);
            check(String.valueOf(field(screen, "status")).startsWith("已保存并启用"),
                    "Save button did not save: " + field(screen, "status"));

            context.getInput().setCursorPos(clickX(screen, 1136), clickY(screen, 50));
            context.waitTicks(2);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(6);
            System.out.println("[ApiProviderGameTest] after close click: " + context.computeOnClient(mc ->
                    String.valueOf(mc.gui.screen())));
            context.waitTicks(30);
            check(context.computeOnClient(mc -> mc.gui.screen()) == codex,
                    "API settings close button did not return to the Codex window");
            context.setScreen(TitleScreen::new);
            context.waitTicks(3);
            System.out.println("[ApiProviderGameTest] model fetch, chat completion and API screen passed");
        } catch (Exception e) {
            throw new RuntimeException("API provider test failed", e);
        } finally {
            if (server != null) server.stop(0);
            try {
                java.nio.file.Files.deleteIfExists(ApiProviderConfig.file());
                CodexChat.reloadProvider();
            } catch (Exception ignored) {
            }
        }
    }

    private static void clickDesign(ClientGameTestContext context, ApiSettingsScreen screen,
                                    float designX, float designY) throws ReflectiveOperationException {
        context.getInput().setCursorPos(clickX(screen, designX), clickY(screen, designY));
        context.waitTicks(2);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    @SuppressWarnings("unchecked")
    private static void clickHit(ClientGameTestContext context, ApiSettingsScreen screen, int index)
            throws ReflectiveOperationException {
        // Hit regions are registered while drawing, so this also proves the geometry the screen
        // paints is the geometry it hit-tests.
        Object hit = ((java.util.List<Object>) field(screen, "hits")).get(index);
        float x = floatField(hit, "x") + floatField(hit, "w") / 2;
        float y = floatField(hit, "y") + floatField(hit, "h") / 2;
        clickDesign(context, screen, x, y);
    }

    private static void shortcut(ClientGameTestContext context, int scancode, int keycode) {
        context.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof ApiSettingsScreen screen)) {
                throw new IllegalStateException("API settings screen is not open");
            }
            screen.keyPressed(new KeyEvent(scancode, keycode, InputConstants.MOD_CONTROL));
            return null;
        });
    }

    private static double clickX(ApiSettingsScreen screen, float designX) throws ReflectiveOperationException {
        return integerField(screen, "originX") + designX * floatField(screen, "s");
    }

    private static double clickY(ApiSettingsScreen screen, float designY) throws ReflectiveOperationException {
        return integerField(screen, "originY") + designY * floatField(screen, "s");
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static float floatField(Object owner, String name) throws ReflectiveOperationException {
        return (float) field(owner, name);
    }

    private static int integerField(Object owner, String name) throws ReflectiveOperationException {
        return (int) field(owner, name);
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
