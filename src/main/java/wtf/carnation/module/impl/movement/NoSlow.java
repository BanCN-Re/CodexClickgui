package wtf.carnation.module.impl.movement;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.UpdateEvent;
import wtf.carnation.event.impl.movement.EventSlowdown;
import wtf.carnation.event.impl.movement.MoveInputEvent;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Port of Naven's {@code tech.naven.modules.impl.move.NoSlow}.
 *
 * <p>Full mode silently swaps the consumable to the other hand; legacy mode intercepts the
 * position/keepalive packets around the swap. Packets Naven sent through
 * {@code NetworkUtils.sendPacketNoEvent} go straight to the connection here.</p>
 */
public class NoSlow extends Module {
    private final FullMode fullMode = new FullMode();
    private final LegacyMode legacyMode = new LegacyMode();

    public NoSlow() {
        super("NoSlow", Category.MOVEMENT, "NoSlowDown");
    }

    @Override
    protected void onEnable() {
        this.fullMode.onEnable();
        this.legacyMode.onEnable();
    }

    @Override
    protected void onDisable() {
        this.fullMode.onDisable();
        this.legacyMode.onDisable();
    }

    /** Used by MixinLocalPlayer's item-use multiplier hook. */
    public boolean shouldCancelSlowdown() {
        return this.isEnabled() && this.isUsingConsumable();
    }

    @EventTarget
    public void onPreTick(RunTickEvent event) {
        if (!event.isPre()) return;
        if (this.fullMode.isActive()) {
            this.fullMode.onPreTick();
            return;
        }
        if (!this.legacyMode.isActive()) {
            this.fullMode.onPreTick();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (this.fullMode.isActive()) return;
        this.legacyMode.onUpdate(event);
    }

    @EventTarget
    public void onSlowDown(EventSlowdown event) {
        if (this.fullMode.isActive()) {
            this.fullMode.onSlowDown(event);
        } else {
            this.legacyMode.onSlowDown(event);
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player != null && mc.player.isUsingItem() && !mc.player.isPassenger()) {
            event.setForward(event.getForward() * 5.0F);
            event.setStrafe(event.getStrafe() * 5.0F);
            if (!event.isSneak()) mc.player.setSprinting(true);
        }
        if (!this.fullMode.isActive()) return;
        this.fullMode.onMoveInput(event);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.fullMode.isActive()) {
            this.fullMode.onPacket(event);
        } else {
            this.legacyMode.onPacket(event);
        }
    }

