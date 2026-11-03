package wtf.carnation.module.impl.misc;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import wtf.carnation.Client;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.ModeValue;

/** Keeps the player from attacking teammates. */
public class Teams extends Module {
    private static Teams instance;

    private final ModeValue mode = new ModeValue("Mode", new String[]{"Scoreboard", "Color"}, "Scoreboard");

    public Teams() {
        super("Teams", Category.MISC, "不攻击队友");
        instance = this;
    }

    public static boolean isSameTeam(Entity entity) {
        if (instance == null || !instance.isEnabled() || mc.player == null) return false;
        if (!(entity instanceof Player player)) return false;
        if (instance.mode.is("Color")) {
            Integer a = player.getTeamColor();
            Integer b = mc.player.getTeamColor();
            return a != null && a.equals(b);
        }
        String team = getTeam(player);
        String own = getTeam(mc.player);
        return team != null && team.equals(own);
    }

    public static String getTeam(Entity entity) {
        if (mc.player == null || mc.player.connection == null) return null;
        var info = mc.player.connection.getPlayerInfo(entity.getUUID());
        if (info == null) return null;
        var team = info.getTeam();
        return team == null ? null : team.getName();
    }
}
