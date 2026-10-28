package wtf.carnation.manager.command.impl;

import wtf.carnation.Client;
import wtf.carnation.manager.command.Command;
import wtf.carnation.manager.command.CommandInfo;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.Value;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.utils.ChatUtil;

@CommandInfo(
        name = "module",
        desc = "List or change a module's settings",
        usage = {".module <module>", ".module <module> <setting> <value>"},
        aliases = {"m", "set"}
)
public class CommandModule extends Command {
    @Override
    public void onCommand(String[] args) {
        if (args.length < 1) {
            ChatUtil.addChatMessage("Usage: .module <module> [setting] [value]");
            return;
        }
        Module module = Client.instance.getModuleManager().getModuleByName(args[0]);
        if (module == null) {
            ChatUtil.addChatMessage("Invalid module: " + args[0]);
            return;
        }
        if (args.length == 1) {
            ChatUtil.addChatMessage(module.getName() + " (" + (module.isEnabled() ? "on" : "off")
                    + ", key " + CommandBind.keyName(module.getKey()) + ")");
            for (Value<?> value : module.getValues()) {
                ChatUtil.addChatMessage("  " + value.getName() + " = " + value.getDisplayValue());
            }
            return;
        }
        if (args.length < 3) {
            ChatUtil.addChatMessage("Usage: .module " + module.getName() + " <setting> <value>");
            return;
        }
        // Setting names may contain spaces, so everything between the module and the last word is the name.
        String settingName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length - 1));
        Value<?> value = module.getValue(settingName);
        if (value == null) {
            ChatUtil.addChatMessage("Invalid setting: " + settingName);
            return;
        }
        if (!value.parse(args[args.length - 1])) {
            ChatUtil.addChatMessage("Invalid value for " + value.getName() + ": " + args[args.length - 1]);
            return;
        }
        ChatUtil.addChatMessage(module.getName() + " " + value.getName() + " = " + value.getDisplayValue());
        Client.instance.getConfigManager().saveAllConfig();
    }

    static String[] moduleNames() {
        return Client.instance.getModuleManager().getModules().stream().map(Module::getName).toArray(String[]::new);
    }

    @Override
    public String[] onTab(String[] args) {
        if (args.length == 1) return moduleNames();
        Module module = Client.instance.getModuleManager().getModuleByName(args[0]);
        if (module == null) return new String[0];
        if (args.length == 2) return module.getValues().stream().map(Value::getName).toArray(String[]::new);
        Value<?> value = module.getValue(args[1]);
        if (value instanceof ModeValue mode) return mode.getModes();
        return new String[0];
    }
}
