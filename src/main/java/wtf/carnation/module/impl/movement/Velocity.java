package wtf.carnation.module.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import mixin.KeyMappingAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MoveInputEvent;
import wtf.carnation.event.impl.movement.RespawnEvent;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.combat.Aura;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.RotationManager;
import wtf.carnation.utils.RotationUtils;
import wtf.carnation.utils.Vector2f;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Port of Naven's {@code tech.naven.modules.impl.combat.Velocity} (category Movement).
 *
 * <p>Uses this client's {@link PacketEvent} in place of Naven's EventHandlePacket; cancelling it
 * queues the packet and the queue is replayed once the delay window closes.</p>
 */
public class Velocity extends Module {
    public final NumberValue attackAmount = new NumberValue("Attack Amount", 5.0, 1.0, 5.0, 1.0);
    public final NumberValue maxDelayTicks = new NumberValue("Max Delay Ticks", 10.0, 0.0, 40.0, 1.0);
    public final BoolValue jumpReset = new BoolValue("Jump Reset", false);

    private final Queue<Packet<?>> packetQueue = new ConcurrentLinkedQueue<>();
    public boolean handlingVelocity;
    public boolean compensating;
    public boolean inDelayWindow;
    public int counterRemaining;

    private boolean positionCorrection;
    private int delayTicks;
    private int noAimTicks;
    private int noSprintTicks;
    private int jumpTicks;
    private boolean jumpKeyForced;
    private long compensateUntilMs = -1L;
    private Entity target;
    private Entity lastAttacker;
    private long lastHurtTime;

    public Velocity() {
        super("Velocity", Category.MOVEMENT, "Reduces knockback.");
    }

    public boolean isActive() {
        return this.inDelayWindow || this.counterRemaining > 0;
    }

    public boolean isCountering() {
        return this.counterRemaining > 0;
    }

    public boolean isInDelayWindow() {
        return this.inDelayWindow;
    }

    public Entity getTarget() {
        return this.target;
    }

    @Override
    protected void onEnable() {
        this.resetAll();
    }

    @Override
    protected void onDisable() {
        this.resetAll();
    }

    @EventTarget
    public void onRespawn(RespawnEvent event) {
        this.resetAll();
    }

    @EventTarget
    public void onTick(RunTickEvent event) {
        if (!event.isPre()) return;
        if (mc.player == null || mc.level == null) return;

        this.tickJumpReset();
        this.target = this.auraOrFallbackTarget();

        if (this.compensateUntilMs != -1L
                && (System.currentTimeMillis() >= this.compensateUntilMs || mc.options.keyUp.isDown())) {
            this.compensating = false;
            this.compensateUntilMs = -1L;
        }

        if (this.shouldNotEngage() || this.noAimTicks >= 3) {
            this.resetAll();
            return;
        }

        if (this.inDelayWindow) {
            this.compensating = true;
            this.counterRemaining = 0;
            if (this.delayTicks >= this.maxDelayTicks.getInt()) {
                this.resetAll();
                return;
            }
            this.delayTicks++;
            if (this.isAimingAtTarget() && mc.player.isSprinting() && mc.player.onGround()) {
                this.counterRemaining = this.attackAmount.getInt();
                this.noAimTicks = 0;
                this.inDelayWindow = false;
                this.handlingVelocity = false;
                this.flushQueue();
                if (this.jumpReset.get()) this.jumpTicks = 1;
            }
        }

        if (this.counterRemaining > 0) {
            if (!this.isAimingAtTarget()) {
                this.noAimTicks++;
                return;
            }
            if (!mc.player.isSprinting()) {
                this.noSprintTicks++;
                if (this.noSprintTicks >= 3) this.resetAll();
                return;
            }
            this.noSprintTicks = 0;
            this.attackReduce(this.target);
            this.counterRemaining--;
        } else if (this.compensating) {
            this.compensateUntilMs = System.currentTimeMillis() + 10L;
        }
    }

    private void tickJumpReset() {
        if (!this.jumpReset.get() || this.jumpTicks <= 0) {
            this.restoreJumpKey();
            return;
        }
        Module scaffold = Client.instance.getModuleManager().getModuleByName("Scaffold");
        if (scaffold != null && scaffold.isEnabled()) return;
        if (mc.player.isOnFire()) {
            this.jumpTicks = 0;
            this.restoreJumpKey();
            return;
        }
        this.jumpKeyForced = true;
        mc.options.keyJump.setDown(true);
        this.jumpTicks--;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.isSend() || mc.player == null || mc.level == null) return;
        Packet<?> packet = event.getPacket();

        if (packet instanceof ClientboundPlayerPositionPacket) {
            this.positionCorrection = true;
            return;
        }

