package wtf.carnation.codex.provider;

import java.util.Locale;

public enum ProviderProtocol {
    OPENAI_CHAT("OpenAI Chat Completions", "https://api.openai.com/v1"),
    OPENAI_RESPONSES("OpenAI Responses", "https://api.openai.com/v1"),
    ANTHROPIC_MESSAGES("Anthropic Messages", "https://api.anthropic.com/v1"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta");

    private final String displayName;
    private final String defaultBaseUrl;

    ProviderProtocol(String displayName, String defaultBaseUrl) {
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public String displayName() {
        return displayName;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    /**
     * Guesses the protocol a Base URL belongs to, or null when the URL says nothing useful.
     * Relay URLs that point at {@code /anthropic} while the OpenAI protocol is selected answer a
     * bare 404, which is hard to read as "wrong protocol".
     */
    public static ProviderProtocol detect(String baseUrl) {
        if (baseUrl == null) return null;
        String url = baseUrl.toLowerCase(Locale.ROOT);
        if (url.contains("/anthropic") || url.contains("anthropic.com")) return ANTHROPIC_MESSAGES;
        if (url.contains("generativelanguage") || url.endsWith("/v1beta")) return GEMINI;
        if (url.endsWith("/responses")) return OPENAI_RESPONSES;
        return null;
    }

    public static ProviderProtocol from(String name) {
        if (name == null) return OPENAI_CHAT;
        String normalized = name.toLowerCase(Locale.ROOT);
        for (ProviderProtocol protocol : values()) {
            if (protocol.name().toLowerCase(Locale.ROOT).equals(normalized)
                    || protocol.displayName.toLowerCase(Locale.ROOT).equals(normalized)) return protocol;
        }
        return OPENAI_CHAT;
    }
}
