package wtf.carnation.module.impl.combat;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.utils.RotationUtils;
import wtf.carnation.utils.Vector2f;

/**
 * Port of Naven's {@code tech.naven.modules.impl.combat.AttackCrystal} ("CrystalAura").
 *
 * <p>26.3 replaced {@code ServerboundInteractPacket.createAttackPacket} with
 * {@link ServerboundAttackPacket}. The click path runs from the pre-tick while the attack key is
 * held, because this client has no EventClick hook yet.</p>
 */
public class AttackCrystal extends Module {
    public static Vector2f rotations;
    private Entity entity;
    private final BoolValue packet = new BoolValue("Attack on Packet (Danger)", false);

    public AttackCrystal() {
        super("CrystalAura", Category.COMBAT, "Automatically attacks end crystals");
    }

    @EventTarget
    public void onPacket(PacketEvent e) {
        if (e.isSend() || !(e.getPacket() instanceof ClientboundAddEntityPacket p)) return;
        if (!this.packet.get() || p.getType() != EntityTypes.END_CRYSTAL || mc.level == null || mc.player == null) {
            return;
        }
        EndCrystal pTarget = new EndCrystal(mc.level, p.getX(), p.getY(), p.getZ());
        pTarget.setId(p.getId());
        if (mc.player.distanceTo(pTarget) > 4.0F) return;

        Vector2f rots = RotationUtils.getRotations(pTarget);
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), rots.x, rots.y,
                mc.player.onGround(), mc.player.horizontalCollision));
        mc.getConnection().send(new ServerboundUseItemPacket(
                InteractionHand.MAIN_HAND, 0, mc.player.getYRot(), mc.player.getXRot()));

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        mc.player.setYRot(rots.x);
        mc.player.setXRot(rots.y);
        mc.getConnection().send(new ServerboundAttackPacket(pTarget.getId()));
        mc.player.swing(InteractionHand.MAIN_HAND,
                net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        mc.player.setYRot(currentYaw);
        mc.player.setXRot(currentPitch);
    }

    @EventTarget
    public void onEarlyTick(RunTickEvent e) {
        if (!e.isPre() || mc.player == null || mc.level == null) return;
        rotations = null;
        this.entity = null;
        for (Entity candidate : mc.level.entitiesForRendering()) {
            if (!(candidate instanceof EndCrystal)) continue;
            Vector2f rots = RotationUtils.getRotations(candidate);
            if (RotationUtils.getMinDistance(candidate, rots) <= 3.0) {
                rotations = rots;
                this.entity = candidate;
            }
            break;
        }
        if (this.entity != null && rotations != null && mc.options.keyAttack.isDown()) {
            this.attack();
        }
    }

    private void attack() {
        if (this.entity == null || rotations == null) return;
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        mc.player.setYRot(rotations.x);
        mc.player.setXRot(rotations.y);
        mc.getConnection().send(new ServerboundAttackPacket(this.entity.getId()));
        mc.player.swing(InteractionHand.MAIN_HAND,
                net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        mc.player.setYRot(currentYaw);
        mc.player.setXRot(currentPitch);
        this.entity = null;
    }
}