        if (packet instanceof ClientboundHurtAnimationPacket hurt && hurt.id() == mc.player.getId()) {
            Entity candidate = Aura.target != null ? Aura.target : this.crosshairTarget();
            if (candidate != null) {
                this.lastAttacker = candidate;
                this.lastHurtTime = System.currentTimeMillis();
            }
        }

        if (this.shouldNotEngage()) return;

        if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == mc.player.getId()) {
            if (!this.inDelayWindow) {
                this.inDelayWindow = true;
                this.delayTicks = 0;
                this.handlingVelocity = true;
            }
            event.setCancelled(true);
            this.packetQueue.add(packet);
        }

        if (this.isAllowedPacket(packet)) return;
        if (!this.inDelayWindow) return;

        event.setCancelled(true);
        this.packetQueue.add(packet);
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null) return;
        if (this.compensating) event.setForward(1.0F);
    }

    private void attackReduce(Entity entity) {
        if (entity == null || mc.player == null || mc.getConnection() == null) return;
        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND,
                net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        Vec3 movement = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(movement.x * 0.6, movement.y, movement.z * 0.6);
    }

    private Entity crosshairTarget() {
        return mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
    }

    private Entity auraOrFallbackTarget() {
        if (Aura.target != null) return Aura.target;
        Entity crosshair = this.crosshairTarget();
        if (crosshair != null) return crosshair;
        return this.lastAttacker != null && this.lastAttacker.isAlive()
                && System.currentTimeMillis() - this.lastHurtTime <= 1500L
                ? this.lastAttacker : null;
    }

    private boolean isAimingAtTarget() {
        if (this.target == null || mc.player == null) return false;
        Vector2f aim = RotationManager.rotations() != null
                ? RotationManager.rotations()
                : new Vector2f(mc.player.getYRot(), mc.player.getXRot());
        return RotationUtils.getDistance(this.target, mc.player.getEyePosition(), aim) < 3.0;
    }

    private boolean shouldNotEngage() {
        if (mc.player == null || mc.getConnection() == null) return true;
        if (mc.player.isUsingItem() || mc.player.isInLava() || mc.player.isInWater()) return true;
        if (mc.level != null && mc.level.getBlockState(mc.player.blockPosition()).is(Blocks.COBWEB)) return true;
        if (this.positionCorrection) return true;
        if (mc.gui.screen() instanceof InventoryScreen) return true;
        if (this.target == null || !this.target.isAlive()) return true;
        if (mc.player.isDeadOrDying() || mc.player.isSpectator()) return true;
        Module scaffold = Client.instance.getModuleManager().getModuleByName("Scaffold");
        if (scaffold != null && scaffold.isEnabled()) return true;
        Module stuck = Client.instance.getModuleManager().getModuleByName("Stuck");
        return stuck != null && stuck.isEnabled();
    }

    private boolean isAllowedPacket(Packet<?> packet) {
        return packet instanceof ClientboundSetEntityMotionPacket
                || packet instanceof ClientboundSetHealthPacket
                || packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundSoundPacket
                || packet instanceof ClientboundPlayerChatPacket
                || packet instanceof ClientboundPlayerCombatKillPacket
                || packet instanceof ClientboundContainerClosePacket
                || packet instanceof ClientboundHurtAnimationPacket
                || packet instanceof ClientboundSetTitleTextPacket
                || packet instanceof ClientboundSetPlayerTeamPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ClientboundDisconnectPacket
                || packet instanceof ClientboundAnimatePacket animation && animation.getId() != mc.player.getId();
    }

    @SuppressWarnings("unchecked")
    private void flushQueue() {
        Minecraft client = mc;
        client.execute(() -> {
            ClientPacketListener connection = client.getConnection();
            if (connection == null) {
                this.packetQueue.clear();
                return;
            }
            Packet<?> packet;
            while ((packet = this.packetQueue.poll()) != null) {
                try {
                    wtf.carnation.event.PacketHook.sendSilently(packet);
                } catch (Exception exception) {
                    this.packetQueue.clear();
                    break;
                }
            }
        });
    }

    private void restoreJumpKey() {
        if (!this.jumpKeyForced) return;
        this.jumpKeyForced = false;
        if (mc.player == null) return;
        Module scaffold = Client.instance.getModuleManager().getModuleByName("Scaffold");
        if (scaffold != null && scaffold.isEnabled()) return;
        int key = ((KeyMappingAccessor) mc.options.keyJump).carnation$getKey().getValue();
        mc.options.keyJump.setDown(InputConstants.isKeyDown(key));
    }

    private void resetAll() {
        this.flushQueue();
        this.inDelayWindow = false;
        this.compensating = false;
        this.positionCorrection = false;
        this.delayTicks = 0;
        this.counterRemaining = 0;
        this.noAimTicks = 0;
        this.noSprintTicks = 0;
        this.compensateUntilMs = -1L;
        this.jumpTicks = 0;
        this.restoreJumpKey();
        this.target = null;
        this.handlingVelocity = false;
    }
}
