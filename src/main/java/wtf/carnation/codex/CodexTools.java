package wtf.carnation.codex;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import wtf.carnation.Client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class CodexTools {
    private static final int MAX_LIST_ENTRIES = 500;
    private static final int MAX_READ_CHARS = 200_000;
    private static final int MAX_COMMAND_OUTPUT_CHARS = 200_000;
    private static final int MAX_WEB_CHARS = 30_000;
    private static final int MAX_GREP_HITS = 200;
    private static final Pattern RESULT_LINK = Pattern.compile("<a[^>]*class=\"result__a\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern RESULT_SNIPPET = Pattern.compile("<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern RESULT_HREF = Pattern.compile("href=\"([^\"]+)\"");
    private static volatile Process runningProcess;

    private CodexTools() {
    }

    public static JsonArray definitions() {
        JsonArray tools = new JsonArray();

        JsonObject listProperties = new JsonObject();
        listProperties.add("path", property("string", "目录路径。支持绝对路径；相对路径基于 Carnation/Work，空或 . 表示默认工作目录"));
        tools.add(tool("list_files", "列出任意本地目录中的文件与子目录。", listProperties, "path"));

        JsonObject readProperties = new JsonObject();
        readProperties.add("path", property("string", "要读取的文本文件路径，支持绝对路径和相对路径"));
        readProperties.add("offset", property("integer", "从第几个字符开始读取，默认 0"));
        readProperties.add("limit", property("integer", "最多读取多少个字符，默认及上限为 200000"));
        tools.add(tool("read_file", "读取任意本地文本文件，可通过偏移分段读取大文件。", readProperties, "path"));

        JsonObject writeProperties = new JsonObject();
        writeProperties.add("path", property("string", "要创建或覆盖的文本文件路径，支持绝对路径和相对路径"));
        writeProperties.add("content", property("string", "要写入的完整文件内容"));
        tools.add(tool("write_file", "在任意本地路径创建或覆盖 UTF-8 文本文件，父目录自动创建。", writeProperties, "path", "content"));

        JsonObject editProperties = new JsonObject();
        editProperties.add("path", property("string", "要编辑的文本文件路径"));
        editProperties.add("old_string", property("string", "必须精确匹配的原文，包括空白和换行"));
        editProperties.add("new_string", property("string", "替换后的文本"));
        editProperties.add("replace_all", property("boolean", "是否替换全部匹配项，默认只替换第一项"));
        tools.add(tool("edit_file", "对任意本地 UTF-8 文本文件做精确字符串替换。", editProperties,
                "path", "old_string", "new_string"));

        JsonObject commandProperties = new JsonObject();
        commandProperties.add("command", property("string", "要执行的完整系统命令"));
        commandProperties.add("working_directory", property("string", "命令工作目录，支持绝对路径和相对路径，默认 Carnation/Work"));
        commandProperties.add("timeout_seconds", property("integer", "超时秒数，默认 120，最大 3600"));
        tools.add(tool("run_command",
                "以当前操作系统用户的完整权限执行本地命令。Windows 使用 cmd.exe，其他系统使用 /bin/sh。返回退出码及合并后的标准输出和错误输出。",
                commandProperties, "command"));

        JsonObject luaProperties = new JsonObject();
        luaProperties.add("code", property("string", "完整 Lua 源码，支持多行"));
        luaProperties.add("file_name", property("string", "保存到 Carnation/scripts 的文件名，默认 ChatScript.lua；同名文件会热重载"));
        tools.add(tool("run_lua", "保存并立即加载 Lua 游戏模块，返回真实加载结果。", luaProperties, "code"));
        tools.add(tool("reload_lua", "重载 Carnation/scripts 中的全部 Lua 模块并恢复配置。", new JsonObject()));
        JsonObject unloadLuaProperties = new JsonObject();
        unloadLuaProperties.add("name", property("string", "要卸载的 Lua 模块名或脚本文件名"));
        tools.add(tool("unload_lua", "卸载指定 Lua 游戏模块。", unloadLuaProperties, "name"));
        tools.add(tool("list_lua", "列出当前已加载的 Lua 游戏模块及启用状态。", new JsonObject()));
        JsonObject probeLuaProperties = new JsonObject();
        probeLuaProperties.add("code", property("string", "要执行的 Lua 探针代码，可用 return 返回要检查的值"));
        tools.add(tool("probe_lua", "执行 Lua 探针并返回真实值，用于验证 26.3 API、字段和行为，避免猜测接口。",
                probeLuaProperties, "code"));
        JsonObject verifyLuaProperties = new JsonObject();
        verifyLuaProperties.add("name", property("string", "要验证的 Lua 模块名"));
        verifyLuaProperties.add("ticks", property("integer", "真实玩家 Tick 数，默认 20，最大 200"));
        tools.add(tool("verify_lua", "启用模块运行指定真实玩家 Tick，恢复原状态并返回新增日志和运行错误。",
                verifyLuaProperties, "name"));
        JsonObject enabledLuaProperties = new JsonObject();
        enabledLuaProperties.add("name", property("string", "要切换的 Lua 模块名"));
        enabledLuaProperties.add("enabled", property("boolean", "true 启用，false 关闭"));
        tools.add(tool("set_lua_enabled", "启用或关闭指定 Lua 模块。", enabledLuaProperties, "name", "enabled"));

        JsonObject planProperties = new JsonObject();
        planProperties.add("plan", property("array",
                "完整计划步骤列表，每项 {\"step\": 描述, \"status\": \"pending|in_progress|completed\"}；每次提交全量列表"));
        tools.add(tool("update_plan",
                "创建或更新当前任务的步骤计划，用户界面会渲染成计划面板。多步任务开工前先建计划，开始一步置 in_progress，完成后置 completed。",
                planProperties, "plan"));

        JsonObject grepProperties = new JsonObject();
        grepProperties.add("pattern", property("string", "Java 正则表达式"));
        grepProperties.add("path", property("string", "递归搜索的起始目录，默认 Carnation/Work"));
        grepProperties.add("glob", property("string", "文件名过滤，如 *.lua、*.java"));
        grepProperties.add("ignore_case", property("boolean", "忽略大小写，默认 false"));
        tools.add(tool("grep_files",
                "在目录中递归按正则搜索文本文件内容，返回 文件:行号: 内容 列表。在多个文件中定位代码或配置时优先用它。",
                grepProperties, "pattern"));

        JsonObject fetchProperties = new JsonObject();
        fetchProperties.add("url", property("string", "要抓取的完整 http/https URL"));
        tools.add(tool("web_fetch", "抓取网页正文并转成纯文本返回，用于查阅文档或页面内容。", fetchProperties, "url"));

        JsonObject searchProperties = new JsonObject();
        searchProperties.add("query", property("string", "搜索关键词"));
        tools.add(tool("web_search", "网页搜索，返回标题、链接和摘要；需要具体内容时再对结果链接调用 web_fetch。",
                searchProperties, "query"));
        return tools;
    }

    public static String instructions() {
        return """
                你是运行在 Minecraft 客户端内的完整本地 Agent，不只是聊天助手。
                用户已明确授权你在当前操作系统用户权限范围内访问任意本地路径、修改文本文件并执行系统命令。
                默认工作目录是游戏目录下的 Carnation/Work；所有文件和命令工具也接受绝对路径。
                当请求需要查看文件、修改代码、运行程序、构建、测试、调试或处理其他本地任务时，必须主动调用工具完成，不要只把命令或代码交给用户执行。
                修改前先读取相关文件；修改后运行最窄且可靠的验证，并根据真实工具输出继续下一步。工具失败时报告实际错误并换用可行方法，不要虚构执行结果。
                文件工具处理 UTF-8 文本；需要处理其他文件类型、创建目录、移动或删除文件时，可通过 run_command 使用系统命令完成。
                多步任务先用 update_plan 提交步骤计划（每项 step 与 status: pending|in_progress|completed），开工前置 in_progress，完成后置 completed；用户界面会渲染计划面板。
                在多个文件中定位内容用 grep_files，比逐个目录 list_files 快得多。
                需要外部资料时先用 web_search 搜索，再用 web_fetch 读取具体页面正文；两者都失败时如实说明网络受限，不要虚构内容。
                """ + wtf.carnation.script.LuaScriptManager.apiDocumentation();
    }

    public static String execute(String name, JsonObject args) throws IOException {
        return switch (name) {
            case "list_files" -> listFiles(resolve(args, "path", "."));
            case "read_file" -> readFile(resolve(args, "path", "."), intValue(args, "offset", 0),
                    intValue(args, "limit", MAX_READ_CHARS));
            case "write_file" -> writeFile(resolve(args, "path", "."), string(args, "content", ""));
            case "edit_file" -> editFile(resolve(args, "path", "."), string(args, "old_string", ""),
                    string(args, "new_string", ""), booleanValue(args, "replace_all", false));
            case "run_command" -> runCommand(args);
            case "run_lua" -> {
                String result = Client.instance.getLuaScriptManager()
                        .loadFromSource(string(args, "code", ""), string(args, "file_name", "ChatScript.lua"));
                yield result.contains("已加载")
                        ? result + "\n下一步：调用 verify_lua 运行真实 Tick 验证，并针对功能行为增加数值或状态断言。"
                        : result;
            }
            case "reload_lua" -> Client.instance.getLuaScriptManager().reload();
            case "unload_lua" -> Client.instance.getLuaScriptManager().unload(string(args, "name", ""));
            case "list_lua" -> Client.instance.getLuaScriptManager().listScripts();
            case "probe_lua" -> Client.instance.getLuaScriptManager().probe(string(args, "code", ""));
            case "verify_lua" -> Client.instance.getLuaScriptManager()
                    .verify(string(args, "name", ""), intValue(args, "ticks", 20));
            case "set_lua_enabled" -> Client.instance.getLuaScriptManager()
                    .setEnabled(string(args, "name", ""), booleanValue(args, "enabled", false));
            case "update_plan" -> updatePlan(args);
            case "grep_files" -> grepFiles(args);
            case "web_fetch" -> webFetch(string(args, "url", ""));
            case "web_search" -> webSearch(string(args, "query", ""));
            default -> "错误: 未知工具 " + name;
        };
    }

    public static void cancel() {
        Process process = runningProcess;
        if (process != null && process.isAlive()) {
            process.destroy();
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    /** Replaces the whole plan; the GUI reads the raw arguments off the call bubble. */
    private static String updatePlan(JsonObject args) {
        if (!args.has("plan") || !args.get("plan").isJsonArray()) return "错误: plan 必须是步骤数组";
        StringBuilder out = new StringBuilder();
        int total = 0, done = 0, active = 0;
        for (var element : args.getAsJsonArray("plan")) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            String step = string(item, "step", "");
            if (step.isBlank()) continue;
            total++;
            switch (string(item, "status", "pending")) {
                case "completed" -> {
                    done++;
                    out.append("[x] ");
                }
                case "in_progress" -> {
                    active++;
                    out.append("[»] ");
                }
                default -> out.append("[ ] ");
            }
            out.append(step).append('\n');
        }
        if (total == 0) return "错误: plan 至少需要一个 {step, status} 项";
        return "计划已更新（" + done + "/" + total + " 完成，" + active + " 进行中）:\n" + out.toString().strip();
    }

    private static String grepFiles(JsonObject args) throws IOException {
        String pattern = string(args, "pattern", "");
        if (pattern.isEmpty()) return "错误: pattern 不能为空";
        Path root = resolve(args, "path", ".");
        if (!Files.isDirectory(root)) return "错误: 目录不存在: " + root;
        Pattern regex;
        try {
            regex = Pattern.compile(pattern, booleanValue(args, "ignore_case", false) ? Pattern.CASE_INSENSITIVE : 0);
        } catch (RuntimeException e) {
            return "错误: 无效正则: " + e.getMessage();
        }
        String glob = string(args, "glob", "").strip();
        PathMatcher matcher = glob.isEmpty() ? null : FileSystems.getDefault().getPathMatcher("glob:" + glob);
        StringBuilder out = new StringBuilder("搜索 ").append(regex.pattern()).append(" 于 ").append(root).append('\n');
        int hits = 0;
        try (Stream<Path> stream = Files.walk(root, 8)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                if (matcher != null && !matcher.matches(file.getFileName())) continue;
                if (file.toFile().length() > 5_000_000L) continue;
                String relative = root.relativize(file).toString();
                if (relative.contains(".git")) continue;
                int lineNo = 0;
                try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lineNo++;
                        if (regex.matcher(line).find()) {
                            out.append(relative).append(':').append(lineNo).append(": ")
                                    .append(line.strip(), 0, Math.min(line.strip().length(), 240)).append('\n');
                            if (++hits >= MAX_GREP_HITS) {
                                out.append("…（已达 ").append(MAX_GREP_HITS).append(" 条上限）");
                                return out.toString().strip();
                            }
                        }
                    }
                } catch (IOException | RuntimeException ignored) {
                    // unreadable or binary file — skipped
                }
            }
        }
        return hits == 0 ? "没有匹配" : out.toString().strip();
    }

    private static String webFetch(String url) {
        String target = url.strip();
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            return "错误: 仅支持 http/https URL";
        }
        try {
            HttpResponse<String> response = CodexChat.http().send(webRequest(target, 30),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) return "错误: HTTP " + response.statusCode();
            return "URL: " + target + "\n\n" + htmlToText(response.body());
        } catch (Exception e) {
            return "抓取失败: " + e.getMessage();
        }
    }

    private static String webSearch(String query) {
        if (query.isBlank()) return "错误: query 不能为空";
        try {
            String url = "https://html.duckduckgo.com/html/?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpResponse<String> response = CodexChat.http().send(webRequest(url, 20),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return "错误: 搜索服务返回 HTTP " + response.statusCode() + "，可改用 web_fetch 直接抓取已知站点";
            }
            return parseSearchResults(response.body());
        } catch (Exception e) {
            return "搜索失败: " + e.getMessage();
        }
    }

    private static HttpRequest webRequest(String url, int timeoutSeconds) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,text/plain;q=0.8,*/*;q=0.5")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET()
                .build();
    }

    private static String parseSearchResults(String html) {
        Matcher links = RESULT_LINK.matcher(html);
        Matcher snippets = RESULT_SNIPPET.matcher(html);
        StringBuilder out = new StringBuilder();
        int count = 0;
        while (count < 8 && links.find()) {
            String title = stripTags(links.group(1));
            Matcher href = RESULT_HREF.matcher(links.group());
            if (!href.find() || title.isBlank()) continue;
            count++;
            out.append(count).append(". ").append(title).append('\n')
                    .append("   ").append(decodeDdgLink(href.group(1))).append('\n');
            if (snippets.find()) {
                String snippet = stripTags(snippets.group(1));
                if (!snippet.isBlank()) out.append("   ").append(truncate(snippet, 200)).append('\n');
            }
        }
        return count == 0 ? "没有搜索结果（页面结构可能变化，可改用 web_fetch）" : out.toString().strip();
    }

    /** DuckDuckGo wraps result URLs in /l/?uddg=<encoded>; unwrap when present. */
    private static String decodeDdgLink(String href) {
        int at = href.indexOf("uddg=");
        if (at < 0) return href.startsWith("//") ? "https:" + href : href;
        String encoded = href.substring(at + 5);
        int end = encoded.indexOf('&');
        if (end >= 0) encoded = encoded.substring(0, end);
        try {
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return href;
        }
    }

    /** Turns HTML into readable text: drops scripts/styles/tags, keeps line breaks and entities. */
    private static String htmlToText(String html) {
        String text = html
                .replaceAll("(?is)<(script|style|noscript|svg|template)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</(p|div|section|article|li|h[1-6]|tr|blockquote)>", "\n")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&ldquo;", "“").replace("&rdquo;", "”")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" ?\\n ?", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
        if (text.length() > MAX_WEB_CHARS) text = text.substring(0, MAX_WEB_CHARS) + "\n…（正文已截断）";
        return text.isEmpty() ? "（页面无可提取文本）" : text;
    }

    private static String stripTags(String html) {
        return html.replaceAll("(?s)<[^>]+>", "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
                .strip();
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static String listFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return "错误: 目录不存在: " + directory;
        StringBuilder out = new StringBuilder("目录: ").append(directory).append('\n');
        int count = 0;
        try (Stream<Path> children = Files.list(directory)) {
            for (Path child : children.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))).toList()) {
                if (++count > MAX_LIST_ENTRIES) {
                    out.append("…（超过 ").append(MAX_LIST_ENTRIES).append(" 项，已截断）\n");
                    break;
                }
                out.append(Files.isDirectory(child) ? "[目录] " : "[文件] ").append(child);
                if (!Files.isDirectory(child)) out.append("  ").append(Files.size(child)).append('B');
                out.append('\n');
            }
        }
        return count == 0 ? out.append("（空目录）").toString() : out.toString().strip();
    }

    private static String readFile(Path file, int offset, int limit) throws IOException {
        if (!Files.isRegularFile(file)) return "错误: 文件不存在: " + file;
        String text = Files.readString(file, StandardCharsets.UTF_8);
        int start = Math.max(0, Math.min(text.length(), offset));
        int length = Math.max(1, Math.min(MAX_READ_CHARS, limit));
        int end = Math.min(text.length(), start + length);
        StringBuilder out = new StringBuilder()
                .append("路径: ").append(file).append('\n')
                .append("范围: ").append(start).append('-').append(end).append(" / ").append(text.length()).append(" 字符\n\n")
                .append(text, start, end);
        if (end < text.length()) out.append("\n…（后续 ").append(text.length() - end).append(" 个字符，可增大 offset 继续读取）");
        return out.toString();
    }

    private static String writeFile(Path file, String content) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return "已写入 " + file + "（" + content.length() + " 字符）";
    }

    private static String editFile(Path file, String oldString, String newString, boolean replaceAll) throws IOException {
        if (!Files.isRegularFile(file)) return "错误: 文件不存在: " + file;
        if (oldString.isEmpty()) return "错误: old_string 不能为空";
        String text = Files.readString(file, StandardCharsets.UTF_8);
        int occurrences = 0;
        for (int index = text.indexOf(oldString); index >= 0; index = text.indexOf(oldString, index + oldString.length())) {
            occurrences++;
        }
        if (occurrences == 0) return "错误: 未找到 old_string 指定的原文";
        String updated;
        if (replaceAll) {
            updated = text.replace(oldString, newString);
        } else {
            int index = text.indexOf(oldString);
            updated = text.substring(0, index) + newString + text.substring(index + oldString.length());
        }
        Files.writeString(file, updated, StandardCharsets.UTF_8);
        return "已编辑 " + file + "（替换 " + (replaceAll ? occurrences : 1) + " 处）";
    }

    private static String runCommand(JsonObject args) throws IOException {
        String command = string(args, "command", "").strip();
        if (command.isEmpty()) return "错误: command 不能为空";
        Path directory = resolve(args, "working_directory", ".");
        if (!Files.isDirectory(directory)) return "错误: 工作目录不存在: " + directory;
        int timeout = Math.max(1, Math.min(3600, intValue(args, "timeout_seconds", 120)));
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> shell = windows
                ? List.of("cmd.exe", "/d", "/s", "/c", command)
                : List.of("/bin/sh", "-c", command);
        Process process = new ProcessBuilder(shell)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        runningProcess = process;
        StringBuilder output = new StringBuilder();
        int[] totalChars = {0};
        Thread reader = new Thread(() -> drain(process.getInputStream(), output, totalChars), "Carnation-Agent-Command");
        reader.setDaemon(true);
        reader.start();
        try {
            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!completed) {
                process.destroy();
                if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly();
                reader.join(2000);
                return "命令超时（" + timeout + " 秒），已终止。\n" + commandOutput(output, totalChars);
            }
            reader.join(2000);
            return "退出码: " + process.exitValue() + "\n工作目录: " + directory + "\n" + commandOutput(output, totalChars);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return "命令已中断。\n" + commandOutput(output, totalChars);
        } finally {
            runningProcess = null;
        }
    }

    private static void drain(InputStream input, StringBuilder output, int[] totalChars) {
        char[] buffer = new char[8192];
        try (java.io.Reader reader = new java.io.InputStreamReader(input, StandardCharsets.UTF_8)) {
            int read;
            while ((read = reader.read(buffer)) != -1) {
                synchronized (output) {
                    totalChars[0] += read;
                    int remaining = MAX_COMMAND_OUTPUT_CHARS - output.length();
                    if (remaining > 0) output.append(buffer, 0, Math.min(read, remaining));
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static String commandOutput(StringBuilder output, int[] totalChars) {
        String text;
        int total;
        synchronized (output) {
            text = output.toString();
            total = totalChars[0];
        }
        if (text.isEmpty()) return "（无输出）";
        return total > text.length() ? text + "\n…（输出已截断）" : text.stripTrailing();
    }

    private static Path resolve(JsonObject args, String key, String fallback) throws IOException {
        String raw = string(args, key, fallback).strip();
        if (raw.isEmpty()) raw = fallback;
        Path path = Path.of(raw);
        if (path.isAbsolute()) return path.normalize();
        Path root = Client.clientFolder.toPath().resolve("Work");
        Files.createDirectories(root);
        return root.resolve(raw).normalize();
    }

    private static JsonObject tool(String name, String description, JsonObject properties, String... required) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        JsonArray requiredFields = new JsonArray();
        for (String field : required) requiredFields.add(field);
        schema.add("required", requiredFields);
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.addProperty("name", name);
        tool.addProperty("description", description);
        tool.add("parameters", schema);
        return tool;
    }

    private static JsonObject property(String type, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", type);
        property.addProperty("description", description);
        return property;
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json != null && json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsString() : fallback;
    }

    private static int intValue(JsonObject json, String key, int fallback) {
        try {
            return json != null && json.has(key) && json.get(key).isJsonPrimitive()
                    ? json.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean booleanValue(JsonObject json, String key, boolean fallback) {
        try {
            return json != null && json.has(key) && json.get(key).isJsonPrimitive()
                    ? json.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
