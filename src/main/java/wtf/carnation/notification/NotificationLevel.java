package wtf.carnation.notification;

/** Severity of a toast; the colour fills the notification's plate. */
public enum NotificationLevel {
    SUCCESS(0xFF179626),
    INFO(0xFF171626),
    WARNING(0xFF8A5A5C),
    ERROR(0xFF942A2B);

    private final int color;

    NotificationLevel(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }
}
