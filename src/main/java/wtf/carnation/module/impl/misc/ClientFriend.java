package wtf.carnation.module.impl.misc;

import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.utils.ChatUtil;
import wtf.carnation.utils.TimeHelper;

/** Port of Naven's {@code ClientFriend}. */
public class ClientFriend extends Module {
    public static final TimeHelper attackTimer = new TimeHelper();

    public ClientFriend() {
        super("ClientFriend", Category.MISC, "Treat other users as friend!");
    }

    @Override
    protected void onDisable() {
        attackTimer.reset();
        ChatUtil.addChatMessage("You can attack other players after 15 seconds.");
    }
}
