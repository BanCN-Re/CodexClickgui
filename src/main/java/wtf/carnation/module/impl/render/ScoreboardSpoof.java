package wtf.carnation.module.impl.render;

import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.utils.TextReplacer;

/**
 * Port of Naven's {@code ScoreboardSpoof} (display name "ServerNameSpoof"). The scoreboard/tab
 * rewriting hooks are applied by the text replacer when those components are built.
 */
public class ScoreboardSpoof extends Module {
    public static final String SERVER_NAME = "布吉岛";
    private static final String SERVER_SPOOF_TEXT = "§d§l吉吉岛";
    private static final String SERVER_SPOOF_PLAIN = "吉吉岛";

    public ScoreboardSpoof() {
        super("ServerNameSpoof", Category.RENDER, "Spoof the server name");
    }

    /** Used by MixinObjective. */
    public String title() {
        return SERVER_SPOOF_TEXT;
    }

    @Override
    protected void onEnable() {
        TextReplacer.setServer(SERVER_NAME, SERVER_SPOOF_PLAIN, SERVER_SPOOF_TEXT);
    }

    @Override
    protected void onDisable() {
        TextReplacer.setServer(null, null, null);
    }
}
