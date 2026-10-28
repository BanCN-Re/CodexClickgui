package wtf.carnation.manager.command;

import lombok.Getter;

@Getter
public abstract class Command {
    private String name;
    private String desc;
    private String[] usage;
    private String[] aliases;

    public void initCommand() {
        CommandInfo info = getClass().getAnnotation(CommandInfo.class);
        if (info == null) throw new IllegalStateException(getClass().getName() + " is missing @CommandInfo");
        this.name = info.name();
        this.desc = info.desc();
        this.usage = info.usage().length == 0 ? new String[]{CommandManager.PREFIX + info.name()} : info.usage();
        this.aliases = info.aliases();
    }

    public abstract void onCommand(String[] args);

    public abstract String[] onTab(String[] args);
}
