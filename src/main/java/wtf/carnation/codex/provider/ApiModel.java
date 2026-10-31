package wtf.carnation.codex.provider;

import com.google.gson.JsonObject;

public record ApiModel(String id, String displayName, int contextLength) {
    public ApiModel {
        if (displayName == null || displayName.isBlank()) displayName = id;
        if (contextLength < 0) contextLength = 0;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("display_name", displayName);
        json.addProperty("context_length", contextLength);
        return json;
    }

    public static ApiModel fromJson(JsonObject json) {
        String id = string(json, "id");
        if (id == null || id.isBlank()) return null;
        String name = string(json, "display_name");
        return new ApiModel(id, name == null ? id : name, integer(json, "context_length"));
    }

    public String contextLabel() {
        if (contextLength <= 0) return "未知";
        if (contextLength >= 1_000_000) return String.format("%.1fM", contextLength / 1_000_000.0);
        if (contextLength >= 1000) return (contextLength / 1000) + "K";
        return String.valueOf(contextLength);
    }

    private static String string(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsString() : null;
    }

    private static int integer(JsonObject json, String key) {
        try {
            return json != null && json.has(key) && json.get(key).isJsonPrimitive()
                    ? json.get(key).getAsInt() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
