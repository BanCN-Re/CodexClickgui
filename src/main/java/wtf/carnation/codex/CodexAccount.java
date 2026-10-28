package wtf.carnation.codex;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * An imported Codex account: the OAuth tokens the in-game chat uses against the ChatGPT backend.
 * Accepts either the official {@code ~/.codex/auth.json} shape ({@code tokens.{...}}) or a sub2api
 * export ({@code accounts[].credentials.{...}}). Tokens are only ever read from the file the user
 * picks and stored back into the client folder; they are never logged.
 */
public final class CodexAccount {
    /** The OAuth client Codex itself uses; auth.json carries no client id, sub2api exports usually do. */
    public static final String DEFAULT_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann";

    private final String accessToken;
    private final String refreshToken;
    private final String idToken;
    private final String accountId;
    private final String clientId;
    private final String email;
    private final String plan;
    private final long expiresAt;
    private final String model;
    private final String effort;

    private CodexAccount(String accessToken, String refreshToken, String idToken, String accountId,
                         String clientId, String email, String plan, long expiresAt,
                         String model, String effort) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.idToken = idToken;
        this.accountId = accountId;
        this.clientId = clientId;
        this.email = email;
        this.plan = plan;
        this.expiresAt = expiresAt;
        this.model = model;
        this.effort = effort;
    }

    /** Parses either supported format, or null when the JSON matches neither. */
    public static CodexAccount parse(String json) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
        if (root.has("tokens") && root.get("tokens").isJsonObject()) {
            JsonObject tokens = root.getAsJsonObject("tokens");
            String access = string(tokens, "access_token");
            if (access == null || access.isBlank()) return null;
            JsonObject claims = claims(access);
            return new CodexAccount(access, string(tokens, "refresh_token"), string(tokens, "id_token"),
                    string(tokens, "account_id"), DEFAULT_CLIENT_ID,
                    email(claims), plan(claims), expiry(claims), null, null);
        }
        if (root.has("accounts") && root.get("accounts").isJsonArray()) {
            for (var element : root.getAsJsonArray("accounts")) {
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                if (!"openai".equals(string(entry, "platform"))) continue;
                if (!entry.has("credentials") || !entry.get("credentials").isJsonObject()) continue;
                JsonObject credentials = entry.getAsJsonObject("credentials");
                String access = string(credentials, "access_token");
                if (access == null || access.isBlank()) continue;
                JsonObject claims = claims(access);
                return new CodexAccount(access, string(credentials, "refresh_token"), string(credentials, "id_token"),
                        string(credentials, "chatgpt_account_id"),
                        stringOr(credentials, "client_id", DEFAULT_CLIENT_ID),
                        stringOr(credentials, "email", email(claims)),
                        stringOr(credentials, "plan_type", plan(claims)), expiry(claims), null, null);
            }
        }
        return null;
    }

    /** Rebuilds an account from this mod's own saved JSON, as written by {@link #toJson()}. */
    public static CodexAccount fromJson(JsonObject json) {
        String access = string(json, "access_token");
        if (access == null || access.isBlank()) return null;
        return new CodexAccount(access, string(json, "refresh_token"), string(json, "id_token"),
                string(json, "account_id"), stringOr(json, "client_id", DEFAULT_CLIENT_ID),
                string(json, "email"), string(json, "plan"),
                json.has("expires_at") && json.get("expires_at").isJsonPrimitive()
                        ? json.get("expires_at").getAsLong() : expiry(claims(access)),
                stringOr(json, "model", null), stringOr(json, "effort", null));
    }

    /** Fresh tokens after a refresh; the new access token's expiry claim replaces the old one. */
    public CodexAccount withTokens(String access, String refresh, String idToken) {
        return new CodexAccount(access, refresh, idToken, accountId, clientId, email, plan,
                expiry(claims(access)), model, effort);
    }

    /** A copy with the chat model or reasoning effort switched (null keeps the current value). */
    public CodexAccount withModel(String model, String effort) {
        return new CodexAccount(accessToken, refreshToken, idToken, accountId, clientId, email, plan,
                expiresAt, model != null ? model : this.model, effort != null ? effort : this.effort);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "carnation-codex-account");
        json.addProperty("access_token", accessToken);
        json.addProperty("refresh_token", refreshToken);
        json.addProperty("id_token", idToken);
        json.addProperty("account_id", accountId);
        json.addProperty("client_id", clientId);
        json.addProperty("email", email);
        json.addProperty("plan", plan);
        json.addProperty("expires_at", expiresAt);
        json.addProperty("model", model);
        json.addProperty("effort", effort);
        return json;
    }

    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public String idToken() {
        return idToken;
    }

    public String accountId() {
        return accountId;
    }

    public String clientId() {
        return clientId;
    }

    public String email() {
        return email;
    }

    public String plan() {
        return plan;
    }

    /** Epoch seconds when the access token dies, 0 when unknown. */
    public long expiresAt() {
        return expiresAt;
    }

    /** The chat model slug the user picked, null until they choose one. */
    public String model() {
        return model;
    }

    /** The reasoning effort the user picked, null until they choose one. */
    public String effort() {
        return effort;
    }

    /** The payload of a Codex JWT. No signature check: the token came from the user's own files. */
    private static JsonObject claims(String jwt) {
        if (jwt == null) return null;
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) return null;
        try {
            return JsonParser.parseString(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static String email(JsonObject claims) {
        return string(claims, "email");
    }

    private static String plan(JsonObject claims) {
        if (claims == null || !claims.has("https://api.openai.com/auth")) return null;
        JsonObject auth = claims.getAsJsonObject("https://api.openai.com/auth");
        return string(auth, "chatgpt_plan_type");
    }

    private static long expiry(JsonObject claims) {
        return claims != null && claims.has("exp") ? claims.get("exp").getAsLong() : 0L;
    }

    private static String string(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : null;
    }

    private static String stringOr(JsonObject json, String key, String fallback) {
        String value = string(json, key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
