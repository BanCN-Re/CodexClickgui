package wtf.carnation.module.impl.movement;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import wtf.carnation.Client;
import wtf.carnation.event.EventPriority;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.MoveInputEvent;
import wtf.carnation.event.impl.movement.RespawnEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.utils.RotationManager;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Port of Naven's {@code tech.naven.modules.impl.move.Stuck}.
 *
 * <p>Naven sends its queued packets through {@code NetworkUtils.sendPacketNoEvent}; this client
 * writes them straight to the connection (nothing in the packet handler cancels them).</p>
 */
public class Stuck extends Module {
    int stage = 0;
    Packet<?> packet;
    float lastYaw;
    float lastPitch;
    boolean tryDisable = false;
    final Queue<ServerboundPongPacket> packets = new ConcurrentLinkedQueue<>();

    public Stuck() {
        super("Stuck", Category.MOVEMENT, "Stuck in air!");
    }

    @Override
    protected void onEnable() {
        this.stage = 0;
        this.packet = null;
        float[] rotations = RotationManager.rotations() == null
                ? new float[]{0.0F, 0.0F}
                : new float[]{RotationManager.rotations().x, RotationManager.rotations().y};
        this.lastYaw = rotations[0];
        this.lastPitch = rotations[1];
        this.tryDisable = false;
    }

    @Override
    public void setState(boolean enabled) {
        if (mc.player != null) {
            if (enabled) {
                super.setState(true);
            } else if (this.stage == 3) {
                super.setState(false);
            } else {
                this.tryDisable = true;
            }
        }
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        Module scaffold = Client.instance.getModuleManager().getModuleByName("Scaffold");
        if (scaffold != null && scaffold.isEnabled()) {
            scaffold.toggle();
            return;
        }
        if (!e.isPre() || mc.player == null) return;

        mc.player.setDeltaMovement(0.0, 0.0, 0.0);
        if (this.stage == 1) {
            this.stage = 2;
            float rotationYaw = mc.player.getYRot();
            float rotationPitch = mc.player.getXRot();
            if (this.shouldRotate() && (this.lastYaw != rotationYaw || this.lastPitch != rotationPitch)) {
                this.send(new ServerboundMovePlayerPacket.Rot(rotationYaw, rotationPitch,
                        mc.player.onGround(), mc.player.horizontalCollision));
                this.flushPongs();
                this.lastYaw = rotationYaw;
                this.lastPitch = rotationPitch;
            }
            this.send(this.packet);
        }

        if (this.tryDisable) {
            this.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX() + 1337.0, mc.player.getY(),
                    mc.player.getZ() + 1337.0, mc.player.onGround(), mc.player.horizontalCollision));
            this.flushPongs();
            this.tryDisable = false;
        }
    }

    private void flushPongs() {
        while (!this.packets.isEmpty()) {
            this.send(this.packets.poll());
        }
    }

    private void send(Packet<?> packet) {
        if (packet != null && mc.getConnection() != null) mc.getConnection().send(packet);
    }

    private static boolean isBowlFood(ItemStack stack) {
        return stack.is(Items.MUSHROOM_STEW) || stack.is(Items.RABBIT_STEW)
                || stack.is(Items.BEETROOT_SOUP) || stack.is(Items.SUSPICIOUS_STEW);
    }

    private boolean shouldRotate() {
        if (this.packet instanceof ServerboundUseItemPacket blockPlacement) {
            ItemStack item = mc.player.getItemInHand(blockPlacement.hand());
            return !isBowlFood(item) && !(item.getItem() instanceof BowItem);
        } else if (this.packet instanceof ServerboundPlayerActionPacket playerDigging) {
            return playerDigging.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                    && mc.player.getUseItem().getItem() instanceof BowItem;
        }
        return false;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent e) {
        e.setForward(0.0F);
        e.setStrafe(0.0F);
        e.setJump(false);
        e.setSneak(false);
    }

    @EventTarget
    public void onRespawn(RespawnEvent e) {
        this.stage = 3;
        this.packet = null;
        this.toggle();
    }

    @EventTarget
    @EventPriority(1)
    public void onPacket(PacketEvent e) {
        if (e.getPacket() instanceof ServerboundMovePlayerPacket) {
            e.setCancelled(true);
        } else if (e.getPacket() instanceof ServerboundPongPacket pong) {
            this.packets.offer(pong);
            e.setCancelled(true);
        } else if (e.getPacket() instanceof ServerboundUseItemPacket
                || e.getPacket() instanceof ServerboundPlayerActionPacket) {
            this.packet = e.getPacket();
            this.stage = 1;
            e.setCancelled(true);
        } else if (e.getPacket() instanceof ClientboundPlayerPositionPacket) {
            this.flushPongs();
            this.stage = 3;
            this.toggle();
        }
    }
}
