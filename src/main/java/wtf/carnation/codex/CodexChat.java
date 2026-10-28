package wtf.carnation.codex;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import wtf.carnation.Client;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * In-game Codex chat: talks to the ChatGPT backend's Responses API with the tokens of an imported
 * account ({@link CodexAccount}), streaming SSE events into a message list the GUI renders. All
 * network work happens on one worker thread; the render thread only reads synchronized snapshots.
 * Tokens are refreshed automatically through auth.openai.com when the backend answers 401.
 */
public final class CodexChat {
    public enum State {NO_ACCOUNT, IDLE, THINKING}

    /** One chat bubble; the assistant's text grows while the answer streams in. */
    public record Msg(Role role, String text, boolean error, String meta) {
        public enum Role {USER, ASSISTANT, CALL, THINKING}
    }

    /** One picker row: the backend slug plus the display name the Codex app shows. */
    public record Model(String slug, String name) {}

    /** The ChatGPT-backend catalog, verified against codex/responses with a ChatGPT account. */
    public static final Model[] MODELS = {
            new Model("gpt-6.1-sol", "GPT-6.1 Sol"),
            new Model("gpt-6-astra", "GPT-6 Astra"),
            new Model("gpt-6-sol", "GPT-6 Sol"),
            new Model("gpt-6-luna", "GPT-6 Luna"),
            new Model("gpt-5.6-sol", "GPT-5.6 Sol"),
            new Model("gpt-5.6-terra", "GPT-5.6 Terra"),
            new Model("gpt-5.6-luna", "GPT-5.6 Luna"),
            new Model("gpt-reserve", "GPT-5.6 Reserve"),
            new Model("gpt-5.5", "GPT-5.5"),
    };
    /** The reasoning ladder the backend accepts, lightest to heaviest. */
    public static final String[] EFFORTS = {"low", "medium", "high", "xhigh", "max"};
    public static final String DEFAULT_MODEL = "gpt-6.1-sol";
    public static final String DEFAULT_EFFORT = "high";

    private static final URI ENDPOINT = URI.create("https://chatgpt.com/backend-api/codex/responses");
    private static final URI TOKEN_ENDPOINT = URI.create("https://auth.openai.com/oauth/token");
    /** The backend keeps no state (store=false), so each turn resends this much history. */
    private static final int HISTORY_LIMIT = 24;

