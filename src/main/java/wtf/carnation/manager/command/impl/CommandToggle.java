package wtf.carnation.manager.command.impl;

import wtf.carnation.Client;
import wtf.carnation.manager.command.Command;
import wtf.carnation.manager.command.CommandInfo;
import wtf.carnation.module.Module;
import wtf.carnation.utils.ChatUtil;

@CommandInfo(
        name = "toggle",
        desc = "Enable or disable a module",
        usage = {".toggle <module>"},
        aliases = {"t"}
)
public class CommandToggle extends Command {
    @Override
    public void onCommand(String[] args) {
        if (args.length < 1) {
            ChatUtil.addChatMessage("Usage: .t <module>");
            return;
        }
        Module module = Client.instance.getModuleManager().getModuleByName(args[0]);
        if (module == null) {
            ChatUtil.addChatMessage("Invalid module: " + args[0]);
            return;
        }
        module.toggle();
        Client.instance.getConfigManager().saveAllConfig();
        if (!module.isMomentaryAction()) {
            ChatUtil.addChatMessage(module.getName() + " " + (module.isEnabled() ? "enabled" : "disabled"));
        }
    }

    @Override
    public String[] onTab(String[] args) {
        return args.length == 1 ? CommandModule.moduleNames() : new String[0];
    }
}
