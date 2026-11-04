package wtf.carnation.module.impl.movement;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.UpdateEvent;
import wtf.carnation.event.impl.input.MouseClickEvent;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.utils.ChatUtil;
import wtf.carnation.utils.MoveUtil;
import wtf.carnation.utils.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Port of Naven's {@code tech.naven.modules.impl.move.LongJump}.
 *
 * <p>Same fireball sequence, packet interception and Mouse4/Mouse5 controls. The on-screen status
 * line is drawn through {@link UiFont} because 26.3's extractor has no drawString with section
 * codes.</p>
 */
public class LongJump extends Module {
    public static Rotation rotation = null;
    private boolean notMoving = false;
    private boolean enabled = false;
    private int rotateTick = 0;
    private int lastSlot = -1;
    private boolean delayed = false;
    private boolean shouldDisableAndRelease = false;
    private boolean isUsingItem = false;
    private boolean mouse4Pressed = false;
    private boolean mouse5Pressed = false;
    private long delayStartTime = 0L;
    private int usedFireballCount = 0;
    private int receivedKnockbacks = 0;
    private int initialFireballCount = 0;
    private int releasedKnockbacks = 0;
    private final List<Integer> knockbackPositions = new ArrayList<>();
    private final LinkedBlockingQueue<Packet<?>> packets = new LinkedBlockingQueue<>();

    public LongJump() {
        super("LongJump", Category.MOVEMENT, "Allows you to use fireball longjump");
    }

    @SuppressWarnings("unchecked")
    private void releaseAll() {
        while (!this.packets.isEmpty()) {
            try {
                Packet<?> packet = this.packets.poll();
                if (packet != null && mc.getConnection() != null) {
                    wtf.carnation.event.PacketHook.sendSilently(packet);
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void releaseToKnockback(int knockbackIndex) {
        if (knockbackIndex >= this.knockbackPositions.size()) return;
        int targetPosition = this.knockbackPositions.get(knockbackIndex);
        int releasedCount = 0;
        while (!this.packets.isEmpty() && releasedCount <= targetPosition) {
            try {
                Packet<?> packet = this.packets.poll();
                if (packet != null && mc.getConnection() != null) {
                    wtf.carnation.event.PacketHook.sendSilently(packet);
                }
                releasedCount++;
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
        for (int i = knockbackIndex + 1; i < this.knockbackPositions.size(); i++) {
            this.knockbackPositions.set(i, this.knockbackPositions.get(i) - (targetPosition + 1));
        }
    }

    private int getFireballSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == Items.FIRE_CHARGE) return i;
        }
        return -1;
    }

    private int getFireballCount() {
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.FIRE_CHARGE) count += stack.getCount();
        }
        return count;
    }

    private int setupFireballSlot() {
        int fireballSlot = this.getFireballSlot();
        if (fireballSlot == -1) {
            ChatUtil.addChatMessage("§cNo FireBall!");
            this.setState(false);
        }
        return fireballSlot;
    }

    @Override
    protected void onEnable() {
        this.releaseAll();
        this.rotateTick = 0;
        this.enabled = true;
        this.lastSlot = -1;
        this.notMoving = false;
        this.delayed = false;
        this.isUsingItem = false;
        rotation = null;
        this.shouldDisableAndRelease = false;
        this.mouse4Pressed = false;
        this.mouse5Pressed = false;
        this.delayStartTime = 0L;
        this.usedFireballCount = 0;
        this.receivedKnockbacks = 0;
        this.initialFireballCount = 0;
        this.releasedKnockbacks = 0;
        this.knockbackPositions.clear();
        ChatUtil.addChatMessage("§aLongJump enabled! Mouse4 to jump & use fireball, Mouse5 to release each knockback");
    }

    @Override
    protected void onDisable() {
        this.releaseAll();
        if (this.lastSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(this.lastSlot);
        }
        mc.options.keyUse.setDown(false);
        mc.options.keyJump.setDown(false);
        rotation = null;
        this.isUsingItem = false;
        this.shouldDisableAndRelease = false;
        this.mouse4Pressed = false;
        this.mouse5Pressed = false;
        this.delayStartTime = 0L;
        this.usedFireballCount = 0;
        this.receivedKnockbacks = 0;
        this.initialFireballCount = 0;
        this.releasedKnockbacks = 0;
        this.knockbackPositions.clear();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || mc.player == null) return;
        if (this.shouldDisableAndRelease) {
            this.setState(false);
            return;
        }

        if (this.enabled) {
            if (!MoveUtil.isMoving()) this.notMoving = true;
            this.enabled = false;
        }
    }

