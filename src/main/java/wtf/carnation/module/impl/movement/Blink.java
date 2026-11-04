package wtf.carnation.module.impl.movement;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.combat.AntiBots;
import wtf.carnation.module.impl.misc.Teams;
import wtf.carnation.module.impl.render.projectiles.ProjectileData;
import wtf.carnation.module.impl.render.projectiles.datas.BasicProjectileData;
import wtf.carnation.module.impl.render.projectiles.datas.EntityArrowData;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.BlinkingPlayer;
import wtf.carnation.utils.FriendManager;
import wtf.carnation.utils.RayTraceUtil;
import wtf.carnation.utils.RotationUtils;
import wtf.carnation.utils.SmoothAnimationTimer;

import java.awt.Color;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Port of Naven's {@code tech.naven.modules.impl.move.Blink}.
 *
 * <p>Holds every outbound packet and replays movement packets at the release speed, with the
 * fake "real position" player for danger checks. The packet whitelist is matched by class name so
 * it survives package moves.</p>
 */
public class Blink extends Module {
    private static final int MAIN_COLOR = new Color(150, 45, 45, 255).getRGB();
    private static final Set<String> whitelist = Set.of(
            "net.minecraft.network.protocol.handshake.ClientIntentionPacket",
            "net.minecraft.network.protocol.status.ServerboundStatusRequestPacket",
            "net.minecraft.network.protocol.ping.ServerboundPingRequestPacket",
            "net.minecraft.network.protocol.login.ServerboundHelloPacket",
            "net.minecraft.network.protocol.login.ServerboundKeyPacket");

    private final EntityArrowData arrowData = new EntityArrowData();
    private final BasicProjectileData eggData = new BasicProjectileData(
            Collections.singleton(ThrownEgg.class), new Color(255, 238, 154));
    private final BasicProjectileData snowballData = new BasicProjectileData(
            Collections.singleton(Snowball.class), new Color(255, 255, 255));

    private final Queue<Packet<?>> packets = new ConcurrentLinkedQueue<>();
    private final SmoothAnimationTimer progress = new SmoothAnimationTimer(0.0F, 0.2F);
    public final NumberValue releaseOnDamage = new NumberValue("Release Ticks on Damage", 20.0, 0.0, 50.0, 1.0);
    public final NumberValue releaseSpeed = new NumberValue("Release Speed (Tick)", 10.0, 3.0, 20.0, 1.0);
    public final NumberValue maxTicks = new NumberValue("Max Ticks", 200.0, 10.0, 500.0, 1.0);
    public final NumberValue playerDistance = new NumberValue("Player Distance", 4.0, 3.0, 10.0, 0.1);
    public final NumberValue tntDistance = new NumberValue("TNT Distance", 5.0, 3.0, 10.0, 0.1);
    public final NumberValue projectilesExpands = new NumberValue("Fake Player HitBoxes", 0.2, 0.0, 3.0, 0.01);

    private boolean disabling = false;
    private BlinkingPlayer fakePlayer;
    private int shouldReleaseTicks = 0;
    private int releasedTicks = 0;

    public Blink() {
        super("Blink", Category.MOVEMENT, "Suspends all movement packets for teleporting!");
    }

    private long getBlinkTicks() {
        return this.packets.stream().filter(packet -> packet instanceof ServerboundMovePlayerPacket).count();
    }

    private void handleMove(ServerboundMovePlayerPacket packet) {
        this.fakePlayer.moveOrInterpolateTo(
                new Vec3(packet.getX(this.fakePlayer.getX()), packet.getY(this.fakePlayer.getY()),
                        packet.getZ(this.fakePlayer.getZ())),
                packet.getYRot(this.fakePlayer.getYRot()), packet.getXRot(this.fakePlayer.getXRot()));
        if (packet.hasRotation()) {
            this.fakePlayer.setYRot(packet.getYRot(this.fakePlayer.getYRot()));
            this.fakePlayer.setYHeadRot(packet.getYRot(this.fakePlayer.getYRot()));
            this.fakePlayer.setXRot(packet.getXRot(this.fakePlayer.getXRot()));
        }
    }

    @SuppressWarnings("unchecked")
    private void releaseTick() {
        while (!this.packets.isEmpty()) {
            Packet<?> poll = this.packets.poll();
            if (poll != null && mc.getConnection() != null) {
                wtf.carnation.event.PacketHook.sendSilently(poll);
            }
            if (poll instanceof ServerboundMovePlayerPacket move) {
                this.releasedTicks++;
                this.handleMove(move);
                break;
            }
        }
    }

    @Override
    protected void onEnable() {
        this.packets.clear();
        this.shouldReleaseTicks = 0;
        this.disabling = false;
        if (mc.player == null || mc.level == null) return;
        this.fakePlayer = new BlinkingPlayer(mc.player);
        this.fakePlayer.setSprinting(mc.player.isSprinting());
        mc.level.addEntity(this.fakePlayer);
    }

