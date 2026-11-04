package wtf.carnation.module.impl.render;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.utils.SmoothAnimationTimer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Port of Naven's {@code EffectDisplay}; the blur pass is replaced by the panel fill. */
public class EffectDisplay extends Module {
    private final Map<Holder<MobEffect>, MobEffectInfo> infos = new ConcurrentHashMap<>();

    public EffectDisplay() {
        super("EffectDisplay", Category.RENDER, "Displays potion effects on the HUD");
    }

    @EventTarget
    public void onRender(Render2DEvent e) {
        if (mc.player == null) return;
        UiFont.useGuiScale();

        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            MobEffectInfo info = this.infos.computeIfAbsent(effect.getEffect(), ignored -> new MobEffectInfo());
            info.maxDuration = Math.max(info.maxDuration, effect.getDuration());
            info.duration = effect.getDuration();
            info.amplifier = effect.getAmplifier();
            info.shouldDisappear = false;
        }

        int startY = mc.getWindow().getGuiScaledHeight() / 2 - this.infos.size() * 16;
        for (Map.Entry<Holder<MobEffect>, MobEffectInfo> entry : this.infos.entrySet()) {
            MobEffectInfo effectInfo = entry.getValue();
            String text = this.getDisplayName(entry.getKey(), effectInfo);
            if (effectInfo.yTimer.value == -1.0F) effectInfo.yTimer.value = startY;

            effectInfo.width = 25.0F + UiFont.Z_SMALL.width(text) + 20.0F;
            float x = effectInfo.xTimer.value;
            float y = effectInfo.yTimer.value;
            effectInfo.shouldDisappear = !mc.player.hasEffect(entry.getKey());
            if (effectInfo.shouldDisappear) {
                effectInfo.xTimer.target = -effectInfo.width - 20.0F;
                if (x <= -effectInfo.width - 20.0F) this.infos.remove(entry.getKey());
            } else {
                effectInfo.durationTimer.target = (float) effectInfo.duration / effectInfo.maxDuration * effectInfo.width;
                if (effectInfo.durationTimer.value <= 0.0F) {
                    effectInfo.durationTimer.value = effectInfo.durationTimer.target;
                }
                effectInfo.xTimer.target = 10.0F;
                effectInfo.yTimer.target = startY;
                effectInfo.yTimer.update(true);
            }

            effectInfo.durationTimer.update(true);
            effectInfo.xTimer.update(true);

            Render2D.roundedRect(e.graphics(), x, y, effectInfo.width, 30.0F, 5.0F, 0x32000000);
            Render2D.roundedRect(e.graphics(), x, y, effectInfo.durationTimer.value, 30.0F, 5.0F, 0x32000000);
            Render2D.roundedRect(e.graphics(), x + effectInfo.width - 10.0F, y + 7.0F, 5.0F, 18.0F, 2.0F, 0xFF962D2D);
            UiFont.Z_SMALL.draw(e.graphics(), text, x + 27.0F, y + 5.0F, 0xFF962D2D);
            String duration = StringUtil.formatTickDuration(effectInfo.duration,
                    mc.level.tickRateManager().tickrate());
            UiFont.Z_SMALL.draw(e.graphics(), duration, x + 27.0F, y + 16.0F, 0xFFFFFFFF);
            startY += 34;
        }
    }

    public String getDisplayName(Holder<MobEffect> effect, MobEffectInfo info) {
        String effectName = effect.value().getDisplayName().getString();
        String amplifierName;
        if (info.amplifier == 0) {
            amplifierName = "";
        } else if (info.amplifier == 1) {
            amplifierName = " " + Component.translatable("enchantment.level.2").getString();
        } else if (info.amplifier == 2) {
            amplifierName = " " + Component.translatable("enchantment.level.3").getString();
        } else if (info.amplifier == 3) {
            amplifierName = " " + Component.translatable("enchantment.level.4").getString();
        } else {
            amplifierName = " " + info.amplifier;
        }
        return effectName + amplifierName;
    }

    public static class MobEffectInfo {
        public final SmoothAnimationTimer xTimer = new SmoothAnimationTimer(-60.0F, 0.2F);
        public final SmoothAnimationTimer yTimer = new SmoothAnimationTimer(-1.0F, 0.2F);
        public final SmoothAnimationTimer durationTimer = new SmoothAnimationTimer(-1.0F, 0.2F);
        public int maxDuration = -1;
        public int duration = 0;
        public int amplifier = 0;
        public boolean shouldDisappear = false;
        public float width;
    }
}
