package wtf.carnation.codex;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class CodexAgentGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        Path directory = Path.of(System.getProperty("java.io.tmpdir"),
                "carnation-agent-test-" + System.nanoTime()).toAbsolutePath();
        Path file = directory.resolve("agent-result.txt");
        try {
            JsonObject write = new JsonObject();
            write.addProperty("path", file.toString());
            write.addProperty("content", "before\nagent\n");
            check(CodexTools.execute("write_file", write).contains("已写入"), "write_file failed");

            JsonObject read = new JsonObject();
            read.addProperty("path", file.toString());
            check(CodexTools.execute("read_file", read).contains("before\nagent"), "read_file failed");

            JsonObject edit = new JsonObject();
            edit.addProperty("path", file.toString());
            edit.addProperty("old_string", "before");
            edit.addProperty("new_string", "after");
            check(CodexTools.execute("edit_file", edit).contains("替换 1 处"), "edit_file failed");
            check(Files.readString(file).equals("after\nagent\n"), "edited content mismatch");

            JsonObject list = new JsonObject();
            list.addProperty("path", directory.toString());
            check(CodexTools.execute("list_files", list).contains("agent-result.txt"), "list_files failed");

            JsonObject command = new JsonObject();
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            command.addProperty("command", windows ? "echo agent-ok" : "printf agent-ok");
            command.addProperty("working_directory", directory.toString());
            command.addProperty("timeout_seconds", 10);
            String result = CodexTools.execute("run_command", command);
            check(result.contains("退出码: 0"), "run_command returned a failure: " + result);
            check(result.contains("agent-ok"), "run_command output missing: " + result);
            System.out.println("[CodexAgentGameTest] Absolute-path file tools and run_command passed");
        } catch (Exception e) {
            throw new RuntimeException("Codex agent tool test failed", e);
        } finally {
            try {
                Files.deleteIfExists(file);
                Files.deleteIfExists(directory);
            } catch (Exception e) {
                throw new RuntimeException("Could not clean the Codex agent test directory", e);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
