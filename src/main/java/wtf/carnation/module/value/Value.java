package wtf.carnation.module.value;

import com.google.gson.JsonElement;

public abstract class Value<V> {
    private final String name;
    private final Dependency dependency;
    protected V value;

    public Value(String name, V value, Dependency dependency) {
        this.name = name;
        this.value = value;
        this.dependency = dependency == null ? () -> true : dependency;
    }

    public String getName() {
        return name;
    }

    public V get() {
        return value;
    }

    public V getValue() {
        return value;
    }

    public void set(V value) {
        this.value = value;
    }

    public void setValue(V value) {
        set(value);
    }

    /** Whether the GUI should show this value right now. */
    public boolean isAvailable() {
        return dependency.check();
    }

    /** Text for the GUI and the .module command. */
    public abstract String getDisplayValue();

    /** Sets the value from user text, as typed into a command. */
    public abstract boolean parse(String text);

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement element);

    @FunctionalInterface
    public interface Dependency {
        boolean check();
    }
}
