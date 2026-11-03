package wtf.carnation.module.impl.misc;

import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

/** Allows attacking players that would normally be treated as friends. */
public class ClientFriend extends Module {
    public ClientFriend() {
        super("ClientFriend", Category.MISC, "把其他人当作好友，可以正常攻击");
    }
}
