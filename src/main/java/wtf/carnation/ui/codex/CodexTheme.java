package wtf.carnation.ui.codex;

import wtf.carnation.graphics.Icons;
import wtf.carnation.module.Category;

/** Colours of the Codex desktop app, sampled from a screenshot of it. */
public final class CodexTheme {
    // Surfaces.
    public static final int CHROME = 0xFF22232D;
    public static final int SIDEBAR = 0xFF262733;
    public static final int MAIN = 0xFF282A36;
    public static final int PANEL_BORDER = 0xFF2C2D38;
    public static final int WINDOW_BORDER = 0xFF3A3B46;
    public static final int SIDEBAR_DIVIDER = 0xFF33343D;
    public static final int HEADER_DIVIDER = 0xFF393B46;

    // Controls.
    public static final int RAIL_ACTIVE = 0xFF32333C;
    public static final int ITEM_HOVER = 0xFF2D2E3A;
    public static final int ITEM_ACTIVE = 0xFF32333F;
    public static final int TAB = 0xFF383A45;
    public static final int TAB_BORDER = 0xFF565860;
    public static final int BUTTON = 0xFF32343F;
    public static final int CHIP_BAR = 0xFF373843;
    public static final int COMPOSER = 0xFF43444E;
    public static final int COMPOSER_BORDER = 0xFF4A4B54;
    public static final int COMPOSER_FOCUS = 0xFF5B5D68;
    public static final int CARD = 0xFF2D2F3B;
    public static final int CARD_HOVER = 0xFF323441;
    public static final int CARD_BORDER = 0xFF393B46;
    public static final int CARD_BORDER_HOVER = 0xFF4A4C58;
    public static final int POPUP = 0xFF2F303C;
    public static final int POPUP_BORDER = 0xFF41434E;
    public static final int TRACK = 0xFF4A4C57;
    public static final int CLOSE_HOVER = 0xFFC42B1C;

    // Text and icons.
    public static final int TEXT = 0xFFF8F8F2;
    public static final int TEXT_SIDEBAR = 0xFFCACAC9;
    public static final int TEXT_SECTION = 0xFF8F9093;
    public static final int TEXT_MUTED = 0xFF8A8B91;
    public static final int PLACEHOLDER = 0xFF6E6F75;
    public static final int ICON = 0xFF8C8D8F;
    public static final int LOGO = 0xFF5E6068;
    public static final int ORANGE = 0xFFF28149;
    public static final int AVATAR = 0xFF9B59B6;
    public static final int SEND = 0xFFF8F8F2;
    public static final int SEND_GLYPH = 0xFF22232D;

    /** The HUD card used by in-game modules, slightly translucent. */
    public static final int HUD = 0xEB282A36;

    private CodexTheme() {
    }

    /**
     * Drops a physical-pixel offset onto the GUI pixel grid. A window placed between two GUI pixels
     * makes every scaled rect and glyph sample across texel edges, which is what reads as blur.
     */
    public static int snap(int value, int guiScale) {
        if (guiScale <= 1) return value;
        return value - Math.floorMod(value, guiScale);
    }

    public static char icon(Category category) {
        if (category == null) return Icons.HOUSE;
        return switch (category) {
            case COMBAT -> Icons.SWORDS;
            case MOVEMENT -> Icons.FOOTPRINTS;
            case PLAYER -> Icons.USER;
            case WORLD -> Icons.GLOBE;
            case RENDER -> Icons.EYE;
            case MISC -> Icons.ELLIPSIS;
        };
    }
}
