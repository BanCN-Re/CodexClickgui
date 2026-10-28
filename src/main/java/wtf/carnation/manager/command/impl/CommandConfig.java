package wtf.carnation.manager.command.impl;

import wtf.carnation.Client;
import wtf.carnation.manager.command.Command;
import wtf.carnation.manager.command.CommandInfo;
import wtf.carnation.utils.ChatUtil;

@CommandInfo(
        name = "config",
        desc = "Save or reload the module config",
        usage = {".config save", ".config load"},
        aliases = {"cfg"}
)
public class CommandConfig extends Command {
    @Override
    public void onCommand(String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("save")) {
            Client.instance.getConfigManager().saveAllConfig();
            ChatUtil.addChatMessage("Config saved.");
        } else if (args.length == 1 && args[0].equalsIgnoreCase("load")) {
            Client.instance.getConfigManager().loadAllConfig();
            ChatUtil.addChatMessage("Config loaded.");
        } else {
            ChatUtil.addChatMessage("Usage: .config <save|load>");
        }
    }

    @Override
    public String[] onTab(String[] args) {
        return args.length == 1 ? new String[]{"save", "load"} : new String[0];
    }
}
