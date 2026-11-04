package wtf.carnation.module.impl.misc;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import wtf.carnation.Client;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.ModeValue;

import java.util.Objects;

/** Port of Naven's {@code tech.naven.modules.impl.misc.Teams}. */
public class Teams extends Module {
    private static Teams instance;

    public final ModeValue mode = new ModeValue("Mode", new String[]{"Scoreboard", "Color"}, "Scoreboard");

    public Teams() {
        super("Teams", Category.MISC, "Prevent attack teammates");
        instance = this;
    }

    public static boolean isSameTeam(Entity player) {
        Teams module = Client.instance == null ? null : Client.instance.getModuleManager().getModule(Teams.class);
        if (module == null || !module.isEnabled() || mc.player == null) return false;
        if (!(player instanceof Player)) return false;
        if (module.mode.is("Color")) {
            return player.getTeamColor() == mc.player.getTeamColor();
        }
        return Objects.equals(getTeam(player), getTeam(mc.player));
    }

    public static String getTeam(Entity entity) {
        if (mc.player == null || mc.getConnection() == null) return null;
        PlayerInfo info = mc.getConnection().getPlayerInfo(entity.getUUID());
        if (info == null) return null;
        return info.getTeam() != null ? info.getTeam().getName() : null;
    }
}