    @EventTarget
    public void onMouseClick(MouseClickEvent event) {
        if (!this.isEnabled() || mc.player == null || !event.isState()) return;
        if (event.getKey() == 4) {
            if (!this.isUsingItem && this.rotateTick == 0) {
                int fireballSlot = this.setupFireballSlot();
                if (fireballSlot != -1) {
                    this.lastSlot = mc.player.getInventory().getSelectedSlot();
                    mc.player.getInventory().setSelectedSlot(fireballSlot);
                    this.rotateTick = 1;
                    ChatUtil.addChatMessage("§eStarting fireball usage #" + (this.usedFireballCount + 1));
                }
            }
        } else if (event.getKey() == 5) {
            if (this.delayed && this.releasedKnockbacks < this.receivedKnockbacks) {
                ChatUtil.addChatMessage("§aReleasing " + (this.releasedKnockbacks + 1) + "/" + this.receivedKnockbacks);
                this.releaseToKnockback(this.releasedKnockbacks);
                this.releasedKnockbacks++;
                if (this.releasedKnockbacks >= this.receivedKnockbacks) {
                    ChatUtil.addChatMessage("§aAll released! Stopping LongJump.");
                    this.delayed = false;
                    this.setState(false);
                }
            } else if (!this.delayed) {
                ChatUtil.addChatMessage("§cNo intercepted packets");
                this.setState(false);
            } else {
                ChatUtil.addChatMessage("§cAll already released");
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;
        String statusText;
        if (this.delayed) {
            long delayDuration = System.currentTimeMillis() - this.delayStartTime;
            statusText = String.format("Intercepting: %d packets | Time: %.1fs | Press Mouse5 (%d/%d)",
                    this.packets.size(), delayDuration / 1000.0F, this.releasedKnockbacks, this.receivedKnockbacks);
        } else if (this.isUsingItem) {
            statusText = "Using fireball #" + this.usedFireballCount;
        } else {
            statusText = "Waiting for input | Mouse4: Jump & use fireball | Mouse5: Release";
        }
        UiFont.useGuiScale();
        float centerX = mc.getWindow().getGuiScaledWidth() / 2.0F;
        float y = mc.getWindow().getGuiScaledHeight() / 2.0F + 20.0F;
        UiFont.Z_SMALL.drawCentered(event.graphics(), statusText, centerX, y, 0xFFFFFFFF);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || mc.level == null) {
            if (this.delayed) {
                mc.execute(() -> {
                    this.releaseAll();
                    this.delayed = false;
                });
            }
            return;
        }

        if (this.delayed && !event.isSend()) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof ClientboundPlayerPositionPacket) {
                this.shouldDisableAndRelease = true;
                event.setCancelled(true);
                return;
            }
            if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == mc.player.getId()) {
                this.receivedKnockbacks++;
                this.knockbackPositions.add(this.packets.size());
                mc.execute(() -> ChatUtil.addChatMessage("§e" + this.receivedKnockbacks + " received"));
            }
            event.setCancelled(true);
            this.packets.add(packet);
        } else if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet
                && !event.isSend() && packet.id() == mc.player.getId()
                && this.usedFireballCount > 0 && !this.delayed) {
            this.receivedKnockbacks++;
            this.knockbackPositions.add(this.packets.size());
            mc.execute(() -> ChatUtil.addChatMessage("§eReceived #" + this.receivedKnockbacks + ", starting interception"));
            event.setCancelled(true);
            this.packets.add(event.getPacket());
            this.delayed = true;
            this.delayStartTime = System.currentTimeMillis();
            mc.execute(() -> ChatUtil.addChatMessage("§ePacket interception started, press Mouse5 to release each"));
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!this.isEnabled() || mc.player == null) return;
        if (event.isPre()) {
            if (this.rotateTick > 0) {
                if (this.rotateTick == 1) {
                    this.usedFireballCount++;
                    ChatUtil.addChatMessage("§aJumping for fireball #" + this.usedFireballCount);
                    mc.options.keyJump.setDown(true);
                    float yaw;
                    float pitch;
                    if (!this.notMoving) {
                        yaw = mc.player.getYRot() - 180.0F;
                        pitch = 88.0F;
                    } else {
                        yaw = mc.player.getYRot();
                        pitch = 90.0F;
                    }
                    rotation = new Rotation(yaw, pitch);
                }
                if (this.rotateTick >= 2) {
                    this.rotateTick = 0;
                    int fireballSlot = this.setupFireballSlot();
                    if (fireballSlot != -1) {
                        mc.player.getInventory().setSelectedSlot(fireballSlot);
                        this.initialFireballCount = this.getFireballCount();
                        mc.options.keyUse.setDown(true);
                        this.isUsingItem = true;
                        ChatUtil.addChatMessage("§eFireball #" + this.usedFireballCount
                                + " started, initial count: " + this.initialFireballCount);
                    } else {
                        this.setState(false);
                    }
                }
                if (this.rotateTick != 0) this.rotateTick++;
            }
        } else if (this.isUsingItem) {
            int currentFireballCount = this.getFireballCount();
            if (currentFireballCount < this.initialFireballCount) {
                mc.options.keyUse.setDown(false);
                mc.options.keyJump.setDown(false);
                rotation = null;
                this.isUsingItem = false;
                ChatUtil.addChatMessage("§eFireball #" + this.usedFireballCount + " used! Count: "
                        + this.initialFireballCount + " -> " + currentFireballCount);
            } else if (this.getFireballSlot() == -1) {
                mc.options.keyUse.setDown(false);
                mc.options.keyJump.setDown(false);
                rotation = null;
                this.isUsingItem = false;
                ChatUtil.addChatMessage("§cNo more fireballs available!");
            }
        }
    }
}
