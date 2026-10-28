package wtf.carnation.script;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import wtf.carnation.Client;
import wtf.carnation.InstanceAccess;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.UpdateEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.Value;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class LuaScriptManager implements InstanceAccess {
    private final Path directory = Client.clientFolder.toPath().resolve("scripts");
    private final List<LuaModule> scripts = new ArrayList<>();
    private final List<LuaVerification> pendingVerifications = new ArrayList<>();

    public void loadAll() {
        try {
            Files.createDirectories(directory);
            installExample();
            try (Stream<Path> files = Files.list(directory)) {
                for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".lua")).sorted().toList()) {
                    try {
                        loadInternal(file);
                    } catch (IOException | RuntimeException e) {
                        failure(file.getFileName().toString(), e);
                    }
                }
            }
        } catch (IOException e) {
            Client.logger.error("Could not initialize the Lua script directory {}", directory, e);
        }
    }

    public String load(Path file) {
        return callOnClient(() -> {
            try {
                return loadInternal(file);
            } catch (IOException e) {
                return failure(file.getFileName().toString(), e);
            }
        });
    }

    public String loadFromSource(String source, String fileName) {
        return callOnClient(() -> {
            try {
                Files.createDirectories(directory);
                Path file = directory.resolve(safeFileName(fileName));
                Files.writeString(file, source, StandardCharsets.UTF_8);
                return loadInternal(file);
            } catch (IOException e) {
                return failure(fileName, e);
            }
        });
    }

    public String reload() {
        return callOnClient(() -> {
            unloadAllInternal();
            loadAll();
            Client.instance.getConfigManager().loadAllConfig();
            return "Lua 脚本已重载，共 " + scripts.size() + " 个模块";
        });
    }

    public String unload(String name) {
        return callOnClient(() -> {
            LuaModule module = findScript(name);
            if (module == null) return "未找到 Lua 模块: " + name;
            unloadModule(module);
            return "已卸载 Lua 模块 " + module.getName();
        });
    }

    public String listScripts() {
        return callOnClient(() -> {
            if (scripts.isEmpty()) return "没有已加载的 Lua 模块";
            StringBuilder out = new StringBuilder("Lua 模块 (" + scripts.size() + "): ");
            for (LuaModule module : scripts) {
                out.append('\n').append(module.getName()).append(" [").append(module.file().getFileName())
                        .append(module.isEnabled() ? "] 已启用" : "] 未启用");
            }
            return out.toString();
        });
    }

    public String probe(String code) {
        return callOnClient(() -> {
            Globals globals = JsePlatform.standardGlobals();
            LuaC.install(globals);
            globals.set("api", CoerceJavaToLua.coerce(new LuaApi()));
            LuaValue result = globals.load(new StringReader(code), "probe_lua").call();
            return "类型: " + result.typename() + "\n值: " + result.tojstring();
        });
    }

    public String setEnabled(String name, boolean enabled) {
        return callOnClient(() -> {
            LuaModule module = findScript(name);
            if (module == null) return "未找到 Lua 模块: " + name;
            module.setState(enabled);
            return module.getName() + (module.isEnabled() ? " 已启用" : " 已关闭");
        });
    }

    public String verify(String name, int ticks) {
        if (mc.isSameThread()) return "错误: verify_lua 必须从 AI 后台线程调用";
        int waitTicks = Math.max(1, Math.min(200, ticks));
        CompletableFuture<String> result = new CompletableFuture<>();
        mc.execute(() -> {
            LuaModule module = findScript(name);
            if (module == null) {
                result.complete("验证失败: 未找到 Lua 模块 " + name);
                return;
            }
            if (mc.player == null) {
                result.complete("验证失败: 当前不在游戏世界内，无法运行真实玩家 Tick");
                return;
            }
            boolean previous = module.isEnabled();
            module.setState(true);
            pendingVerifications.add(new LuaVerification(module, previous, waitTicks, logSize(), result));
        });
        try {
            return result.get(waitTicks * 50L + 15_000L, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            mc.execute(() -> pendingVerifications.removeIf(v -> v.result == result));
            return failure("verify_lua", e);
        }
    }

    public String[] scriptNames() {
        return scripts.stream().map(Module::getName).toArray(String[]::new);
    }

    public Path directory() {
        return directory;
    }

    public LuaModule findLoaded(String name) {
        return findScript(name);
    }

    public void unloadAll() {
        if (mc.isSameThread()) unloadAllInternal();
        else callOnClient(() -> {
            unloadAllInternal();
            return "已卸载全部 Lua 模块";
        });
    }

    private String loadInternal(Path file) throws IOException {
        Path normalized = file.toAbsolutePath().normalize();
        LuaModule previous = findByFile(normalized);
        if (previous != null) unloadModule(previous);
        String source = Files.readString(normalized, StandardCharsets.UTF_8);
        ScriptBuilder builder = new ScriptBuilder(stripExtension(normalized.getFileName().toString()));
        Globals globals = JsePlatform.standardGlobals();
        LuaC.install(globals);
        globals.set("module", CoerceJavaToLua.coerce(builder));
        globals.set("api", CoerceJavaToLua.coerce(new LuaApi()));
        globals.load(new StringReader(source), normalized.getFileName().toString()).call();
        if (Client.instance.getModuleManager().getModuleByName(builder.name) != null) {
            throw new IOException("模块名已存在: " + builder.name);
        }
        LuaModule module = new LuaModule(normalized, builder.name, builder.category,
                builder.description, builder.callbacks, builder.values);
        scripts.add(module);
        Client.instance.getModuleManager().registerModule(module);
        Client.logger.info("Loaded Lua module {} from {}", module.getName(), normalized);
        return "已加载 Lua 模块 " + module.getName() + "（" + normalized.getFileName() + "）";
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        for (LuaVerification verification : List.copyOf(pendingVerifications)) {
            if (--verification.ticks > 0) continue;
            pendingVerifications.remove(verification);
            verification.complete();
        }
    }

    private long logSize() {
        try {
            return Files.size(mc.gameDirectory.toPath().resolve("logs").resolve("latest.log"));
        } catch (IOException e) {
            return 0;
        }
    }

    private static String readLog(long marker) {
        Path file = mc.gameDirectory.toPath().resolve("logs").resolve("latest.log");
        try (InputStream input = Files.newInputStream(file)) {
            input.skipNBytes(marker);
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return text.length() <= 8000 ? text : text.substring(text.length() - 8000);
        } catch (IOException e) {
            return "无法读取客户端日志: " + e.getMessage();
        }
    }

    private static final class LuaVerification {
        private final LuaModule module;
        private final boolean previous;
        private final long marker;
        private final CompletableFuture<String> result;
        private int ticks;

        private LuaVerification(LuaModule module, boolean previous, int ticks, long marker,
                                CompletableFuture<String> result) {
            this.module = module;
            this.previous = previous;
            this.ticks = ticks;
            this.marker = marker;
            this.result = result;
        }

        private void complete() {
            boolean stopped = !module.isEnabled();
            String log = readLog(marker);
            boolean error = log.contains("attempt to call nil") || log.contains("failed in on")
                    || log.contains("update failed") || log.contains("Lua operation") || log.contains("执行出错");
            module.setState(previous);
            String status = stopped || error
                    ? "验证失败: " + module.getName() + " 在真实 Tick 中出现运行错误"
                    : "验证通过: " + module.getName() + " 在真实玩家 Tick 中保持运行，未检测到 Lua 错误";
            result.complete(status + "\n已恢复模块原状态: " + (previous ? "启用" : "关闭")
                    + "\n--- 验证期间日志 ---\n" + (log.isBlank() ? "（无新增日志）" : log));
        }
    }

    private void installExample() throws IOException {
        Path example = directory.resolve("KillAura.lua");
        if (Files.exists(example)) return;
        try (InputStream input = LuaScriptManager.class.getResourceAsStream("/assets/carnation/scripts/KillAura.lua")) {
            if (input != null) Files.copy(input, example);
        }
    }

    private void unloadAllInternal() {
        for (LuaModule module : List.copyOf(scripts)) unloadModule(module);
        scripts.clear();
    }

    private void unloadModule(LuaModule module) {
        if (module.isEnabled()) module.setState(false);
        Client.instance.getModuleManager().unregisterModule(module);
        scripts.remove(module);
    }

    private LuaModule findScript(String name) {
        for (LuaModule module : scripts) {
            if (module.getName().equalsIgnoreCase(name)
                    || module.file().getFileName().toString().equalsIgnoreCase(name)) return module;
        }
        return null;
    }

    private LuaModule findByFile(Path file) {
        for (LuaModule module : scripts) {
            if (module.file().equals(file)) return module;
        }
        return null;
    }

    private String callOnClient(Supplier<String> action) {
        if (mc.isSameThread()) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                return failure("Lua", e);
            }
        }
        CompletableFuture<String> result = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                result.complete(action.get());
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        try {
            return result.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            return failure("Lua", e);
        }
    }

    private static String failure(String name, Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        Client.logger.error("Lua operation {} failed", name, cause);
        return "Lua 加载失败: " + cause.getMessage();
    }

    private static String safeFileName(String fileName) {
        String value = fileName == null || fileName.isBlank() ? "ChatScript.lua" : fileName.trim();
        value = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return value.endsWith(".lua") ? value : value + ".lua";
    }

    private static String stripExtension(String name) {
        return name.endsWith(".lua") ? name.substring(0, name.length() - 4) : name;
    }

    public static String apiDocumentation() {
        return """

                Lua 游戏模块：用户要求创建、修改或加载游戏功能时，使用 run_lua 保存并立即注册真实模块；需要更新已有脚本时直接再次 run_lua 同名文件即可热重载，不要只回复 Lua 代码。
                脚本目录是 Carnation/scripts。脚本内提供 module 与 api 两个 Java 对象，方法使用冒号调用；也可使用 luajava 调用其他 Java 类。
                module:name(string)、module:category("Combat|Movement|Player|World|Render|Misc")、module:description(string)。
                module:bool(name, default)、module:number(name, default, min, max, increment)、module:mode(name, {"A", "B"}, default) 创建设置，返回对象可用 :get()、:getFloat()、:getInt()。
                module:onEnable(function() ... end)、module:onDisable(...)、module:onUpdate(...)、module:onRender2D(function(graphics, partialTicks) ... end)、module:onKey(function(key, state) ... end) 注册回调。
                api:isInGame()、api:minecraft()、api:player()、api:world()、api:chat(text)、api:log(text)。
                api:nearestLiving(range, playersOnly) 返回最近 LivingEntity 或 nil；api:canAttack() 检查攻击冷却；api:lookAt(entity)；api:attack(entity)。
                api:lookAtPos(x, y, z) 把视角转向世界坐标。
                api:hotbarSlot() 返回当前快捷栏槽位 0-8；api:setHotbarSlot(i) 切换；api:hotbarStack(i) 返回该槽位 ItemStack；api:findHotbarBlock() 返回快捷栏中第一个方块物品的槽位（没有则 -1）。
                api:setSneaking(bool)、api:isSneaking() 控制潜行状态。
                api:placeBlockAdjacent(x, y, z) 向坐标处的可替换方块（空气、水、草）放置当前选中方块，自动寻找相邻坚硬面并发送合法放置封包，返回 placed/rejected:…/no_block/no_face/not_replaceable/no_player。
                api:boostHorizontal(multiplier, maxSpeed) 按倍率提高当前水平速度并保留垂直速度，返回加速后的速度。直接操作 Vec3 时使用 .x/.y/.z 字段，26.3 没有 getX()/getY()/getZ() 方法。
                示例：
                module:name("KillAura")
                module:category("Combat")
                local range = module:number("Range", 3.0, 1.0, 6.0, 0.1)
                module:onUpdate(function()
                  if not api:isInGame() then return end
                  local target = api:nearestLiving(range:getFloat(), false)
                  if target ~= nil and api:canAttack() then
                    api:lookAt(target)
                    api:attack(target)
                  end
                end)
                搭路（Scaffold）模块标准写法：onUpdate 中取 local below = api:player():blockPosition():below()，检查 api:world():getBlockState(below) 可替换后，用 api:findHotbarBlock() 找方块、api:setHotbarSlot(slot) 切换，再 api:placeBlockAdjacent(below:getX(), below:getY(), below:getZ())，最后恢复原槽位；防掉落可先 api:setSneaking(true)。不要用 luajava 构造 BlockHitResult 或直接调用 gameMode:useItemOn，封包细节由封装处理。
                """;
    }

    public static final class ScriptBuilder {
        private String name;
        private Category category = Category.MISC;
        private String description = "Lua script";
        private final Map<String, LuaValue> callbacks = new HashMap<>();
        private final List<Value<?>> values = new ArrayList<>();

        ScriptBuilder(String name) {
            this.name = name;
        }

        public void name(String value) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("模块名不能为空");
            name = value.trim();
        }

        public void category(String value) {
            for (Category candidate : Category.values()) {
                if (candidate.getName().equalsIgnoreCase(value) || candidate.getDisplayName().equals(value)) {
                    category = candidate;
                    return;
                }
            }
            throw new IllegalArgumentException("未知模块分类: " + value);
        }

        public void description(String value) {
            description = value == null ? "" : value;
        }

        public BoolValue bool(String name, boolean value) {
            BoolValue setting = new BoolValue(name, value);
            values.add(setting);
            return setting;
        }

        public NumberValue number(String name, double value, double min, double max, double increment) {
            NumberValue setting = new NumberValue(name, value, min, max, increment);
            values.add(setting);
            return setting;
        }

        public ModeValue mode(String name, LuaTable modes, String value) {
            String[] options = new String[modes.length()];
            for (int i = 0; i < options.length; i++) options[i] = modes.get(i + 1).tojstring();
            ModeValue setting = new ModeValue(name, options, value);
            values.add(setting);
            return setting;
        }

        public void onEnable(LuaValue callback) {
            callbacks.put("onEnable", callback.checkfunction());
        }

        public void onDisable(LuaValue callback) {
            callbacks.put("onDisable", callback.checkfunction());
        }

        public void onUpdate(LuaValue callback) {
            callbacks.put("onUpdate", callback.checkfunction());
        }

        public void onRender2D(LuaValue callback) {
            callbacks.put("onRender2D", callback.checkfunction());
        }

        public void onKey(LuaValue callback) {
            callbacks.put("onKey", callback.checkfunction());
        }
    }
}
