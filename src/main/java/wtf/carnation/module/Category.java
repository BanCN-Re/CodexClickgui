package wtf.carnation.module;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Category {
    COMBAT("Combat", "战斗"),
    MOVEMENT("Movement", "移动"),
    PLAYER("Player", "玩家"),
    WORLD("World", "世界"),
    RENDER("Render", "渲染"),
    MISC("Misc", "杂项");

    /** The English name, for identifiers and commands. */
    private final String name;
    /** The name shown in the GUI. */
    private final String displayName;
}
