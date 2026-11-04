package wtf.carnation.utils;

/** Port of Naven's {@code tech.naven.utils.TimeHelper}. */
public final class TimeHelper {
    private long lastMS = 0L;

    public boolean delay(double milliseconds) {
        return getCurrentMS() - lastMS >= milliseconds;
    }

    public boolean delay(double milliseconds, boolean reset) {
        boolean result = getCurrentMS() - lastMS >= milliseconds;
        if (result && reset) reset();
        return result;
    }

    public void reset() {
        lastMS = getCurrentMS();
    }

    public long time() {
        return getCurrentMS() - lastMS;
    }

    public long getCurrentMS() {
        return System.nanoTime() / 1_000_000L;
    }
}
