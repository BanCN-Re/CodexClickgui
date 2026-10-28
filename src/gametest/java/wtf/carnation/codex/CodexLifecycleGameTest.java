package wtf.carnation.codex;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import wtf.carnation.ui.codex.CodexScreen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CodexLifecycleGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        try {
            CodexChat.ensureLoaded();
            setState(CodexChat.State.THINKING);
            AtomicBoolean cancelled = cancelled();
            cancelled.set(false);
            List<CodexChat.Msg> before = CodexChat.messages();

            CodexChat.newSession();
            check(CodexChat.state() == CodexChat.State.THINKING, "newSession changed the active state");
            check(!cancelled.get(), "newSession cancelled the active turn");
            check(CodexChat.messages().equals(before), "newSession cleared the active messages");

            CodexChat.openSession(Path.of("ignored-while-thinking.jsonl"));
            check(CodexChat.state() == CodexChat.State.THINKING, "openSession changed the active state");
            check(!cancelled.get(), "openSession cancelled the active turn");
            check(CodexChat.messages().equals(before), "openSession cleared the active messages");

            context.runOnClient(mc -> mc.gui.setScreen(new CodexScreen()));
            context.waitTicks(2);
            context.runOnClient(mc -> {
                if (mc.gui.screen() instanceof CodexScreen screen) screen.onClose();
            });
            context.waitTicks(40);
            check(CodexChat.state() == CodexChat.State.THINKING, "Closing ClickGUI changed the active state");
            check(!cancelled.get(), "Closing ClickGUI cancelled the active turn");
            check(CodexChat.messages().equals(before), "Closing ClickGUI cleared the active messages");

            setState(CodexChat.State.IDLE);
            CodexChat.newSession();
            check(CodexChat.messages().isEmpty(), "newSession did not clear an idle conversation");

            CodexAccount account = CodexAccount.parse("{\"tokens\":{\"access_token\":\"preview\"}}");
            Method buildRequest = CodexChat.class.getDeclaredMethod("buildRequest", CodexAccount.class);
            buildRequest.setAccessible(true);
            HttpRequest request = (HttpRequest) buildRequest.invoke(null, account);
            check(request.timeout().orElseThrow().toSeconds() == 600,
                    "Unexpected Responses timeout: " + request.timeout().orElseThrow());
            System.out.println("[CodexLifecycleGameTest] Session switching, ClickGUI close and timeout passed");
        } catch (Exception e) {
            throw new RuntimeException("Codex lifecycle test failed", e);
        } finally {
            try {
                setState(CodexChat.State.IDLE);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static AtomicBoolean cancelled() throws ReflectiveOperationException {
        Field field = CodexChat.class.getDeclaredField("cancelled");
        field.setAccessible(true);
        return (AtomicBoolean) field.get(null);
    }

    private static void setState(CodexChat.State state) throws ReflectiveOperationException {
        Field field = CodexChat.class.getDeclaredField("state");
        field.setAccessible(true);
        field.set(null, state);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
