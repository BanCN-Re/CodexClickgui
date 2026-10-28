package wtf.carnation.manager.command.impl;

import wtf.carnation.Client;
import wtf.carnation.manager.command.Command;
import wtf.carnation.manager.command.CommandInfo;
import wtf.carnation.script.LuaScriptManager;
import wtf.carnation.utils.ChatUtil;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

@CommandInfo(
        name = "lua",
        desc = "Load, reload or unload Lua modules, or execute Lua source",
        usage = {".lua list|reload", ".lua load <file>", ".lua unload <module>", ".lua <source>"},
        aliases = {"script"}
)
public class CommandLua extends Command {
    @Override
    public void onCommand(String[] args) {
        LuaScriptManager manager = Client.instance.getLuaScriptManager();
        if (args.length == 0) {
            ChatUtil.addChatMessage("Usage: .lua list|reload|load <file>|unload <module>|<source>");
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> ChatUtil.addChatMessage(manager.listScripts());
            case "reload" -> ChatUtil.addChatMessage(manager.reload());
            case "load" -> {
                if (args.length != 2) {
                    ChatUtil.addChatMessage("Usage: .lua load <file>");
                    return;
                }
                Path path = Path.of(args[1]);
                if (!path.isAbsolute()) path = manager.directory().resolve(path);
                ChatUtil.addChatMessage(manager.load(path));
            }
            case "unload" -> {
                if (args.length < 2) {
                    ChatUtil.addChatMessage("Usage: .lua unload <module>");
                    return;
                }
                ChatUtil.addChatMessage(manager.unload(String.join(" ", Arrays.copyOfRange(args, 1, args.length))));
            }
            default -> ChatUtil.addChatMessage(manager.loadFromSource(String.join(" ", args), "ChatScript.lua"));
        }
    }

    @Override
    public String[] onTab(String[] args) {
        if (args.length == 1) return new String[]{"list", "load", "reload", "unload"};
        if (args.length == 2 && args[0].equalsIgnoreCase("unload")) {
            return Client.instance.getLuaScriptManager().scriptNames();
        }
        return new String[0];
    }
}
