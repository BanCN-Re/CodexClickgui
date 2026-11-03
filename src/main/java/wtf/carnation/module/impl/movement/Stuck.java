package wtf.carnation.module.impl.movement;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.PacketHook;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.ModeValue;

/**
 * Cancels the client's movement packets so the server keeps the last position.
 * Disabling sends one position packet to resynchronise.
 */
public class Stuck extends Module {
    private final ModeValue mode = new ModeValue("Mode", new String[]{"Cancel Move"}, "Cancel Move");

    public Stuck() {
        super("Stuck", Category.MOVEMENT, "取消移动包，让服务端停在原地");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!mode.is("Cancel Move")) return;
        if (event.isSend()) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            setState(false);
        }
    }

    @Override
    protected void onDisable() {
        if (mc.player == null) return;
        PacketHook.sendSilently(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.getYRot(), mc.player.getXRot(),
                mc.player.onGround(), false));
    }
}
