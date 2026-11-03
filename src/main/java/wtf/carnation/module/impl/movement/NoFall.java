package wtf.carnation.module.impl.movement;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.PacketUtil;

public class NoFall extends Module {
    private final NumberValue fallDistance = new NumberValue("Fall Distance", 3.0, 3.0, 15.0, 0.5);

    public NoFall() {
        super("NoFall", Category.MOVEMENT, "下落时把移动包标记为着地，避免摔落伤害");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isSend() || mc.player == null) return;
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket)) return;
        if (mc.player.onGround() || mc.player.fallDistance < fallDistance.get()) return;
        event.setPacket(PacketUtil.withOnGround(event.getPacket(), true));
    }
}
