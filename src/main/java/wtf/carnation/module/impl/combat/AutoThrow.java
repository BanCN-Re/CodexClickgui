package wtf.carnation.module.impl.combat;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.UpdateEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.misc.Teams;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.FriendManager;
import wtf.carnation.utils.InventoryUtils;
import wtf.carnation.utils.RayTraceUtil;
import wtf.carnation.utils.Rotation;
import wtf.carnation.utils.TimeHelper;
import wtf.carnation.utils.Vector2f;

import java.util.Comparator;
import java.util.Optional;

/**
 * Port of Naven's {@code tech.naven.modules.impl.combat.AutoThrow}.
 *
 * <p>The trajectory, prediction, slot switching and state machine are the original's. This client
 * has no EventUpdateHeldItem hook, so the visual held-item override is omitted; the rotation
 * conflict checks only look at whether those modules are enabled (their rotation fields are not
 * shared across packages here).</p>
 */
public class AutoThrow extends Module {
    private final NumberValue minDistance = new NumberValue("Min Distance", 3.0, 3.0, 10.0, 1.0);
    private final NumberValue maxDistance = new NumberValue("Max Distance", 8.0, 3.0, 30.0, 1.0);
    private final NumberValue delay = new NumberValue("Delay", 500.0, 50.0, 2000.0, 50.0);
    private final NumberValue predict = new NumberValue("Predict", 0.8, 0.0, 2.0, 0.1);

    private final TimeHelper timer = new TimeHelper();
    public Vector2f rotation;
    private int originalSlot;
    private boolean hasThrown = false;
    private InteractionHand pendingThrowHand;

