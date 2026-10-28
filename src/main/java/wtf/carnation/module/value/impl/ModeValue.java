package wtf.carnation.module.value.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import wtf.carnation.module.value.Value;

public class ModeValue extends Value<String> {
    private final String[] modes;

    public ModeValue(String name, String[] modes, String value) {
        this(name, modes, value, null);
    }

    public ModeValue(String name, String[] modes, String value, Dependency dependency) {
        super(name, value, dependency);
        this.modes = modes;
    }

    public String[] getModes() {
        return modes.clone();
    }

    public boolean is(String mode) {
        return value.equalsIgnoreCase(mode);
    }

    public int index() {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equalsIgnoreCase(value)) return i;
        }
        return 0;
    }

    public void cycle(int direction) {
        int next = Math.floorMod(index() + direction, modes.length);
        set(modes[next]);
    }

    @Override
    public void set(String mode) {
        for (String candidate : modes) {
            if (candidate.equalsIgnoreCase(mode)) {
                super.set(candidate);
                return;
            }
        }
    }

    @Override
    public String getDisplayValue() {
        return value;
    }

    @Override
    public boolean parse(String text) {
        for (String candidate : modes) {
            if (candidate.equalsIgnoreCase(text)) {
                set(candidate);
                return true;
            }
        }
        return false;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element.isJsonPrimitive()) set(element.getAsString());
    }
}
