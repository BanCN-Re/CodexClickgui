package wtf.carnation.module.impl.misc;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.RespawnEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.Value;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.ChatUtil;
import wtf.carnation.utils.TimeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;

/** Port of Naven's {@code tech.naven.modules.impl.misc.KillSay}. */
public class KillSay extends Module {
    private final Queue<String> messageQueue = new ConcurrentLinkedQueue<>();
    private final NumberValue delay = new NumberValue("Delay", 6000.0, 0.0, 15000.0, 100.0);
    private final ModeValue prefix = new ModeValue("Prefix", new String[]{"None", "@", "!", "/shout "}, "None");
    private final List<BoolValue> values = new ArrayList<>();
    public static final Set<String> attackedPlayers = new CopyOnWriteArraySet<>();
    private final TimeHelper timer = new TimeHelper();
    private final Random random = new Random();

    public KillSay() {
        super("KillSay", Category.MISC, "Automatic send message when you killed someone!");
    }

    @EventTarget
    public void onRespawn(RespawnEvent e) {
        attackedPlayers.clear();
    }

    @EventTarget
    public void onPacket(PacketEvent e) {
        if (e.isSend() || !(e.getPacket() instanceof ClientboundPlayerInfoRemovePacket packet)
                || mc.getConnection() == null) {
            return;
        }
        for (UUID entry : packet.profileIds()) {
            PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(entry);
            if (playerInfo == null) continue;
            String playerName = playerInfo.getProfile().name();
            if (!attackedPlayers.contains(playerName)) continue;

            String messagePrefix = this.prefix.is("None") ? "" : this.prefix.get();
            List<String> styles = this.values.stream().filter(BoolValue::get).map(Value::getName).toList();
            if (styles.isEmpty()) continue;
            String style = styles.get(this.random.nextInt(styles.size()));
            this.messageQueue.offer(messagePrefix + String.format(style, playerName));
            attackedPlayers.remove(playerName);
        }
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (!e.isPre() || mc.player == null) return;
        if (mc.player.tickCount < 10) {
            this.messageQueue.clear();
            attackedPlayers.clear();
            return;
        }
        if (this.timer.delay(this.delay.get()) && !this.messageQueue.isEmpty()) {
            String message = this.messageQueue.poll();
            if (mc.player.connection != null) mc.player.connection.sendChat(message);
            this.timer.reset();
        }
    }

    public List<BoolValue> getKillValues() {
        return this.values;
    }
}
