package wtf.carnation.codex.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import wtf.carnation.codex.CodexChat;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ApiProviderClient {
    public record Completion(String text, List<ApiToolCall> calls) {
    }

    private ApiProviderClient() {
    }

    public static Completion complete(ApiProviderConfig config, String system, List<ApiMessage> history,
                                      JsonArray tools) throws IOException {
        JsonObject body = switch (config.protocol()) {
            case OPENAI_CHAT -> openAiChatBody(config, system, history, tools);
            case OPENAI_RESPONSES -> openAiResponsesBody(config, system, history, tools);
            case ANTHROPIC_MESSAGES -> anthropicBody(config, system, history, tools);
            case GEMINI -> geminiBody(config, system, history, tools);
        };
        URI endpoint = switch (config.protocol()) {
            case OPENAI_CHAT -> endpoint(config.baseUrl(), "/chat/completions");
            case OPENAI_RESPONSES -> endpoint(config.baseUrl(), "/responses");
            case ANTHROPIC_MESSAGES -> anthropicEndpoint(config.baseUrl(), "/messages");
            case GEMINI -> geminiGenerateEndpoint(config.baseUrl(), config.selectedModel());
        };
        JsonObject response = post(config, endpoint, body);
        return switch (config.protocol()) {
            case OPENAI_CHAT -> parseOpenAiChat(response);
            case OPENAI_RESPONSES -> parseOpenAiResponses(response);
            case ANTHROPIC_MESSAGES -> parseAnthropic(response);
            case GEMINI -> parseGemini(response);
        };
    }

    public static List<ApiModel> fetchModels(ApiProviderConfig config) throws IOException {
        URI endpoint = switch (config.protocol()) {
            case OPENAI_CHAT, OPENAI_RESPONSES -> endpoint(config.baseUrl(), "/models");
            case ANTHROPIC_MESSAGES -> anthropicEndpoint(config.baseUrl(), "/models");
            case GEMINI -> geminiModelsEndpoint(config.baseUrl());
        };
        JsonObject response = get(config, endpoint);
        return switch (config.protocol()) {
            case OPENAI_CHAT, OPENAI_RESPONSES -> parseOpenAiModels(response);
            case ANTHROPIC_MESSAGES -> parseAnthropicModels(response);
            case GEMINI -> parseGeminiModels(response);
        };
    }

    private static JsonObject openAiChatBody(ApiProviderConfig config, String system, List<ApiMessage> history,
                                              JsonArray tools) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.selectedModel());
        body.addProperty("stream", false);
        JsonArray messages = new JsonArray();
        if (system != null && !system.isBlank()) {
            JsonObject message = new JsonObject();
            message.addProperty("role", "system");
            message.addProperty("content", system);
            messages.add(message);
        }
        for (ApiMessage item : history) {
            JsonObject message = new JsonObject();
            if ("tool".equals(item.role())) {
                message.addProperty("role", "tool");
                message.addProperty("tool_call_id", item.toolCallId());
                message.addProperty("content", item.content());
            } else if ("assistant".equals(item.role()) && !item.calls().isEmpty()) {
                message.addProperty("role", "assistant");
                message.addProperty("content", item.content());
                JsonArray calls = new JsonArray();
                for (ApiToolCall call : item.calls()) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", call.id());
                    entry.addProperty("type", "function");
                    JsonObject function = new JsonObject();
                    function.addProperty("name", call.name());
                    function.addProperty("arguments", call.arguments());
                    entry.add("function", function);
                    calls.add(entry);
                }
                message.add("tool_calls", calls);
            } else {
                message.addProperty("role", item.role());
                message.addProperty("content", item.content());
            }
            messages.add(message);
        }
        body.add("messages", messages);
        JsonArray chatTools = new JsonArray();
        for (JsonElement element : tools) {
            if (!element.isJsonObject()) continue;
            JsonObject definition = element.getAsJsonObject();
            JsonObject tool = new JsonObject();
            tool.addProperty("type", "function");
            JsonObject function = new JsonObject();
            function.addProperty("name", string(definition, "name", ""));
            function.addProperty("description", string(definition, "description", ""));
            function.add("parameters", definition.has("parameters")
                    ? definition.get("parameters") : new JsonObject());
            tool.add("function", function);
            chatTools.add(tool);
        }
        body.add("tools", chatTools);
        body.addProperty("tool_choice", "auto");
        return body;
    }

    private static JsonObject openAiResponsesBody(ApiProviderConfig config, String system,
                                                   List<ApiMessage> history, JsonArray tools) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.selectedModel());
        if (system != null && !system.isBlank()) body.addProperty("instructions", system);
        JsonArray input = new JsonArray();
        for (ApiMessage item : history) {
            if ("tool".equals(item.role())) {
                JsonObject output = new JsonObject();
                output.addProperty("type", "function_call_output");
                output.addProperty("call_id", item.toolCallId());
                output.addProperty("output", item.content());
                input.add(output);
            } else if ("assistant".equals(item.role()) && !item.calls().isEmpty()) {
                if (!item.content().isBlank()) input.add(responseMessage("assistant", item.content()));
                for (ApiToolCall call : item.calls()) {
                    JsonObject function = new JsonObject();
                    function.addProperty("type", "function_call");
                    function.addProperty("call_id", call.id());
                    function.addProperty("name", call.name());
                    function.addProperty("arguments", call.arguments());
                    input.add(function);
                }
            } else {
                input.add(responseMessage(item.role(), item.content()));
            }
        }
        body.add("input", input);
        body.add("tools", tools);
        body.addProperty("tool_choice", "auto");
        body.addProperty("store", false);
        JsonObject reasoning = new JsonObject();
        reasoning.addProperty("effort", config.effort());
        body.add("reasoning", reasoning);
        return body;
    }

    private static JsonObject responseMessage(String role, String text) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "message");
        message.addProperty("role", role);
        JsonObject content = new JsonObject();
        content.addProperty("type", "assistant".equals(role) ? "output_text" : "input_text");
        content.addProperty("text", text);
        JsonArray contents = new JsonArray();
        contents.add(content);
        message.add("content", contents);
        return message;
    }

    private static JsonObject anthropicBody(ApiProviderConfig config, String system,
                                             List<ApiMessage> history, JsonArray tools) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.selectedModel());
        body.addProperty("max_tokens", 8192);
        if (system != null && !system.isBlank()) body.addProperty("system", system);
        JsonArray messages = new JsonArray();
        for (ApiMessage item : history) {
            String role = "assistant".equals(item.role()) ? "assistant" : "user";
            JsonArray content = new JsonArray();
            if ("tool".equals(item.role())) {
                JsonObject block = new JsonObject();
                block.addProperty("type", "tool_result");
                block.addProperty("tool_use_id", item.toolCallId());
                block.addProperty("content", item.content());
                content.add(block);
            } else if ("assistant".equals(item.role())) {
                if (!item.content().isBlank()) {
                    JsonObject text = new JsonObject();
                    text.addProperty("type", "text");
                    text.addProperty("text", item.content());
                    content.add(text);
                }
                for (ApiToolCall call : item.calls()) {
                    JsonObject tool = new JsonObject();
                    tool.addProperty("type", "tool_use");
                    tool.addProperty("id", call.id());
                    tool.addProperty("name", call.name());
                    tool.add("input", parseObject(call.arguments()));
                    content.add(tool);
                }
            } else {
                JsonObject text = new JsonObject();
                text.addProperty("type", "text");
                text.addProperty("text", item.content());
                content.add(text);
            }
            if (content.isEmpty()) continue;
            JsonObject last = messages.isEmpty() ? null : messages.get(messages.size() - 1).getAsJsonObject();
            if (last != null && role.equals(string(last, "role", ""))) {
                JsonArray existing = last.getAsJsonArray("content");
                for (JsonElement block : content) existing.add(block);
            } else {
                JsonObject message = new JsonObject();
                message.addProperty("role", role);
                message.add("content", content);
                messages.add(message);
            }
        }
        body.add("messages", messages);
        JsonArray anthropicTools = new JsonArray();
        for (JsonElement element : tools) {
            if (!element.isJsonObject()) continue;
            JsonObject definition = element.getAsJsonObject();
            JsonObject tool = new JsonObject();
            tool.addProperty("name", string(definition, "name", ""));
            tool.addProperty("description", string(definition, "description", ""));
            tool.add("input_schema", definition.has("parameters")
                    ? definition.get("parameters") : new JsonObject());
            anthropicTools.add(tool);
        }
        body.add("tools", anthropicTools);
        return body;
    }

    private static JsonObject geminiBody(ApiProviderConfig config, String system,
                                          List<ApiMessage> history, JsonArray tools) {
        JsonObject body = new JsonObject();
        if (system != null && !system.isBlank()) {
            JsonObject instruction = new JsonObject();
            JsonObject part = new JsonObject();
            part.addProperty("text", system);
            JsonArray parts = new JsonArray();
            parts.add(part);
            instruction.add("parts", parts);
            body.add("systemInstruction", instruction);
        }
        JsonArray contents = new JsonArray();
        for (ApiMessage item : history) {
            String role = "assistant".equals(item.role()) ? "model" : "user";
            JsonArray parts = new JsonArray();
            if ("tool".equals(item.role())) {
                JsonObject function = new JsonObject();
                function.addProperty("name", item.toolName());
                JsonObject response = new JsonObject();
                response.addProperty("result", item.content());
                function.add("response", response);
                JsonObject part = new JsonObject();
                part.add("functionResponse", function);
                parts.add(part);
            } else {
                if (!item.content().isBlank()) {
                    JsonObject part = new JsonObject();
                    part.addProperty("text", item.content());
                    parts.add(part);
                }
                for (ApiToolCall call : item.calls()) {
                    JsonObject function = new JsonObject();
                    function.addProperty("name", call.name());
                    function.add("args", parseObject(call.arguments()));
                    JsonObject part = new JsonObject();
                    part.add("functionCall", function);
                    parts.add(part);
                }
            }
            if (parts.isEmpty()) continue;
            JsonObject last = contents.isEmpty() ? null : contents.get(contents.size() - 1).getAsJsonObject();
            if (last != null && role.equals(string(last, "role", ""))) {
                JsonArray existing = last.getAsJsonArray("parts");
                for (JsonElement part : parts) existing.add(part);
            } else {
                JsonObject content = new JsonObject();
                content.addProperty("role", role);
                content.add("parts", parts);
                contents.add(content);
            }
        }
        body.add("contents", contents);
        JsonArray declarations = new JsonArray();
        for (JsonElement element : tools) {
            if (!element.isJsonObject()) continue;
            JsonObject definition = element.getAsJsonObject();
            JsonObject declaration = new JsonObject();
            declaration.addProperty("name", string(definition, "name", ""));
            declaration.addProperty("description", string(definition, "description", ""));
            declaration.add("parameters", definition.has("parameters")
                    ? definition.get("parameters") : new JsonObject());
            declarations.add(declaration);
        }
        JsonObject tool = new JsonObject();
        tool.add("functionDeclarations", declarations);
        JsonArray toolArray = new JsonArray();
        toolArray.add(tool);
        body.add("tools", toolArray);
        return body;
    }

    private static Completion parseOpenAiChat(JsonObject root) throws IOException {
        JsonArray choices = array(root, "choices");
        if (choices.isEmpty() || !choices.get(0).isJsonObject()) return new Completion("", List.of());
        JsonObject message = object(choices.get(0).getAsJsonObject(), "message");
        String text = string(message, "content", "");
        List<ApiToolCall> calls = new ArrayList<>();
        JsonArray toolCalls = array(message, "tool_calls");
        for (int i = 0; i < toolCalls.size(); i++) {
            if (!toolCalls.get(i).isJsonObject()) continue;
            JsonObject tool = toolCalls.get(i).getAsJsonObject();
            JsonObject function = object(tool, "function");
            calls.add(new ApiToolCall(string(tool, "id", "call_" + i), string(function, "name", ""),
                    string(function, "arguments", "{}")));
        }
        return new Completion(text, calls);
    }

    private static Completion parseOpenAiResponses(JsonObject root) {
        StringBuilder text = new StringBuilder();
        List<ApiToolCall> calls = new ArrayList<>();
        JsonArray output = array(root, "output");
        for (JsonElement element : output) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            String type = string(item, "type", "");
            if ("message".equals(type)) {
                for (JsonElement contentElement : array(item, "content")) {
                    if (!contentElement.isJsonObject()) continue;
                    JsonObject content = contentElement.getAsJsonObject();
                    if ("output_text".equals(string(content, "type", ""))) {
                        text.append(string(content, "text", ""));
                    }
                }
            } else if ("function_call".equals(type)) {
                calls.add(new ApiToolCall(string(item, "call_id", string(item, "id", "call")),
                        string(item, "name", ""), string(item, "arguments", "{}")));
            }
        }
        return new Completion(text.toString(), calls);
    }

    private static Completion parseAnthropic(JsonObject root) {
        StringBuilder text = new StringBuilder();
        List<ApiToolCall> calls = new ArrayList<>();
        JsonArray content = array(root, "content");
        for (int i = 0; i < content.size(); i++) {
            if (!content.get(i).isJsonObject()) continue;
            JsonObject block = content.get(i).getAsJsonObject();
            String type = string(block, "type", "");
            if ("text".equals(type)) text.append(string(block, "text", ""));
            else if ("tool_use".equals(type)) {
                JsonElement input = block.has("input") ? block.get("input") : new JsonObject();
                calls.add(new ApiToolCall(string(block, "id", "call_" + i), string(block, "name", ""),
                        input.toString()));
            }
        }
        return new Completion(text.toString(), calls);
    }

    private static Completion parseGemini(JsonObject root) {
        JsonArray candidates = array(root, "candidates");
        if (candidates.isEmpty() || !candidates.get(0).isJsonObject()) return new Completion("", List.of());
        JsonObject content = object(candidates.get(0).getAsJsonObject(), "content");
        StringBuilder text = new StringBuilder();
        List<ApiToolCall> calls = new ArrayList<>();
        JsonArray parts = array(content, "parts");
        for (int i = 0; i < parts.size(); i++) {
            if (!parts.get(i).isJsonObject()) continue;
            JsonObject part = parts.get(i).getAsJsonObject();
            if (part.has("text") && part.get("text").isJsonPrimitive()) text.append(part.get("text").getAsString());
            if (part.has("functionCall") && part.get("functionCall").isJsonObject()) {
                JsonObject call = part.getAsJsonObject("functionCall");
                JsonElement args = call.has("args") ? call.get("args") : new JsonObject();
                calls.add(new ApiToolCall("gemini_" + i, string(call, "name", ""), args.toString()));
            }
        }
        return new Completion(text.toString(), calls);
    }

    private static List<ApiModel> parseOpenAiModels(JsonObject root) {
        List<ApiModel> models = new ArrayList<>();
        for (JsonElement element : array(root, "data")) {
            if (!element.isJsonObject()) continue;
            JsonObject model = element.getAsJsonObject();
            String id = string(model, "id", "");
            if (id.isBlank()) continue;
            int context = integer(model, "context_length", integer(model, "context_window",
                    integer(model, "max_input_tokens", 0)));
            models.add(new ApiModel(id, string(model, "display_name", string(model, "name", id)), context));
        }
        models.sort(java.util.Comparator.comparing(ApiModel::id));
        return models;
    }

    private static List<ApiModel> parseAnthropicModels(JsonObject root) {
        List<ApiModel> models = new ArrayList<>();
        for (JsonElement element : array(root, "data")) {
            if (!element.isJsonObject()) continue;
            JsonObject model = element.getAsJsonObject();
            String id = string(model, "id", "");
            if (id.isBlank()) continue;
            models.add(new ApiModel(id, string(model, "display_name", id),
                    integer(model, "context_window", integer(model, "max_input_tokens", 0))));
        }
        return models;
    }

    private static List<ApiModel> parseGeminiModels(JsonObject root) {
        List<ApiModel> models = new ArrayList<>();
        for (JsonElement element : array(root, "models")) {
            if (!element.isJsonObject()) continue;
            JsonObject model = element.getAsJsonObject();
            String id = string(model, "name", "");
            if (id.startsWith("models/")) id = id.substring("models/".length());
            if (id.isBlank()) continue;
            models.add(new ApiModel(id, string(model, "displayName", id), integer(model, "inputTokenLimit", 0)));
        }
        return models;
    }

    private static JsonObject get(ApiProviderConfig config, URI endpoint) throws IOException {
        HttpRequest.Builder builder = request(config, endpoint);
        return send(builder.GET().build());
    }

    private static JsonObject post(ApiProviderConfig config, URI endpoint, JsonObject body) throws IOException {
        HttpRequest.Builder builder = request(config, endpoint);
        builder.header("Content-Type", "application/json");
        builder.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        return send(builder.build());
    }

    private static HttpRequest.Builder request(ApiProviderConfig config, URI endpoint) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(600))
                .header("Accept", "application/json");
        switch (config.protocol()) {
            case OPENAI_CHAT, OPENAI_RESPONSES -> builder.header("Authorization", "Bearer " + config.apiKey());
            case ANTHROPIC_MESSAGES -> builder
                    .header("x-api-key", config.apiKey())
                    .header("anthropic-version", "2023-06-01");
            case GEMINI -> builder.header("x-goog-api-key", config.apiKey());
        }
        return builder;
    }

    private static JsonObject send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = CodexChat.http().send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String hint = response.statusCode() == 404
                        ? "（" + request.uri().getPath() + " 不存在：协议和 Base URL 不匹配？例如 /anthropic 结尾的地址要选 Anthropic 协议）"
                        : "";
                throw new IOException("HTTP " + response.statusCode() + ": "
                        + errorMessage(response.body()) + hint);
            }
            try {
                JsonElement parsed = JsonParser.parseString(response.body());
                if (parsed.isJsonObject()) return parsed.getAsJsonObject();
            } catch (Exception ignored) {
            }
            throw new IOException("API 返回的不是 JSON 对象: " + truncate(response.body(), 300));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断", e);
        }
    }

    private static String errorMessage(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("error")) {
                JsonElement error = json.get("error");
                if (error.isJsonObject()) return string(error.getAsJsonObject(), "message", truncate(body, 300));
                if (error.isJsonPrimitive()) return error.getAsString();
            }
            return string(json, "message", truncate(body, 300));
        } catch (Exception e) {
            return truncate(body, 300);
        }
    }

    private static URI endpoint(String baseUrl, String suffix) throws IOException {
        String base = stripSlash(baseUrl);
        if (base.endsWith(suffix)) return uri(base);
        if (base.endsWith("/v1")) return uri(base + suffix);
        return uri(base + "/v1" + suffix);
    }

    private static URI anthropicEndpoint(String baseUrl, String suffix) throws IOException {
        String base = stripSlash(baseUrl);
        if (base.endsWith(suffix)) return uri(base);
        if (base.endsWith("/v1")) return uri(base + suffix);
        return uri(base + "/v1" + suffix);
    }

    private static URI geminiModelsEndpoint(String baseUrl) throws IOException {
        String base = geminiBase(baseUrl);
        return uri(base + "/models");
    }

    private static URI geminiGenerateEndpoint(String baseUrl, String model) throws IOException {
        String name = model.startsWith("models/") ? model.substring("models/".length()) : model;
        return uri(geminiBase(baseUrl) + "/models/" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + ":generateContent");
    }

    private static String geminiBase(String baseUrl) throws IOException {
        String base = stripSlash(baseUrl);
        if (base.endsWith("/models")) base = base.substring(0, base.length() - "/models".length());
        if (base.endsWith(":generateContent")) throw new IOException("Gemini Base URL 应填写到 /v1beta，不要包含 :generateContent");
        return base;
    }

    private static String stripSlash(String value) {
        String result = value == null ? "" : value.strip();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static URI uri(String value) throws IOException {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IOException("无效的 API URL: " + value, e);
        }
    }

    private static JsonObject parseObject(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static JsonArray array(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).isJsonArray()
                ? json.getAsJsonArray(key) : new JsonArray();
    }

    private static JsonObject object(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).isJsonObject()
                ? json.getAsJsonObject(key) : new JsonObject();
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json != null && json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        try {
            return json != null && json.has(key) && json.get(key).isJsonPrimitive()
                    ? json.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