    private boolean isConsumable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.has(DataComponents.FOOD) || stack.getItem() instanceof PotionItem;
    }

    private boolean isUsingConsumable() {
        return mc.player != null && mc.player.isUsingItem()
                && mc.player.getUseItemRemainingTicks() > 0
                && this.isConsumable(mc.player.getUseItem());
    }

    private static void send(Packet<?> packet) {
        if (packet != null && mc.getConnection() != null) wtf.carnation.event.PacketHook.sendSilently(packet);
    }

    private final class FullMode {
        private Step step = Step.NONE;
        private int noUsingItemTicks = 0;
        private final Queue<ServerboundPongPacket> queuedPongs = new ConcurrentLinkedQueue<>();
        private InteractionHand targetHand = InteractionHand.MAIN_HAND;
        private boolean swapped = false;
        private boolean forcedUseKeyDown = false;

        void onEnable() {
            this.step = Step.NONE;
            this.noUsingItemTicks = 0;
            this.queuedPongs.clear();
            this.targetHand = InteractionHand.MAIN_HAND;
            this.swapped = false;
            this.forcedUseKeyDown = false;
        }

        void onDisable() {
            this.abort(true);
        }

        boolean isActive() {
            return this.step != Step.NONE;
        }

        private boolean shouldStart() {
            if (!isUsingConsumable() || mc.player == null) return false;
            return mc.player.getUsedItemHand() == InteractionHand.OFF_HAND
                    && !mc.player.getMainHandItem().is(Items.AIR);
        }

        void onPreTick() {
            if (mc.player == null) return;

            if (this.step == Step.NONE && this.shouldStart()) {
                if (mc.getConnection() == null) return;
                InteractionHand usedHand = mc.player.getUsedItemHand();
                this.targetHand = usedHand == InteractionHand.MAIN_HAND
                        ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                this.step = Step.PRE_SWAP;
                this.noUsingItemTicks = 0;
                this.queuedPongs.clear();
                this.swapped = true;
                if (mc.player.containerMenu != mc.player.inventoryMenu) {
                    NoSlow.send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                }
                mc.options.keyUse.setDown(false);
                this.forcedUseKeyDown = true;
                this.swapSilent();
            }

            if (this.step != Step.NONE && this.step != Step.USING) {
                mc.options.keyUse.setDown(false);
                this.forcedUseKeyDown = true;
            }

            if (this.step == Step.USING) {
                if (isUsingConsumable()) {
                    this.noUsingItemTicks = 0;
                } else {
                    this.noUsingItemTicks++;
                    if (this.noUsingItemTicks >= 5) {
                        this.releaseQueuedPongs();
                        NoSlow.send(new ServerboundPlayerActionPacket(
                                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                                BlockPos.ZERO, Direction.DOWN));
                        this.abort(false);
                    }
                }
            } else {
                this.noUsingItemTicks = 0;
            }
        }

        void onSlowDown(EventSlowdown event) {
            if (mc.player == null) return;
            if (this.step != Step.USING) return;
            if (!isUsingConsumable()) return;
        }

        void onMoveInput(MoveInputEvent event) {
            if (mc.player == null) return;
            if (this.step == Step.USING && !event.isSneak()) mc.player.setSprinting(true);
        }

        void onPacket(PacketEvent event) {
            if (mc.player == null) return;
            Packet<?> packet = event.getPacket();
            if (!event.isSend()) {
                if (this.step == Step.PRE_SWAP
                        && (packet instanceof ClientboundContainerSetSlotPacket
                        || packet instanceof ClientboundContainerSetContentPacket)) {
                    mc.options.keyUse.setDown(true);
                    this.forcedUseKeyDown = true;
                    this.step = Step.USING;
                    this.noUsingItemTicks = 0;
                }
                return;
            }

            if (packet instanceof ServerboundPongPacket pong && this.step != Step.NONE) {
                event.setCancelled(true);
                this.queuedPongs.add(pong);
                return;
            }

            if (packet instanceof ServerboundPlayerActionPacket actionPacket
                    && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                    && this.step == Step.USING) {
                this.abort(true);
                return;
            }

            if (packet instanceof ServerboundUseItemPacket usePacket
                    && this.step == Step.USING && this.swapped
                    && usePacket.hand() != this.targetHand && mc.getConnection() != null) {
                event.setCancelled(true);
                NoSlow.send(new ServerboundUseItemPacket(this.targetHand, usePacket.sequence(),
                        mc.player.getYRot(), mc.player.getXRot()));
            }
        }

        private void releaseQueuedPongs() {
            if (mc.getConnection() == null) {
                this.queuedPongs.clear();
                return;
            }
            ServerboundPongPacket pong;
            while ((pong = this.queuedPongs.poll()) != null) NoSlow.send(pong);
        }

        private void swapSilent() {
            NoSlow.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        }

        private void abort(boolean revertSwap) {
            this.releaseQueuedPongs();
            if (revertSwap && this.swapped) this.swapSilent();
            this.step = Step.NONE;
            this.noUsingItemTicks = 0;
            this.swapped = false;
            this.targetHand = InteractionHand.MAIN_HAND;
            if (this.forcedUseKeyDown) mc.options.keyUse.setDown(false);
            this.forcedUseKeyDown = false;
        }

        private enum Step {
            NONE, PRE_SWAP, USING
        }
    }

    private final class LegacyMode {
        private Step step = Step.NONE;
        private int noUsingItemTicks = 0;
        private final Queue<Packet<?>> packets = new ConcurrentLinkedQueue<>();

        void onEnable() {
            this.step = Step.NONE;
            this.noUsingItemTicks = 0;
            this.packets.clear();
        }

        void onDisable() {
            this.step = Step.NONE;
            this.noUsingItemTicks = 0;
            this.releasePackets();
        }

        boolean isActive() {
            return this.step != Step.NONE;
        }

        @SuppressWarnings("unchecked")
        private void releasePackets() {
            this.step = Step.NONE;
            while (!this.packets.isEmpty()) {
                Packet<? super ClientPacketListener> packet =
                        (Packet<? super ClientPacketListener>) this.packets.poll();
                if (packet != null && mc.getConnection() != null) wtf.carnation.event.PacketHook.sendSilently(packet);
            }
        }

        private void swap() {
            NoSlow.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        }

        private boolean isUsable(ItemUseAnimation useAnim) {
            return useAnim == ItemUseAnimation.EAT || useAnim == ItemUseAnimation.DRINK;
        }

        void onUpdate(UpdateEvent event) {
            if (mc.player == null) return;
            if (this.step != Step.EATING) {
                this.noUsingItemTicks = 0;
                return;
            }
            if (mc.player.isUsingItem()) {
                this.noUsingItemTicks = 0;
            } else {
                this.noUsingItemTicks++;
                if (this.noUsingItemTicks >= 5) {
                    this.releasePackets();
                    this.swap();
                }
            }
        }

        void onSlowDown(EventSlowdown event) {
            if (mc.player == null) return;
            ItemUseAnimation activeUseAnim = mc.player.getUseItem().getUseAnimation();
            if (!this.isUsable(activeUseAnim)
                    || mc.player.getUseItemRemainingTicks() <= 0
                    || mc.player.getUsedItemHand() == InteractionHand.OFF_HAND
                    && !mc.player.getMainHandItem().is(Items.AIR)) {
                return;
            }

            InteractionHand oppositeHand = mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND
                    ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            if (this.isUsable(mc.player.getItemInHand(oppositeHand).getUseAnimation())) return;

            if (this.step != Step.EATING) mc.options.keyUse.setDown(false);

            if (this.step == Step.NONE) {
                this.step = Step.CANCEL_C0F;
                if (mc.player.containerMenu != mc.player.inventoryMenu && mc.getConnection() != null) {
                    mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                }
            } else if (this.step == Step.EATING) {
                mc.player.setSprinting(true);
            }
        }

        void onPacket(PacketEvent event) {
            if (mc.player == null) return;
            Packet<?> packet = event.getPacket();
            if (this.step != Step.NONE) {
                if (packet instanceof ClientboundPingPacket
                        || packet instanceof ClientboundKeepAlivePacket
                        || packet instanceof ClientboundPlayerPositionPacket
                        || packet instanceof ClientboundSetEntityMotionPacket s12 && s12.id() == mc.player.getId()) {
                    event.setCancelled(true);
                    this.packets.add(packet);
                    if (this.step == Step.CANCEL_C0F && packet instanceof ClientboundPingPacket) {
                        this.step = Step.SWAP_HANDS;
                        this.swap();
                    }
                }
            }

            if (packet instanceof ClientboundContainerSetSlotPacket && this.step == Step.SWAP_HANDS) {
                mc.options.keyUse.setDown(true);
                this.step = Step.EATING;
            }

            if (this.step == Step.EATING) {
                if (packet instanceof ServerboundPlayerActionPacket actionPacket
                        && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                    this.releasePackets();
                    this.swap();
                }
                if (packet instanceof ServerboundUseItemPacket c08) {
                    event.setCancelled(true);
                    NoSlow.send(new ServerboundUseItemPacket(
                            c08.hand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                            c08.sequence(), mc.player.getYRot(), mc.player.getXRot()));
                }
                if (packet instanceof ClientboundContainerSetSlotPacket
                        || packet instanceof ClientboundContainerSetContentPacket) {
                    event.setCancelled(true);
                }
            }
        }

        private enum Step {
            NONE, CANCEL_C0F, SWAP_HANDS, EATING
        }
    }
}
