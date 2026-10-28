package wtf.carnation.manager.command;

import lombok.Getter;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.network.ChatEvent;
import wtf.carnation.manager.command.impl.CommandBind;
import wtf.carnation.manager.command.impl.CommandConfig;
import wtf.carnation.manager.command.impl.CommandHelp;
import wtf.carnation.manager.command.impl.CommandLua;
import wtf.carnation.manager.command.impl.CommandModule;
import wtf.carnation.manager.command.impl.CommandToggle;
import wtf.carnation.utils.ChatUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CommandManager {
    public static final String PREFIX = ".";

    private final Map<String, Command> aliasMap = new HashMap<>();
    @Getter
    private final List<Command> commands = new ArrayList<>();

    public CommandManager() {
        registerCommand(new CommandToggle());
        registerCommand(new CommandBind());
        registerCommand(new CommandModule());
        registerCommand(new CommandConfig());
        registerCommand(new CommandLua());
        registerCommand(new CommandHelp());
    }

    private void registerCommand(Command command) {
        command.initCommand();
        commands.add(command);
        aliasMap.put(command.getName().toLowerCase(Locale.ROOT), command);
        for (String alias : command.getAliases()) {
            aliasMap.put(alias.toLowerCase(Locale.ROOT), command);
        }
    }

    public Command getCommand(String name) {
        return name == null ? null : aliasMap.get(name.toLowerCase(Locale.ROOT));
    }

    /** Runs a command line without the prefix, e.g. "toggle Sprint". Returns false when no command matched. */
    public boolean execute(String line) {
        String[] arguments = line.trim().split("\\s+");
        if (arguments.length == 0 || arguments[0].isEmpty()) {
            ChatUtil.addChatMessage("Invalid command.");
            return false;
        }
        Command command = getCommand(arguments[0]);
        if (command == null) {
            ChatUtil.addChatMessage("Unknown command: " + arguments[0] + ", try " + PREFIX + "help");
            return false;
        }
        String[] args = new String[arguments.length - 1];
        System.arraycopy(arguments, 1, args, 0, args.length);
        try {
            command.onCommand(args);
        } catch (Exception e) {
            ChatUtil.addChatMessage("Error: " + e.getMessage());
            e.printStackTrace();
        }
        return true;
    }

    public List<String> getCompletions(String typed) {
        String[] parts = typed.substring(PREFIX.length()).split(" ", -1);
        List<String> results = new ArrayList<>();
        if (parts.length <= 1) {
            String partial = parts[0].toLowerCase(Locale.ROOT);
            for (String alias : aliasMap.keySet()) {
                if (alias.startsWith(partial)) results.add(PREFIX + alias);
            }
        } else {
            Command command = getCommand(parts[0]);
            if (command != null) {
                String[] tabArgs = new String[parts.length - 1];
                System.arraycopy(parts, 1, tabArgs, 0, tabArgs.length);
                String partial = tabArgs[tabArgs.length - 1].toLowerCase(Locale.ROOT);
                String[] suggestions = command.onTab(tabArgs);
                if (suggestions != null) {
                    for (String s : suggestions) {
                        if (s != null && s.toLowerCase(Locale.ROOT).startsWith(partial)) results.add(s);
                    }
                }
            }
        }
        results.sort(String.CASE_INSENSITIVE_ORDER);
        return results;
    }

    @EventTarget
    public void onChat(ChatEvent e) {
        if (!e.getMessage().startsWith(PREFIX)) return;
        e.setCancelled(true);
        execute(e.getMessage().substring(PREFIX.length()));
    }
}
