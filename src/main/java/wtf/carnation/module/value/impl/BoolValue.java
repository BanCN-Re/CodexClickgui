package wtf.carnation.module.value.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import wtf.carnation.module.value.Value;

public class BoolValue extends Value<Boolean> {

    public BoolValue(String name, boolean value) {
        this(name, value, null);
    }

    public BoolValue(String name, boolean value, Dependency dependency) {
        super(name, value, dependency);
    }

    public void toggle() {
        set(!value);
    }

    @Override
    public String getDisplayValue() {
        return value ? "On" : "Off";
    }

    @Override
    public boolean parse(String text) {
        switch (text.toLowerCase()) {
            case "true", "on", "1", "yes" -> set(true);
            case "false", "off", "0", "no" -> set(false);
            case "toggle" -> toggle();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element.isJsonPrimitive()) set(element.getAsBoolean());
    }
}
