package wtf.carnation.codex.provider;

import java.util.List;

public record ApiMessage(String role, String content, String toolCallId, String toolName, List<ApiToolCall> calls) {
    public ApiMessage {
        if (content == null) content = "";
        if (calls == null) calls = List.of();
    }

    public static ApiMessage user(String text) {
        return new ApiMessage("user", text, null, null, List.of());
    }

    public static ApiMessage assistant(String text, List<ApiToolCall> calls) {
        return new ApiMessage("assistant", text, null, null, calls);
    }

    public static ApiMessage tool(String callId, String name, String result) {
        return new ApiMessage("tool", result, callId, name, List.of());
    }
}
