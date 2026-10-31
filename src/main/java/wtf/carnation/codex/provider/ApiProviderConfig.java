package wtf.carnation.codex.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import wtf.carnation.Client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ApiProviderConfig {
    private ProviderProtocol protocol = ProviderProtocol.OPENAI_CHAT;
    private String apiKey = "";
    private String baseUrl = ProviderProtocol.OPENAI_CHAT.defaultBaseUrl();
    private String selectedModel = "";
    private String effort = "high";
    private boolean enabled;
    private List<ApiModel> models = new ArrayList<>();

    public ProviderProtocol protocol() {
        return protocol;
    }

    public void protocol(ProviderProtocol protocol) {
        this.protocol = protocol == null ? ProviderProtocol.OPENAI_CHAT : protocol;
    }

    public String apiKey() {
        return apiKey;
    }

    public void apiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public void baseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.strip();
    }

    public String selectedModel() {
        return selectedModel;
    }

    public void selectedModel(String selectedModel) {
        this.selectedModel = selectedModel == null ? "" : selectedModel.strip();
    }

    public String effort() {
        return effort == null || effort.isBlank() ? "high" : effort;
    }

    public void effort(String effort) {
        this.effort = effort == null ? "high" : effort;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<ApiModel> models() {
        return List.copyOf(models);
    }

    public void models(List<ApiModel> models) {
        this.models = models == null ? new ArrayList<>() : new ArrayList<>(models);
        if (selectedModel.isBlank() && !this.models.isEmpty()) selectedModel = this.models.getFirst().id();
    }

    public boolean usable() {
        return enabled && !apiKey.isBlank() && !baseUrl.isBlank() && !selectedModel.isBlank();
    }

    public boolean configured() {
        return !apiKey.isBlank() && !baseUrl.isBlank() && !selectedModel.isBlank();
    }

    public String modelDisplay() {
        for (ApiModel model : models) {
            if (model.id().equals(selectedModel)) return model.displayName();
        }
        return selectedModel;
    }

    public ApiProviderConfig copy() {
        ApiProviderConfig copy = new ApiProviderConfig();
        copy.protocol = protocol;
        copy.apiKey = apiKey;
        copy.baseUrl = baseUrl;
        copy.selectedModel = selectedModel;
        copy.effort = effort;
        copy.enabled = enabled;
        copy.models = new ArrayList<>(models);
        return copy;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        json.addProperty("protocol", protocol.name());
        json.addProperty("api_key", apiKey);
        json.addProperty("base_url", baseUrl);
        json.addProperty("selected_model", selectedModel);
        json.addProperty("effort", effort);
        json.addProperty("enabled", enabled);
        JsonArray array = new JsonArray();
        for (ApiModel model : models) array.add(model.toJson());
        json.add("models", array);
        return json;
    }

    public static ApiProviderConfig fromJson(JsonObject json) {
        ApiProviderConfig config = new ApiProviderConfig();
        if (json == null) return config;
        config.protocol = ProviderProtocol.from(string(json, "protocol"));
        config.apiKey = stringOr(json, "api_key", "");
        config.baseUrl = stringOr(json, "base_url", config.protocol.defaultBaseUrl());
        config.selectedModel = stringOr(json, "selected_model", "");
        config.effort = stringOr(json, "effort", "high");
        config.enabled = json.has("enabled") && json.get("enabled").getAsBoolean();
        if (json.has("models") && json.get("models").isJsonArray()) {
            List<ApiModel> models = new ArrayList<>();
            for (var element : json.getAsJsonArray("models")) {
                if (!element.isJsonObject()) continue;
                ApiModel model = ApiModel.fromJson(element.getAsJsonObject());
                if (model != null) models.add(model);
            }
            config.models = models;
        }
        return config;
    }

    public static ApiProviderConfig load() {
        Path file = file();
        if (file == null || !Files.isRegularFile(file)) return new ApiProviderConfig();
        try {
            return fromJson(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) {
            Client.logger.warn("Could not read the API provider config", e);
            return new ApiProviderConfig();
        }
    }

    public void save() throws java.io.IOException {
        Path file = file();
        if (file == null) throw new java.io.IOException("无法访问客户端目录");
        Files.createDirectories(file.getParent());
        Files.writeString(file, toJson().toString(), StandardCharsets.UTF_8);
    }

    public static Path file() {
        try {
            return Client.clientFolder.toPath().resolve("api-provider.json");
        } catch (Exception e) {
            return null;
        }
    }

    private static String string(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsString() : null;
    }

    private static String stringOr(JsonObject json, String key, String fallback) {
        String value = string(json, key);
        return value == null ? fallback : value;
    }
}
