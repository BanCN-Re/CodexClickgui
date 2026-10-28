package wtf.carnation.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import wtf.carnation.Client;

public final class ChatUtil {
    private static final int PREFIX_GRADIENT_START = 0x7AA2FF;
    private static final int PREFIX_GRADIENT_END = 0xB18CFF;

    private ChatUtil() {
    }

    private static MutableComponent prefix() {
        MutableComponent result = Component.empty().append(Component.literal("[").withStyle(ChatFormatting.GRAY));
        String text = Client.name;
        int steps = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            int color = lerpRgb(PREFIX_GRADIENT_START, PREFIX_GRADIENT_END, (float) i / steps);
            result.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(style -> style.withColor(TextColor.fromRgb(color))));
        }
        return result.append(Component.literal("] ").withStyle(ChatFormatting.GRAY));
    }

    private static int lerpRgb(int a, int b, float t) {
        int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return r << 16 | g << 8 | bl;
    }

    /** Adds a client-side line to chat; never sent to the server. */
    public static void addChatMessage(Object message) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.gui.hud == null) return;
            mc.gui.hud.getChat().addClientSystemMessage(prefix().append(Component.literal(String.valueOf(message))
                    .withStyle(ChatFormatting.WHITE)));
        });
    }
}
