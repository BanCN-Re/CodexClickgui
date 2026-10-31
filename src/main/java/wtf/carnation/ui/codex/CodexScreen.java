package wtf.carnation.ui.codex;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import wtf.carnation.Client;
import wtf.carnation.codex.CodexAccount;
import wtf.carnation.codex.CodexChat;
import wtf.carnation.graphics.Icons;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.manager.command.CommandManager;
import wtf.carnation.manager.command.impl.CommandBind;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.Value;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static wtf.carnation.ui.codex.CodexTheme.*;

/**
 * The ClickGUI as a one-to-one copy of the Codex desktop app.
 *
 * <p>The layout is written in the pixel coordinates of a 1551x1115 Codex window. Each frame the
 * whole window is placed in the game window at one of {@link UiFont}'s baked scales, so shapes
 * (renderpearl triangles, see {@link Render2D}) and TrueType text stay pixel-exact.
 *
 * <p>Mapping: the rail and the sidebar's 分类 section pick a category, 最近 lists modules toggled
 * here, module cards sit under the prompt, clicking a card opens its settings in a pane on the
 * right, and the composer filters modules as you type or runs client commands starting with ".".
 * Everything is drawn immediate-mode; clickable areas are collected while drawing.
 */
public class CodexScreen extends Screen {
    private static final float RAIL_W = 64, SIDEBAR_W = 303, PANEL_TOP = 54, PANEL_BOTTOM = 1110, PANEL_RIGHT = 1547;
    private static final float PANE_W = 404;
    private static final float CARDS_TOP = 640, CARDS_BOTTOM = 900, CARD_H = 104, CARD_GAP = 12;
    private static final float[] MENU_X = {153, 218, 283, 347};
    private static final String[] MENUS = {"文件", "编辑", "视图", "帮助"};
    private static final float POPUP_X = 84, POPUP_Y = 828, POPUP_W = 384, POPUP_H = 244;
    private static final float ACCOUNT_PANEL_W = 680, ACCOUNT_PANEL_H = 515;
    private static final float USAGE_POPUP_W = 380, USAGE_POPUP_H = 252;
    private static final int PINK = 0xFFEC4899;

    /** The design size this window is laid out in; subclasses draw other apps at their own size. */
    protected final float W, H;
    /** The account panel sits in the middle of the window. */
    protected final float accountPanelX, accountPanelY;
    /** Top-left of the quota card; each layout puts it next to the control that opens it. */
    protected float usagePopupX = 388, usagePopupY = 128;

    /** Modules toggled from this GUI, newest first; kept across openings. */
    private static final Deque<Module> RECENT = new ArrayDeque<>();
    protected static boolean sidebarOpen = true, categoriesOpen = true, recentOpen = false, archivedOpen = false;

    protected enum View { ALL, CATEGORY, RECENT }

    /** Whether the main area shows the module grid or the conversation stream. */
    protected enum MainView {MODULES, CHAT}

    protected record Place(View view, Category category) {
    }

    protected Place place = new Place(View.ALL, null);
    protected final Deque<Place> back = new ArrayDeque<>(), forward = new ArrayDeque<>();

    protected String input = "";
    protected List<String> completions = List.of();
    protected int completionIndex;
    /** Dedicated account-login field inside the account popup; never mixes with the composer. */
    protected String accountInput = "";
    protected boolean accountFieldFocused;
    /** Wrapped preview of the account field; recomputed only when the text changes. */
    protected String accountFieldCache = null;
    protected List<String> accountFieldLines = List.of();
    /** Hard cap for the composer: pasted JSON belongs in the account popup, not here. */
    private static final int MAX_INPUT = 2000;

    protected Module selected;
    protected Module binding;
    protected NumberValue dragging;
    protected float dragX, dragW;
    protected int openMenu = -1;
    protected boolean accountOpen;
    protected boolean modelOpen;
    protected boolean usageOpen;
    protected boolean chatFollow = true;
    protected MainView mainView = MainView.MODULES;
    /** Conversation indices whose thinking block the user expanded. */
    protected final Set<Integer> expandedThinking = new HashSet<>();
    /** Set while a conversation rename is pending; the composer text becomes the new title. */
    protected Path renamePending;
    /** The composing IME string, shown inline until the commit lands in charTyped. */
    protected String preedit = "";
    /**
     * A no-op focused target that keeps SDL text input on. 26.x only feeds typed characters and
     * IME events to a screen while a capturesInput() listener holds focus (EditBox.setFocused is
     * what normally flips the switch), and every control here is drawn by hand — without the
     * anchor the composer never receives a single typed character, English included.
     */
    protected final GuiEventListener textAnchor = new GuiEventListener() {
        @Override
        public void setFocused(boolean focused) {
        }

        @Override
        public boolean isFocused() {
            return true;
        }

        @Override
        public boolean capturesInput() {
            return true;
        }
    };

    protected String toast = "";
    protected long toastAt;
    protected boolean toastError;
    protected long placeChangedAt = System.currentTimeMillis();
    protected String heroText = "", heroPrevious = "";
    protected long heroChangedAt;

    protected float scroll, targetScroll, maxScroll;
    protected float open;
    protected boolean closing;
    protected long lastFrame, typedAt;
    protected final long openedAt = System.currentTimeMillis();

    // Frame state.
    protected float s, alpha, mouseX = -1, mouseY = -1, dt;
    protected int originX, originY, frame;
    protected final Map<Object, float[]> animations = new HashMap<>();
    protected final List<Hit> hits = new ArrayList<>();
    protected final Deque<float[]> clips = new ArrayDeque<>();
    protected float clipX0 = -1e9f, clipY0 = -1e9f, clipX1 = 1e9f, clipY1 = 1e9f;

    protected record Hit(float x, float y, float w, float h, Runnable left, Runnable right) {
        boolean contains(float px, float py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    public CodexScreen() {
        this(1551, 1115);
    }

    protected CodexScreen(float designWidth, float designHeight) {
        super(Component.literal("Carnation"));
        W = designWidth;
        H = designHeight;
        accountPanelX = Math.round((W - ACCOUNT_PANEL_W) / 2);
        accountPanelY = Math.round((H - ACCOUNT_PANEL_H) / 2);
        CodexChat.ensureLoaded();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        closing = true;
    }

    @Override
    protected void init() {
        // Nothing here is a real widget, so focus the anchor or SDL never starts text input.
        minecraft.onTextInputFocusChange(textAnchor, true);
    }

    @Override
    public void removed() {
        minecraft.onTextInputFocusChange(textAnchor, false);
        Client.instance.getConfigManager().saveAllConfig();
    }

    // ---- frame --------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int guiMouseX, int guiMouseY, float partialTick) {
        super.extractRenderState(g, guiMouseX, guiMouseY, partialTick);

        long now = System.nanoTime();
        dt = lastFrame == 0 ? 1 / 60f : Math.min(0.1f, (now - lastFrame) / 1.0e9f);
        lastFrame = now;
        frame++;
        open = approach(open, closing ? 0 : 1, closing ? 20 : 11);
        if (closing && open < 0.02f) {
            minecraft.gui.setScreen(null);
            return;
        }
        alpha = ease(open);

        Window window = minecraft.getWindow();
        int guiScale = window.getGuiScale();
        float fit = Math.min(window.getWidth() * 0.95f / W, window.getHeight() * 0.95f / H);
        // A baked scale keeps text sharp, but the whole window must always fit, so never
        // draw larger than the game window — on small windows everything shrinks instead.
        s = Math.min(UiFont.useScale(fit), fit);
        originX = snap(Math.round((window.getWidth() - W * s) / 2), guiScale);
        originY = snap(Math.round((window.getHeight() - H * s) / 2), guiScale);
        mouseX = toDesignX(guiMouseX);
        mouseY = toDesignY(guiMouseY);
        hits.clear();

        if (dragging != null) dragValue(dragging);
        scroll = approach(scroll, targetScroll, 18);

        g.pose().pushMatrix();
        g.pose().translate(originX / (float) guiScale, originY / (float) guiScale);
        g.pose().scale(s / guiScale, s / guiScale);
        float zoom = 0.965f + 0.035f * alpha;
        g.pose().translate(W / 2, H / 2 + (1 - alpha) * 14);
        g.pose().scale(zoom, zoom);
        g.pose().translate(-W / 2, -H / 2);

        drawWindow(g);

        g.pose().popMatrix();
    }

    protected void drawWindow(GuiGraphicsExtractor g) {
        Render2D.shadow(g, 0, 0, W, H, 10, 56, 18, c(0x90000000));
        Render2D.shadow(g, 0, 0, W, H, 10, 8, 2, c(0x60000000));
        Render2D.roundedRect(g, 0, 0, W, H, 10, c(CHROME));

        float sidebar = anim("sidebar", sidebarOpen ? 1 : 0, 14);
        float sidebarW = SIDEBAR_W * ease(sidebar);
        float mainX = RAIL_W + sidebarW;

        drawTitleBar(g);
        drawRail(g);

        // Content panel: sidebar on the left, main area on the right.
        Render2D.roundedRect(g, RAIL_W, PANEL_TOP, PANEL_RIGHT - RAIL_W, PANEL_BOTTOM - PANEL_TOP, 14, c(MAIN));
        if (sidebarW > 1) {
            pushClip(g, RAIL_W, PANEL_TOP, sidebarW, PANEL_BOTTOM - PANEL_TOP);
            Render2D.roundedRect(g, RAIL_W, PANEL_TOP, SIDEBAR_W + 20, PANEL_BOTTOM - PANEL_TOP, 14, c(SIDEBAR));
            drawSidebar(g, RAIL_W - (SIDEBAR_W - sidebarW));
            popClip(g);
            Render2D.rect(g, mainX - 1, PANEL_TOP, 1, PANEL_BOTTOM - PANEL_TOP, c(SIDEBAR_DIVIDER));
        }
        Render2D.roundedOutline(g, RAIL_W, PANEL_TOP, PANEL_RIGHT - RAIL_W, PANEL_BOTTOM - PANEL_TOP, 14, 1, c(PANEL_BORDER));

        drawMain(g, mainX);
        Render2D.roundedOutline(g, 0, 0, W, H, 10, 1, c(WINDOW_BORDER));
        drawMenuDropdown(g);
        drawAccountPopup(g);
        drawEffortPopup(g, effortStyle());
        drawUsagePopup(g);
    }

    // ---- title bar ----------------------------------------------------------------------------

    protected void drawTitleBar(GuiGraphicsExtractor g) {
        iconButton(g, Icons.ARROW_LEFT, 25, 23, 34, back.isEmpty() ? 0xFF4C4D54 : ICON, this::goBack);
        iconButton(g, Icons.ARROW_RIGHT, 68, 23, 34, forward.isEmpty() ? 0xFF4C4D54 : ICON, this::goForward);
        iconButton(g, Icons.PANEL_LEFT, 110, 23, 34, ICON, () -> sidebarOpen = !sidebarOpen);

        for (int i = 0; i < MENUS.length; i++) {
            float w = UiFont.SEMIBOLD.width(MENUS[i]);
            float x = MENU_X[i];
            boolean active = openMenu == i;
            float t = anim("menu" + i, active || hover(x - 8, 8, w + 16, 30) ? 1 : 0, 18);
            Render2D.roundedRect(g, x - 8, 8, w + 16, 30, 6, c(Render2D.fade(0xFF30313B, t)));
            UiFont.SEMIBOLD.drawMiddle(g, MENUS[i], x, 23, c(Render2D.mix(ICON, TEXT_SIDEBAR, t)));
            final int index = i;
            hit(x - 8, 8, w + 16, 30, () -> openMenu = openMenu == index ? -1 : index, null);
        }

        windowButton(g, Icons.MINUS, 1408, 0xFFC8C8CA, false, this::onClose);
        windowButton(g, Icons.SQUARE, 1465, 0xFFC8C8CA, false, () -> sidebarOpen = !sidebarOpen);
        windowButton(g, Icons.X, 1522, 0xFFFFFFFF, true, this::onClose);
    }

    protected void windowButton(GuiGraphicsExtractor g, char icon, float cx, int color, boolean close, Runnable action) {
        float x = cx - 28, w = 57;
        float t = anim("win" + icon, hover(x, 0, w, 46) ? 1 : 0, 20);
        int bg = close ? CLOSE_HOVER : 0xFF30313B;
        if (close) {
            // The close button meets the window's rounded top-right corner.
            pushClip(g, x, 0, w, 46);
            Render2D.roundedRect(g, x, 0, w + 10, 56, 10, c(Render2D.fade(bg, t)));
            popClip(g);
        } else {
            Render2D.rect(g, x, 0, w, 46, c(Render2D.fade(bg, t)));
        }
        UiFont.ICON_S.icon(g, icon, cx, 23, c(color));
        hit(x, 0, w, 46, action, null);
    }

    protected void iconButton(GuiGraphicsExtractor g, char icon, float cx, float cy, float size, int color, Runnable action) {
        float t = anim("icon" + icon + cx + cy, hover(cx - size / 2, cy - size / 2, size, size) ? 1 : 0, 18);
        Render2D.roundedRect(g, cx - size / 2, cy - size / 2, size, size, 8, c(Render2D.fade(0xFF30313B, t)));
        UiFont.ICON_M.icon(g, icon, cx, cy, c(Render2D.mix(color, TEXT_SIDEBAR, t * 0.6f)));
        hit(cx - size / 2, cy - size / 2, size, size, action, null);
    }

    // ---- rail ---------------------------------------------------------------------------------

    protected void drawRail(GuiGraphicsExtractor g) {
        List<Place> places = new ArrayList<>();
        places.add(new Place(View.ALL, null));
        for (Category category : Category.values()) places.add(new Place(View.CATEGORY, category));

        float activeY = -1;
        float y = 87;
        float[] ys = new float[places.size()];
        for (int i = 0; i < places.size(); i++) {
            ys[i] = y;
            if (place.equals(places.get(i)) && !isSearching()) activeY = y;
            y += 55;
        }
        float separatorY = y - 22;
        float recentY = separatorY + 33;
        if (place.view() == View.RECENT && !isSearching()) activeY = recentY;

        // The highlight slides between icons.
        float shownY = anim("railActive", activeY < 0 ? -200 : activeY, 16);
        float shown = anim("railActiveAlpha", activeY < 0 ? 0 : 1, 16);
        if (shown > 0.01f) Render2D.roundedRect(g, 11, shownY - 22, 42, 44, 10, c(Render2D.fade(RAIL_ACTIVE, shown)));

        for (int i = 0; i < places.size(); i++) {
            Place target = places.get(i);
            char icon = CodexTheme.icon(target.category());
            railIcon(g, icon, ys[i], activeY == ys[i], () -> navigate(target));
        }
        Render2D.rect(g, 19, separatorY, 28, 1, c(0xFF3A3B45));
        railIcon(g, Icons.HISTORY, recentY, activeY == recentY, () -> navigate(new Place(View.RECENT, null)));
        railIcon(g, Icons.LAYOUT_GRID, recentY + 55, false, () -> sidebarOpen = !sidebarOpen);

        // Account bubble.
        String name = minecraft.getUser().getName();
        String initials = name.length() >= 2 ? name.substring(0, 2).toUpperCase(Locale.ROOT) : "MO";
        Render2D.circle(g, 32.5f, 1082.5f, 14.5f, c(AVATAR));
        UiFont.SMALL.drawCentered(g, initials, 32.5f, 1082.5f, c(0xFFE8DDF0));
        hit(16, 1066, 33, 33, () -> accountOpen = !accountOpen, null);
    }

    protected void railIcon(GuiGraphicsExtractor g, char icon, float cy, boolean active, Runnable action) {
        boolean hovered = hover(11, cy - 22, 42, 44);
        float t = anim("rail" + icon, active ? 1 : hovered ? 0.55f : 0, 18);
        UiFont.ICON_L.icon(g, icon, 32, cy, c(Render2D.mix(ICON, TEXT, t)));
        hit(11, cy - 22, 42, 44, action, null);
    }

    // ---- sidebar ------------------------------------------------------------------------------

