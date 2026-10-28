package wtf.carnation.manager.command.impl;

import com.mojang.blaze3d.platform.InputConstants;
import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.input.KeyEvent;
import wtf.carnation.manager.command.Command;
import wtf.carnation.manager.command.CommandInfo;
import wtf.carnation.module.Module;
import wtf.carnation.utils.ChatUtil;

import java.util.Locale;

@CommandInfo(
        name = "bind",
        desc = "Bind or unbind a module key",
        usage = {".bind <module> [key|none]"},
        aliases = {"b"}
)
public class CommandBind extends Command {
    @Override
    public void onCommand(String[] args) {
        if (args.length < 1 || args.length > 2) {
            ChatUtil.addChatMessage("Usage: .bind <module> [key|none]");
            return;
        }
        Module module = Client.instance.getModuleManager().getModuleByName(args[0]);
        if (module == null) {
            ChatUtil.addChatMessage("Invalid module: " + args[0]);
            return;
        }

        if (args.length == 1) {
            // Interactive: the next key pressed outside a screen becomes the bind.
            ChatUtil.addChatMessage("Press a key to bind " + module.getName() + ".");
            Client.instance.getEventManager().register(new Object() {
                @EventTarget
                public void onKey(KeyEvent e) {
                    if (!e.state()) return;
                    Client.instance.getEventManager().unregister(this);
                    module.setKey(e.key());
                    ChatUtil.addChatMessage("Bound " + module.getName() + " to " + keyName(e.key()) + ".");
                    Client.instance.getConfigManager().saveAllConfig();
                }
            });
            return;
        }

        if (args[1].equalsIgnoreCase("none")) {
            module.setKey(0);
            ChatUtil.addChatMessage("Unbound " + module.getName() + ".");
        } else {
            InputConstants.Key key = InputConstants.getKey("key.keyboard." + args[1].toLowerCase(Locale.ROOT));
            if (key == InputConstants.UNKNOWN) {
                ChatUtil.addChatMessage("Invalid key: " + args[1]);
                return;
            }
            module.setKey(key.getValue());
            ChatUtil.addChatMessage("Bound " + module.getName() + " to " + keyName(key.getValue()) + ".");
        }
        Client.instance.getConfigManager().saveAllConfig();
    }

    public static String keyName(int key) {
        if (key <= 0) return "None";
        return InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString().toUpperCase(Locale.ROOT);
    }

    @Override
    public String[] onTab(String[] args) {
        if (args.length == 1) return CommandModule.moduleNames();
        if (args.length == 2) return new String[]{"none"};
        return new String[0];
    }
}
