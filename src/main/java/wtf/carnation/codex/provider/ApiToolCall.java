package wtf.carnation.codex.provider;

import java.util.List;

public record ApiToolCall(String id, String name, String arguments) {
    public ApiToolCall {
        if (id == null || id.isBlank()) id = "call_" + Math.abs((name + arguments).hashCode());
        if (arguments == null || arguments.isBlank()) arguments = "{}";
    }
}
