package wtf.carnation.manager.command.impl;

import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import wtf.carnation.boot.RhineBootOverlay;
import wtf.carnation.manager.command.Command;
import wtf.carnation.manager.command.CommandInfo;
import wtf.carnation.utils.ChatUtil;

@CommandInfo(
        name = "boot",
        desc = "Replay the Rhine Lab boot sequence",
        usage = {".boot"},
        aliases = {"bootanim"}
)
public class CommandBoot extends Command {
    @Override
    public void onCommand(String[] args) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setOverlay(new RhineBootOverlay(Util.getMillis(), 0L, true));
        ChatUtil.addChatMessage("Rhine Lab boot sequence: replaying.");
    }

    @Override
    public String[] onTab(String[] args) {
        return new String[0];
    }
}