    protected void drawSidebar(GuiGraphicsExtractor g, float x0) {
        float dx = x0 - RAIL_W;
        UiFont.TITLE.drawMiddle(g, Client.name, 86 + dx, 87, c(TEXT));
        UiFont.ICON_S.icon(g, Icons.CHEVRON_DOWN, 86 + dx + UiFont.TITLE.width(Client.name) + 14, 88, c(ICON));
        iconButton(g, Icons.BELL, 292 + dx, 88, 32, ICON, () -> usageOpen = !usageOpen);
        iconButton(g, Icons.SEARCH, 334 + dx, 88, 32, ICON, () -> {
            input = "";
            navigate(new Place(View.ALL, null));
        });

        sidebarRow(g, dx, 139, Icons.SQUARE_PEN, "全部模块", null, place.view() == View.ALL && !isSearching(),
                () -> navigate(new Place(View.ALL, null)));

        float y = 192;
        float categories = sectionHeader(g, dx, y, "分类", categoriesOpen, () -> categoriesOpen = !categoriesOpen, "categories");
        y += 28;
        float rowsH = Category.values().length * 38;
        if (categories > 0.01f) {
            pushClip(g, RAIL_W, y, SIDEBAR_W, rowsH * categories);
            float rowY = y + 17 - (1 - categories) * 10;
            for (Category category : Category.values()) {
                Place target = new Place(View.CATEGORY, category);
                sidebarRow(g, dx, rowY, CodexTheme.icon(category), category.getDisplayName(),
                        String.valueOf(count(category)), place.equals(target) && !isSearching(), () -> navigate(target));
                rowY += 38;
            }
            popClip(g);
        }
        y += rowsH * categories + 29;

        float recent = sectionHeader(g, dx, y, "对话", recentOpen, () -> recentOpen = !recentOpen, "recent");
        y += 28;
        List<CodexChat.SessionInfo> sessions = CodexChat.sessions();
        List<CodexChat.SessionInfo> archived = CodexChat.archivedSessions();
        int archiveRows = 1 + (archivedOpen ? Math.max(1, archived.size()) : 0);
        float recentH = (Math.max(1, sessions.size()) + 1 + archiveRows) * 38;
        if (recent > 0.01f) {
            pushClip(g, RAIL_W, y, SIDEBAR_W, recentH * recent);
            float rowY = y + 17 - (1 - recent) * 10;
            sidebarRow(g, dx, rowY, Icons.SQUARE_PEN, "开始新对话", null,
                    mainView == MainView.CHAT && CodexChat.messages().isEmpty(), () -> {
                        CodexChat.newSession();
                        renamePending = null;
                        mainView = MainView.CHAT;
                        chatFollow = true;
                    });
            rowY += 38;
            if (sessions.isEmpty()) {
                UiFont.SMALL.drawMiddle(g, "还没有对话记录", 115 + dx, rowY, c(TEXT_MUTED));
            }
            java.time.format.DateTimeFormatter day = java.time.format.DateTimeFormatter.ofPattern("MM-dd");
            for (CodexChat.SessionInfo session : sessions) {
                String title = UiFont.SEMIBOLD.ellipsize(session.title(), 116);
                String when = day.format(java.time.Instant.ofEpochMilli(session.lastModified())
                        .atZone(java.time.ZoneId.systemDefault()));
                boolean rowHover = hover(76 + dx, rowY - 18, 280, 36);
                sidebarRow(g, dx, rowY, Icons.MESSAGE_CIRCLE, title, rowHover ? null : when,
                        session.file().equals(CodexChat.currentSessionFile()), () -> {
                            renamePending = null;
                            CodexChat.openSession(session.file());
                            mainView = MainView.CHAT;
                            chatFollow = true;
                        }, () -> {
                            renamePending = session.file();
                            input = "";
                            updateCompletions();
                            showToast("在下方输入新标题，回车确认");
                        });
                if (rowHover) drawArchiveButton(g, dx, rowY, session.file());
                rowY += 38;
            }
            // The archive: hidden conversations live in Work/sessions/archived until restored.
            sidebarRow(g, dx, rowY, Icons.FOLDER, "已归档",
                    archived.isEmpty() ? null : String.valueOf(archived.size()), archivedOpen,
                    () -> archivedOpen = !archivedOpen);
            rowY += 38;
            if (archivedOpen && archived.isEmpty()) {
                UiFont.SMALL.drawMiddle(g, "没有归档的对话", 115 + dx, rowY, c(TEXT_MUTED));
            }
            for (CodexChat.SessionInfo session : archived) {
                if (!archivedOpen) break;
                String title = UiFont.SEMIBOLD.ellipsize(session.title(), 128);
                String when = day.format(java.time.Instant.ofEpochMilli(session.lastModified())
                        .atZone(java.time.ZoneId.systemDefault()));
                sidebarRow(g, dx, rowY, Icons.ROTATE_CCW, title, when, false, () -> {
                    String result = CodexChat.unarchiveSession(session.file());
                    showToast(result);
                    if (result.startsWith("已恢复") && session.file().getParent() != null) {
                        CodexChat.openSession(session.file().getParent().getParent()
                                .resolve(session.file().getFileName()));
                        mainView = MainView.CHAT;
                        chatFollow = true;
                    }
                });
                rowY += 38;
            }
            popClip(g);
        }
    }

    /** Hover-only archive button at a session row's right edge; collected after the row hit. */
    protected void drawArchiveButton(GuiGraphicsExtractor g, float dx, float rowY, Path file) {
        float bx = 76 + dx + 280 - 34, by = rowY;
        boolean btnHover = hover(bx - 4, by - 11, 26, 22);
        float t = anim("arch" + file, btnHover ? 1 : 0, 14);
        Render2D.roundedRect(g, bx - 4, by - 11, 26, 22, 6, c(Render2D.fade(ITEM_HOVER, t)));
        UiFont.ICON_S.icon(g, Icons.FOLDER, bx + 9, by, c(Render2D.mix(TEXT_MUTED, TEXT, Math.max(t, 0.35f))));
        hit(bx - 4, by - 11, 26, 22, () -> {
            if (CodexChat.isRunningSession(file)) {
                showToast("该对话有后台任务进行中，完成后再归档");
                return;
            }
            showToast(CodexChat.archiveSession(file));
        }, null);
    }

    /** A collapsible section title ("项目 >") whose chevron turns as it opens; returns the open amount. */
    protected float sectionHeader(GuiGraphicsExtractor g, float dx, float cy, String label, boolean expanded,
                                Runnable toggle, String key) {
        float t = anim("section" + key, expanded ? 1 : 0, 14);
        float w = UiFont.SEMIBOLD.width(label);
        boolean hovered = hover(76 + dx, cy - 16, w + 50, 32);
        float h = anim("sectionHover" + key, hovered ? 1 : 0, 18);
        UiFont.SEMIBOLD.drawMiddle(g, label, 86 + dx, cy, c(Render2D.mix(TEXT_SECTION, TEXT_SIDEBAR, h)));
        float chevronX = 86 + dx + w + 14, chevronY = cy + 1;
        g.pose().pushMatrix();
        g.pose().translate(chevronX, chevronY);
        g.pose().rotate((float) (Math.PI / 2 * ease(t)));
        g.pose().translate(-chevronX, -chevronY);
        UiFont.ICON_S.icon(g, Icons.CHEVRON_RIGHT, chevronX, chevronY, c(Render2D.mix(TEXT_SECTION, TEXT_SIDEBAR, h)));
        g.pose().popMatrix();
        hit(76 + dx, cy - 16, w + 50, 32, toggle, null);
        return ease(t);
    }

    protected void sidebarRow(GuiGraphicsExtractor g, float dx, float cy, char icon, String label, String trailing,
                            boolean active, Runnable action) {
        sidebarRow(g, dx, cy, icon, label, trailing, active, action, null);
    }

    protected void sidebarRow(GuiGraphicsExtractor g, float dx, float cy, char icon, String label, String trailing,
                            boolean active, Runnable action, Runnable right) {
        float x = 76 + dx, w = 280, h = 36;
        boolean hovered = hover(x, cy - h / 2, w, h);
        float t = anim("row" + label, active ? 1 : hovered ? 0.6f : 0, 18);
        int bg = active ? ITEM_ACTIVE : ITEM_HOVER;
        Render2D.roundedRect(g, x, cy - h / 2, w, h, 8, c(Render2D.fade(bg, t)));
        UiFont.ICON_S.icon(g, icon, 95 + dx, cy, c(active ? TEXT : 0xFFC2C2C1));
        UiFont.SEMIBOLD.drawMiddle(g, label, 115 + dx, cy, c(active ? TEXT : TEXT_SIDEBAR));
        if (trailing != null) UiFont.SMALL.drawRight(g, trailing, x + w - 12, cy, c(TEXT_MUTED));
        hit(x, cy - h / 2, w, h, action, right);
    }

    // ---- main area ----------------------------------------------------------------------------

    protected void drawMain(GuiGraphicsExtractor g, float mainX) {
        float pane = ease(anim("pane", selected != null ? 1 : 0, 13));
        float paneX = PANEL_RIGHT - PANE_W * pane;
        float contentRight = PANEL_RIGHT - PANE_W * pane;
        float cx = (mainX + contentRight) / 2;
        float composerW = Math.min(920, contentRight - mainX - 80);
        float composerX = cx - composerW / 2;

        drawHeader(g, mainX);
        if (mainView == MainView.CHAT) {
            drawChat(g, composerX, composerW, 130, 908);
        } else {
            drawHero(g, cx);
            drawCards(g, composerX, composerW);
        }
        drawComposer(g, composerX, 968, composerW, 122);
        if (pane > 0.002f) drawPane(g, paneX, pane);
        drawToast(g, (mainX + PANEL_RIGHT) / 2);
    }

    protected void drawHeader(GuiGraphicsExtractor g, float mainX) {
        float tabX = mainX + 17;
        Render2D.roundedRect(g, tabX, 68, 298, 40, 8, c(TAB));
        Render2D.roundedOutline(g, tabX, 68, 298, 40, 8, 1, c(TAB_BORDER));
        char icon = mainView == MainView.CHAT ? Icons.MESSAGE_CIRCLE
                : isSearching() ? Icons.SEARCH : place.view() == View.RECENT ? Icons.HISTORY
                : place.view() == View.CATEGORY ? CodexTheme.icon(place.category()) : Icons.MESSAGE_CIRCLE;
        UiFont.ICON_S.icon(g, icon, tabX + 22, 88, c(TEXT));
        UiFont.SEMIBOLD.drawMiddle(g, UiFont.SEMIBOLD.ellipsize(placeTitle(), 240), tabX + 38, 88, c(TEXT));
        iconButton(g, Icons.PLUS, mainX + 345, 88, 32, ICON, () -> {
            input = "";
            updateCompletions();
            navigate(new Place(View.ALL, null));
        });

        // Top-right: details pane toggle in a filled button, then a reload button.
        float t = anim("paneButton", hover(1455, 70, 35, 35) || selected != null ? 1 : 0, 18);
        Render2D.roundedRect(g, 1455, 70, 35, 35, 8, c(Render2D.mix(BUTTON, 0xFF3B3D49, t)));
        UiFont.ICON_S.icon(g, Icons.PANEL_RIGHT, 1472.5f, 87.5f, c(Render2D.mix(ICON, TEXT, t)));
        hit(1455, 70, 35, 35, () -> {
            if (selected != null) selected = null;
            else if (!visibleModules().isEmpty()) select(visibleModules().getFirst());
        }, null);
        iconButton(g, Icons.SQUARE_PLUS, 1515, 88, 32, ICON, () -> {
            Client.instance.getConfigManager().loadAllConfig();
            showToast("已重新加载配置");
        });

        Render2D.rect(g, mainX, 119, PANEL_RIGHT - mainX, 1, c(HEADER_DIVIDER));
    }

    protected String placeTitle() {
        if (mainView == MainView.CHAT) return "对话";
        if (isSearching()) return "搜索 “" + displayClip(input.trim(), 40) + "”";
        return switch (place.view()) {
            case ALL -> "全部模块";
            case RECENT -> "最近";
            case CATEGORY -> place.category().getDisplayName();
        };
    }

    protected void drawHero(GuiGraphicsExtractor g, float cx) {
        float breathe = 1 + 0.018f * (float) Math.sin((System.currentTimeMillis() - openedAt) / 700.0);
        g.pose().pushMatrix();
        g.pose().translate(cx, 492);
        g.pose().scale(breathe, breathe);
        drawLogo(g, 1);
        g.pose().popMatrix();

        String text = heroLine();
        if (!text.equals(heroText)) {
            heroPrevious = heroText;
            heroText = text;
            heroChangedAt = System.currentTimeMillis();
        }
        float t = ease(Math.min(1, (System.currentTimeMillis() - heroChangedAt) / 280f));
        if (t < 1 && !heroPrevious.isEmpty()) drawHeadline(g, heroPrevious, cx, 579 - 10 * t, 1 - t);
        drawHeadline(g, heroText, cx, 579 + 10 * (1 - t), heroPrevious.isEmpty() ? 1 : t);
    }

    protected String heroLine() {
        if (isSearching()) return "搜索 \u0001" + displayClip(input.trim(), 48) + "\u0001 的模块";
        return switch (place.view()) {
            case ALL -> "你想让我们在 \u0001" + Client.name + "\u0001 中启用什么?";
            case RECENT -> "最近在 \u0001" + Client.name + "\u0001 中切换的模块";
            case CATEGORY -> "你想在 \u0001" + place.category().getDisplayName() + "\u0001 中启用什么?";
        };
    }

    /** A headline whose part between \u0001 marks gets the dotted underline, as Codex does for the project. */
    protected void drawHeadline(GuiGraphicsExtractor g, String line, float cx, float cy, float fade) {
        String[] parts = line.split("\u0001", -1);
        float total = 0;
        for (String part : parts) total += UiFont.HERO.width(part);
        float x = cx - total / 2;
        for (int i = 0; i < parts.length; i++) {
            float w = UiFont.HERO.width(parts[i]);
            UiFont.HERO.drawMiddle(g, parts[i], x, cy, c(Render2D.fade(TEXT, fade)));
            if (i % 2 == 1) {
                for (float d = 0; d < w; d += 4) {
                    Render2D.rect(g, x + d, cy + 18, Math.min(2, w - d), 1.2f, c(Render2D.fade(0xFF8A8B91, fade)));
                }
            }
            x += w;
        }
    }

    /** The Codex blossom: the outer contour of a ring of circles, with a prompt inside. */
    protected void drawLogo(GuiGraphicsExtractor g, float scale) {
        int petals = 8;
        float orbit = 17 * scale, radius = 13 * scale, stroke = 2.4f * scale;
        for (int i = 0; i < petals; i++) {
            double a = Math.PI * 2 * i / petals + Math.PI / 8;
            Render2D.circle(g, (float) Math.cos(a) * orbit, (float) Math.sin(a) * orbit, radius, c(LOGO));
        }
        for (int i = 0; i < petals; i++) {
            double a = Math.PI * 2 * i / petals + Math.PI / 8;
            Render2D.circle(g, (float) Math.cos(a) * orbit, (float) Math.sin(a) * orbit, radius - stroke, c(MAIN));
        }
        Render2D.circle(g, 0, 0, orbit, c(MAIN));
        Render2D.line(g, -11 * scale, -7 * scale, -4 * scale, 0, stroke, c(LOGO));
        Render2D.line(g, -4 * scale, 0, -11 * scale, 7 * scale, stroke, c(LOGO));
        Render2D.line(g, 0, 8 * scale, 11 * scale, 8 * scale, stroke, c(LOGO));
    }

    // ---- module cards -------------------------------------------------------------------------

    protected void drawCards(GuiGraphicsExtractor g, float x, float w) {
        List<Module> modules = visibleModules();
        int columns = w >= 780 ? 3 : 2;
        float cardW = (w - CARD_GAP * (columns - 1)) / columns;
        int rows = (modules.size() + columns - 1) / columns;
        float total = rows * (CARD_H + CARD_GAP) - CARD_GAP;
        float area = CARDS_BOTTOM - CARDS_TOP;
        maxScroll = Math.max(0, total - area);
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));

        if (modules.isEmpty()) {
            String empty = place.view() == View.RECENT && !isSearching() ? "还没有在这里切换过模块" : "没有匹配的模块";
            UiFont.BODY.drawCentered(g, empty, x + w / 2, CARDS_TOP + 40, c(TEXT_MUTED));
            return;
        }

        pushClip(g, x - 8, CARDS_TOP - 8, w + 16, area + 16);
        long since = System.currentTimeMillis() - placeChangedAt;
        for (int i = 0; i < modules.size(); i++) {
            int row = i / columns, column = i % columns;
            float cardX = x + column * (cardW + CARD_GAP);
            float cardY = CARDS_TOP + row * (CARD_H + CARD_GAP) - scroll;
            if (cardY > CARDS_BOTTOM + 8 || cardY + CARD_H < CARDS_TOP - 8) continue;
            // Cards rise in one after another whenever the view changes.
            float appear = ease(Math.max(0, Math.min(1, (since - i * 45) / 260f)));
            drawCard(g, modules.get(i), cardX, cardY + (1 - appear) * 14, cardW, appear);
        }
        popClip(g);

