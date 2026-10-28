package wtf.carnation.module.value.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import wtf.carnation.module.value.Value;

@Getter
public class NumberValue extends Value<Double> {
    private final double min, max, inc;

    public NumberValue(String name, double value, double min, double max, double inc) {
        this(name, value, min, max, inc, null);
    }

    public NumberValue(String name, double value, double min, double max, double inc, Dependency dependency) {
        super(name, value, dependency);
        this.min = min;
        this.max = max;
        this.inc = inc;
    }

    /** Clamps to the range and snaps to the increment. */
    @Override
    public void set(Double value) {
        double clamped = Math.max(min, Math.min(max, value));
        if (inc > 0) clamped = min + Math.round((clamped - min) / inc) * inc;
        super.set(Math.max(min, Math.min(max, clamped)));
    }

    public float getFloat() {
        return value.floatValue();
    }

    public int getInt() {
        return value.intValue();
    }

    @Override
    public String getDisplayValue() {
        if (inc >= 1 && inc == Math.rint(inc)) return String.valueOf(value.longValue());
        int decimals = Math.max(1, (int) Math.ceil(-Math.log10(inc)));
        return String.format("%." + decimals + "f", value);
    }

    @Override
    public boolean parse(String text) {
        try {
            set(Double.parseDouble(text));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element.isJsonPrimitive()) set(element.getAsDouble());
    }
}
