package wtf.carnation.utils;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;

import java.util.UUID;

/**
 * Port of Naven's {@code tech.naven.utils.BlinkingPlayer}: the fake "real position" entity Blink
 * leaves behind. Skin overrides are omitted; rendering is not what this class is used for here.
 */
public class BlinkingPlayer extends RemotePlayer {
    private final AbstractClientPlayer player;

    public BlinkingPlayer(AbstractClientPlayer player) {
        super(Minecraft.getInstance().level, new GameProfile(UUID.randomUUID(), "Real Position"));
        this.player = player;
        // 26.3 refuses to register an entity whose id is still unassigned, and a purely client-side
        // stand-in never gets one from the server, so claim a free negative id up front.
        int id = -2;
        while (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getEntity(id) != null
                && id > Integer.MIN_VALUE + 16) {
            id--;
        }
        this.setId(id);
        this.copyPosition(player);
        this.noPhysics = true;
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.yHeadRot = player.yHeadRot;
        this.yBodyRot = player.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.yBodyRotO = this.yBodyRot;
    }

    public AbstractClientPlayer getOwner() {
        return this.player;
    }
}