    /** The open conversation's bubbles and API transcript; the references swap on session switch. */
    private static volatile List<Msg> MESSAGES = new ArrayList<>();
    private static volatile List<JsonObject> TRANSCRIPT = new ArrayList<>();
    /**
     * The conversation a running turn belongs to, captured at send time. The stream keeps
     * appending here — and to its own session file — no matter which conversation is open,
     * so switching views can never redirect a background answer into another session.
     */
    private static volatile List<Msg> turnMessages;
    private static volatile List<JsonObject> turnTranscript;
    private static volatile Path turnFile;
    /** One-shot "background turn finished elsewhere" notice the GUI polls each frame. */
    private static volatile String finishedNotice;
    /** function_call items of the response currently streaming; executed once it completes. */
    private static final List<JsonObject> turnCalls = new ArrayList<>();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Carnation-Codex");
        thread.setDaemon(true);
        return thread;
    });
    /** Local ports the popular proxy clients listen on; tried when no system proxy is set. */
    private static final int[] LOCAL_PROXY_PORTS = {7897, 7890, 10809, 2080, 8888, 1080};

    private static volatile CodexAccount account;
    private static volatile State state = State.NO_ACCOUNT;
    private static volatile Usage usage;
    private static volatile CompletableFuture<HttpResponse<Stream<String>>> turn;
    private static final AtomicBoolean cancelled = new AtomicBoolean();
    private static volatile HttpClient http;
    private static volatile long turnStartAt;
    private static volatile boolean turnSaved;
    private static volatile boolean streamStarted;
    private static volatile int agentSteps;
    private static volatile long thinkingStartAt;
    private static Path sessionFile;
    private static boolean loaded;

    /** Hard cap on tool rounds per user message, so a looping agent cannot run forever. */
    private static final int MAX_AGENT_STEPS = 40;
    private static final int MAX_TOOL_RESULT_CHARS = 30_000;
    /** How much of the API-side transcript rides along with each request. */
    private static final int TRANSCRIPT_LIMIT = 100;

    private CodexChat() {
    }

    /** The shared client with the proxy fix; web tools borrow it so they work behind proxies too. */
    public static HttpClient http() {
        HttpClient client = http;
        if (client != null) return client;
        synchronized (CodexChat.class) {
            if (http == null) http = buildClient();
            return http;
        }
    }

    private static HttpClient buildClient() {
        // Read the Windows system proxy (Clash & friends write it); without this flag Java
        // connects directly and chatgpt.com times out on firewalled networks.
        System.setProperty("java.net.useSystemProxies", "true");
        ProxySelector selector;
        try {
            selector = ProxySelector.getDefault();
        } catch (Throwable ignored) {
            selector = ProxySelector.of(null);
        }
        try {
            List<Proxy> proxies = selector.select(URI.create("https://chatgpt.com"));
            if (proxies.size() == 1 && proxies.getFirst().type() == Proxy.Type.DIRECT) {
                ProxySelector detected = detectLocalProxy();
                if (detected != null) selector = detected;
            } else {
                Client.logger.info("Codex chat using the system proxy");
            }
        } catch (Throwable ignored) {
        }
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .proxy(selector)
                .build();
    }

    /** Quick TCP probes of the well-known local proxy ports; the first open one wins. */
    private static ProxySelector detectLocalProxy() {
        for (int port : LOCAL_PROXY_PORTS) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 150);
                Client.logger.info("Codex chat using local proxy 127.0.0.1:{}", port);
                return ProxySelector.of(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    /** Loads a previously imported account from the client folder; call before showing the GUI. */
    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path file = store();
        if (file != null && Files.isRegularFile(file)) {
            try {
                CodexAccount parsed = CodexAccount.fromJson(
                        JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
                if (parsed != null) {
                    account = parsed;
                    state = State.IDLE;
                }
            } catch (Exception e) {
                Client.logger.warn("Could not read the imported Codex account", e);
            }
        }
        restoreLatestSession();
    }

    public static State state() {
        return state;
    }

    public static CodexAccount account() {
        return account;
    }

    /** The model slug chats use: the user's pick, else the default. */
    public static String model() {
        CodexAccount acc = account;
        return acc != null && acc.model() != null ? acc.model() : DEFAULT_MODEL;
    }

    /** The display name of the current model, as the Codex app writes it. */
    public static String modelDisplay() {
        String slug = model();
        for (Model candidate : MODELS) {
            if (candidate.slug().equals(slug)) return candidate.name();
        }
        return slug;
    }

    public static void setModel(String slug) {
        CodexAccount acc = account;
        if (acc == null) return;
        account = acc.withModel(slug, null);
        save();
    }

    public static String effort() {
        CodexAccount acc = account;
        return acc != null && acc.effort() != null ? acc.effort() : DEFAULT_EFFORT;
    }

    public static void setEffort(String effort) {
        CodexAccount acc = account;
        if (acc == null) return;
        account = acc.withModel(null, effort);
        save();
    }

    public static int effortIndex() {
        for (int i = 0; i < EFFORTS.length; i++) {
            if (EFFORTS[i].equals(effort())) return i;
        }
        return 2;
    }

    /** Chinese label for an effort level, matching the app's slider wording. */
    public static String effortLabel(String effort) {
        return switch (effort) {
            case "low" -> "低";
            case "medium" -> "中";
            case "high" -> "高";
            case "xhigh" -> "极高";
            default -> "最大";
        };
    }

    /** Quota snapshot captured from the x-codex-* response headers every turn carries. */
    public record Usage(String plan, int primaryUsedPercent, int primaryWindowMinutes, long primaryResetAt,
                        int secondaryUsedPercent, boolean hasCredits, String creditBalance) {}

    public static Usage usage() {
        return usage;
    }

    /** Reads the quota headers off a responses call; ignores replies without them. */
    private static void captureUsage(HttpResponse<?> response) {
        HttpHeaders headers = response.headers();
        int primary = intHeader(headers, "x-codex-primary-used-percent", -1);
        if (primary < 0) return;
        usage = new Usage(
                headers.firstValue("x-codex-plan-type").orElse(null),
                primary,
                intHeader(headers, "x-codex-primary-window-minutes", 0),
                longHeader(headers, "x-codex-primary-reset-at", 0),
                intHeader(headers, "x-codex-secondary-used-percent", 0),
                Boolean.parseBoolean(headers.firstValue("x-codex-credits-has-credits").orElse("false")),
                headers.firstValue("x-codex-credits-balance").orElse("0"));
    }

    /**
     * Refreshes the quota with one minimal throwaway generation — the only way to read the
     * headers without chatting, since the usage endpoints sit behind Cloudflare (403).
     */
    public static void probeUsage(Runnable onDone) {
        CodexAccount acc = account;
        if (acc == null || state == State.THINKING) {
            if (onDone != null) onDone.run();
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject content = new JsonObject();
                content.addProperty("type", "input_text");
                content.addProperty("text", "hi");
                JsonArray contents = new JsonArray();
                contents.add(content);
                JsonObject item = new JsonObject();
                item.addProperty("type", "message");
                item.addProperty("role", "user");
                item.add("content", contents);
                JsonArray input = new JsonArray();
                input.add(item);
                JsonObject reasoning = new JsonObject();
                reasoning.addProperty("effort", "low");
                JsonObject body = new JsonObject();
                body.addProperty("model", model());
                body.addProperty("instructions", "Reply with one word.");
                body.add("input", input);
                body.add("reasoning", reasoning);
                body.addProperty("max_output_tokens", 16);
                body.addProperty("store", false);
                body.addProperty("stream", true);
                HttpRequest.Builder builder = HttpRequest.newBuilder(ENDPOINT)
                        .header("Authorization", "Bearer " + acc.accessToken())
                        .header("Content-Type", "application/json")
                        .header("OpenAI-Beta", "responses=experimental")
                        .header("originator", "codex_cli_rs")
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
                if (acc.accountId() != null && !acc.accountId().isBlank()) {
                    builder.header("chatgpt-account-id", acc.accountId());
                }
                HttpResponse<Stream<String>> response = http().send(builder.build(), HttpResponse.BodyHandlers.ofLines());
                captureUsage(response);
                try (Stream<String> lines = response.body()) {
                    lines.forEach(ignored -> {
                    });
                }
            } catch (Exception e) {
                Client.logger.warn("Could not refresh the Codex usage", e);
            } finally {
                if (onDone != null) net.minecraft.client.Minecraft.getInstance().execute(onDone);
            }
        }, IO);
    }

    private static int intHeader(HttpHeaders headers, String name, int fallback) {
        String value = headers.firstValue(name).orElse("");
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long longHeader(HttpHeaders headers, String name, long fallback) {
        String value = headers.firstValue(name).orElse("");
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static synchronized List<Msg> messages() {
        return List.copyOf(MESSAGES);
    }

    /** The session file new messages append to, for highlight comparisons in the sidebar. */
    public static synchronized Path currentSessionFile() {
        return sessionFile;
    }

    /** True while a background turn is still appending to this session's file. */
    public static boolean isRunningSession(Path file) {
        return state == State.THINKING && file != null && file.equals(turnFile);
    }

    /** True when the conversation on screen is the one currently streaming. */
    public static boolean thinkingHere() {
        if (state != State.THINKING) return false;
        List<Msg> active = MESSAGES;
        return active != null && active == turnMessages;
    }

    /** One-shot notice for the GUI: a background turn finished in a non-open conversation. */
    public static synchronized String pollFinishedNotice() {
        String notice = finishedNotice;
        finishedNotice = null;
        return notice;
    }

    /** Imports an account from the picked JSON file and persists it in the client folder. */
    public static String importFromFile(Path file) throws IOException {
        CodexAccount parsed = CodexAccount.parse(Files.readString(file, StandardCharsets.UTF_8));
        if (parsed == null) throw new IOException("不是有效的账号 JSON（支持 auth.json 与 sub2api 导出格式）");
        account = parsed;
        state = State.IDLE;
        save();
        return parsed.email();
    }

    /**
     * Imports pasted content: raw JSON when it starts with '{', otherwise treated as a file
     * path and read from disk. Both land in the same store as {@link #importFromFile}.
     */
    public static String importFromText(String text) throws IOException {
        String trimmed = text.strip();
        if (!trimmed.startsWith("{")) return importFromFile(Path.of(trimmed));
        CodexAccount parsed = CodexAccount.parse(trimmed);
        if (parsed == null) throw new IOException("不是有效的账号 JSON（支持 auth.json 与 sub2api 导出格式）");
        account = parsed;
        state = State.IDLE;
        save();
        return parsed.email();
    }

    public static void logout() {
        account = null;
        state = State.NO_ACCOUNT;
        try {
            Path file = store();
            if (file != null) Files.deleteIfExists(file);
        } catch (IOException e) {
            Client.logger.warn("Could not delete the stored Codex account", e);
        }
    }

    /** Queues a user message and starts the agent loop; the caller guards state and account. */
    public static void send(String text) {
        if (state == State.THINKING || account == null) return;
        state = State.THINKING;
        cancelled.set(false);
        turnStartAt = System.currentTimeMillis();
        turnSaved = false;
        streamStarted = false;
        thinkingStartAt = 0;
        agentSteps = 0;
        synchronized (CodexChat.class) {
            // The turn owns this conversation from here on; switching the view swaps
            // MESSAGES/TRANSCRIPT/sessionFile away but never touches these references.
            turnMessages = MESSAGES;
            turnTranscript = TRANSCRIPT;
            turnFile = sessionFile;
            turnCalls.clear();
            if (turnFile == null) {
                turnFile = newSessionPath();
                sessionFile = turnFile; // sidebar highlight points at the conversation written to
            }
            Msg user = new Msg(Msg.Role.USER, text, false, null);
            turnMessages.add(user);
            turnTranscript.add(messageItem("user", text));
            appendEvent(messageEvent(user));
        }
        CompletableFuture.runAsync(() -> runTurn(0), IO);
    }

    /** Stops the running turn; a partial answer stays on screen. */
    public static void cancel() {
        cancelled.set(true);
        CompletableFuture<HttpResponse<Stream<String>>> current = turn;
        if (current != null) current.cancel(true);
        CodexTools.cancel();
        finishTurn();
    }

    public static void clear() {
        newSession();
    }

    /** Starts a blank conversation; a running turn keeps writing to its own session in the background. */
    public static synchronized void newSession() {
        MESSAGES = new ArrayList<>();
        TRANSCRIPT = new ArrayList<>();
        sessionFile = null;
    }

    public record SessionInfo(Path file, String title, long lastModified) {}

    /** Recent conversations for the sidebar; newest first, at most twelve. */
    public static synchronized List<SessionInfo> sessions() {
        Path sessions = sessionDir();
        if (sessions == null || !Files.isDirectory(sessions)) return List.of();
        try (Stream<Path> files = Files.list(sessions)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                    .limit(12)
                    .map(p -> new SessionInfo(p, sessionTitle(p), p.toFile().lastModified()))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Archived conversations for the sidebar's archive section; newest first, at most twelve. */
    public static List<SessionInfo> archivedSessions() {
        Path archived = archiveDir();
        if (archived == null || !Files.isDirectory(archived)) return List.of();
        try (Stream<Path> files = Files.list(archived)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                    .limit(12)
                    .map(p -> new SessionInfo(p, sessionTitle(p), p.toFile().lastModified()))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Moves a conversation out of the recent list; archiving the open one clears the chat. */
    public static synchronized String archiveSession(Path file) {
        Path archived = archiveDir();
        if (archived == null) return "归档失败: 无法创建归档目录";
        if (file == null || !Files.isRegularFile(file)) return "归档失败: 会话不存在";
        try {
            Path target = archived.resolve(file.getFileName());
            moveWith(file, target);
            Path title = titleFile(file);
            if (Files.isRegularFile(title)) moveWith(title, archived.resolve(title.getFileName()));
            if (file.equals(sessionFile)) {
                MESSAGES.clear();
                TRANSCRIPT.clear();
                sessionFile = null;
            }
            return "已归档「" + sessionTitle(target) + "」";
        } catch (IOException e) {
            Client.logger.warn("Could not archive the Codex session", e);
            return "归档失败: " + e.getMessage();
        }
    }

    /** Moves an archived conversation back to the recent list and opens it. */
    public static synchronized String unarchiveSession(Path file) {
        Path sessions = sessionDir();
        if (sessions == null) return "恢复失败: 无法访问会话目录";
        if (file == null || !Files.isRegularFile(file)) return "恢复失败: 会话不存在";
        try {
            Path target = sessions.resolve(file.getFileName());
            moveWith(file, target);
            Path title = titleFile(file);
            if (Files.isRegularFile(title)) moveWith(title, sessions.resolve(title.getFileName()));
            return "已恢复「" + sessionTitle(target) + "」";
        } catch (IOException e) {
            Client.logger.warn("Could not restore the Codex session", e);
            return "恢复失败: " + e.getMessage();
        }
    }

    private static void moveWith(Path from, Path to) throws IOException {
        try {
            Files.move(from, to);
        } catch (IOException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Conversations land here once archived; a subfolder keeps them out of the recent list. */
    private static Path archiveDir() {
        Path sessions = sessionDir();
        if (sessions == null) return null;
        try {
            Path archived = sessions.resolve("archived");
            Files.createDirectories(archived);
            return archived;
        } catch (IOException e) {
            return null;
        }
    }

    private static Path titleFile(Path session) {
        return session.resolveSibling(session.getFileName().toString() + ".title");
    }

    /** The user's custom title if renamed, else the creation timestamp from the file name. */
    public static String sessionTitle(Path file) {
        Path title = titleFile(file);
        if (Files.isRegularFile(title)) {
            try {
                String text = Files.readString(title, StandardCharsets.UTF_8).strip();
                if (!text.isEmpty()) return text;
            } catch (IOException ignored) {
            }
        }
        String name = file.getFileName().toString();
        if (name.startsWith("session-")) name = name.substring("session-".length());
        if (name.endsWith(".jsonl")) name = name.substring(0, name.length() - ".jsonl".length());
        return name;
    }

    /**
     * Loads a conversation from the sidebar; it keeps appending to the same file. A session
     * whose turn is still streaming re-attaches to the live lists instead of replaying a
     * snapshot that would immediately go stale.
     */
    public static synchronized void openSession(Path file) {
        if (state == State.THINKING && file != null && file.equals(turnFile)) {
            MESSAGES = turnMessages;
            TRANSCRIPT = turnTranscript;
            sessionFile = turnFile;
            return;
        }
        MESSAGES = new ArrayList<>();
        TRANSCRIPT = new ArrayList<>();
        sessionFile = file;
        replay(file, 200);
        // Never flip the global state while a background turn is still running.
        if (!MESSAGES.isEmpty() && state != State.THINKING) {
            state = account == null ? State.NO_ACCOUNT : State.IDLE;
        }
    }

    public static synchronized void renameSession(Path file, String title) {
        try {
            Files.writeString(titleFile(file), title.strip(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Client.logger.warn("Could not rename the Codex session", e);
        }
    }

    /** Workspace root: the session logs and everything the client produces live in Carnation/Work. */
    private static Path workDir() {
        try {
            return Client.clientFolder.toPath().resolve("Work");
        } catch (Exception e) {
            return null;
        }
    }

    private static Path sessionDir() {
        Path work = workDir();
        if (work == null) return null;
        try {
            Path sessions = work.resolve("sessions");
            Files.createDirectories(sessions);
            return sessions;
        } catch (IOException e) {
            Client.logger.warn("Could not create the Codex workspace", e);
            return null;
        }
    }

    /** The path a fresh conversation's first event creates. */
    private static Path newSessionPath() {
        Path sessions = sessionDir();
        if (sessions == null) return null;
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
        return sessions.resolve("session-" + stamp + ".jsonl");
    }

    private static synchronized void appendEvent(JsonObject event) {
        if (turnFile == null) turnFile = newSessionPath();
        Path file = turnFile;
        if (file == null) return;
        try {
            Files.writeString(file, event.toString() + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Client.logger.warn("Could not append to the Codex session log", e);
        }
    }

    private static JsonObject messageEvent(Msg message) {
        if (message.role() == Msg.Role.THINKING) {
            JsonObject event = new JsonObject();
            event.addProperty("type", "thinking");
            event.addProperty("text", message.text());
            if (message.meta() != null) event.addProperty("seconds", message.meta());
            return event;
        }
        JsonObject event = new JsonObject();
        event.addProperty("type", "message");
        event.addProperty("role", message.role() == Msg.Role.USER ? "user" : "assistant");
        event.addProperty("text", message.text());
        if (message.error()) event.addProperty("error", true);
        if (message.meta() != null) event.addProperty("meta", message.meta());
        return event;
    }

    /** Replays the newest session file, so reopening the GUI continues the last conversation. */
    private static synchronized void restoreLatestSession() {
        Path sessions = sessionDir();
        if (sessions == null) return;
        Path newest = null;
        try (Stream<Path> files = Files.list(sessions)) {
            newest = files.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified())).orElse(null);
        } catch (IOException ignored) {
        }
        if (newest == null) return;
        sessionFile = newest; // keep appending to the same conversation
        replay(newest, 200);
        if (!MESSAGES.isEmpty()) {
            Client.logger.info("Restored {} messages from {}", MESSAGES.size(), newest.getFileName());
        }
    }

    /** Rebuilds bubbles and the API transcript from a session file, calls included. */
    private static void replay(Path file, int maxLines) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines.subList(Math.max(0, lines.size() - maxLines), lines.size())) {
                line = line.strip();
                if (line.isEmpty()) continue;
                try {
                    JsonObject event = JsonParser.parseString(line).getAsJsonObject();
                    String type = string(event, "type", null);
                    if ("message".equals(type)) {
                        Msg.Role role = "assistant".equals(string(event, "role", "user"))
                                ? Msg.Role.ASSISTANT : Msg.Role.USER;
                        String text = string(event, "text", "");
                        MESSAGES.add(new Msg(role, text,
                                event.has("error") && event.get("error").getAsBoolean(),
                                string(event, "meta", null)));
                        TRANSCRIPT.add(messageItem(role == Msg.Role.ASSISTANT ? "assistant" : "user", text));
                    } else if ("thinking".equals(type)) {
                        MESSAGES.add(new Msg(Msg.Role.THINKING, string(event, "text", ""),
                                false, string(event, "seconds", null)));
                    } else if ("call".equals(type)) {
                        String name = string(event, "name", "?");
                        String args = string(event, "arguments", "{}");
                        String callId = string(event, "call_id", "call_0");
                        String result = string(event, "result", "");
                        MESSAGES.add(new Msg(Msg.Role.CALL, name + "(" + truncate(args.replace("\n", " "), 120)
                                + ") → " + truncate(result.replace("\n", " "), 160), false,
                                "update_plan".equals(name) ? args : null));
                        JsonObject call = new JsonObject();
                        call.addProperty("type", "function_call");
                        call.addProperty("name", name);
                        call.addProperty("arguments", args);
                        call.addProperty("call_id", callId);
                        TRANSCRIPT.add(call);
                        JsonObject output = new JsonObject();
                        output.addProperty("type", "function_call_output");
                        output.addProperty("call_id", callId);
                        output.addProperty("output", result);
                        TRANSCRIPT.add(output);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            Client.logger.warn("Could not replay the Codex session", e);
        }
    }

    private static void runTurn(int attempt) {
        CodexAccount current = account;
        if (current == null || cancelled.get()) {
            finishTurn();
            return;
        }
        streamStarted = false;
        HttpRequest request;
        try {
            request = buildRequest(current);
        } catch (Exception e) {
            fail("请求构建失败: " + e.getMessage());
            return;
        }
        CompletableFuture<HttpResponse<Stream<String>>> future =
                http().sendAsync(request, HttpResponse.BodyHandlers.ofLines());
        turn = future;
        future.whenCompleteAsync((response, error) -> {
            if (cancelled.get()) {
                finishTurn();
                return;
            }
            if (error != null) {
                fail(networkMessage(error));
                return;
            }
            if (response.statusCode() == 401 && attempt == 0 && refresh(current)) {
                runTurn(1);
                return;
            }
            if (response.statusCode() != 200) {
                fail(errorBody(response.statusCode(), response.body()));
                return;
            }
            try (Stream<String> lines = response.body()) {
                lines.forEach(CodexChat::onLine);
            } catch (Exception e) {
                fail("连接中断: " + e.getMessage());
                return;
            }
            afterTurn();
        }, IO);
    }

    private static HttpRequest buildRequest(CodexAccount acc) {
        JsonArray input = new JsonArray();
        synchronized (CodexChat.class) {
            int from = Math.max(0, turnTranscript.size() - TRANSCRIPT_LIMIT);
            for (JsonObject item : turnTranscript.subList(from, turnTranscript.size())) {
                input.add(item);
            }
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", model());
        String instructions = instructions();
        String agentInstructions = CodexTools.instructions();
        body.addProperty("instructions", instructions == null || instructions.isBlank()
                ? agentInstructions : instructions + "\n\n" + agentInstructions);
        body.add("input", input);
        body.add("tools", agentTools());
        body.addProperty("tool_choice", "auto");
        JsonObject reasoning = new JsonObject();
        reasoning.addProperty("effort", effort());
        // summary:auto asks the backend to stream the reasoning summary, the same
        // "Thinking…" feed the Codex desktop app shows above the answer.
        reasoning.addProperty("summary", "auto");
        body.add("reasoning", reasoning);
        body.addProperty("store", false);
        body.addProperty("stream", true);
        HttpRequest.Builder builder = HttpRequest.newBuilder(ENDPOINT)
                .header("Authorization", "Bearer " + acc.accessToken())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("OpenAI-Beta", "responses=experimental")
                .header("originator", "codex_cli_rs")
                .header("User-Agent", "carnation/0.1.0")
                .timeout(Duration.ofSeconds(600))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (acc.accountId() != null && !acc.accountId().isBlank()) {
            builder.header("chatgpt-account-id", acc.accountId());
        }
        return builder.build();
    }

    /** One SSE line: deltas append to the trailing assistant bubble, failures mark it red. */
    private static void onLine(String raw) {
        String line = raw.strip();
        if (!line.startsWith("data:")) return;
        String payload = line.substring(5).strip();
        if (payload.isEmpty() || payload.equals("[DONE]")) return;
        JsonObject event;
        try {
            event = JsonParser.parseString(payload).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        String type = event.has("type") && event.get("type").isJsonPrimitive()
                ? event.get("type").getAsString() : "";
        switch (type) {
            case "response.reasoning_summary_part.added" -> ensureThinkingBubble();
            case "response.reasoning_summary_text.delta" -> {
                if (event.has("delta") && event.get("delta").isJsonPrimitive()) {
                    appendThinking(event.get("delta").getAsString());
                }
            }
            case "response.output_text.delta" -> {
                if (event.has("delta") && event.get("delta").isJsonPrimitive()) {
                    sealThinking(); // the answer begins: freeze the summary above it
                    if (!streamStarted) {
                        streamStarted = true;
                        openAssistantBubble();
                    }
                    append(event.get("delta").getAsString());
                }
            }
            case "response.output_item.done" -> {
                JsonObject item = event.has("item") && event.get("item").isJsonObject()
                        ? event.getAsJsonObject("item") : null;
                if (item != null && "reasoning".equals(string(item, "type", null))) {
                    sealThinking();
                }
                if (item != null && "function_call".equals(string(item, "type", null))) {
                    JsonObject call = new JsonObject();
                    call.addProperty("type", "function_call");
                    call.addProperty("name", string(item, "name", "?"));
                    call.addProperty("arguments", string(item, "arguments", "{}"));
                    call.addProperty("call_id", string(item, "call_id", string(item, "id", "call_" + turnCalls.size())));
                    turnCalls.add(call);
                    addCallMessage(string(item, "name", "?"), string(item, "arguments", "{}"));
                }
            }
            case "response.completed" -> {
                sealThinking();
                turnSaved = true; // first: a repeated completed event must not save the bubble twice
                saveLastAssistant();
                sealAssistantTranscript();
            }
            case "response.failed" -> {
                String message = "回复失败";
                if (event.has("response") && event.getAsJsonObject("response").has("error")
                        && event.getAsJsonObject("response").get("error").isJsonObject()) {
                    message = string(event.getAsJsonObject("response").getAsJsonObject("error"), "message", message);
                }
                fail(message);
            }
            case "error" -> fail(string(event, "message", "服务返回错误"));
            default -> {
            }
        }
    }

    /**
     * Runs after one response finishes: executes any requested tools, feeds the results back
     * and keeps the loop going, or closes the turn when the model is actually done.
     */
    private static void afterTurn() {
        List<JsonObject> calls = List.copyOf(turnCalls);
        turnCalls.clear();
        if (calls.isEmpty() || cancelled.get()) {
            finishTurn();
            return;
        }
        for (JsonObject call : calls) {
            if (cancelled.get()) {
                finishTurn();
                return;
            }
            String name = string(call, "name", "?");
            String args = string(call, "arguments", "{}");
            String callId = string(call, "call_id", "call_0");
            String result;
            try {
                result = executeTool(name, JsonParser.parseString(args).getAsJsonObject());
            } catch (Exception e) {
                result = "工具执行出错: " + e.getMessage();
            }
            if (cancelled.get()) {
                finishTurn();
                return;
            }
            result = truncate(result, MAX_TOOL_RESULT_CHARS);
            synchronized (CodexChat.class) {
                turnTranscript.add(call);
                JsonObject output = new JsonObject();
                output.addProperty("type", "function_call_output");
                output.addProperty("call_id", callId);
                output.addProperty("output", result);
                turnTranscript.add(output);
                updateCallMessage(name, args, result);
                appendEvent(callEvent(name, args, callId, result));
            }
        }
        agentSteps++;
        if (agentSteps >= MAX_AGENT_STEPS) {
            markError("已连续执行 " + MAX_AGENT_STEPS + " 轮工具，暂停等待指示");
            finishTurn();
            return;
        }
        if (cancelled.get()) {
            finishTurn();
            return;
        }
        streamStarted = false;
        runTurn(0);
    }

    private static JsonArray agentTools() {
        return CodexTools.definitions();
    }

    private static String executeTool(String name, JsonObject args) throws IOException {
        return CodexTools.execute(name, args);
    }

    /** Exchanges the refresh token for a fresh access token and persists it. */
    private static boolean refresh(CodexAccount old) {
        if (old.refreshToken() == null || old.refreshToken().isBlank()) return false;
        try {
            JsonObject body = new JsonObject();
            body.addProperty("client_id", old.clientId());
            body.addProperty("grant_type", "refresh_token");
            body.addProperty("refresh_token", old.refreshToken());
            HttpRequest request = HttpRequest.newBuilder(TOKEN_ENDPOINT)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) return false;
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String access = string(json, "access_token", null);
            if (access == null) return false;
            account = old.withTokens(access, string(json, "refresh_token", old.refreshToken()),
                    string(json, "id_token", old.idToken()));
            save();
            Client.logger.info("Codex access token refreshed");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static synchronized void append(String delta) {
        if (turnMessages.isEmpty()) return;
        Msg last = turnMessages.getLast();
        if (last.role() != Msg.Role.ASSISTANT) {
            turnMessages.add(new Msg(Msg.Role.ASSISTANT, delta, false, null));
        } else {
            turnMessages.set(turnMessages.size() - 1, new Msg(Msg.Role.ASSISTANT, last.text() + delta, last.error(), last.meta()));
        }
    }

    /** Opens the trailing thinking bubble that the reasoning summary streams into. */
    private static synchronized void ensureThinkingBubble() {
        if (!turnMessages.isEmpty() && turnMessages.getLast().role() == Msg.Role.THINKING) return;
        thinkingStartAt = System.currentTimeMillis();
        turnMessages.add(new Msg(Msg.Role.THINKING, "", false, null));
    }

    private static synchronized void appendThinking(String delta) {
        ensureThinkingBubble();
        Msg last = turnMessages.getLast();
        turnMessages.set(turnMessages.size() - 1, new Msg(Msg.Role.THINKING, last.text() + delta, false, null));
    }

    /**
     * Freezes the streaming summary and stamps its duration; a sealed block collapses
     * to one header line in the GUI. Safe to call repeatedly and from any state.
     */
    private static synchronized void sealThinking() {
        if (turnMessages.isEmpty() || turnMessages.getLast().role() != Msg.Role.THINKING) return;
        Msg last = turnMessages.getLast();
        if (last.meta() != null) return; // already sealed
        long seconds = Math.max(1, Math.round((System.currentTimeMillis() - thinkingStartAt) / 1000.0));
        turnMessages.set(turnMessages.size() - 1, new Msg(Msg.Role.THINKING, last.text(), false, String.valueOf(seconds)));
        if (!last.text().isBlank()) appendEvent(messageEvent(last));
    }

    /** Each response's text opens its own bubble, so agent rounds stay visually separate. */
    private static synchronized void openAssistantBubble() {
        turnMessages.add(new Msg(Msg.Role.ASSISTANT, "", false, null));
    }

    private static synchronized void addCallMessage(String name, String args) {
        // update_plan carries its raw arguments in meta so the GUI renders the plan as a card.
        turnMessages.add(new Msg(Msg.Role.CALL,
                name + "(" + truncate(args.replace("\n", " "), 120) + ")", false,
                "update_plan".equals(name) ? args : null));
    }

    private static synchronized void updateCallMessage(String name, String args, String result) {
        String head = name + "(" + truncate(args.replace("\n", " "), 120) + ")";
        for (int i = turnMessages.size() - 1; i >= 0; i--) {
            Msg message = turnMessages.get(i);
            if (message.role() == Msg.Role.CALL && message.text().startsWith(head)) {
                turnMessages.set(i, new Msg(Msg.Role.CALL,
                        head + " → " + truncate(result.replace("\n", " "), 160), false, message.meta()));
                return;
            }
        }
    }

    private static JsonObject messageItem(String role, String text) {
        JsonObject item = new JsonObject();
        item.addProperty("type", "message");
        item.addProperty("role", role);
        JsonObject content = new JsonObject();
        content.addProperty("type", "user".equals(role) ? "input_text" : "output_text");
        content.addProperty("text", text);
        JsonArray contents = new JsonArray();
        contents.add(content);
        item.add("content", contents);
        return item;
    }

    /** Freezes the streamed text into the API transcript once a response completes. */
    private static synchronized void sealAssistantTranscript() {
        if (turnMessages.isEmpty()) return;
        Msg last = turnMessages.getLast();
        if (last.role() == Msg.Role.ASSISTANT && !last.text().isBlank()) {
            turnTranscript.add(messageItem("assistant", last.text()));
        }
    }

    private static JsonObject callEvent(String name, String args, String callId, String result) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "call");
        event.addProperty("name", name);
        event.addProperty("arguments", args);
        event.addProperty("call_id", callId);
        event.addProperty("result", result);
        return event;
    }

    private static synchronized void markError(String message) {
        Msg last = turnMessages.isEmpty() ? null : turnMessages.getLast();
        if (last != null && last.role() == Msg.Role.ASSISTANT) {
            String text = last.text().isBlank() ? message : last.text();
            turnMessages.set(turnMessages.size() - 1, new Msg(Msg.Role.ASSISTANT, text, true, last.meta()));
            return;
        }
        if (last != null && last.role() == Msg.Role.THINKING) sealThinking();
        turnMessages.add(new Msg(Msg.Role.ASSISTANT, message, true, null));
    }

    private static void fail(String message) {
        markError(message);
        finishTurn();
    }

    private static void finishTurn() {
        sealThinking(); // a cancel or network drop must not leave an unsealed block behind
        if (!turnSaved) {
            turnSaved = true;
            saveLastAssistant();
        }
        state = State.IDLE;
        turn = null;
        synchronized (CodexChat.class) {
            // The user switched away: tell them their background task landed somewhere else.
            if (turnMessages != null && MESSAGES != turnMessages) {
                finishedNotice = turnFile == null ? "后台任务已完成"
                        : "后台任务已完成: " + sessionTitle(turnFile);
            }
            turnMessages = null;
            turnTranscript = null;
            turnFile = null;
        }
    }

    /** The finished answer lands in the session log the same way the user's message did. */
    private static synchronized void saveLastAssistant() {
        if (turnMessages.isEmpty()) return;
        Msg last = turnMessages.getLast();
        if (last.role() == Msg.Role.ASSISTANT && !last.text().isBlank()) {
            appendEvent(messageEvent(last));
        }
    }

    private static String errorBody(int statusCode, Stream<String> body) {
        StringBuilder text = new StringBuilder();
        try (Stream<String> lines = body) {
            lines.forEach(line -> text.append(line).append('\n'));
        } catch (Exception e) {
            // body unreadable; the status code alone still tells the story
        }
        try {
            JsonObject json = JsonParser.parseString(text.toString().strip()).getAsJsonObject();
            String message = string(json, "message", null);
            if (message == null && json.has("error") && json.get("error").isJsonObject()) {
                message = string(json.getAsJsonObject("error"), "message", null);
            }
            if (message != null) return message;
        } catch (Exception e) {
            // not JSON; fall through to the status code
        }
        return "HTTP " + statusCode + (text.isEmpty() ? "" : ": " + truncate(text.toString().strip(), 160));
    }

    private static String networkMessage(Throwable error) {
        if (error instanceof HttpConnectTimeoutException) {
            return "连接 chatgpt.com 超时——请开启代理（系统代理或 TUN 模式）后重试";
        }
        if (error instanceof HttpTimeoutException) return "请求超时";
        if (error instanceof ConnectException) return "无法连接服务器";
        return "网络错误: " + error.getMessage();
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json != null && json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsString() : fallback;
    }

    private static void save() {
        try {
            Path file = store();
            if (file == null) return;
            Files.createDirectories(file.getParent());
            Files.writeString(file, account.toJson().toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Client.logger.warn("Could not save the imported Codex account", e);
        }
    }

    private static Path store() {
        try {
            return Client.clientFolder.toPath().resolve("codex-account.json");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The system prompt sent with every turn: the prompt bundled in the mod jar
     * (assets/carnation/codex-instructions.md) unless <gameDir>/Carnation/codex-instructions.txt
     * overrides it; with neither present no instructions field is sent and the model behaves
     * exactly like a vanilla Codex session. Re-read per turn so edits land without a restart.
     */
    private static String instructions() {
        Path folder = store() == null ? null : store().getParent();
        if (folder != null) {
            Path override = folder.resolve("codex-instructions.txt");
            if (Files.isRegularFile(override)) {
                try {
                    String text = Files.readString(override, StandardCharsets.UTF_8).strip();
                    if (!text.isEmpty()) return text;
                } catch (IOException e) {
                    Client.logger.warn("Could not read the Codex instructions override", e);
                }
            }
        }
        try (InputStream in = CodexChat.class.getResourceAsStream("/assets/carnation/codex-instructions.md")) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            Client.logger.warn("Could not read the bundled Codex instructions", e);
        }
        return null;
    }
}
