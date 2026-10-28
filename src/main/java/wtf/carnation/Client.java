package wtf.carnation;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import wtf.carnation.event.EventManager;
import wtf.carnation.manager.command.CommandManager;
import wtf.carnation.manager.config.ConfigManager;
import wtf.carnation.module.ModuleManager;
import wtf.carnation.script.LuaScriptManager;

import java.io.File;

@Getter
public class Client implements InstanceAccess {
    public static Client instance;

    public static String name = "Carnation";
    public static String ver = "26.3";
    public static Logger logger = LogManager.getLogger(name);

    public static final File clientFolder = new File(Minecraft.getInstance().gameDirectory, name);

    private final EventManager eventManager;
    private final ModuleManager moduleManager;
    private final LuaScriptManager luaScriptManager;
    private final CommandManager commandManager;
    private final ConfigManager configManager;
    private volatile boolean shuttingDown;

    public Client() {
        instance = this;
        eventManager = new EventManager();

        moduleManager = new ModuleManager();
        luaScriptManager = new LuaScriptManager();
        luaScriptManager.loadAll();
        commandManager = new CommandManager();
        configManager = new ConfigManager();

        eventManager.register(moduleManager);
        eventManager.register(luaScriptManager);
        eventManager.register(commandManager);

        configManager.loadAllConfig();
        logger.info("{} {} loaded with {} modules", name, ver, moduleManager.getModules().size());
    }

    public synchronized void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        configManager.saveAllConfig();
        luaScriptManager.unloadAll();
    }
}