    public AutoThrow() {
        super("AutoThrow", Category.COMBAT, "Automatically throw snowballs and eggs.");
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (mc.gui.screen() != null) {
            this.resetState();
            return;
        }
        if (this.isMiningBlock()) {
            this.cancelThrow(false);
            return;
        }
        if (mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            this.cancelThrow(false);
            return;
        }

        ItemStack heldItem = mc.player.getMainHandItem();
        if (heldItem.is(Items.CROSSBOW)) {
            this.resetState();
            return;
        }

        if (this.enabled("Scaffold") || this.enabled("Stuck") || this.enabled("Blink")) {
            this.cancelThrow(false);
            return;
        }
        if (this.enabled("AutoMLG") || this.enabled("LongJump") || this.enabled("CrystalAura")
                || this.enabled("KillAura") || this.enabled("Helper") || this.enabled("PearlPrediction")) {
            this.cancelThrow(false);
            return;
        }

        ItemStack offhandItem = mc.player.getOffhandItem();
        if ((offhandItem.is(Items.ENCHANTED_GOLDEN_APPLE) || offhandItem.is(Items.GOLDEN_APPLE))
                && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
            this.cancelThrow(false);
            return;
        }

        if (this.pendingThrowHand != null) {
            if (!this.timer.delay(this.delay.get())) {
                this.cancelThrow(false);
                return;
            }
            Optional<AbstractClientPlayer> targetOpt = this.getTarget();
            if (targetOpt.isEmpty() || this.rotation == null) {
                this.cancelThrow(false);
                return;
            }
            Entity target = targetOpt.get();
            double targetDistance = mc.player.distanceTo(target);
            if (this.isThrowBlocked(this.rotation, Math.min(3.0, targetDistance))) {
                this.cancelThrow(false);
                return;
            }
            if (this.pendingThrowHand == InteractionHand.MAIN_HAND && !isThrowableItem(mc.player.getMainHandItem())) {
                this.cancelThrow(false);
                return;
            }
            if (this.pendingThrowHand == InteractionHand.OFF_HAND && !isThrowableItem(offhandItem)) {
                this.cancelThrow(false);
                return;
            }
            mc.gameMode.useItem(mc.player, this.pendingThrowHand);
            this.sendServerSwing(this.pendingThrowHand);
            this.timer.reset();
            this.rotation = null;
            this.pendingThrowHand = null;
            return;
        }

        boolean mainHandThrowable = isThrowableItem(heldItem);
        boolean offHandThrowable = isThrowableItem(offhandItem);
        boolean useOffHand = !mainHandThrowable && offHandThrowable;
        int throwableSlot = -1;
        if (!useOffHand) {
            throwableSlot = mainHandThrowable ? mc.player.getInventory().getSelectedSlot() : this.getThrowableSlot();
            if (throwableSlot == -1) {
                this.cancelThrow(false);
                return;
            }
        } else if (this.hasThrown) {
            mc.player.getInventory().setSelectedSlot(this.originalSlot);
            this.hasThrown = false;
        }

        if (!this.timer.delay(this.delay.get())) {
            this.cancelThrow(false);
            return;
        }

        Optional<AbstractClientPlayer> targetOpt = this.getTarget();
        if (targetOpt.isEmpty()) {
            this.cancelThrow(false);
            return;
        }
        if (!useOffHand && !canUseMainHand(heldItem)) {
            this.cancelThrow(false);
            return;
        }

        Entity target = targetOpt.get();
        Vector2f calcRotations = this.getRotationToEntity(target);
        if (calcRotations == null) {
            this.cancelThrow(false);
            return;
        }

        calcRotations.y = Math.max(-90.0F, Math.min(90.0F, calcRotations.y));
        double targetDistance = mc.player.distanceTo(target);
        if (this.isThrowBlocked(calcRotations, Math.min(3.0, targetDistance))) {
            this.cancelThrow(false);
            return;
        }

        this.rotation = calcRotations;
        if (useOffHand) {
            this.pendingThrowHand = InteractionHand.OFF_HAND;
            return;
        }

        if (!this.hasThrown) {
            if (mc.player.getInventory().getSelectedSlot() == throwableSlot) {
                this.pendingThrowHand = InteractionHand.MAIN_HAND;
                return;
            }
            this.originalSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(throwableSlot);
            this.hasThrown = true;
            return;
        }

        if (mc.player.getInventory().getSelectedSlot() != throwableSlot) {
            this.cancelThrow(false);
            return;
        }
        if (isThrowableItem(mc.player.getMainHandItem())) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            this.sendServerSwing(InteractionHand.MAIN_HAND);
            this.timer.reset();
        }
        mc.player.getInventory().setSelectedSlot(this.originalSlot);
        this.hasThrown = false;
    }

    private boolean enabled(String name) {
        Module module = Client.instance.getModuleManager().getModuleByName(name);
        return module != null && module.isEnabled();
    }

    private Vector2f getRotationToEntity(Entity target) {
        if (this.isFakePlayer(target)) return null;
        double distanceToEnt = mc.player.distanceTo(target);
        double predictTicks = distanceToEnt * this.predict.get();
        double predictX = target.getX() + (target.getX() - target.xo) * predictTicks;
        double predictY = target.getY() + (target.getY() - target.yo) * predictTicks;
        double predictZ = target.getZ() + (target.getZ() - target.zo) * predictTicks;
        double x = predictX - mc.player.getX();
        double h = predictY + 1.2 - (mc.player.getY() + mc.player.getEyeHeight());
        double z = predictZ - mc.player.getZ();
        double h1 = Math.sqrt(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0F;
        float pitch = -this.getTrajectoryAngle((float) h1, (float) h, 0.6F, 0.006F);
        return new Vector2f(yaw, pitch);
    }

    private float getTrajectoryAngle(float dist, float height, float velocity, float gravity) {
        float sqrt = velocity * velocity * velocity * velocity
                - gravity * (gravity * dist * dist + 2.0F * height * velocity * velocity);
        if (sqrt < 0.0F) return 0.0F;
        return (float) Math.toDegrees(Math.atan((velocity * velocity - Math.sqrt(sqrt)) / (gravity * dist)));
    }

    public static boolean isThrowableItem(ItemStack stack) {
        return stack.is(Items.EGG) || stack.is(Items.SNOWBALL);
    }

    private static boolean canUseMainHand(ItemStack heldItem) {
        return heldItem.isEmpty()
                || !heldItem.is(Items.ENDER_PEARL) && !heldItem.is(Items.BOW)
                && !heldItem.has(DataComponents.FOOD) && !isPotionItem(heldItem);
    }

    private static boolean isPotionItem(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
    }

    private int getThrowableSlot() {
        int eggSlot = InventoryUtils.getItemSlot(Items.EGG);
        if (eggSlot != -1 && eggSlot < 9) return eggSlot;
        int snowballSlot = InventoryUtils.getItemSlot(Items.SNOWBALL);
        return snowballSlot != -1 && snowballSlot < 9 ? snowballSlot : -1;
    }

    private boolean isThrowBlocked(Vector2f rotations, double range) {
        HitResult hitResult = RayTraceUtil.rayCast(new Rotation(rotations.getX(), rotations.getY()),
                range, 0.0F, mc.player, null, false);
        return hitResult != null && hitResult.getType() == HitResult.Type.BLOCK;
    }

    private boolean isMiningBlock() {
        if (mc.gameMode == null) return false;
        return mc.gameMode.isDestroying()
                || mc.options.keyAttack.isDown() && mc.hitResult != null
                && mc.hitResult.getType() == HitResult.Type.BLOCK;
    }

    private Optional<AbstractClientPlayer> getTarget() {
        return mc.level.players().stream()
                .filter(e -> e != mc.player)
                .filter(e -> !Teams.isSameTeam(e))
                .filter(e -> !FriendManager.isFriend(e))
                .filter(e -> !AntiBots.isBot(e))
                .filter(e -> !this.isFakePlayer(e))
                .filter(e -> {
                    double distance = mc.player.distanceTo(e);
                    return distance <= this.maxDistance.get() && distance >= this.minDistance.get();
                })
                .filter(mc.player::hasLineOfSight)
                .filter(e -> !e.isInvisible())
                .min(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
    }

    private boolean isFakePlayer(Entity entity) {
        return entity instanceof RemotePlayer && entity.getId() < 0;
    }

    private void sendServerSwing(InteractionHand hand) {
        mc.player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
    }

    private void resetState() {
        this.rotation = null;
        if (this.hasThrown && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(this.originalSlot);
            this.hasThrown = false;
        }
        this.pendingThrowHand = null;
        this.timer.reset();
    }

    private void cancelThrow(boolean resetTimer) {
        this.rotation = null;
        if (this.hasThrown && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(this.originalSlot);
            this.hasThrown = false;
        }
        this.pendingThrowHand = null;
        if (resetTimer) this.timer.reset();
    }

    @Override
    protected void onEnable() {
        this.rotation = null;
        this.hasThrown = false;
        this.pendingThrowHand = null;
        this.timer.reset();
    }

    @Override
    protected void onDisable() {
        this.resetState();
    }
}
