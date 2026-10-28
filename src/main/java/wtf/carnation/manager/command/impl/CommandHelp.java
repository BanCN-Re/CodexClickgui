package wtf.carnation.manager.command.impl;

import wtf.carnation.Client;
import wtf.carnation.manager.command.Command;
import wtf.carnation.manager.command.CommandInfo;
import wtf.carnation.utils.ChatUtil;

@CommandInfo(
        name = "help",
        desc = "List every command",
        aliases = {"h", "?"}
)
public class CommandHelp extends Command {
    @Override
    public void onCommand(String[] args) {
        for (Command command : Client.instance.getCommandManager().getCommands()) {
            ChatUtil.addChatMessage(String.join(" | ", command.getUsage()) + "  - " + command.getDesc());
        }
    }

    @Override
    public String[] onTab(String[] args) {
        return new String[0];
    }
}
