package wtf.carnation.notification;

import wtf.carnation.graphics.UiFont;
import wtf.carnation.utils.SmoothAnimationTimer;

/**
 * Port of Naven's {@code tech.naven.ui.notification.Notification}: one toast with its own
 * slide-in timers and a lifetime.
 */
public final class Notification {
    private static final float PLATE_HEIGHT = 20.0F;
    private static final float HORIZONTAL_PADDING = 12.0F;

    private NotificationLevel level;
    private String message;
    private long maxAge;
    private long createTime = System.currentTimeMillis();
    private final SmoothAnimationTimer widthTimer = new SmoothAnimationTimer(0.0F);
    private final SmoothAnimationTimer heightTimer = new SmoothAnimationTimer(0.0F);

    public Notification(NotificationLevel level, String message, long maxAge) {
        this.level = level;
        this.message = message;
        this.maxAge = maxAge;
    }

    /**
     * Naven measures with {@code Fonts.harmony.getWidth(message, 0.35)}. Our baked TrueType atlas
     * has fixed sizes, so the message is drawn at {@link UiFont#Z_SMALL} and measured there.
     */
    public float getWidth() {
        return UiFont.Z_SMALL.width(this.message) + HORIZONTAL_PADDING;
    }

    public float getHeight() {
        return PLATE_HEIGHT;
    }

    public NotificationLevel getLevel() {
        return level;
    }

    public void setLevel(NotificationLevel level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public SmoothAnimationTimer getWidthTimer() {
        return widthTimer;
    }

    public SmoothAnimationTimer getHeightTimer() {
        return heightTimer;
    }
}