    @Override
    protected void onDisable() {
        if (this.fakePlayer != null && mc.level != null) {
            mc.level.removeEntity(this.fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
            this.fakePlayer = null;
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.fakePlayer == null) return;
        int x = mc.getWindow().getGuiScaledWidth() / 2 - 50;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 15;
        this.progress.update(true);
        Render2D.roundedRect(event.graphics(), x, y, 100.0F, 5.0F, 2.0F, 0xFF101014);
        Render2D.roundedRect(event.graphics(), x, y, this.progress.value, 5.0F, 2.0F, MAIN_COLOR);
    }

    private boolean isPlayerNear(double distance) {
        if (this.fakePlayer == null || mc.level == null) return false;
        long players = mc.level.players().stream().filter(player -> {
            if (player == mc.player) return false;
            if (player instanceof BlinkingPlayer) return false;
            if (Teams.isSameTeam(player)) return false;
            if (FriendManager.isFriend(player)) return false;
            if (AntiBots.isBot(player)) return false;
            Vec3 eyePosition = player.getEyePosition();
            Vec3 closestPoint = RotationUtils.getClosestPoint(eyePosition, this.fakePlayer.getBoundingBox());
            return eyePosition.distanceTo(closestPoint) < distance;
        }).count();
        return players > 0L;
    }

    private boolean isTNTNear(double distance) {
        if (this.fakePlayer == null || mc.level == null) return false;
        return mc.level.entitiesForRendering().iterator().hasNext() && java.util.stream.StreamSupport
                .stream(mc.level.entitiesForRendering().spliterator(), true)
                .anyMatch(entity -> entity instanceof PrimedTnt && this.fakePlayer.distanceTo(entity) <= distance);
    }

    private boolean isArrowNear(double expands) {
        if (mc.level == null || this.fakePlayer == null) return false;
        for (Entity entity : mc.level.entitiesForRendering()) {
            ProjectileData data;
            if (entity instanceof Arrow) {
                data = this.arrowData;
            } else if (entity instanceof ThrownEgg) {
                data = this.eggData;
            } else if (entity instanceof Snowball) {
                data = this.snowballData;
            } else {
                continue;
            }
            if (this.checkProjectile(entity, data, expands)) return true;
        }
        return false;
    }

    private boolean checkProjectile(Entity entity, ProjectileData projectileInfo, double expands) {
        LocalPlayer thePlayer = mc.player;
        ClientLevel theWorld = mc.level;
        if (thePlayer == null || theWorld == null || this.fakePlayer == null) return false;
        double posX = entity.getX();
        double posY = entity.getY();
        double posZ = entity.getZ();
        double motionX = entity.getDeltaMovement().x;
        double motionY = entity.getDeltaMovement().y;
        double motionZ = entity.getDeltaMovement().z;

        while (true) {
            float data1 = projectileInfo.getData1();
            float data2 = projectileInfo.getData2();
            AABB aabb = new AABB(posX - data1, posY, posZ - data1, posX + data1, posY + data2, posZ + data1);
            Vec3 vec3 = new Vec3(posX, posY, posZ);
            Vec3 vec3WithMotion = new Vec3(posX + motionX, posY + motionY, posZ + motionZ);
            HitResult movingObj = RayTraceUtil.rayTraceBlocks(vec3, vec3WithMotion, false,
                    entity instanceof Arrow, false, entity);
            List<Entity> entities = theWorld.getEntities(thePlayer,
                    aabb.contract(motionX, motionY, motionZ).expandTowards(1.0, 1.0, 1.0)
                            .inflate(expands, expands, expands));
            if (entities.contains(this.fakePlayer)) return true;

            posX += motionX;
            posY += motionY;
            posZ += motionZ;
            if (!movingObj.getType().equals(HitResult.Type.MISS) || posY < -128.0) return false;

            motionX *= entity.isInWater() ? 0.8 : 0.99;
            double gravityY = motionY * (entity.isInWater() ? 0.8 : 0.99);
            motionZ *= entity.isInWater() ? 0.8 : 0.99;
            motionY = gravityY - projectileInfo.getGravity();
        }
    }

    private boolean isPlayerInDanger() {
        return this.isTNTNear(this.tntDistance.get())
                || this.isPlayerNear(this.playerDistance.get())
                || this.isArrowNear(this.projectilesExpands.get());
    }

    @Override
    public void setState(boolean enabled) {
        if (mc.player == null) return;
        if (enabled) {
            super.setState(true);
        } else if (!this.disabling) {
            this.disabling = true;
        } else if (this.packets.isEmpty()) {
            super.setState(false);
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null) return;
        this.setSuffix(this.getBlinkTicks() + " Ticks Behind");
        this.progress.target = Mth.clamp((float) this.getBlinkTicks()
                / this.maxTicks.getFloat() * 100.0F, 0.0F, 100.0F);
        this.releasedTicks = 0;
        if (mc.player.hurtTime == 10) {
            this.shouldReleaseTicks += this.releaseOnDamage.getInt();
        }

        while ((float) this.releasedTicks < this.releaseSpeed.getFloat()
                && this.shouldReleaseTicks > 0 && !this.packets.isEmpty()) {
            this.releaseTick();
            this.shouldReleaseTicks--;
        }
        while ((float) this.releasedTicks < this.releaseSpeed.getFloat()
                && this.isPlayerInDanger() && !this.packets.isEmpty()) {
            this.releaseTick();
        }
        while ((float) this.releasedTicks < this.releaseSpeed.getFloat()
                && (float) this.getBlinkTicks() >= this.maxTicks.getFloat() && !this.packets.isEmpty()) {
            this.releaseTick();
        }

        if (this.disabling) {
            while ((float) this.releasedTicks < this.releaseSpeed.getFloat() && !this.packets.isEmpty()) {
                this.releaseTick();
            }
            if (this.packets.isEmpty()) this.setState(false);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isSend() || mc.player == null || event.isCancelled()) return;
        if (whitelist.contains(event.getPacket().getClass().getName())) return;
        event.setCancelled(true);
        this.packets.offer(event.getPacket());
    }
}
