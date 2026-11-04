package wtf.carnation.notification;

import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.utils.SmoothAnimationTimer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Port of Naven's {@code NotificationManager}: a stack of toasts in the bottom-right corner.
 *
 * <p>Naven renders these from its HUD module so the {@code Notification} switch can turn them off;
 * {@link wtf.carnation.module.impl.render.HUD} does the same here. The shadow pass Naven draws
 * through {@code EventShader} has no 26.3 equivalent, so the plate is flat.</p>
 */
public final class NotificationManager {
    private final List<Notification> notifications = new CopyOnWriteArrayList<>();

    public void addNotification(Notification notification) {
        if (!this.notifications.contains(notification)) {
            this.notifications.add(notification);
        }
    }

    /** Drops expired toasts and draws the rest. Called from the HUD module. */
    public void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        float height = 5.0F;
        float screenW = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
        float screenH = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();

        for (Notification notification : this.notifications) {
            float width = notification.getWidth();
            height += notification.getHeight();

            SmoothAnimationTimer widthTimer = notification.getWidthTimer();
            SmoothAnimationTimer heightTimer = notification.getHeightTimer();
            long lifeTime = System.currentTimeMillis() - notification.getCreateTime();

            if (lifeTime > notification.getMaxAge()) {
                widthTimer.target = 0.0F;
                heightTimer.target = 0.0F;
                if (widthTimer.isAnimationDone(true)) {
                    this.notifications.remove(notification);
                }
            } else {
                widthTimer.target = width;
                heightTimer.target = height;
            }

            widthTimer.update(true);
            heightTimer.update(true);
            if (widthTimer.value <= 0.0F) continue;

            float x = screenW - widthTimer.value + 2.0F;
            float y = screenH - heightTimer.value;
            Render2D.roundedRect(graphics, x, y, notification.getWidth(),
                    notification.getHeight(), 5.0F, notification.getLevel().getColor());
            UiFont.Z_SMALL.drawInBox(graphics, notification.getMessage(), x + 6.0F, y,
                    notification.getHeight(), 0xFFFFFFFF);
        }
    }

    public void clear() {
        this.notifications.clear();
    }
}