        if (maxScroll > 0) {
            float barH = Math.max(30, area * area / (total));
            float barY = CARDS_TOP + (area - barH) * (scroll / maxScroll);
            Render2D.roundedRect(g, x + w + 14, barY, 4, barH, 2, c(0x40FFFFFF));
        }
    }

    protected void drawCard(GuiGraphicsExtractor g, Module module, float x, float y, float w, float appear) {
        boolean hovered = hover(x, y, w, CARD_H);
        float t = anim("card", module, hovered || selected == module ? 1 : 0, 16);
        float lift = 2 * t;
        y -= lift;
        Render2D.shadow(g, x, y, w, CARD_H, 12, 6, 2, c(Render2D.fade(0x30000000, appear)));
        Render2D.shadow(g, x, y, w, CARD_H, 12, 26, 10, c(Render2D.fade(0x60000000, t * appear)));
        Render2D.roundedRect(g, x, y, w, CARD_H, 12, c(Render2D.fade(Render2D.mix(CARD, CARD_HOVER, t), appear)));
        Render2D.roundedOutline(g, x, y, w, CARD_H, 12, 1, c(Render2D.fade(selected == module ? TAB_BORDER
                : Render2D.mix(CARD_BORDER, CARD_BORDER_HOVER, t), appear)));

        UiFont.ICON_M.icon(g, CodexTheme.icon(module.getCategory()), x + 26, y + 26, c(Render2D.fade(ICON, appear)));
        String key = module.getKey() > 0 ? CommandBind.keyName(module.getKey()) : "";
        float keyW = key.isEmpty() ? 0 : UiFont.SMALL.width(key) + 14;
        UiFont.SEMIBOLD.drawMiddle(g, UiFont.SEMIBOLD.ellipsize(module.getName(), w - 80 - keyW), x + 46, y + 26,
                c(Render2D.fade(TEXT, appear)));
        if (!key.isEmpty()) {
            Render2D.roundedRect(g, x + w - 14 - keyW, y + 15, keyW, 22, 6, c(Render2D.fade(TAB, appear)));
            UiFont.SMALL.drawCentered(g, key, x + w - 14 - keyW / 2, y + 26, c(Render2D.fade(TEXT_MUTED, appear)));
        }
        UiFont.SMALL.drawMiddle(g, UiFont.SMALL.ellipsize(module.getDescription(), w - 36), x + 18, y + 55,
                c(Render2D.fade(TEXT_MUTED, appear)));

        hit(x, y, w, CARD_H, () -> select(selected == module ? null : module), () -> select(module));
        if (module.isMomentaryAction()) {
            UiFont.SMALL.drawMiddle(g, "按键打开", x + 18, y + 82, c(Render2D.fade(TEXT_MUTED, appear)));
        } else {
            boolean on = module.isEnabled();
            Render2D.circle(g, x + 22, y + 82, 3.5f, c(Render2D.fade(on ? 0xFF4ADE80 : 0xFF5E6068, appear)));
            UiFont.SMALL.drawMiddle(g, on ? "已启用" : "未启用", x + 32, y + 82,
                    c(Render2D.fade(on ? TEXT_SIDEBAR : TEXT_MUTED, appear)));
            drawSwitch(g, x + w - 18 - 36, y + 72, on, module, appear, () -> toggleModule(module));
        }
    }

    /** A Codex-style switch: white track and dark knob when on, grey when off. */
    protected void drawSwitch(GuiGraphicsExtractor g, float x, float y, boolean on, Object key, float fade, Runnable action) {
        float t = ease(anim("switch", key, on ? 1 : 0, 16));
        boolean hovered = hover(x - 2, y - 2, 40, 24);
        Render2D.roundedRect(g, x, y, 36, 20, 10, c(Render2D.fade(Render2D.mix(TRACK, SEND, t), fade)));
        float knobX = x + 10 + 16 * t;
        Render2D.circle(g, knobX, y + 10, hovered ? 8 : 7.5f, c(Render2D.fade(Render2D.mix(0xFFD8D8DC, MAIN, t), fade)));
        hit(x - 2, y - 2, 40, 24, action, null);
    }

    /** Wraps display text: strips control glyphs that render as boxes, keeps source line breaks. */
    protected static List<String> wrapRich(UiFont font, String text, float maxWidth) {
        String clean = text.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]", "");
        List<String> out = new ArrayList<>();
        for (String raw : clean.split("\n", -1)) {
            if (raw.isBlank()) {
                out.add("");
            } else {
                out.addAll(wrap(font, raw, maxWidth, Integer.MAX_VALUE));
            }
        }
        return out;
    }

    // ---- codex chat ---------------------------------------------------------------------------

    /** The conversation stream shown while the chat view is open. */
    protected void drawChat(GuiGraphicsExtractor g, float x, float w, float top, float bottom) {
        List<CodexChat.Msg> chat = CodexChat.messages();
        if (chat.isEmpty()) {
            maxScroll = 0;
            if (CodexChat.state() == CodexChat.State.NO_ACCOUNT) {
                float cy = (top + bottom) / 2;
                UiFont.TITLE.drawCentered(g, "连接你的 AI", x + w / 2, cy - 46, c(TEXT));
                UiFont.BODY.drawCentered(g, "登录任意兼容 API，或导入 Codex 账号后开始对话", x + w / 2, cy - 10,
                        c(TEXT_MUTED));
                UiFont.SMALL.drawCentered(g, "支持 OpenAI Chat / Responses、Anthropic、Gemini 与兼容中转",
                        x + w / 2, cy + 16, c(TEXT_MUTED));
                drawImportButton(g, x + w / 2, cy + 58);
            } else {
                UiFont.BODY.drawCentered(g, "在下方输入，开始对话", x + w / 2, (top + bottom) / 2,
                        c(TEXT_MUTED));
            }
            return;
        }
        if (CodexChat.state() == CodexChat.State.NO_ACCOUNT) {
            drawAccountBanner(g, x, w, top);
            top += 52; // the stream starts below the permanent no-account notice
        }
        float textW = Math.min(620, w - 48);
        List<List<String>> lines = new ArrayList<>();
        List<Float> heights = new ArrayList<>();
        float total = 0;
        for (int i = 0; i < chat.size(); i++) {
            CodexChat.Msg message = chat.get(i);
            List<String> wrapped = List.of("");
            float height;
            if (message.role() == CodexChat.Msg.Role.THINKING) {
                // Collapsed by default like the Codex app; expanded shows the summary in grey.
                if (expandedThinking.contains(i)) {
                    wrapped = wrapRich(UiFont.SMALL, message.text(), textW - 40);
                    if (wrapped.size() > 60) {
                        wrapped = new ArrayList<>(wrapped.subList(0, 60));
                        wrapped.add("…（已截断）");
                    }
                }
                height = 30 + (expandedThinking.contains(i) ? wrapped.size() * 16 + 8 : 0);
            } else if (message.role() == CodexChat.Msg.Role.USER) {
                wrapped = message.text().isEmpty() ? List.of("") : wrapRich(UiFont.BODY, message.text(), textW - 36);
                height = wrapped.size() * 24 + 24;
            } else if (message.role() == CodexChat.Msg.Role.CALL) {
                List<String> plan = planSteps(message);
                if (plan != null) {
                    height = 36 + Math.min(plan.size(), 8) * 22 + 10;
                } else {
                    wrapped = wrapRich(UiFont.SMALL, message.text(), textW - 36);
                    height = wrapped.size() * 18 + 12;
                }
            } else {
                wrapped = message.text().isEmpty() ? List.of("") : wrapRich(UiFont.BODY, message.text(), textW - 36);
                height = wrapped.size() * 24 + 4;
            }
            lines.add(wrapped);
            heights.add(height);
            total += height + 18;
        }
        total = Math.max(0, total - 18);
        maxScroll = Math.max(0, total - (bottom - top));
        if (chatFollow) targetScroll = maxScroll;
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));

        pushClip(g, x - 10, top, w + 20, bottom - top);
        float y = top - scroll;
        for (int i = 0; i < chat.size(); i++) {
            float height = heights.get(i);
            if (y + height >= top - 24 && y <= bottom + 24) {
                drawChatMessage(g, chat.get(i), lines.get(i), x, w, y, textW, i, i == chat.size() - 1);
            }
            y += height + 18;
        }
        popClip(g);

        if (maxScroll > 0) {
            float area = bottom - top;
            float barH = Math.max(30, area * area / total);
            float barY = top + (area - barH) * (scroll / maxScroll);
            Render2D.roundedRect(g, x + w + 14, barY, 4, barH, 2, c(0x40FFFFFF));
        }
    }

    /** Centered login actions of the empty chat view while no provider is configured. */
    protected void drawImportButton(GuiGraphicsExtractor g, float cx, float cy) {
        float bw = 176, bh = 44, gap = 12;
        float apiX = cx - bw - gap / 2, codexX = cx + gap / 2;
        drawLoginButton(g, apiX, cy - bh / 2, bw, bh, "API 登录", this::openApiSettings);
        drawLoginButton(g, codexX, cy - bh / 2, bw, bh, "导入 Codex", () -> accountOpen = true);
    }

    private void drawLoginButton(GuiGraphicsExtractor g, float bx, float by, float bw, float bh,
                                 String label, Runnable action) {
        boolean btnHover = hover(bx, by, bw, bh);
        float t = anim("emptyLogin" + label, btnHover ? 1 : 0, 16);
        Render2D.roundedRect(g, bx, by, bw, bh, 12, c(Render2D.mix(TAB, ITEM_ACTIVE, t)));
        Render2D.roundedOutline(g, bx, by, bw, bh, 12, 1, c(Render2D.mix(CARD_BORDER, CARD_BORDER_HOVER, t)));
        UiFont.SEMIBOLD.drawCentered(g, label, bx + bw / 2, by + bh / 2, c(TEXT));
        hit(bx, by, bw, bh, action, null);
    }

    protected void openApiSettings() {
        minecraft.gui.setScreen(new ApiSettingsScreen(this));
    }

    /** Slim notice above the chat stream while a provider is not configured. */
    protected void drawAccountBanner(GuiGraphicsExtractor g, float x, float w, float by) {
        float bw = Math.min(620, w - 48), bx = x + (w - bw) / 2;
        Render2D.roundedRect(g, bx, by, bw, 40, 10, c(CARD));
        Render2D.roundedOutline(g, bx, by, bw, 40, 10, 1, c(CARD_BORDER));
        UiFont.ICON_S.icon(g, Icons.CIRCLE_ALERT, bx + 22, by + 20, c(ORANGE));
        UiFont.SEMIBOLD.drawMiddle(g, "尚未配置 AI，请登录 API 或导入 Codex 账号", bx + 40, by + 20, c(TEXT));
        float pw = 92, px = bx + bw - pw - 14, py = by + 8;
        boolean btnHover = hover(px, py, pw, 24);
        float t = anim("bannerImport", btnHover ? 1 : 0, 14);
        Render2D.roundedRect(g, px, py, pw, 24, 12, c(Render2D.mix(TAB, ITEM_ACTIVE, t)));
        UiFont.SMALL.drawCentered(g, "配置", px + pw / 2, py + 12, c(TEXT));
        hit(px, py, pw, 24, this::openApiSettings, null);
    }

    /** User messages sit in a right-aligned bubble; assistant replies are plain text by the blossom. */
    protected void drawChatMessage(GuiGraphicsExtractor g, CodexChat.Msg message, List<String> lines,
                                 float x, float w, float y, float textW, int index, boolean last) {
        if (message.role() == CodexChat.Msg.Role.CALL) {
            List<String> plan = planSteps(message);
            if (plan != null) {
                drawPlanCard(g, plan, x + 18, y);
                return;
            }
            UiFont.ICON_S.icon(g, Icons.TERMINAL, x + 18, y + 14, c(ICON));
            for (int i = 0; i < lines.size(); i++) {
                UiFont.SMALL.drawMiddle(g, lines.get(i), x + 38, y + 14 + i * 18,
                        c(message.error() ? 0xFFFF6F61 : TEXT_MUTED));
            }
            return;
        }
        if (message.role() == CodexChat.Msg.Role.THINKING) {
            boolean streaming = message.meta() == null;
            boolean expanded = expandedThinking.contains(index);
            float pulse = 0.45f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 240.0);
            String label = streaming ? "思考中…"
                    : "已思考 " + message.meta() + " 秒";
            UiFont.SEMIBOLD.drawMiddle(g, label, x + 22, y + 13,
                    streaming ? c(Render2D.fade(TEXT_MUTED, pulse)) : c(TEXT_MUTED));
            UiFont.ICON_S.icon(g, expanded ? Icons.CHEVRON_DOWN : Icons.CHEVRON_RIGHT, x + 8, y + 13, c(ICON));
            hit(x + 2, y - 2, 240, 28, () -> {
                if (expandedThinking.contains(index)) expandedThinking.remove(index);
                else expandedThinking.add(index);
            }, null);
            for (int i = 0; i < lines.size(); i++) {
                UiFont.SMALL.drawMiddle(g, lines.get(i), x + 22, y + 30 + i * 16, c(TEXT_MUTED));
            }
            return;
        }
        if (message.role() == CodexChat.Msg.Role.USER) {
            float width = 0;
            for (String line : lines) width = Math.max(width, UiFont.BODY.width(line));
            float bubbleW = Math.min(textW, width + 36);
            float height = lines.size() * 24 + 24;
            float bubbleX = x + w - bubbleW;
            Render2D.shadow(g, bubbleX, y, bubbleW, height, 16, 18, 6, c(0x30000000));
            Render2D.roundedRect(g, bubbleX, y, bubbleW, height, 16, c(CARD_HOVER));
            Render2D.roundedOutline(g, bubbleX, y, bubbleW, height, 16, 1, c(CARD_BORDER));
            for (int i = 0; i < lines.size(); i++) {
                UiFont.BODY.drawMiddle(g, lines.get(i), bubbleX + 18, y + 24 + i * 24, c(TEXT));
            }
            return;
        }
        boolean thinking = last && CodexChat.thinkingHere()
                && message.role() == CodexChat.Msg.Role.ASSISTANT;
        float textX = x + 2;
        if (message.text().isEmpty()) {
            float pulse = 0.45f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 240.0);
            UiFont.BODY.drawMiddle(g, "思考中…", textX, y + 14, c(Render2D.fade(TEXT_MUTED, pulse)));
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            UiFont.BODY.drawMiddle(g, lines.get(i), textX, y + 14 + i * 24,
                    c(message.error() ? 0xFFFF6F61 : TEXT));
        }
        if (thinking) {
            float caretX = textX + UiFont.BODY.width(lines.getLast()) + 4;
            float caretY = y + 14 + (lines.size() - 1) * 24;
            float blink = 0.5f + 0.5f * (float) Math.cos(System.currentTimeMillis() / 530.0);
            Render2D.rect(g, caretX, caretY - 9, 1.5f, 19, c(Render2D.fade(TEXT, blink)));
        }
    }

    /** Step labels of an update_plan call ("status|step" entries), or null for other calls. */
    protected static List<String> planSteps(CodexChat.Msg message) {
        if (message.meta() == null || !message.text().startsWith("update_plan(")) return null;
        try {
            JsonObject args = JsonParser.parseString(message.meta()).getAsJsonObject();
            if (!args.has("plan") || !args.get("plan").isJsonArray()) return null;
            List<String> out = new ArrayList<>();
            for (var element : args.getAsJsonArray("plan")) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                String status = item.has("status") && item.get("status").isJsonPrimitive()
                        ? item.get("status").getAsString() : "pending";
                String step = item.has("step") && item.get("step").isJsonPrimitive()
                        ? item.get("step").getAsString() : "";
                if (!step.isBlank()) out.add(status + "|" + step);
            }
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Codex-style plan card: one check/arrow/circle row per step. */
    protected void drawPlanCard(GuiGraphicsExtractor g, List<String> plan, float x, float y) {
        float cardW = Math.min(560, PANEL_RIGHT - x - 24);
        float cardH = 36 + Math.min(plan.size(), 8) * 22 + 10;
        Render2D.roundedRect(g, x, y, cardW, cardH, 12, c(CARD));
        Render2D.roundedOutline(g, x, y, cardW, cardH, 12, 1, c(CARD_BORDER));
        UiFont.SEMIBOLD.drawMiddle(g, "计划", x + 36, y + 18, c(TEXT));
        UiFont.ICON_S.icon(g, Icons.SLIDERS, x + 16, y + 18, c(ICON));
        float rowY = y + 36;
        int shown = 0;
        for (String entry : plan) {
            if (shown++ >= 8) break;
            int sep = entry.indexOf('|');
            String status = entry.substring(0, sep);
            String step = entry.substring(sep + 1);
            boolean done = "completed".equals(status);
            boolean active = "in_progress".equals(status);
            UiFont.ICON_S.icon(g, done ? Icons.CIRCLE_CHECK : active ? Icons.CHEVRON_RIGHT : Icons.SQUARE,
                    x + 18, rowY + 9, c(done ? 0xFF4ADE80 : active ? ORANGE : ICON));
            UiFont.SMALL.drawMiddle(g, step, x + 40, rowY + 9, c(done ? TEXT_MUTED : TEXT_SIDEBAR));
            rowY += 22;
        }
    }

    // ---- settings pane ------------------------------------------------------------------------

    protected Module paneModule;

    protected void drawPane(GuiGraphicsExtractor g, float x, float open) {
        if (selected != null) paneModule = selected;
        Module module = paneModule;
        if (module == null) return;
        float w = PANEL_RIGHT - x;

        Render2D.shadow(g, x, 120, w, PANEL_BOTTOM - 120, 0, 40, 0, c(Render2D.fade(0x70000000, open)));
        pushClip(g, x, 120, w, PANEL_BOTTOM - 120);
        Render2D.roundedRect(g, x, 120, w, PANEL_BOTTOM - 120, 14, c(SIDEBAR));
        Render2D.rect(g, x, 120, w, 20, c(SIDEBAR));
        Render2D.rect(g, x, 120, 20, PANEL_BOTTOM - 120, c(SIDEBAR));
        Render2D.rect(g, x, 120, 1, PANEL_BOTTOM - 120, c(SIDEBAR_DIVIDER));

        float px = x + 24, pw = PANE_W - 48;
        float fade = open;
        // Header: icon tile, name, close.
        Render2D.roundedRect(g, px, 146, 40, 40, 10, c(Render2D.fade(TAB, fade)));
        UiFont.ICON_L.icon(g, CodexTheme.icon(module.getCategory()), px + 20, 166, c(Render2D.fade(TEXT, fade)));
        UiFont.TITLE.drawMiddle(g, module.getName(), px + 54, 155, c(Render2D.fade(TEXT, fade)));
        UiFont.SMALL.drawMiddle(g, module.getCategory().getDisplayName() + " · " + module.getCategory().getName(),
                px + 55, 180, c(Render2D.fade(TEXT_MUTED, fade)));
        iconButton(g, Icons.X, x + w - 34, 166, 32, ICON, () -> selected = null);

        float y = 206;
        for (String line : wrap(UiFont.SMALL, module.getDescription(), pw, 3)) {
            UiFont.SMALL.drawMiddle(g, line, px, y + 8, c(Render2D.fade(TEXT_SIDEBAR, fade)));
            y += 20;
        }

        // Enable row.
        y += 14;
        Render2D.roundedRect(g, px, y, pw, 52, 12, c(Render2D.fade(CARD, fade)));
        Render2D.roundedOutline(g, px, y, pw, 52, 12, 1, c(Render2D.fade(CARD_BORDER, fade)));
        UiFont.SEMIBOLD.drawMiddle(g, module.isMomentaryAction() ? "打开" : "启用模块", px + 16, y + 26, c(Render2D.fade(TEXT, fade)));
        if (module.isMomentaryAction()) {
            float bx = px + pw - 16 - 60;
            float t = anim("paneOpen", hover(bx, y + 12, 60, 28) ? 1 : 0, 18);
            Render2D.roundedRect(g, bx, y + 12, 60, 28, 14, c(Render2D.fade(Render2D.mix(TAB, SEND, t), fade)));
            UiFont.SMALL.drawCentered(g, "运行", bx + 30, y + 26, c(Render2D.fade(Render2D.mix(TEXT, SEND_GLYPH, t), fade)));
            hit(bx, y + 12, 60, 28, () -> toggleModule(module), null);
        } else {
            drawSwitch(g, px + pw - 16 - 36, y + 16, module.isEnabled(), module, fade, () -> toggleModule(module));
        }
        y += 72;

        // Settings.
        List<Value<?>> values = module.getValues().stream().filter(Value::isAvailable).toList();
        UiFont.SMALL.drawMiddle(g, "设置", px + 2, y, c(Render2D.fade(TEXT_SECTION, fade)));
        y += 16;
        float blockH = 8;
        for (Value<?> value : values) blockH += rowHeight(value);
        blockH += 52;
        Render2D.roundedRect(g, px, y, pw, blockH, 12, c(Render2D.fade(CARD, fade)));
        Render2D.roundedOutline(g, px, y, pw, blockH, 12, 1, c(Render2D.fade(CARD_BORDER, fade)));
        float rowY = y + 4;
        for (Value<?> value : values) {
            drawValueRow(g, value, px + 16, rowY, pw - 32, fade);
            rowY += rowHeight(value);
            Render2D.rect(g, px + 16, rowY - 0.5f, pw - 32, 1, c(Render2D.fade(0xFF34363F, fade)));
        }
        drawBindRow(g, module, px + 16, rowY, pw - 32, fade);
        popClip(g);
    }

    protected float rowHeight(Value<?> value) {
        return value instanceof NumberValue ? 64 : 48;
    }

    protected void drawValueRow(GuiGraphicsExtractor g, Value<?> value, float x, float y, float w, float fade) {
        if (value instanceof BoolValue bool) {
            UiFont.BODY.drawMiddle(g, value.getName(), x, y + 24, c(Render2D.fade(TEXT_SIDEBAR, fade)));
            drawSwitch(g, x + w - 36, y + 14, bool.get(), bool, fade, () -> {
                bool.toggle();
                Client.instance.getConfigManager().saveAllConfig();
            });
        } else if (value instanceof NumberValue number) {
            UiFont.BODY.drawMiddle(g, value.getName(), x, y + 22, c(Render2D.fade(TEXT_SIDEBAR, fade)));
            UiFont.SMALL.drawRight(g, number.getDisplayValue(), x + w, y + 22, c(Render2D.fade(TEXT, fade)));
            float fraction = (float) ((number.get() - number.getMin()) / (number.getMax() - number.getMin()));
            float shown = anim("slider", number, fraction, 22);
            float trackY = y + 44;
            boolean active = dragging == number || hover(x - 6, trackY - 10, w + 12, 20);
            float grow = anim("sliderHover", number, active ? 1 : 0, 18);
            Render2D.roundedRect(g, x, trackY - 2, w, 4, 2, c(Render2D.fade(TRACK, fade)));
            Render2D.roundedRect(g, x, trackY - 2, Math.max(4, w * shown), 4, 2, c(Render2D.fade(SEND, fade)));
            Render2D.circle(g, x + w * shown, trackY, 6 + 2 * grow, c(Render2D.fade(Render2D.withAlpha(SEND, 0x30), grow * fade)));
            Render2D.circle(g, x + w * shown, trackY, 6, c(Render2D.fade(SEND, fade)));
            hit(x - 8, trackY - 12, w + 16, 24, () -> {
                dragging = number;
                dragX = x;
                dragW = w;
                dragValue(number);
            }, null);
        } else if (value instanceof ModeValue mode) {
            UiFont.BODY.drawMiddle(g, value.getName(), x, y + 24, c(Render2D.fade(TEXT_SIDEBAR, fade)));
            String[] modes = mode.getModes();
            float[] widths = new float[modes.length];
            float total = 6;
            for (int i = 0; i < modes.length; i++) {
                widths[i] = UiFont.SMALL.width(modes[i]) + 22;
                total += widths[i];
            }
            float sx = x + w - total, sy = y + 9;
            Render2D.roundedRect(g, sx, sy, total, 30, 9, c(Render2D.fade(CHROME, fade)));
            // The selected pill slides between options.
            float ox = sx + 3, selectedX = ox, selectedW = widths[0];
            for (int i = 0; i < modes.length; i++) {
                if (mode.is(modes[i])) {
                    selectedX = ox;
                    selectedW = widths[i];
                }
                ox += widths[i];
            }
            float pillX = anim("modeX", mode, selectedX, 18), pillW = anim("modeW", mode, selectedW, 18);
            Render2D.roundedRect(g, pillX, sy + 3, pillW, 24, 7, c(Render2D.fade(TAB, fade)));
            Render2D.roundedOutline(g, pillX, sy + 3, pillW, 24, 7, 1, c(Render2D.fade(TAB_BORDER, fade)));
            ox = sx + 3;
            for (int i = 0; i < modes.length; i++) {
                boolean on = mode.is(modes[i]);
                UiFont.SMALL.drawCentered(g, modes[i], ox + widths[i] / 2, sy + 15,
                        c(Render2D.fade(on ? TEXT : TEXT_MUTED, fade)));
                final String target = modes[i];
                hit(ox, sy, widths[i], 30, () -> {
                    mode.set(target);
                    Client.instance.getConfigManager().saveAllConfig();
                }, null);
                ox += widths[i];
            }
        }
    }

    protected void drawBindRow(GuiGraphicsExtractor g, Module module, float x, float y, float w, float fade) {
        UiFont.ICON_S.icon(g, Icons.KEYBOARD, x + 8, y + 24, c(Render2D.fade(ICON, fade)));
        UiFont.BODY.drawMiddle(g, "按键绑定", x + 26, y + 24, c(Render2D.fade(TEXT_SIDEBAR, fade)));
        boolean listening = binding == module;
        String text = listening ? "按下任意键 · Esc 清除" : CommandBind.keyName(module.getKey());
        float pw = UiFont.SMALL.width(text) + 24, px = x + w - pw;
        float pulse = listening ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 160.0) : 0;
        float t = anim("bind", module, listening || hover(px, y + 10, pw, 28) ? 1 : 0, 18);
        Render2D.roundedRect(g, px, y + 10, pw, 28, 8, c(Render2D.fade(Render2D.mix(CHROME, TAB, t), fade)));
        Render2D.roundedOutline(g, px, y + 10, pw, 28, 8, 1,
                c(Render2D.fade(Render2D.mix(TAB_BORDER, SEND, pulse), fade)));
        UiFont.SMALL.drawCentered(g, text, px + pw / 2, y + 24, c(Render2D.fade(listening ? TEXT : TEXT_SIDEBAR, fade)));
        hit(px, y + 10, pw, 28, () -> binding = listening ? null : module, () -> {
            module.setKey(0);
            binding = null;
            Client.instance.getConfigManager().saveAllConfig();
        });
    }

    // ---- composer -----------------------------------------------------------------------------

    protected void drawComposer(GuiGraphicsExtractor g, float x, float y, float w, float h) {
        // Context chips on a bar tucked behind the composer's top edge.
        float barX = x + 17, barW = w - 34;
        Render2D.roundedRect(g, barX, 920, barW, 64, 12, c(CHIP_BAR));
        UiFont.ICON_S.icon(g, Icons.FOLDER, x + 43, 944, c(0xFFE0E0DC));
        UiFont.BODY.drawMiddle(g, Client.name, x + 60, 944, c(TEXT));
        float chip2 = x + 60 + UiFont.BODY.width(Client.name) + 32;
        UiFont.ICON_S.icon(g, Icons.LAPTOP, chip2, 944, c(0xFFE0E0DC));
        UiFont.SEMIBOLD.drawMiddle(g, "Minecraft " + Client.ver, chip2 + 18, 944, c(TEXT));
        iconButton(g, Icons.SETTINGS, x + w - 39, 944, 26, ICON, this::openApiSettings);

        boolean focused = !input.isEmpty() || binding == null;
        float typing = anim("typing", System.currentTimeMillis() - typedAt < 600 ? 1 : 0, 10);
        Render2D.shadow(g, x, y, w, h, 20, 32, 12, c(0x60000000));
        Render2D.roundedRect(g, x, y, w, h, 20, c(COMPOSER));
        Render2D.roundedOutline(g, x, y, w, h, 20, 1, c(Render2D.mix(COMPOSER_BORDER, COMPOSER_FOCUS, typing)));

        // Input line with a softly blinking caret.
        float textX = x + 16, textY = 998;
        float placeholder = anim("placeholder", input.isEmpty() ? 1 : 0, 20);
        if (placeholder > 0.01f) {
            String hint = CodexChat.state() == CodexChat.State.NO_ACCOUNT
                    ? "先点击左下角头像登录 API 或导入 Codex" : "发送消息…";
            UiFont.SEMIBOLD.drawMiddle(g, hint, textX + (1 - placeholder) * 6, textY,
                    c(Render2D.fade(PLACEHOLDER, placeholder)));
        }
        String shown = fitLine(input, preedit, w - 40, UiFont.BODY);
        UiFont.BODY.drawMiddle(g, shown, textX, textY, c(TEXT));
        float afterInput = textX + UiFont.BODY.width(shown);
        if (!preedit.isEmpty()) {
            // The pinyin being composed, underlined like the vanilla chat draws it.
            UiFont.BODY.drawMiddle(g, preedit, afterInput + 2, textY, c(0xBFF8F8F2));
            Render2D.rect(g, afterInput + 2, textY + 10, UiFont.BODY.width(preedit), 1, c(0x66F8F8F2));
        }
        if (focused && binding == null) {
            float caretX = afterInput + (preedit.isEmpty() ? 1 : UiFont.BODY.width(preedit) + 4);
            double phase = (System.currentTimeMillis() - Math.max(openedAt, typedAt)) / 1060.0 * Math.PI * 2;
            float blink = System.currentTimeMillis() - typedAt < 500 ? 1 : (float) (0.5 + 0.5 * Math.cos(phase));
            Render2D.rect(g, caretX, textY - 9, 1.5f, 19, c(Render2D.fade(TEXT, blink)));
        }
        // Point the IME candidate window at the input line (GUI-space coordinates).
        float guiScale = minecraft.getWindow().getGuiScale();
        minecraft.textInputManager().setTextInputArea(
                Math.round((originX + textX * s) / guiScale), Math.round((originY + (textY - 14) * s) / guiScale),
                Math.round(w * s / guiScale), Math.round(28 * s / guiScale));

        // Bottom row: add, status, model and send.
        float rowY = 1062;
        iconButton(g, Icons.PLUS, x + 28, rowY, 30, TEXT_SIDEBAR, () -> {
            input = CommandManager.PREFIX;
            typedAt = System.currentTimeMillis();
            updateCompletions();
        });
        UiFont.ICON_S.icon(g, Icons.CIRCLE_ALERT, x + 69, rowY + 1, c(ORANGE));
        UiFont.SEMIBOLD.drawMiddle(g, "已启用 " + enabledCount() + " 个模块", x + 85, rowY, c(ORANGE));

        // Model and effort; the button opens the reasoning slider card above the composer.
        drawEffortTrigger(g, x + w - 94, rowY, UiFont.SEMIBOLD, UiFont.ICON_S, 10, 32, 8, TEXT, ICON, ITEM_ACTIVE);
        effortAnchorRight = x + w;
        effortAnchorBottom = rowY - 22;
        UiFont.ICON_M.icon(g, Icons.MIC, x + w - 72, rowY, c(TEXT_SIDEBAR));

        float sendX = x + w - 28, sendY = rowY;
        boolean busy = CodexChat.thinkingHere();
        float hov = anim("send", hover(sendX - 18, sendY - 18, 36, 36) ? 1 : 0, 20);
        float busyT = anim("sendBusy", busy ? 1 : 0, 16);
        Render2D.circle(g, sendX, sendY, 17.5f + hov, c(Render2D.mix(SEND, CLOSE_HOVER, busyT)));
        if (busyT > 0.5f) {
            UiFont.ICON_M.icon(g, Icons.SQUARE, sendX, sendY, c(0xFFFFFFFF));
        } else {
            float arrow = anim("sendArrow", input.isBlank() ? 0 : 1, 16);
            UiFont.ICON_M.icon(g, Icons.AUDIO_LINES, sendX, sendY - arrow * 6, c(Render2D.fade(SEND_GLYPH, 1 - arrow)));
            UiFont.ICON_M.icon(g, Icons.ARROW_UP, sendX, sendY + (1 - arrow) * 6, c(Render2D.fade(SEND_GLYPH, arrow)));
        }
        hit(sendX - 18, sendY - 18, 36, 36, busy ? CodexChat::cancel : this::submit, null);
        drawCompletions(g, barX, 912, barW);
    }

    protected void drawCompletions(GuiGraphicsExtractor g, float x, float bottom, float w) {
        float shown = anim("completions", completions.isEmpty() ? 0 : 1, 18);
        if (shown < 0.01f || completions.isEmpty()) return;
        int count = Math.min(6, completions.size());
        float rowH = 34, h = count * rowH + 12, y = bottom - h + (1 - shown) * 10;
        g.nextStratum();
        Render2D.shadow(g, x, y, w, h, 12, 30, 12, c(Render2D.fade(0x90000000, shown)));
        Render2D.roundedRect(g, x, y, w, h, 12, c(Render2D.fade(POPUP, shown)));
        Render2D.roundedOutline(g, x, y, w, h, 12, 1, c(Render2D.fade(POPUP_BORDER, shown)));
        int first = Math.max(0, Math.min(completionIndex - count + 1, completions.size() - count));
        float highlight = anim("completionY", y + 6 + (completionIndex - first) * rowH, 24);
        Render2D.roundedRect(g, x + 6, highlight, w - 12, rowH, 8, c(Render2D.fade(ITEM_ACTIVE, shown)));
        for (int i = 0; i < count; i++) {
            int index = first + i;
            float ry = y + 6 + i * rowH;
            UiFont.ICON_S.icon(g, Icons.TERMINAL, x + 26, ry + rowH / 2, c(Render2D.fade(ICON, shown)));
            UiFont.BODY.drawMiddle(g, completions.get(index), x + 44, ry + rowH / 2,
                    c(Render2D.fade(index == completionIndex ? TEXT : TEXT_SIDEBAR, shown)));
            final int target = index;
            hit(x + 6, ry, w - 12, rowH, () -> {
                completionIndex = target;
                applyCompletion();
            }, null);
        }
    }

    // ---- overlays -----------------------------------------------------------------------------

    protected void drawToast(GuiGraphicsExtractor g, float cx) {
        String finished = CodexChat.pollFinishedNotice();
        if (finished != null) showToast(finished);
        long age = System.currentTimeMillis() - toastAt;
        if (toast.isEmpty() || age > 2800) return;
        float in = ease(Math.min(1, age / 220f));
        float out = age > 2400 ? 1 - (age - 2400) / 400f : 1;
        float t = in * out;
        float w = UiFont.BODY.width(toast) + 56, h = 38;
        float y = 136 - (1 - in) * 12;
        g.nextStratum();
        Render2D.shadow(g, cx - w / 2, y, w, h, 19, 24, 8, c(Render2D.fade(0x80000000, t)));
        Render2D.roundedRect(g, cx - w / 2, y, w, h, 19, c(Render2D.fade(POPUP, t)));
        Render2D.roundedOutline(g, cx - w / 2, y, w, h, 19, 1, c(Render2D.fade(POPUP_BORDER, t)));
        UiFont.ICON_S.icon(g, toastError ? Icons.CIRCLE_ALERT : Icons.CIRCLE_CHECK, cx - w / 2 + 22, y + h / 2,
                c(Render2D.fade(toastError ? 0xFFE5544B : 0xFF4ADE80, t)));
        UiFont.BODY.drawMiddle(g, toast, cx - w / 2 + 38, y + h / 2, c(Render2D.fade(TEXT, t)));
    }

    // ---- account popup ------------------------------------------------------------------------

    /** The full account panel: login by pasted JSON or path, status, usage and logout. */
    protected void drawAccountPopup(GuiGraphicsExtractor g) {
        float shown = anim("account", accountOpen ? 1 : 0, 20);
        if (shown < 0.01f) return;
        g.nextStratum();
        // The panel floats above every view; drop any clip the view behind it left behind,
        // otherwise hover()/hit() of the panel's controls get cropped by that stale region.
        clips.clear();
        clipX0 = -1e9f;
        clipY0 = -1e9f;
        clipX1 = 1e9f;
        clipY1 = 1e9f;
        Render2D.rect(g, 0, 0, W, H, c(Render2D.fade(0xA0000000, shown)));
        float x = accountPanelX, y = accountPanelY + (1 - shown) * 12, w = ACCOUNT_PANEL_W, h = ACCOUNT_PANEL_H;
        Render2D.shadow(g, x, y, w, h, 16, 40, 16, c(Render2D.fade(0xB0000000, shown)));
        Render2D.roundedRect(g, x, y, w, h, 16, c(Render2D.fade(POPUP, shown)));
        Render2D.roundedOutline(g, x, y, w, h, 16, 1, c(Render2D.fade(POPUP_BORDER, shown)));
        // Background hit first: hits resolve last-to-first, so anything registered later (the
        // header controls, the field, the buttons) still wins over this one.
        hit(x, y, w, h, () -> accountFieldFocused = false, null);
        UiFont.TITLE.drawMiddle(g, "账号与 API", x + 28, y + 42, c(Render2D.fade(TEXT, shown)));
        drawLoginButton(g, x + w - 250, y + 26, 110, 32, "API 登录", this::openApiSettings);
        iconButton(g, Icons.SETTINGS, x + w - 84, y + 42, 32, ICON, this::openApiSettings);
        iconButton(g, Icons.X, x + w - 42, y + 42, 32, ICON, () -> {
            accountOpen = false;
            accountFieldFocused = false;
        });
        Render2D.rect(g, x + 24, y + 68, w - 48, 1, c(Render2D.fade(HEADER_DIVIDER, shown)));

        CodexAccount acc = CodexChat.account();

        float rowY = y + 96;
        if (acc == null && CodexChat.genericConfigured()) {
            Render2D.circle(g, x + 34, rowY, 4, c(Render2D.fade(0xFF4ADE80, shown)));
            UiFont.SEMIBOLD.drawMiddle(g, "API 已连接", x + 48, rowY, c(Render2D.fade(TEXT, shown)));
            UiFont.SMALL.drawMiddle(g, CodexChat.providerDetail(), x + 48, rowY + 22,
                    c(Render2D.fade(TEXT_MUTED, shown)));

            float cardY = rowY + 52;
            Render2D.roundedRect(g, x + 28, cardY, w - 56, 108, 12, c(Render2D.fade(CARD, shown)));
            Render2D.roundedOutline(g, x + 28, cardY, w - 56, 108, 12, 1, c(Render2D.fade(CARD_BORDER, shown)));
            UiFont.SMALL.drawMiddle(g, "协议", x + 48, cardY + 28, c(Render2D.fade(TEXT_SECTION, shown)));
            UiFont.SEMIBOLD.drawMiddle(g, CodexChat.providerName(), x + 130, cardY + 28, c(Render2D.fade(TEXT, shown)));
            UiFont.SMALL.drawMiddle(g, "当前模型", x + 48, cardY + 58, c(Render2D.fade(TEXT_SECTION, shown)));
            UiFont.SEMIBOLD.drawMiddle(g, CodexChat.modelDisplay(), x + 130, cardY + 58, c(Render2D.fade(TEXT, shown)));
            UiFont.SMALL.drawMiddle(g, "模型列表由 API 提供，可在设置中重新获取", x + 48, cardY + 86,
                    c(Render2D.fade(TEXT_MUTED, shown)));

            float btnY = y + h - 74, btnH = 46;
            drawLoginButton(g, x + 28, btnY, 170, btnH, "API 设置", this::openApiSettings);
            drawLoginButton(g, x + 214, btnY, 150, btnH, "清空对话", this::clearChatAction);
            return;
        }
        if (acc == null) {
            Render2D.circle(g, x + 34, rowY, 4, c(Render2D.fade(0xFF5E6068, shown)));
            UiFont.SEMIBOLD.drawMiddle(g, "未配置 AI", x + 48, rowY, c(Render2D.fade(TEXT_SIDEBAR, shown)));

            // The login field: paste the JSON content itself or its full path, then 导入.
            float fieldX = x + 28, fieldY = rowY + 30, fieldW = w - 56, fieldH = 176;
            Render2D.roundedRect(g, fieldX, fieldY, fieldW, fieldH, 12,
                    c(Render2D.fade(accountFieldFocused ? CARD_HOVER : CARD, shown)));
            Render2D.roundedOutline(g, fieldX, fieldY, fieldW, fieldH, 12, 1,
                    c(Render2D.fade(accountFieldFocused ? COMPOSER_FOCUS : CARD_BORDER, shown)));
            if (accountInput != accountFieldCache) {
                accountFieldCache = accountInput;
                List<String> lines = accountInput.isEmpty() ? List.of()
                        : wrapRich(UiFont.BODY, accountInput, fieldW - 32);
                if (lines.size() > 7) {
                    lines = new ArrayList<>(lines.subList(0, 7));
                    lines.add("…（共 " + accountInput.length() + " 字符）");
                }
                accountFieldLines = lines;
            }
            if (accountInput.isEmpty()) {
                UiFont.BODY.drawMiddle(g, "把账号 JSON 内容直接粘贴到这里，或输入它的完整路径",
                        fieldX + 16, fieldY + 22, c(Render2D.fade(PLACEHOLDER, shown)));
                UiFont.SMALL.drawMiddle(g, "支持官方 auth.json 与 sub2api 导出格式",
                        fieldX + 16, fieldY + 46, c(Render2D.fade(PLACEHOLDER, shown)));
            } else {
                for (int i = 0; i < accountFieldLines.size(); i++) {
                    UiFont.BODY.draw(g, accountFieldLines.get(i), fieldX + 16,
                            fieldY + 12 + i * 20, c(TEXT));
                }
                if (accountFieldFocused) {
                    String last = accountFieldLines.isEmpty() ? "" : accountFieldLines.getLast();
                    float caretX = fieldX + 16 + UiFont.BODY.width(last);
                    int lastLine = Math.max(0, accountFieldLines.size() - 1);
                    double phase = (System.currentTimeMillis() % 2120) / 2120.0 * Math.PI * 2;
                    float blink = (float) (0.5 + 0.5 * Math.cos(phase));
                    Render2D.rect(g, caretX + 1, fieldY + 12 + lastLine * 20, 1.5f, 18,
                            c(Render2D.fade(TEXT, blink)));
                }
            }
            hit(fieldX, fieldY, fieldW, fieldH, () -> accountFieldFocused = true, null);

            float btnY = fieldY + fieldH + 20, btnH = 46;
            float pasteW = 150, importW = 130, clearW = 130;
            boolean pasteHover = hover(fieldX, btnY, pasteW, btnH);
            float pasteT = anim("accountPaste", pasteHover ? 1 : 0, 14);
            Render2D.roundedRect(g, fieldX, btnY, pasteW, btnH, 10,
                    c(Render2D.fade(Render2D.mix(BUTTON, CARD_HOVER, pasteT), shown)));
            Render2D.roundedOutline(g, fieldX, btnY, pasteW, btnH, 10, 1,
                    c(Render2D.fade(Render2D.mix(POPUP_BORDER, TEXT_SIDEBAR, pasteT), shown)));
            UiFont.SEMIBOLD.drawCentered(g, "从剪贴板粘贴", fieldX + pasteW / 2, btnY + btnH / 2,
                    c(Render2D.fade(TEXT, shown)));
            hit(fieldX, btnY, pasteW, btnH, this::pasteClipboard, null);

            float importX = fieldX + pasteW + 14;
            boolean importHover = hover(importX, btnY, importW, btnH);
            float importT = anim("accountImport", importHover ? 1 : 0, 14);
            Render2D.roundedRect(g, importX, btnY, importW, btnH, 10,
                    c(Render2D.fade(Render2D.mix(BUTTON, 0xFF4ADE80, importT), shown)));
            UiFont.SEMIBOLD.drawCentered(g, "导入", importX + importW / 2, btnY + btnH / 2,
                    c(Render2D.fade(TEXT, shown)));
            hit(importX, btnY, importW, btnH, () -> importAccountText(accountInput), null);

            float clearX = importX + importW + 14;
            boolean clearHover = hover(clearX, btnY, clearW, btnH);
            float clearT = anim("accountClear", clearHover ? 1 : 0, 14);
            Render2D.roundedRect(g, clearX, btnY, clearW, btnH, 10,
                    c(Render2D.fade(Render2D.mix(BUTTON, CARD_HOVER, clearT), shown)));
            Render2D.roundedOutline(g, clearX, btnY, clearW, btnH, 10, 1,
                    c(Render2D.fade(Render2D.mix(POPUP_BORDER, TEXT_SIDEBAR, clearT), shown)));
            UiFont.SEMIBOLD.drawCentered(g, "清空输入", clearX + clearW / 2, btnY + btnH / 2,
                    c(Render2D.fade(TEXT_SIDEBAR, shown)));
            hit(clearX, btnY, clearW, btnH, this::clearAccountInput, null);

            float hintY = btnY + btnH + 22;
            String hint = "导入后账号仅保存在本机 Carnation 目录，对话直连 ChatGPT 后端，不会使用本机的 Codex 配置。";
            for (String line : wrap(UiFont.SMALL, hint, w - 56, 3)) {
                UiFont.SMALL.drawMiddle(g, line, x + 28, hintY, c(Render2D.fade(TEXT_MUTED, shown)));
                hintY += 20;
            }
        } else {
            Render2D.circle(g, x + 34, rowY, 4, c(Render2D.fade(0xFF4ADE80, shown)));
            // auth.json / sub2api exports carry no email field — never draw a null here.
            UiFont.SEMIBOLD.drawMiddle(g, acc.email() == null ? "已导入账号" : acc.email(),
                    x + 48, rowY, c(Render2D.fade(TEXT, shown)));
            long expiresAt = acc.expiresAt();
            String detail = (acc.plan() == null ? "codex" : acc.plan());
            if (expiresAt > 0) {
                detail += expiresAt < System.currentTimeMillis() / 1000 ? " · 已过期，重新导入可刷新"
                        : " · " + java.time.Instant.ofEpochSecond(expiresAt).atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate() + " 到期";
            }
            UiFont.SMALL.drawMiddle(g, detail, x + 48, rowY + 22, c(Render2D.fade(TEXT_MUTED, shown)));

            float cardY = rowY + 48;
            float cardH = 84;
            Render2D.roundedRect(g, x + 28, cardY, w - 56, cardH, 12, c(Render2D.fade(CARD, shown)));
            Render2D.roundedOutline(g, x + 28, cardY, w - 56, cardH, 12, 1, c(Render2D.fade(CARD_BORDER, shown)));
            CodexChat.Usage usage = CodexChat.usage();
            UiFont.SMALL.drawMiddle(g, "当前模型", x + 48, cardY + 26, c(Render2D.fade(TEXT_SECTION, shown)));
            UiFont.SEMIBOLD.drawMiddle(g, CodexChat.modelDisplay(), x + 130, cardY + 26,
                    c(Render2D.fade(TEXT, shown)));
            UiFont.SMALL.drawMiddle(g, "推理力度", x + 48, cardY + 54, c(Render2D.fade(TEXT_SECTION, shown)));
            UiFont.SEMIBOLD.drawMiddle(g, CodexChat.effortLabel(CodexChat.effort()), x + 130, cardY + 54,
                    c(Render2D.fade(TEXT, shown)));
            if (usage != null) {
                UiFont.SMALL.drawMiddle(g, "本窗口额度", x + 300, cardY + 26, c(Render2D.fade(TEXT_SECTION, shown)));
                UiFont.SEMIBOLD.drawMiddle(g, usage.primaryUsedPercent() + "%", x + 390, cardY + 26,
                        c(Render2D.fade(TEXT, shown)));
                UiFont.SMALL.drawMiddle(g, "套餐", x + 300, cardY + 54, c(Render2D.fade(TEXT_SECTION, shown)));
                UiFont.SEMIBOLD.drawMiddle(g, String.valueOf(usage.plan()), x + 390, cardY + 54,
                        c(Render2D.fade(TEXT, shown)));
            }

            float btnY = y + h - 74, btnH = 46;
            float apiW = 160, clearW = 140, logoutW = 160;
            boolean apiHover = hover(x + 28, btnY, apiW, btnH);
            float apiT = anim("accountApi", apiHover ? 1 : 0, 14);
            Render2D.roundedRect(g, x + 28, btnY, apiW, btnH, 10,
                    c(Render2D.fade(Render2D.mix(BUTTON, 0xFF4ADE80, apiT), shown)));
            Render2D.roundedOutline(g, x + 28, btnY, apiW, btnH, 10, 1,
                    c(Render2D.fade(Render2D.mix(POPUP_BORDER, TEXT_SIDEBAR, apiT), shown)));
            UiFont.SEMIBOLD.drawCentered(g, "API 登录", x + 28 + apiW / 2, btnY + btnH / 2,
                    c(Render2D.fade(TEXT, shown)));
            hit(x + 28, btnY, apiW, btnH, this::openApiSettings, null);

            float clearX = x + 28 + apiW + 14;
            boolean clearHover = hover(clearX, btnY, clearW, btnH);
            float clearT = anim("accountClearChat", clearHover ? 1 : 0, 14);
            Render2D.roundedRect(g, clearX, btnY, clearW, btnH, 10,
                    c(Render2D.fade(Render2D.mix(BUTTON, CARD_HOVER, clearT), shown)));
            Render2D.roundedOutline(g, clearX, btnY, clearW, btnH, 10, 1,
                    c(Render2D.fade(Render2D.mix(POPUP_BORDER, TEXT_SIDEBAR, clearT), shown)));
            UiFont.SEMIBOLD.drawCentered(g, "清空对话", clearX + clearW / 2, btnY + btnH / 2,
                    c(Render2D.fade(TEXT_SIDEBAR, shown)));
            hit(clearX, btnY, clearW, btnH, this::clearChatAction, null);

            float logoutX = x + w - 28 - logoutW;
            boolean logoutHover = hover(logoutX, btnY, logoutW, btnH);
            float logoutT = anim("accountLogout", logoutHover ? 1 : 0, 14);
            Render2D.roundedRect(g, logoutX, btnY, logoutW, btnH, 10,
                    c(Render2D.fade(Render2D.mix(BUTTON, 0xFFE5544B, logoutT), shown)));
            UiFont.SEMIBOLD.drawCentered(g, "退出登录", logoutX + logoutW / 2, btnY + btnH / 2,
                    c(Render2D.fade(TEXT, shown)));
            hit(logoutX, btnY, logoutW, btnH, () -> {
                CodexChat.logout();
                accountOpen = false;
                showToast("已退出登录");
            }, null);
        }
    }

    // ---- model & effort picker ----------------------------------------------------------------

    // ---- model & reasoning effort slider ------------------------------------------------------
    //
    // A port of bakabaicai/DSH-Codex-reasoning-effort-slider, itself a 1:1 copy of the Codex
    // desktop slider: a 272x104 card with the Fast bolt and reset in its top corners, the level
    // and model in the middle (click to pick a model) and a 26px rail under a 30px knob. Ultra
    // (the top level) turns the fill into a drifting purple gradient with a shimmer and sparks,
    // flashes a quota warning and bursts particles off the knob. Sizes are the original's CSS
    // pixels times the style's k, so each window can scale it to its own type.

    private static final int EFFORT_BLUE = 0xFF3988F6, EFFORT_BLUE_TEXT = 0xFF4A95F0;
    private static final int ULTRA_PURPLE = 0xFFA96BF0, ULTRA_BOLT = 0xFF9B48EB, ULTRA_SPARK = 0xFFA56BE9;
    private static final int[] ULTRA_STOPS = {0xFF3065DE, 0xFF7757EE, 0xFFB052F1, 0xFF7D42E7, 0xFF5631D0};
    private static final float[] ULTRA_AT = {0, 0.32f, 0.53f, 0.76f, 1};
    private static final int[][] SPARK_SEEDS = {{17, 15}, {20, 12}, {28, 18}, {48, 21}, {69, 23}, {95, 15}, {112, 20},
            {134, 15}, {138, 24}, {150, 9}, {165, 20}, {177, 10}, {190, 22}, {198, 26}, {217, 15},
            {237, 18}, {254, 12}, {275, 24}, {290, 14}, {315, 20}};
    private static final String ULTRA_WARNING = "更快消耗使用额度";

    /** Scale, fonts and colours of the slider card; each window passes its own. */
    protected record EffortStyle(float k, UiFont heading, UiFont caption, UiFont option, UiFont icon, UiFont smallIcon,
                                 int background, int border, int text, int secondary, int muted,
                                 int hoverBg, int rail, int knob) {
    }

    /** The Fast bolt only changes the animation, exactly like the original. */
    protected static boolean fastMode;
    protected boolean effortModels, effortDragging;
    /** The level shown while dragging, committed on release; -1 shows the saved one. */
    protected int effortPreview = -1;
    protected long ultraEnteredAt;
    protected float effortAnchorRight, effortAnchorBottom;
    protected float effortPopupX, effortPopupY, effortPopupW, effortPopupH;
    protected float effortTriggerX, effortTriggerY, effortTriggerW, effortTriggerH;
    private float effortRailX, effortRailY, effortRailW, effortK = 1;
    private float sparkSpeed, sparkFrom, sparkTarget, sparkTravel;
    private long sparkChangedAt;
    private Runnable effortTooltip;

    protected EffortStyle effortStyle() {
        return new EffortStyle(1.2f, UiFont.SEMIBOLD, UiFont.SMALL, UiFont.SMALL, UiFont.ICON_S, UiFont.Z_ICON_XS,
                POPUP, POPUP_BORDER, TEXT, 0xFFB4B5BA, ICON, ITEM_ACTIVE, 0xFF454752, 0xFFF2F2F4);
    }

    /**
     * The composer's model button, "[bolt] Model Effort ⌄", right-aligned at {@code right}: the
     * level turns purple at Ultra, a bolt leads while Fast is on and the chevron turns over while
     * the card is open. Without an account it only says so.
     */
    protected void drawEffortTrigger(GuiGraphicsExtractor g, float right, float cy, UiFont font, UiFont icon,
                                     float padX, float height, float radius, int text, int muted, int hoverBg) {
        boolean linked = CodexChat.state() != CodexChat.State.NO_ACCOUNT;
        String model = linked ? CodexChat.modelDisplay() : "未配置 AI";
        String effort = linked ? " " + CodexChat.effortLabel(CodexChat.effort()) : "";
        boolean ultra = linked && CodexChat.effortIndex() == CodexChat.EFFORTS.length - 1;
        float iconW = icon.size();
        float boltW = linked && fastMode ? iconW + 3 : 0;
        float w = padX + boltW + font.width(model) + font.width(effort) + 5 + iconW + padX * 0.6f;
        float x = right - w, y = cy - height / 2;
        float t = anim("effortTrigger", linked && (hover(x, y, w, height) || modelOpen) ? 1 : 0, 18);
        Render2D.roundedRect(g, x, y, w, height, radius, c(Render2D.fade(hoverBg, t)));
        float tx = x + padX;
        if (boltW > 0) {
            drawBolt(g, tx + iconW / 2, cy, iconW * 0.82f, c(text), true);
            tx += boltW;
        }
        font.drawMiddle(g, model, tx, cy, c(linked ? text : muted));
        tx += font.width(model);
        font.drawMiddle(g, effort, tx, cy, c(ultra ? ULTRA_PURPLE : muted));
        tx += font.width(effort) + 5;
        float turn = ease(anim("effortChevron", modelOpen ? 1 : 0, 16));
        float chevronX = tx + iconW / 2;
        g.pose().pushMatrix();
        g.pose().translate(chevronX, cy);
        g.pose().rotate((float) Math.PI * turn);
        g.pose().translate(-chevronX, -cy);
        icon.icon(g, Icons.CHEVRON_DOWN, chevronX, cy, c(muted));
        g.pose().popMatrix();
        effortTriggerX = x;
        effortTriggerY = y;
        effortTriggerW = w;
        effortTriggerH = height;
        if (linked) {
            hit(x, y, w, height, () -> {
                if (modelOpen) closeEffortPopup();
                else {
                    modelOpen = true;
                    effortModels = false;
                }
            }, null);
        }
    }

    /** Lucide's zap, filled when {@code filled} (the outline glyph alone otherwise). */
    protected void drawBolt(GuiGraphicsExtractor g, float cx, float cy, float size, int color, boolean filled) {
        float u = size / 24f, ox = cx - 12 * u, oy = cy - 12 * u;
        if (filled) {
            Render2D.quad(g, ox + 4 * u, oy + 14 * u, ox + 13.6f * u, oy + 2.2f * u, ox + 11.6f * u, oy + 10 * u,
                    ox + 12.4f * u, oy + 14 * u, color, color, color, color);
            Render2D.quad(g, ox + 11.6f * u, oy + 10 * u, ox + 20 * u, oy + 10 * u, ox + 10.4f * u, oy + 21.8f * u,
                    ox + 12.4f * u, oy + 14 * u, color, color, color, color);
        }
        float scale = size / UiFont.ICON_S.size();
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        UiFont.ICON_S.icon(g, Icons.ZAP, 0, 0, color);
        g.pose().popMatrix();
    }

    protected void closeEffortPopup() {
        if (effortDragging) commitEffort();
        modelOpen = false;
    }

    protected boolean insideEffortPopup(float px, float py) {
        boolean card = effortPopupW > 0 && px >= effortPopupX && px < effortPopupX + effortPopupW
                && py >= effortPopupY && py < effortPopupY + effortPopupH;
        boolean trigger = px >= effortTriggerX && px < effortTriggerX + effortTriggerW
                && py >= effortTriggerY && py < effortTriggerY + effortTriggerH;
        return card || trigger;
    }

    private void setEffortPreview(int level) {
        int max = CodexChat.EFFORTS.length - 1;
        int before = effortPreview >= 0 ? effortPreview : CodexChat.effortIndex();
        effortPreview = level;
        if (level == max && before != max) ultraEnteredAt = System.currentTimeMillis();
        else if (level != max) ultraEnteredAt = 0;
    }

    protected void commitEffort() {
        if (effortPreview >= 0 && effortPreview != CodexChat.effortIndex()) {
            CodexChat.setEffort(CodexChat.EFFORTS[effortPreview]);
        }
        effortPreview = -1;
        effortDragging = false;
    }

    private int nearestEffort() {
        float travel = Math.max(1, effortRailW - 30 * effortK);
        float f = Math.max(0, Math.min(1, (mouseX - effortRailX - 15 * effortK) / travel));
        return Math.round(f * (CodexChat.EFFORTS.length - 1));
    }

    /** The card, opened above the trigger with its bottom-right corner at the anchor. */
    protected void drawEffortPopup(GuiGraphicsExtractor g, EffortStyle st) {
        float shown = anim("effortPopup", modelOpen ? 1 : 0, 20);
        if (shown < 0.01f) {
            effortPopupW = 0;
            ultraEnteredAt = 0;
            return;
        }
        float k = st.k();
        effortK = k;
        if (effortDragging) setEffortPreview(nearestEffort());
        int index = effortPreview >= 0 ? effortPreview : CodexChat.effortIndex();
        boolean ultra = index == CodexChat.EFFORTS.length - 1;

        float models = ease(anim("effortModels", effortModels ? 1 : 0, 16));
        float mainH = 104 * k, modelsH = (5 + 24 + Math.max(1, CodexChat.models().size()) * 30 + 5) * k;
        float h = mainH + (modelsH - mainH) * models, w = 272 * k;
        float x = Math.round(Math.max(8, Math.min(W - 8 - w, effortAnchorRight - w)));
        float bottom = effortAnchorBottom + (1 - shown) * 5 * k, y = bottom - h;
        effortPopupX = x;
        effortPopupY = effortAnchorBottom - h;
        effortPopupW = w;
        effortPopupH = h;

        g.nextStratum();
        Render2D.shadow(g, x, y, w, h, 14 * k, 34 * k, 10 * k, c(Render2D.fade(0x8C000000, shown)));
        Render2D.roundedRect(g, x, y, w, h, 14 * k, c(Render2D.fade(st.background(), shown)));
        Render2D.roundedOutline(g, x, y, w, h, 14 * k, 1, c(Render2D.fade(st.border(), shown)));
        hit(x, y, w, h, () -> {
        }, null);
        effortTooltip = null;
        pushClip(g, x, y, w, h);
        if (models < 0.99f) {
            drawEffortMain(g, x, bottom - mainH + models * 5 * k, w, st, shown * (1 - models), index, ultra,
                    modelOpen && !effortModels);
        }
        if (models > 0.01f) {
            drawEffortModels(g, x, bottom - modelsH + (1 - models) * 5 * k, w, st, shown * models,
                    modelOpen && effortModels);
        }
        popClip(g);
        if (models < 0.5f) drawUltraBurst(g, k, shown);
        if (effortTooltip != null) effortTooltip.run();
    }

    private void drawEffortMain(GuiGraphicsExtractor g, float x, float y, float w, EffortStyle st, float fade,
                                int index, boolean ultra, boolean live) {
        float k = st.k();
        long now = System.currentTimeMillis();
        boolean message = ultraEnteredAt > 0 && now - ultraEnteredAt < 1800;
        float messageT = anim("effortMessage", message ? 1 : 0, 14);
        float toolFade = fade * (1 - messageT);

        // Fast, top-left.
        float ts = 28 * k, fx = x + 7 * k, fy = y + 7 * k;
        boolean fastHover = live && !message && hover(fx, fy, ts, ts);
        float fh = anim("effortFastHover", fastHover ? 1 : 0, 20);
        Render2D.roundedRect(g, fx, fy, ts, ts, 8 * k, c(Render2D.fade(st.hoverBg(), fh * toolFade)));
        int fastColor = fastMode ? (ultra ? ULTRA_BOLT : EFFORT_BLUE) : st.muted();
        drawBolt(g, fx + ts / 2, fy + ts / 2, 16 * k, c(Render2D.fade(fastColor, toolFade)), fastMode);
        if (live && !message) {
            hit(fx, fy, ts, ts, () -> fastMode = !fastMode, null);
            if (fastHover) {
                effortTooltip = () -> drawEffortTooltip(g, st, fx + ts / 2, fy - 4 * k, false,
                        "1.5倍速", "用量更多(无实际作用)");
            }
        }

        // Reset, top-right.
        float rx = x + w - 7 * k - ts;
        boolean resetHover = live && !message && hover(rx, fy, ts, ts);
        float rh = anim("effortResetHover", resetHover ? 1 : 0, 20);
        Render2D.roundedRect(g, rx, fy, ts, ts, 8 * k, c(Render2D.fade(st.hoverBg(), rh * toolFade)));
        st.icon().icon(g, Icons.ROTATE_CCW, rx + ts / 2, fy + ts / 2, c(Render2D.fade(st.muted(), toolFade)));
        if (live && !message) {
            hit(rx, fy, ts, ts, () -> {
                effortPreview = -1;
                ultraEnteredAt = 0;
                CodexChat.setEffort(CodexChat.DEFAULT_EFFORT);
            }, null);
            if (resetHover) {
                effortTooltip = () -> drawEffortTooltip(g, st, rx + ts + 15 * k, fy - 4 * k, true, "重置为默认", null);
            }
        }

        // The level and model in the middle open the model list.
        float lx = x + 43 * k, lw = w - 86 * k, ly = y + 8 * k, lh = 42 * k;
        boolean linkHover = live && !message && hover(lx, ly, lw, lh);
        float linkT = anim("effortLinkHover", linkHover ? 1 : 0, 20);
        Render2D.roundedRect(g, lx, ly, lw, lh, 5 * k, c(Render2D.fade(st.hoverBg(), linkT * toolFade)));
        String label = CodexChat.effortLabel(CodexChat.EFFORTS[index]);
        float chevron = st.smallIcon().size();
        float headingW = st.heading().width(label) + 3 * k + chevron;
        float headingX = x + w / 2 - headingW / 2, headingY = ly + 12 * k;
        st.heading().drawMiddle(g, label, headingX, headingY,
                c(Render2D.fade(ultra ? ULTRA_PURPLE : EFFORT_BLUE_TEXT, toolFade)));
        st.smallIcon().icon(g, Icons.CHEVRON_RIGHT, headingX + headingW - chevron / 2, headingY + k,
                c(Render2D.fade(st.muted(), toolFade)));
        String model = st.caption().ellipsize(CodexChat.modelDisplay(), lw - 8 * k);
        st.caption().drawCentered(g, model, x + w / 2, ly + 31 * k, c(Render2D.fade(st.secondary(), toolFade)));
        if (live && !message) hit(lx, ly, lw, lh, () -> effortModels = true, null);

        // The quota warning that replaces the header for a moment on entering Ultra.
        if (messageT > 0.01f) drawUltraWarning(g, st, x + w / 2, y + 29 * k, fade * messageT, now);

        // The rail.
        float railX = x + 12 * k, railY = y + 65 * k, railW = w - 24 * k, railH = 26 * k, radius = 13 * k;
        effortRailX = railX;
        effortRailY = railY;
        effortRailW = railW;
        int levels = CodexChat.EFFORTS.length;
        float travel = railW - 30 * k;
        float pos = anim("effortPos", index / (float) (levels - 1), 20);
        float fillW = travel * pos + 15 * k;
        Render2D.roundedRect(g, railX, railY, railW, railH, radius, c(Render2D.fade(st.rail(), fade)));

        float ultraT = anim("effortUltra", ultra ? 1 : 0, ultra ? 2.2f : 3.2f);
        float slide = ease(anim("effortUltraSlide", ultra ? 1 : 0, 2.6f));
        double clock = (now - openedAt) / 1000.0;
        // The overlay box is the fill plus 24px each side; it slides in from 42% to the right.
        float glowW = fillW + 48 * k, glowX = railX - 24 * k + glowW * 0.42f * (1 - slide);
        // The shimmer box is the fill plus 40px each side, swinging -45%..45% every 4.5s.
        double swing = (clock % 9.0) / 4.5;
        swing = swing > 1 ? 2 - swing : swing;
        swing = swing * swing * (3 - 2 * swing);
        float shineW = fillW + 80 * k, shineX = railX - 40 * k + shineW * (float) (-0.45 + 0.9 * swing);
        Render2D.gradientBar(g, railX, railY, fillW, railH, radius, 0, px -> {
            int color = EFFORT_BLUE;
            if (ultraT > 0.001f) {
                float u = (px - glowX) / glowW;
                if (u >= 0 && u <= 1) {
                    float mask = u < 0.22f ? u / 0.22f : u > 0.78f ? (1 - u) / 0.22f : 1;
                    color = Render2D.mix(color, ultraGradient(u), 0.9f * ultraT * mask);
                }
                float v = (px - shineX) / shineW;
                color = Render2D.mix(color, 0xFFFFFFFF, ultraT * shimmer(v));
            }
            return c(Render2D.fade(color, fade));
        });
        drawSparks(g, railX, railY, fillW, railH, k, fade, ultra, clock);

        // The level dots: white once passed, hidden under Ultra and under Fast's sparks.
        int hovered = live && (effortDragging || hover(railX, railY - 4 * k, railW, railH + 8 * k)) ? nearestEffort() : -1;
        float dots = anim("effortDots", ultra ? 0 : 1, ultra ? 30 : 9);
        for (int i = 0; i < levels; i++) {
            float covered = anim("effortCovered" + i, fastMode && i < index ? 0 : 1, 14);
            float alpha = dots * covered * fade;
            if (alpha < 0.01f) continue;
            float grow = anim("effortDot" + i, hovered == i ? 1.75f : 1, 22);
            float cx = railX + 15 * k + travel * i / (levels - 1);
            int color = i <= index ? 0x65FFFFFF : st.muted();
            Render2D.circle(g, cx, railY + 13 * k, 2 * k * grow, c(Render2D.fade(color, alpha)));
        }

        // The knob.
        float knobX = railX + travel * pos, knobY = railY - 2 * k, size = 30 * k;
        boolean knobHover = live && (effortDragging || hover(knobX, knobY, size, size));
        float scale = anim("effortKnob", knobHover ? 1.08f : 1, 22);
        float r = size / 2 * scale, cx = knobX + size / 2, cy = knobY + size / 2;
        Render2D.shadow(g, cx - r, cy - r, r * 2, r * 2, r, 10 * k, 2 * k, c(Render2D.fade(0x70000000, fade)));
        Render2D.circle(g, cx, cy, r, c(Render2D.fade(st.knob(), fade)));
        Render2D.circleOutline(g, cx, cy, r, 0.5f * k, c(Render2D.fade(0x26000000, fade)));
        if (live) {
            hit(railX, knobY, railW, size, () -> {
                effortDragging = true;
                setEffortPreview(nearestEffort());
            }, null);
        }
    }

    /** The Ultra overlay: 95deg, #3065de 0%, #7757ee 32%, #b052f1 53%, #7d42e7 76%, #5631d0 100%. */
    private static int ultraGradient(float u) {
        for (int i = 1; i < ULTRA_AT.length; i++) {
            if (u <= ULTRA_AT[i]) {
                return Render2D.mix(ULTRA_STOPS[i - 1], ULTRA_STOPS[i], (u - ULTRA_AT[i - 1]) / (ULTRA_AT[i] - ULTRA_AT[i - 1]));
            }
        }
        return ULTRA_STOPS[ULTRA_STOPS.length - 1];
    }

    /** White strength of the shimmer band: clear at 18%, 3% at 35%, 19% at 48%, 2% at 66%, clear at 84%. */
    private static float shimmer(float v) {
        float[] at = {0.18f, 0.35f, 0.48f, 0.66f, 0.84f};
        float[] alpha = {0, 0.03f, 0.19f, 0.024f, 0};
        if (v <= at[0] || v >= at[4]) return 0;
        for (int i = 1; i < at.length; i++) {
            if (v <= at[i]) return alpha[i - 1] + (alpha[i] - alpha[i - 1]) * (v - at[i - 1]) / (at[i] - at[i - 1]);
        }
        return 0;
    }

    /** The star field inside the fill: twinkling in place, streaming left once Ultra and Fast are both on. */
    private void drawSparks(GuiGraphicsExtractor g, float railX, float railY, float fillW, float railH, float k,
                            float fade, boolean ultra, double clock) {
        long now = System.currentTimeMillis();
        float target = ultra && fastMode ? 1 : 0;
        if (target != sparkTarget) {
            sparkFrom = sparkSpeed;
            sparkTarget = target;
            sparkChangedAt = now;
        }
        float p = Math.min(1, Math.max(0, (now - sparkChangedAt) / (target > 0 ? 600f : 750f)));
        sparkSpeed = sparkFrom + (sparkTarget - sparkFrom) * p * p * (3 - 2 * p);
        if (ultra) sparkTravel += dt * sparkSpeed;
        float shown = anim("effortSparks", ultra || fastMode ? 1 : 0, 10) * fade;
        if (shown < 0.01f) return;
        float speed = ultra ? sparkSpeed : 0;
        float width = effortRailW / k;
        pushClip(g, railX, railY, fillW, railH);
        for (int i = 0; i < SPARK_SEEDS.length; i++) {
            double phase = i * 2.39;
            double offset = ultra ? sparkTravel * (145 + i % 6 * 17) : 0;
            double sx = SPARK_SEEDS[i][0] / 346.0 * width - offset + Math.sin(clock * 9 + phase) * 0.7 * (1 - speed);
            sx = (sx % width + width) % width;
            double sy = (SPARK_SEEDS[i][1] + Math.cos(clock * 10 + phase) * 0.55 * (1 - speed)
                    + Math.sin(clock * 1.3 + phase) * 0.8 * speed) * 26 / 36;
            double idle = 0.55 + 0.3 * (0.5 + 0.5 * Math.sin(clock * 1.1 + phase));
            double moving = 0.3 + 0.45 * (0.5 + 0.5 * Math.sin(clock * 2.4 + phase));
            float radius = (i % 7 == 0 ? 2.1f : i % 4 == 0 ? 1.55f : 1.1f) * 26 / 36f;
            float alpha = (float) (idle + (moving - idle) * speed) * shown;
            Render2D.circle(g, railX + (float) sx * k, railY + (float) sy * k, radius * k,
                    c(Render2D.withAlpha(0xFFFFFF, Math.round(255 * alpha))));
        }
        popClip(g);
    }

    /** 22 purple specks thrown off the knob 220ms after Ultra is reached, gone after 520ms. */
    private void drawUltraBurst(GuiGraphicsExtractor g, float k, float fade) {
        if (ultraEnteredAt == 0) return;
        float age = (System.currentTimeMillis() - ultraEnteredAt - 220) / 1000f;
        if (age < 0 || age >= 0.52f) return;
        float unit = 52f / 144f * k;
        float cx = effortRailX + effortRailW - 15 * k, cy = effortRailY + 13 * k;
        int color = Render2D.withAlpha(ULTRA_SPARK, Math.round(255 * 0.7f * (1 - age / 0.52f) * fade));
        for (int i = 0; i < 22; i++) {
            double angle = i * 2.399 + 0.18;
            float px = cx + (float) (Math.cos(angle) * (43 + (48 + i % 5 * 12) * age * 2)) * unit;
            float py = cy + (float) (Math.sin(angle) * (43 + (48 + i % 4 * 10) * age * 2)) * unit;
            float radius = (i % 6 == 0 ? 2.3f : 1.35f) * 2 * (1 - age) * unit;
            Render2D.circle(g, px, py, radius, c(color));
        }
    }

    /** "更快消耗使用额度" in purple with a bright band sweeping across it once. */
    private void drawUltraWarning(GuiGraphicsExtractor g, EffortStyle st, float cx, float cy, float fade, long now) {
        UiFont font = st.heading();
        float total = font.width(ULTRA_WARNING);
        float p = Math.max(0, Math.min(1, (now - ultraEnteredAt - 200) / 1100f));
        float band = (-0.5f + 2 * p) * total, half = 0.24f * total;
        float left = cx - total / 2, x = left;
        for (int i = 0; i < ULTRA_WARNING.length(); i++) {
            String ch = String.valueOf(ULTRA_WARNING.charAt(i));
            float cw = font.width(ch);
            float d = Math.abs(x + cw / 2 - left - band) / half;
            int color = Render2D.mix(ULTRA_PURPLE, 0xFFF0E2FF, Math.max(0, 1 - d));
            font.drawMiddle(g, ch, x, cy, c(Render2D.fade(color, fade)));
            x += cw;
        }
    }

    /** A small card above a tool button: centred on {@code anchorX}, or ending there when {@code alignRight}. */
    private void drawEffortTooltip(GuiGraphicsExtractor g, EffortStyle st, float anchorX, float bottom,
                                   boolean alignRight, String first, String second) {
        float k = st.k();
        UiFont font = st.caption();
        float lineH = 18 * k;
        float w = Math.max(font.width(first), second == null ? 0 : font.width(second)) + 18 * k;
        float h = (second == null ? 1 : 2) * lineH + 12 * k;
        float x = alignRight ? anchorX - w : anchorX - w / 2, y = bottom - h;
        Render2D.shadow(g, x, y, w, h, 8 * k, 18 * k, 4 * k, c(0x80000000));
        Render2D.roundedRect(g, x, y, w, h, 8 * k, c(st.background()));
        Render2D.roundedOutline(g, x, y, w, h, 8 * k, 1, c(st.border()));
        font.drawCentered(g, first, x + w / 2, y + 6 * k + lineH / 2, c(st.text()));
        if (second != null) font.drawCentered(g, second, x + w / 2, y + 6 * k + lineH * 1.5f, c(st.secondary()));
    }

    /** The model list that slides in over the slider, with a back button. */
    private void drawEffortModels(GuiGraphicsExtractor g, float x, float y, float w, EffortStyle st, float fade,
                                  boolean live) {
        float k = st.k(), pad = 5 * k;
        float titleY = y + pad + 12 * k;
        st.caption().drawMiddle(g, "选择模型", x + pad + 8 * k, titleY, c(Render2D.fade(st.muted(), fade)));
        float bs = 22 * k, bx = x + w - pad - 2 * k - bs, by = titleY - bs / 2;
        boolean backHover = live && hover(bx, by, bs, bs);
        float bh = anim("effortBack", backHover ? 1 : 0, 20);
        Render2D.roundedRect(g, bx, by, bs, bs, 5 * k, c(Render2D.fade(st.hoverBg(), bh * fade)));
        st.smallIcon().icon(g, Icons.CHEVRON_LEFT, bx + bs / 2, by + bs / 2, c(Render2D.fade(st.muted(), fade)));
        if (live) hit(bx, by, bs, bs, () -> effortModels = false, null);

        float rowY = y + pad + 24 * k, rowH = 30 * k;
        List<CodexChat.Model> modelList = CodexChat.models();
        for (CodexChat.Model candidate : modelList) {
            boolean active = candidate.slug().equals(CodexChat.model());
            boolean hovered = live && hover(x + pad, rowY, w - pad * 2, rowH);
            float t = anim("effortRow" + candidate.slug(), hovered ? 1 : 0, 20);
            Render2D.roundedRect(g, x + pad, rowY, w - pad * 2, rowH, 8 * k, c(Render2D.fade(st.hoverBg(), t * fade)));
            st.option().drawMiddle(g, st.option().ellipsize(candidate.name(), w - pad * 2 - 40 * k),
                    x + pad + 8 * k, rowY + rowH / 2, c(Render2D.fade(st.text(), fade)));
            if (active) {
                st.icon().icon(g, Icons.CHECK, x + w - pad - 8 * k - st.icon().size() / 2, rowY + rowH / 2,
                        c(Render2D.fade(st.text(), fade)));
            }
            if (live) {
                hit(x + pad, rowY, w - pad * 2, rowH, () -> {
                    CodexChat.setModel(candidate.slug());
                    effortModels = false;
                }, null);
            }
            rowY += rowH;
        }
    }

    // ---- usage popup --------------------------------------------------------------------------

    /** Quota card from the x-codex-* headers, opened from the sidebar's bell. */
    protected void drawUsagePopup(GuiGraphicsExtractor g) {
        float shown = anim("usagePopup", usageOpen ? 1 : 0, 20);
        if (shown < 0.01f) return;
        g.nextStratum();
        float x = usagePopupX, y = usagePopupY + (1 - shown) * 10, w = USAGE_POPUP_W, h = USAGE_POPUP_H;
        Render2D.shadow(g, x, y, w, h, 12, 30, 12, c(Render2D.fade(0x90000000, shown)));
        Render2D.roundedRect(g, x, y, w, h, 12, c(Render2D.fade(POPUP, shown)));
        Render2D.roundedOutline(g, x, y, w, h, 12, 1, c(Render2D.fade(POPUP_BORDER, shown)));
        UiFont.TITLE.drawMiddle(g, "额度用量", x + 22, y + 30, c(Render2D.fade(TEXT, shown)));
        iconButton(g, Icons.ROTATE_CCW, x + w - 62, y + 30, 30, ICON, this::refreshUsage);
        iconButton(g, Icons.X, x + w - 30, y + 30, 30, ICON, () -> usageOpen = false);

        CodexChat.Usage usage = CodexChat.usage();
        if (usage == null) {
            float hintY = y + 78;
            String hint = "还没有用量数据。发一条消息，或点右上角刷新（会发送一次极小的探测请求）。";
            for (String line : wrap(UiFont.SMALL, hint, w - 44, 3)) {
                UiFont.SMALL.drawMiddle(g, line, x + 22, hintY, c(Render2D.fade(TEXT_MUTED, shown)));
                hintY += 20;
            }
            return;
        }

        float rowY = y + 76;
        String plan = planName(usage.plan() == null ? "codex" : usage.plan());
        float badgeW = UiFont.SEMIBOLD.width(plan) + 24;
        Render2D.roundedRect(g, x + 22, rowY - 14, badgeW, 28, 8, c(Render2D.fade(TAB, shown)));
        UiFont.SEMIBOLD.drawCentered(g, plan, x + 22 + badgeW / 2, rowY, c(Render2D.fade(TEXT, shown)));

        rowY += 44;
        int used = usage.primaryUsedPercent();
        String windowLabel = usage.primaryWindowMinutes() >= 10000 ? "周限额" : "主窗口";
        UiFont.SEMIBOLD.drawMiddle(g, windowLabel, x + 22, rowY, c(Render2D.fade(TEXT, shown)));
        UiFont.SEMIBOLD.drawRight(g, "已用 " + used + "%", x + w - 22, rowY,
                c(Render2D.fade(used >= 90 ? 0xFFE5544B : PINK, shown)));
        float barY = rowY + 22;
        Render2D.roundedRect(g, x + 22, barY - 2, w - 44, 4, 2, c(Render2D.fade(TRACK, shown)));
        float fill = (w - 44) * Math.max(0, Math.min(100, used)) / 100f;
        if (fill > 1) Render2D.roundedRect(g, x + 22, barY - 2, fill, 4, 2, c(Render2D.fade(PINK, shown)));
        if (usage.primaryResetAt() > 0) {
            String reset = java.time.Instant.ofEpochSecond(usage.primaryResetAt())
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"));
            UiFont.SMALL.drawMiddle(g, windowLabel + "重置于 " + reset, x + 22, barY + 22,
                    c(Render2D.fade(TEXT_MUTED, shown)));
        }

        float bottomY = y + h - 30;
        if (usage.secondaryUsedPercent() > 0) {
            UiFont.SMALL.drawMiddle(g, "短窗口已用 " + usage.secondaryUsedPercent() + "%", x + 22, bottomY - 24,
                    c(Render2D.fade(TEXT_MUTED, shown)));
        }
        String credits = usage.hasCredits() ? usage.creditBalance() + " credits" : "无 credits";
        UiFont.SMALL.drawMiddle(g, "Credits: " + credits, x + 22, bottomY, c(Render2D.fade(TEXT_SIDEBAR, shown)));
    }

    /** The quota only ships on real generations, so refreshing sends one minimal probe. */
    protected void refreshUsage() {
        if (CodexChat.state() == CodexChat.State.THINKING) {
            showToast("回复结束后再刷新");
            return;
        }
        showToast("正在刷新用量…");
        CodexChat.probeUsage(() -> {
            if (CodexChat.usage() == null) showToastError("用量刷新失败");
            else showToast("用量已更新");
        });
    }

    protected static String planName(String plan) {
        return switch (plan) {
            case "plus" -> "Plus";
            case "prolite" -> "Pro Lite";
            case "pro" -> "Pro";
            case "free" -> "Free";
            case "business" -> "Business";
            case "team" -> "Team";
            default -> plan.substring(0, 1).toUpperCase(Locale.ROOT) + plan.substring(1);
        };
    }

    protected void drawMenuDropdown(GuiGraphicsExtractor g) {
        float shown = anim("dropdown", openMenu >= 0 ? 1 : 0, 20);
        int menu = openMenu >= 0 ? openMenu : lastMenu;
        if (shown < 0.01f || menu < 0) return;
        lastMenu = menu;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        switch (menu) {
            case 0 -> {
                labels.add("保存配置");
                actions.add(() -> {
                    Client.instance.getConfigManager().saveAllConfig();
                    showToast("配置已保存");
                });
                labels.add("重新加载配置");
                actions.add(() -> {
                    Client.instance.getConfigManager().loadAllConfig();
                    showToast("已重新加载配置");
                });
                labels.add("关闭");
                actions.add(this::onClose);
            }
            case 1 -> {
                labels.add("清空输入");
                actions.add(() -> {
                    input = "";
                    updateCompletions();
                });
                labels.add("插入指令前缀 .");
                actions.add(() -> {
                    input = CommandManager.PREFIX;
                    updateCompletions();
                });
                labels.add("清空对话");
                actions.add(() -> {
                    CodexChat.clear();
                    chatFollow = true;
                    targetScroll = 0;
                });
            }
            case 2 -> {
                labels.add(sidebarOpen ? "隐藏侧边栏" : "显示侧边栏");
                actions.add(() -> sidebarOpen = !sidebarOpen);
                labels.add(selected != null ? "关闭设置面板" : "打开设置面板");
                actions.add(() -> {
                    if (selected != null) selected = null;
                    else if (!visibleModules().isEmpty()) select(visibleModules().getFirst());
                });
            }
            default -> {
                labels.add("指令列表 (.help)");
                actions.add(() -> runCommand(".help"));
                labels.add("关于 " + Client.name + " " + Client.ver);
                actions.add(() -> showToast(Client.name + " " + Client.ver + " · Fabric · renderpearl"));
            }
        }
        float x = MENU_X[menu] - 8, y = 44 - (1 - shown) * 8, w = 220, rowH = 34, h = labels.size() * rowH + 10;
        g.nextStratum();
        Render2D.shadow(g, x, y, w, h, 10, 30, 12, c(Render2D.fade(0x90000000, shown)));
        Render2D.roundedRect(g, x, y, w, h, 10, c(Render2D.fade(POPUP, shown)));
        Render2D.roundedOutline(g, x, y, w, h, 10, 1, c(Render2D.fade(POPUP_BORDER, shown)));
        for (int i = 0; i < labels.size(); i++) {
            float ry = y + 5 + i * rowH;
            boolean hovered = hover(x + 5, ry, w - 10, rowH);
            float t = anim("dropRow" + menu + i, hovered ? 1 : 0, 22);
            Render2D.roundedRect(g, x + 5, ry, w - 10, rowH, 7, c(Render2D.fade(ITEM_ACTIVE, t * shown)));
            UiFont.BODY.drawMiddle(g, labels.get(i), x + 18, ry + rowH / 2, c(Render2D.fade(TEXT_SIDEBAR, shown)));
            final Runnable action = actions.get(i);
            if (openMenu >= 0) {
                hit(x + 5, ry, w - 10, rowH, () -> {
                    openMenu = -1;
                    action.run();
                }, null);
            }
        }
    }

    protected int lastMenu = -1;

    // ---- actions ------------------------------------------------------------------------------

    protected void navigate(Place target) {
        mainView = MainView.MODULES;
        if (isSearching()) {
            input = "";
            updateCompletions();
        }
        if (target.equals(place)) return;
        back.push(place);
        forward.clear();
        setPlace(target);
    }

    protected void goBack() {
        if (back.isEmpty()) return;
        forward.push(place);
        setPlace(back.pop());
    }

    protected void goForward() {
        if (forward.isEmpty()) return;
        back.push(place);
        setPlace(forward.pop());
    }

    protected void setPlace(Place target) {
        place = target;
        placeChangedAt = System.currentTimeMillis();
        targetScroll = 0;
        if (target.view() == View.RECENT) recentOpen = true;
    }

    protected void select(Module module) {
        selected = module;
        binding = null;
    }

    protected void toggleModule(Module module) {
        module.toggle();
        RECENT.remove(module);
        RECENT.addFirst(module);
        while (RECENT.size() > 8) RECENT.removeLast();
        if (!module.isMomentaryAction()) showToast(module.getName() + (module.isEnabled() ? " 已启用" : " 已关闭"));
        Client.instance.getConfigManager().saveAllConfig();
    }

    protected void submit() {
        String text = input.trim();
        if (text.isEmpty()) return;
        if (renamePending != null) {
            CodexChat.renameSession(renamePending, text);
            renamePending = null;
            input = "";
            showToast("已重命名对话");
            updateCompletions();
            return;
        }
        if (text.startsWith(CommandManager.PREFIX)) {
            runCommand(text);
            input = "";
        } else {
            sendToCodex(text);
        }
        updateCompletions();
    }

    /** Non-command input with no module match becomes a chat turn. */
    protected void sendToCodex(String text) {
        CodexChat.ensureLoaded();
        if (CodexChat.state() == CodexChat.State.NO_ACCOUNT) {
            accountOpen = true;
            showToast("先登录 API 或导入 Codex 账号才能对话");
            return;
        }
        if (CodexChat.state() == CodexChat.State.THINKING) {
            showToast(CodexChat.thinkingHere() ? "正在回复中，可点发送键停止" : "后台任务进行中，完成后可发送");
            return;
        }
        input = "";
        chatFollow = true;
        mainView = MainView.CHAT;
        CodexChat.send(text);
    }

    protected void runCommand(String text) {
        boolean known = Client.instance.getCommandManager().execute(text.substring(CommandManager.PREFIX.length()));
        showToast(known ? "已执行 " + text : "未知指令 " + text);
    }

    protected void showToast(String text) {
        toastError = false;
        toast = text;
        toastAt = System.currentTimeMillis();
    }

    protected void showToastError(String text) {
        toastError = true;
        toast = text;
        toastAt = System.currentTimeMillis();
    }

    protected void updateCompletions() {
        completions = input.startsWith(CommandManager.PREFIX)
                ? Client.instance.getCommandManager().getCompletions(input)
                : List.of();
        completionIndex = Math.min(completionIndex, Math.max(0, completions.size() - 1));
    }

    protected void applyCompletion() {
        if (completions.isEmpty()) return;
        String choice = completions.get(completionIndex);
        int space = input.lastIndexOf(' ');
        input = space < 0 ? choice + " " : input.substring(0, space + 1) + choice + " ";
        completionIndex = 0;
        updateCompletions();
    }

    protected boolean isSearching() {
        return !input.isBlank() && !input.startsWith(CommandManager.PREFIX);
    }

    protected List<Module> visibleModules() {
        List<Module> all = Client.instance.getModuleManager().getModules();
        if (isSearching()) {
            String query = displayClip(input.trim().toLowerCase(Locale.ROOT), 64);
            return all.stream().filter(m -> m.getName().toLowerCase(Locale.ROOT).contains(query)
                    || m.getDescription().toLowerCase(Locale.ROOT).contains(query)).toList();
        }
        return switch (place.view()) {
            case ALL -> all;
            case RECENT -> List.copyOf(RECENT);
            case CATEGORY -> Client.instance.getModuleManager().getModulesByCategory(place.category());
        };
    }

    protected int count(Category category) {
        return Client.instance.getModuleManager().getModulesByCategory(category).size();
    }

    protected long enabledCount() {
        return Client.instance.getModuleManager().getModules().stream()
                .filter(m -> m.isEnabled() && !m.isMomentaryAction()).count();
    }

    protected void dragValue(NumberValue number) {
        float fraction = Math.max(0, Math.min(1, (mouseX - dragX) / dragW));
        number.set(number.getMin() + (number.getMax() - number.getMin()) * fraction);
    }

    /** Greedy line breaking that works for both spaced Latin text and Chinese. */
    protected static List<String> wrap(UiFont font, String text, float maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            line.append(text.charAt(i));
            if (font.width(line.toString()) > maxWidth) {
                line.setLength(line.length() - 1);
                if (lines.size() == maxLines - 1) {
                    lines.add(font.ellipsize(line + text.substring(i), maxWidth));
                    return lines;
                }
                lines.add(line.toString());
                line.setLength(0);
                line.append(text.charAt(i));
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    // ---- input --------------------------------------------------------------------------------

    protected float toDesignX(double guiX) {
        // Same half-pixel term the render-frame mouseX uses, so clicks and hover agree.
        return (float) ((guiX * minecraft.getWindow().getGuiScale() + minecraft.getWindow().getGuiScale() / 2f - originX) / s);
    }

    protected float toDesignY(double guiY) {
        return (float) ((guiY * minecraft.getWindow().getGuiScale() + minecraft.getWindow().getGuiScale() / 2f - originY) / s);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        float x = toDesignX(event.x()), y = toDesignY(event.y());
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (accountOpen && (x < accountPanelX || x >= accountPanelX + ACCOUNT_PANEL_W
                || y < accountPanelY || y >= accountPanelY + ACCOUNT_PANEL_H)) {
            accountOpen = false;
            accountFieldFocused = false;
        }
        // The panel's buttons are hit-tested directly against the same numbers the drawing
        // uses, bypassing the hit list entirely — a stale clip or a covering hit can no
        // longer make a visible button unclickable.
        if (!right) {
            Runnable panelAction = accountPanelAction(x, y);
            if (panelAction != null) {
                panelAction.run();
                return true;
            }
        }
        if (modelOpen && !insideEffortPopup(x, y)) closeEffortPopup();
        if (usageOpen && (x < usagePopupX || x >= usagePopupX + USAGE_POPUP_W
                || y < usagePopupY || y >= usagePopupY + USAGE_POPUP_H)) {
            usageOpen = false;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(x, y)) continue;
            Runnable action = right ? hit.right : hit.left;
            if (action == null) continue;
            int menuBefore = openMenu;
            action.run();
            if (openMenu == menuBefore && menuBefore >= 0 && !isMenuHit(hit)) openMenu = -1;
            return true;
        }
        openMenu = -1;
        return true;
    }

    protected boolean isMenuHit(Hit hit) {
        return hit.y() < 46 && hit.x() < 420;
    }

    /**
     * Geometric hit-test of the account panel's controls, mirroring the draw code's numbers
     * exactly. Returns the action for the control under the cursor, or null when the click
     * lands on panel background or outside the panel.
     */
    protected Runnable accountPanelAction(float px, float py) {
        if (!accountOpen) return null;
        float x = accountPanelX, y = accountPanelY, w = ACCOUNT_PANEL_W, h = ACCOUNT_PANEL_H;
        if (px < x || px >= x + w || py < y || py >= y + h) return null;
        // Header controls: the API login pill, its gear and the close cross. These sit above the
        // panel-wide background hit, so they are answered here before anything else.
        if (py >= y + 26 && py < y + 58) {
            if (px >= x + w - 250 && px < x + w - 140) return this::openApiSettings;
            if (px >= x + w - 100 && px < x + w - 68) return this::openApiSettings;
            if (px >= x + w - 58 && px < x + w - 26) return () -> {
                accountOpen = false;
                accountFieldFocused = false;
            };
        }
        if (CodexChat.account() == null && CodexChat.genericConfigured()) {
            float btnY = y + h - 74, btnH = 46;
            if (py >= btnY && py < btnY + btnH) {
                if (px >= x + 28 && px < x + 198) return this::openApiSettings;
                if (px >= x + 214 && px < x + 364) return this::clearChatAction;
            }
            return null;
        }
        if (CodexChat.account() == null) {
            float fieldX = x + 28, fieldY = y + 126, fieldW = w - 56, fieldH = 176;
            float btnY = fieldY + fieldH + 20, btnH = 46;
            if (px >= fieldX && px < fieldX + fieldW && py >= fieldY && py < fieldY + fieldH) {
                accountFieldFocused = true;
                return () -> {
                };
            }
            if (py >= btnY && py < btnY + btnH) {
                if (px >= fieldX && px < fieldX + 150) return this::pasteClipboard;
                float importX = fieldX + 164;
                if (px >= importX && px < importX + 130) return () -> importAccountText(accountInput);
                float clearX = importX + 144;
                if (px >= clearX && px < clearX + 130) return this::clearAccountInput;
            }
            return null;
        }
        float btnY = y + h - 74, btnH = 46;
        if (py >= btnY && py < btnY + btnH) {
            if (px >= x + 28 && px < x + 198) return this::openApiSettings;
            if (px >= x + 214 && px < x + 364) return this::clearChatAction;
            float logoutX = x + w - 188;
            if (px >= logoutX && px < logoutX + 160) return () -> {
                CodexChat.logout();
                accountOpen = false;
                showToast("已退出登录");
            };
        }
        return null;
    }

    /** Pastes the clipboard into the account field; empty clipboard gets an error toast. */
    protected void pasteClipboard() {
        String clip = minecraft.keyboardHandler.getClipboard();
        if (clip == null || clip.isBlank()) {
            showToastError("剪贴板是空的，先复制账号 JSON 内容");
            return;
        }
        appendAccountInput(clip);
        showToast("已粘贴 " + clip.length() + " 个字符");
    }

    /** Clears the login field; emptying an empty field says so instead of staying silent. */
    protected void clearAccountInput() {
        if (accountInput.isEmpty()) {
            showToast("输入框本来就是空的");
            return;
        }
        accountInput = "";
        accountFieldFocused = false;
        showToast("已清空输入");
    }

    /** Clears the current conversation; empty list says so instead of appearing dead. */
    protected void clearChatAction() {
        if (CodexChat.messages().isEmpty()) {
            showToast("当前没有对话");
            return;
        }
        CodexChat.clear();
        chatFollow = true;
        targetScroll = 0;
        showToast("对话已清空");
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (effortDragging) commitEffort();
        if (dragging != null) {
            dragging = null;
            Client.instance.getConfigManager().saveAllConfig();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        targetScroll = Math.max(0, Math.min(maxScroll, targetScroll - (float) scrollY * 48));
        chatFollow = targetScroll >= maxScroll - 4;
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (binding != null || !event.isAllowedChatCharacter()) return false;
        typeInto(event.codepointAsString());
        return true;
    }

    /** Appends committed text — plain keyboard chars and IME commits alike — to the composer. */
    protected void typeInto(String text) {
        if (accountFieldFocused) {
            appendAccountInput(text);
            return;
        }
        if (input.length() + text.length() > MAX_INPUT) {
            showToastError("内容过长：账号 JSON 请通过账号面板导入");
            return;
        }
        input += text;
        typedAt = System.currentTimeMillis();
        completionIndex = 0;
        updateCompletions();
    }

    protected void appendAccountInput(String text) {
        if (accountInput.length() + text.length() > 200_000) {
            showToastError("内容过长");
            return;
        }
        accountInput += text;
        typedAt = System.currentTimeMillis();
    }

    protected void deleteAccountChar(boolean wholeWord) {
        if (accountInput.isEmpty()) return;
        if (wholeWord) {
            accountInput = accountInput.stripTrailing();
            int space = accountInput.lastIndexOf(' ');
            accountInput = space < 0 ? "" : accountInput.substring(0, space + 1);
        } else {
            accountInput = accountInput.substring(0, accountInput.offsetByCodePoints(accountInput.length(), -1));
        }
        typedAt = System.currentTimeMillis();
    }

    /** The pinyin being composed while an IME is active; cleared once the commit lands. */
    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        preedit = event == null ? "" : event.fullText();
        return true;
    }

    /** Some IME commits arrive here instead of charTyped; accept them the same way. */
    @Override
    protected void insertText(String text, boolean narrate) {
        if (binding != null || text == null || text.isEmpty()) return;
        typeInto(text.replace("\n", "").replace("\r", ""));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (binding != null) {
            binding.setKey(key == InputConstants.KEY_ESCAPE ? 0 : key);
            showToast(binding.getName() + " 绑定到 " + CommandBind.keyName(binding.getKey()));
            binding = null;
            Client.instance.getConfigManager().saveAllConfig();
            return true;
        }
        boolean ctrl = (event.modifiers() & InputConstants.MOD_CONTROL) != 0;
        if (accountFieldFocused) {
            switch (key) {
                case InputConstants.KEY_ESCAPE -> accountFieldFocused = false;
                case InputConstants.KEY_BACKSPACE -> deleteAccountChar(ctrl);
                case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> importAccountText(accountInput);
                case InputConstants.KEY_V -> {
                    if (ctrl) appendAccountInput(minecraft.keyboardHandler.getClipboard());
                }
                default -> {
                }
            }
            return true;
        }
        switch (key) {
            case InputConstants.KEY_ESCAPE -> {
                if (openMenu >= 0) openMenu = -1;
                else if (modelOpen || usageOpen) {
                    closeEffortPopup();
                    usageOpen = false;
                }
                else if (accountOpen) {
                    accountOpen = false;
                    accountFieldFocused = false;
                }
                else if (!input.isEmpty()) {
                    input = "";
                    updateCompletions();
                } else if (selected != null) selected = null;
                else onClose();
                return true;
            }
            case InputConstants.KEY_BACKSPACE -> {
                if (!input.isEmpty()) {
                    if (ctrl) {
                        String trimmed = input.stripTrailing();
                        int space = trimmed.lastIndexOf(' ');
                        input = space < 0 ? "" : trimmed.substring(0, space + 1);
                    } else {
                        input = input.substring(0, input.offsetByCodePoints(input.length(), -1));
                    }
                    typedAt = System.currentTimeMillis();
                    updateCompletions();
                }
                return true;
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                submit();
                return true;
            }
            case InputConstants.KEY_TAB -> {
                applyCompletion();
                return true;
            }
            case InputConstants.KEY_UP -> {
                if (!completions.isEmpty()) completionIndex = Math.floorMod(completionIndex - 1, completions.size());
                return true;
            }
            case InputConstants.KEY_DOWN -> {
                if (!completions.isEmpty()) completionIndex = Math.floorMod(completionIndex + 1, completions.size());
                return true;
            }
            case InputConstants.KEY_V -> {
                if (ctrl) {
                    String clipboard = minecraft.keyboardHandler.getClipboard().replaceAll("[\\r\\n]", " ");
                    if (input.length() + clipboard.length() > MAX_INPUT) {
                        showToastError("内容过长：账号 JSON 请通过账号面板导入");
                        return true;
                    }
                    input += clipboard;
                    typedAt = System.currentTimeMillis();
                    updateCompletions();
                    return true;
                }
            }
            default -> {
            }
        }
        return super.keyPressed(event);
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** Character-level clip for headline/filter use; never re-measures, so any length is safe. */
    protected static String displayClip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    /** Clips a single-line field from the head so it plus {@code suffix} fits; one linear pass. */
    protected static String fitLine(String text, String suffix, float maxWidth, UiFont font) {
        float total = font.width(text) + font.width(suffix);
        if (total <= maxWidth || text.length() <= 1) return text;
        float cut = total - maxWidth;
        float acc = 0;
        int i = 0;
        while (i < text.length() - 1 && acc <= cut) {
            acc += font.width(String.valueOf(text.charAt(i)));
            i++;
        }
        return text.substring(i);
    }

    /** Accepts raw JSON content or a file path, imports it and reports via the toast. */
    protected void importAccountText(String text) {
        if (text == null || text.isBlank()) {
            showToastError("先在输入框粘贴账号 JSON 内容或路径");
            return;
        }
        try {
            String email = CodexChat.importFromText(text);
            accountInput = "";
            accountFieldFocused = false;
            showToast("已导入 " + (email == null ? "账号" : email));
        } catch (Exception e) {
            showToastError("导入失败: " + e.getMessage());
        }
    }

    /** Applies the open/close fade to a colour. */
    protected int c(int color) {
        return Render2D.fade(color, alpha);
    }

    protected boolean hover(float x, float y, float w, float h) {
        if (openMenu >= 0 && !(y < 46)) {
            // An open menu covers what is under it; only the menu itself and the title bar react.
            float mx = MENU_X[openMenu] - 8;
            if (mouseX >= mx && mouseX < mx + 220 && mouseY >= 44 && mouseY < 200) return false;
        }
        return mouseX >= Math.max(x, clipX0) && mouseY >= Math.max(y, clipY0)
                && mouseX < Math.min(x + w, clipX1) && mouseY < Math.min(y + h, clipY1);
    }

    /** Records a clickable area, cut to the current clip so hidden parts cannot be clicked. */
    protected void hit(float x, float y, float w, float h, Runnable left, Runnable right) {
        float x0 = Math.max(x, clipX0), y0 = Math.max(y, clipY0);
        float x1 = Math.min(x + w, clipX1), y1 = Math.min(y + h, clipY1);
        if (x1 > x0 && y1 > y0) hits.add(new Hit(x0, y0, x1 - x0, y1 - y0, left, right));
    }

    protected void pushClip(GuiGraphicsExtractor g, float x, float y, float w, float h) {
        clips.push(new float[]{clipX0, clipY0, clipX1, clipY1});
        clipX0 = Math.max(clipX0, x);
        clipY0 = Math.max(clipY0, y);
        clipX1 = Math.min(clipX1, x + w);
        clipY1 = Math.min(clipY1, y + h);
        g.enableScissor((int) Math.floor(clipX0), (int) Math.floor(clipY0),
                (int) Math.ceil(Math.max(clipX0, clipX1)), (int) Math.ceil(Math.max(clipY0, clipY1)));
    }

    protected void popClip(GuiGraphicsExtractor g) {
        g.disableScissor();
        float[] previous = clips.pop();
        clipX0 = previous[0];
        clipY0 = previous[1];
        clipX1 = previous[2];
        clipY1 = previous[3];
    }

    protected float anim(String key, float target, float speed) {
        return anim(key, null, target, speed);
    }

    /**
     * A value per key that eases towards {@code target}, keyed by a name plus the owner's identity.
     * It steps at most once per frame, so it can be read from several places while drawing.
     */
    protected float anim(String name, Object owner, float target, float speed) {
        Object key = owner == null ? name : List.of(name, System.identityHashCode(owner));
        float[] state = animations.computeIfAbsent(key, k -> new float[]{target, -1});
        if (state[1] != frame) {
            state[1] = frame;
            float next = state[0] + (target - state[0]) * (1 - (float) Math.exp(-speed * dt));
            state[0] = Math.abs(next - target) < 0.001f ? target : next;
        }
        return state[0];
    }

    protected float approach(float current, float target, float speed) {
        float next = current + (target - current) * (1 - (float) Math.exp(-speed * dt));
        return Math.abs(next - target) < 0.001f ? target : next;
    }

    protected static float ease(float t) {
        float inv = 1 - Math.max(0, Math.min(1, t));
        return 1 - inv * inv * inv;
    }
}
