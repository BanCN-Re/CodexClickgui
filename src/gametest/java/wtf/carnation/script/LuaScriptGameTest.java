package wtf.carnation.script;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import wtf.carnation.Client;
import wtf.carnation.codex.CodexTools;
import wtf.carnation.event.impl.network.ChatEvent;
import wtf.carnation.module.Module;

import java.nio.file.Files;

public final class LuaScriptGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        LuaScriptManager manager = Client.instance.getLuaScriptManager();
        try {
            check(manager.findLoaded("KillAura") != null, "Bundled KillAura.lua was not registered");
            String source = """
                    module:name("LuaSmoke")
                    module:category("Misc")
                    module:description("Real client Lua callback test")
                    local range = module:number("Range", 2.0, 1.0, 4.0, 0.5)
                    local flag = module:bool("Flag", true)
                    local mode = module:mode("Mode", {"A", "B"}, "A")
                    local System = luajava.bindClass("java.lang.System")
                    module:onUpdate(function()
                        System:setProperty("carnation.lua.smoke", mode:get() .. ":" .. string.format("%.1f", range:getFloat()) .. ":" .. tostring(flag:get()))
                    end)
                    module:onRender2D(function(graphics, partialTicks)
                        System:setProperty("carnation.lua.ui", tostring(graphics ~= nil) .. ":" .. tostring(ui:textWidth("Carnation") > 0) .. ":" .. tostring(ui:ellipsize("Carnation client API", 40) ~= nil))
                    end)
                    module:onChat(function(message)
                        System:setProperty("carnation.lua.chat", message)
                        return false
                    end)
                    """;
            JsonObject run = new JsonObject();
            run.addProperty("code", source);
            run.addProperty("file_name", "LuaSmoke.lua");
            String loaded = context.computeOnClient(mc -> CodexTools.execute("run_lua", run));
            check(loaded.contains("已加载"), "run_lua failed: " + loaded);
            Module module = context.computeOnClient(mc -> Client.instance.getModuleManager().getModuleByName("LuaSmoke"));
            check(module instanceof LuaModule, "Lua source did not create a LuaModule");
            check(module.getValues().size() == 3, "Lua settings were not registered");
            JsonObject probe = new JsonObject();
            probe.addProperty("code", "return 2 + 2");
            check(context.computeOnClient(mc -> CodexTools.execute("probe_lua", probe)).contains("4"),
                    "probe_lua returned the wrong value");
            try (TestSingleplayerContext world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksRender();
                JsonObject verify = new JsonObject();
                verify.addProperty("name", "LuaSmoke");
                verify.addProperty("ticks", 4);
                java.util.concurrent.CompletableFuture<String> verification = verifyAsync(verify);
                context.waitTicks(8);
                String verificationResult = verification.get(15, java.util.concurrent.TimeUnit.SECONDS);
                check(verificationResult.contains("验证通过"), "verify_lua failed: " + verificationResult);
                String callback = System.getProperty("carnation.lua.smoke");
                check("A:2.0:true".equals(callback), "onUpdate did not run with Lua settings: " + callback);
                String uiCallback = System.getProperty("carnation.lua.ui");
                check("true:true:true".equals(uiCallback), "Lua UI callback failed: " + uiCallback);
                context.runOnClient(mc -> {
                    module.setState(true);
                    ChatEvent chat = new ChatEvent(".lua-event");
                    Client.instance.getEventManager().call(chat);
                    check(chat.isCancelled(), "onChat(false) did not cancel the outgoing message");
                    module.setState(false);
                });
                check(".lua-event".equals(System.getProperty("carnation.lua.chat")),
                        "onChat did not receive the outgoing message");
                check(!module.isEnabled(), "verify_lua did not restore the original module state");
                check(context.computeOnClient(mc -> CodexTools.execute("list_lua", new JsonObject())).contains("LuaSmoke"),
                        "list_lua missed the module");
                check(context.computeOnClient(mc -> CodexTools.execute("unload_lua", name("LuaSmoke"))).contains("已卸载"),
                        "unload_lua failed");
                check(Client.instance.getModuleManager().getModuleByName("LuaSmoke") == null, "Lua module stayed registered");
                JsonObject badRun = new JsonObject();
                badRun.addProperty("code", "module:name(\"BadLua\")\nmodule:onUpdate(function() local missing = nil; missing() end)");
                badRun.addProperty("file_name", "BadLua.lua");
                check(context.computeOnClient(mc -> CodexTools.execute("run_lua", badRun)).contains("已加载"),
                        "BadLua fixture did not load");
                JsonObject badVerify = new JsonObject();
                badVerify.addProperty("name", "BadLua");
                badVerify.addProperty("ticks", 2);
                java.util.concurrent.CompletableFuture<String> badVerification = verifyAsync(badVerify);
                context.waitTicks(6);
                String badResult = badVerification.get(15, java.util.concurrent.TimeUnit.SECONDS);
                check(badResult.contains("验证失败") && badResult.contains("attempt to call nil"),
                        "verify_lua did not return actionable failure evidence: " + badResult);
                context.computeOnClient(mc -> manager.unload("BadLua"));

                String chatLoaded = context.computeOnClient(mc -> {
                    Client.instance.getCommandManager().execute("lua module:name(\"ChatSmoke\")");
                    return manager.listScripts();
                });
                check(chatLoaded.contains("ChatSmoke"), "Chat .lua command did not load the module: " + chatLoaded);
                context.computeOnClient(mc -> manager.unload("ChatSmoke"));

                Module killAura = context.computeOnClient(mc -> Client.instance.getModuleManager().getModuleByName("KillAura"));
                context.runOnClient(mc -> killAura.setState(true));
                context.waitTicks(4);
                check(killAura.isEnabled(), "KillAura.lua failed during a real player tick");
                context.runOnClient(mc -> killAura.setState(false));
                context.runOnClient(mc -> {
                    mc.player.setDeltaMovement(0.1, 0.0, 0.0);
                    double boosted = new LuaApi().boostHorizontal(3.0, 1.5);
                    check(boosted > 0.29, "Horizontal boost returned the original speed: " + boosted);
                    check(mc.player.getDeltaMovement().x > 0.29, "Horizontal boost did not change player velocity");
                    mc.player.setDeltaMovement(0.0, 0.0, 0.0);
                });
                context.runOnClient(mc -> {
                    LuaApi api = new LuaApi();
                    // The player stands on solid ground, so the block below is not replaceable;
                    // the call must still return a known status instead of throwing.
                    String status = api.placeBlockAdjacent(
                            mc.player.getX(), mc.player.getY() - 1.0, mc.player.getZ());
                    check(java.util.Set.of("placed", "not_replaceable", "no_block", "no_face")
                                    .contains(status),
                            "placeBlockAdjacent returned an unexpected status: " + status);
                    check(api.hotbarSlot() == mc.player.getInventory().getSelectedSlot(),
                            "hotbarSlot did not report the selected slot");
                    check(api.findHotbarBlock() >= -1, "findHotbarBlock returned an invalid slot");
                    api.lookAtPos(mc.player.getX(), mc.player.getY() + 1.0, mc.player.getZ());
                    check(api.position() != null && api.eyePosition() != null && api.velocity() != null,
                            "player position APIs returned null");
                    check(api.health() >= 0 && api.maxHealth() > 0 && api.foodLevel() >= 0,
                            "player state APIs returned invalid values");
                    check(api.entities(8, "all") != null && api.entities(8, "players") != null,
                            "entity list APIs returned null");
                    check(api.blockName(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ()) != null,
                            "blockName returned null");
                    check(api.windowWidth() > 0 && api.windowHeight() > 0 && api.fps() >= 0,
                            "window/fps APIs returned invalid values");
                    api.setMovementInput(false, false, false, false, false, false, false);
                    api.setSneaking(true);
                    check(api.isSneaking(), "setSneaking(true) did not apply");
                    api.setSneaking(false);
                    check(!api.isSneaking(), "setSneaking(false) did not apply");
                });
            }
            System.out.println("[LuaScriptGameTest] run_lua, settings, onUpdate, unload, KillAura and block APIs passed");
        } catch (Exception e) {
            throw new RuntimeException("Lua script integration test failed", e);
        } finally {
            System.clearProperty("carnation.lua.smoke");
            System.clearProperty("carnation.lua.ui");
            System.clearProperty("carnation.lua.chat");
            context.runOnClient(mc -> manager.unload("LuaSmoke"));
            try {
                Files.deleteIfExists(manager.directory().resolve("LuaSmoke.lua"));
            } catch (Exception e) {
                throw new RuntimeException("Could not clean the Lua test script", e);
            }
        }
    }

    private static java.util.concurrent.CompletableFuture<String> verifyAsync(JsonObject args) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return CodexTools.execute("verify_lua", args);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static JsonObject name(String name) {
        JsonObject args = new JsonObject();
        args.addProperty("name", name);
        return args;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
