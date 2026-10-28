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
    private static final float W = 1551, H = 1115;
    private static final float RAIL_W = 64, SIDEBAR_W = 303, PANEL_TOP = 54, PANEL_BOTTOM = 1110, PANEL_RIGHT = 1547;
    private static final float PANE_W = 404;
    private static final float CARDS_TOP = 640, CARDS_BOTTOM = 900, CARD_H = 104, CARD_GAP = 12;
    private static final float[] MENU_X = {153, 218, 283, 347};
    private static final String[] MENUS = {"文件", "编辑", "视图", "帮助"};
    private static final float POPUP_X = 84, POPUP_Y = 828, POPUP_W = 384, POPUP_H = 244;
    private static final float ACCOUNT_PANEL_X = 435, ACCOUNT_PANEL_Y = 300, ACCOUNT_PANEL_W = 680, ACCOUNT_PANEL_H = 515;
    private static final float MODEL_POPUP_X = 557, MODEL_POPUP_Y = 476, MODEL_POPUP_W = 400, MODEL_POPUP_H = 436;
    private static final float USAGE_POPUP_X = 388, USAGE_POPUP_Y = 128, USAGE_POPUP_W = 380, USAGE_POPUP_H = 252;
    private static final int PINK = 0xFFEC4899;

    /** Modules toggled from this GUI, newest first; kept across openings. */
    private static final Deque<Module> RECENT = new ArrayDeque<>();
    private static boolean sidebarOpen = true, categoriesOpen = true, recentOpen = false, archivedOpen = false;

    private enum View { ALL, CATEGORY, RECENT }

    /** Whether the main area shows the module grid or the conversation stream. */
    private enum MainView {MODULES, CHAT}

    private record Place(View view, Category category) {
    }

    private Place place = new Place(View.ALL, null);
    private final Deque<Place> back = new ArrayDeque<>(), forward = new ArrayDeque<>();

    private String input = "";
    private List<String> completions = List.of();
    private int completionIndex;
    /** Dedicated account-login field inside the account popup; never mixes with the composer. */
    private String accountInput = "";
    private boolean accountFieldFocused;
    /** Wrapped preview of the account field; recomputed only when the text changes. */
    private String accountFieldCache = null;
    private List<String> accountFieldLines = List.of();
    /** Hard cap for the composer: pasted JSON belongs in the account popup, not here. */
    private static final int MAX_INPUT = 2000;

    private Module selected;
    private Module binding;
    private NumberValue dragging;
    private float dragX, dragW;
    private int openMenu = -1;
    private boolean accountOpen;
    private boolean modelOpen;
    private boolean usageOpen;
    private boolean chatFollow = true;
    private MainView mainView = MainView.MODULES;
    /** Conversation indices whose thinking block the user expanded. */
    private final Set<Integer> expandedThinking = new HashSet<>();
    /** Set while a conversation rename is pending; the composer text becomes the new title. */
    private Path renamePending;
    /** The composing IME string, shown inline until the commit lands in charTyped. */
    private String preedit = "";
    /**
     * A no-op focused target that keeps SDL text input on. 26.x only feeds typed characters and
     * IME events to a screen while a capturesInput() listener holds focus (EditBox.setFocused is
     * what normally flips the switch), and every control here is drawn by hand — without the
     * anchor the composer never receives a single typed character, English included.
     */
    private final GuiEventListener textAnchor = new GuiEventListener() {
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

    private String toast = "";
    private long toastAt;
    private boolean toastError;
    private long placeChangedAt = System.currentTimeMillis();
    private String heroText = "", heroPrevious = "";
    private long heroChangedAt;

    private float scroll, targetScroll, maxScroll;
    private float open;
    private boolean closing;
    private long lastFrame, typedAt;
    private final long openedAt = System.currentTimeMillis();

    // Frame state.
    private float s, alpha, mouseX = -1, mouseY = -1, dt;
    private int originX, originY, frame;
    private final Map<Object, float[]> animations = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Deque<float[]> clips = new ArrayDeque<>();
    private float clipX0 = -1e9f, clipY0 = -1e9f, clipX1 = 1e9f, clipY1 = 1e9f;

    private record Hit(float x, float y, float w, float h, Runnable left, Runnable right) {
        boolean contains(float px, float py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    public CodexScreen() {
        super(Component.literal("Carnation"));
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
        originX = Math.round((window.getWidth() - W * s) / 2);
        originY = Math.round((window.getHeight() - H * s) / 2);
        mouseX = (guiMouseX * guiScale + guiScale / 2f - originX) / s;
        mouseY = (guiMouseY * guiScale + guiScale / 2f - originY) / s;
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

    private void drawWindow(GuiGraphicsExtractor g) {
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
        drawModelPopup(g);
        drawUsagePopup(g);
    }

    // ---- title bar ----------------------------------------------------------------------------

    private void drawTitleBar(GuiGraphicsExtractor g) {
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

    private void windowButton(GuiGraphicsExtractor g, char icon, float cx, int color, boolean close, Runnable action) {
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

    private void iconButton(GuiGraphicsExtractor g, char icon, float cx, float cy, float size, int color, Runnable action) {
        float t = anim("icon" + icon + cx + cy, hover(cx - size / 2, cy - size / 2, size, size) ? 1 : 0, 18);
        Render2D.roundedRect(g, cx - size / 2, cy - size / 2, size, size, 8, c(Render2D.fade(0xFF30313B, t)));
        UiFont.ICON_M.icon(g, icon, cx, cy, c(Render2D.mix(color, TEXT_SIDEBAR, t * 0.6f)));
        hit(cx - size / 2, cy - size / 2, size, size, action, null);
    }

    // ---- rail ---------------------------------------------------------------------------------

    private void drawRail(GuiGraphicsExtractor g) {
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

    private void railIcon(GuiGraphicsExtractor g, char icon, float cy, boolean active, Runnable action) {
        boolean hovered = hover(11, cy - 22, 42, 44);
        float t = anim("rail" + icon, active ? 1 : hovered ? 0.55f : 0, 18);
        UiFont.ICON_L.icon(g, icon, 32, cy, c(Render2D.mix(ICON, TEXT, t)));
        hit(11, cy - 22, 42, 44, action, null);
    }

    // ---- sidebar ------------------------------------------------------------------------------

    private void drawSidebar(GuiGraphicsExtractor g, float x0) {
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
    private void drawArchiveButton(GuiGraphicsExtractor g, float dx, float rowY, Path file) {
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
    private float sectionHeader(GuiGraphicsExtractor g, float dx, float cy, String label, boolean expanded,
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

    private void sidebarRow(GuiGraphicsExtractor g, float dx, float cy, char icon, String label, String trailing,
                            boolean active, Runnable action) {
        sidebarRow(g, dx, cy, icon, label, trailing, active, action, null);
    }

    private void sidebarRow(GuiGraphicsExtractor g, float dx, float cy, char icon, String label, String trailing,
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

    private void drawMain(GuiGraphicsExtractor g, float mainX) {
        float pane = ease(anim("pane", selected != null ? 1 : 0, 13));
        float paneX = PANEL_RIGHT - PANE_W * pane;
        float contentRight = PANEL_RIGHT - PANE_W * pane;
        float cx = (mainX + contentRight) / 2;
        float composerW = Math.min(920, contentRight - mainX - 80);
        float composerX = cx - composerW / 2;

        drawHeader(g, mainX);
        if (mainView == MainView.CHAT) {
            drawChat(g, composerX, composerW);
        } else {
            drawHero(g, cx);
            drawCards(g, composerX, composerW);
        }
        drawComposer(g, composerX, 968, composerW, 122);
        if (pane > 0.002f) drawPane(g, paneX, pane);
        drawToast(g, (mainX + PANEL_RIGHT) / 2);
    }

    private void drawHeader(GuiGraphicsExtractor g, float mainX) {
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

    private String placeTitle() {
        if (mainView == MainView.CHAT) return "对话";
        if (isSearching()) return "搜索 “" + displayClip(input.trim(), 40) + "”";
        return switch (place.view()) {
            case ALL -> "全部模块";
            case RECENT -> "最近";
            case CATEGORY -> place.category().getDisplayName();
        };
    }

    private void drawHero(GuiGraphicsExtractor g, float cx) {
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

    private String heroLine() {
        if (isSearching()) return "搜索 \u0001" + displayClip(input.trim(), 48) + "\u0001 的模块";
        return switch (place.view()) {
            case ALL -> "你想让我们在 \u0001" + Client.name + "\u0001 中启用什么?";
            case RECENT -> "最近在 \u0001" + Client.name + "\u0001 中切换的模块";
            case CATEGORY -> "你想在 \u0001" + place.category().getDisplayName() + "\u0001 中启用什么?";
        };
    }

    /** A headline whose part between \u0001 marks gets the dotted underline, as Codex does for the project. */
    private void drawHeadline(GuiGraphicsExtractor g, String line, float cx, float cy, float fade) {
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
    private void drawLogo(GuiGraphicsExtractor g, float scale) {
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

    private void drawCards(GuiGraphicsExtractor g, float x, float w) {
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

    private void drawCard(GuiGraphicsExtractor g, Module module, float x, float y, float w, float appear) {
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
    private void drawSwitch(GuiGraphicsExtractor g, float x, float y, boolean on, Object key, float fade, Runnable action) {
        float t = ease(anim("switch", key, on ? 1 : 0, 16));
        boolean hovered = hover(x - 2, y - 2, 40, 24);
        Render2D.roundedRect(g, x, y, 36, 20, 10, c(Render2D.fade(Render2D.mix(TRACK, SEND, t), fade)));
        float knobX = x + 10 + 16 * t;
        Render2D.circle(g, knobX, y + 10, hovered ? 8 : 7.5f, c(Render2D.fade(Render2D.mix(0xFFD8D8DC, MAIN, t), fade)));
        hit(x - 2, y - 2, 40, 24, action, null);
    }

    /** Wraps display text: strips control glyphs that render as boxes, keeps source line breaks. */
    private static List<String> wrapRich(UiFont font, String text, float maxWidth) {
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
    private void drawChat(GuiGraphicsExtractor g, float x, float w) {
        float top = 130, bottom = 908;
        List<CodexChat.Msg> chat = CodexChat.messages();
        if (chat.isEmpty()) {
            maxScroll = 0;
            if (CodexChat.state() == CodexChat.State.NO_ACCOUNT) {
                float cy = (top + bottom) / 2;
                UiFont.TITLE.drawCentered(g, "开始使用 Codex", x + w / 2, cy - 46, c(TEXT));
                UiFont.BODY.drawCentered(g, "导入 Codex 账号后即可在游戏内与它对话", x + w / 2, cy - 10,
                        c(TEXT_MUTED));
                UiFont.SMALL.drawCentered(g, "支持官方 auth.json 与 sub2api 导出格式，账号仅保存在本机",
                        x + w / 2, cy + 16, c(TEXT_MUTED));
                drawImportButton(g, x + w / 2, cy + 58);
            } else {
                UiFont.BODY.drawCentered(g, "在下方输入，开始与 Codex 的对话", x + w / 2, (top + bottom) / 2,
                        c(TEXT_MUTED));
            }
            return;
        }
        if (CodexChat.state() == CodexChat.State.NO_ACCOUNT) {
            drawAccountBanner(g, x, w);
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

    /** Centered "导入账号" call-to-action of the empty chat view while no account is imported. */
    private void drawImportButton(GuiGraphicsExtractor g, float cx, float cy) {
        float bw = 176, bh = 44;
        float bx = cx - bw / 2, by = cy - bh / 2;
        boolean btnHover = hover(bx, by, bw, bh);
        float t = anim("emptyImport", btnHover ? 1 : 0, 16);
        Render2D.roundedRect(g, bx, by, bw, bh, 12, c(Render2D.mix(TAB, ITEM_ACTIVE, t)));
        Render2D.roundedOutline(g, bx, by, bw, bh, 12, 1, c(Render2D.mix(CARD_BORDER, CARD_BORDER_HOVER, t)));
        UiFont.SEMIBOLD.drawCentered(g, "导入账号", cx, cy, c(TEXT));
        hit(bx, by, bw, bh, () -> accountOpen = true, null);
    }

    /** Slim notice above the chat stream while a conversation is open but no account is imported. */
    private void drawAccountBanner(GuiGraphicsExtractor g, float x, float w) {
        float bw = Math.min(620, w - 48), bx = x + (w - bw) / 2, by = 130;
        Render2D.roundedRect(g, bx, by, bw, 40, 10, c(CARD));
        Render2D.roundedOutline(g, bx, by, bw, 40, 10, 1, c(CARD_BORDER));
        UiFont.ICON_S.icon(g, Icons.CIRCLE_ALERT, bx + 22, by + 20, c(ORANGE));
        UiFont.SEMIBOLD.drawMiddle(g, "未导入 Codex 账号，回复功能不可用", bx + 40, by + 20, c(TEXT));
        float pw = 92, px = bx + bw - pw - 14, py = by + 8;
        boolean btnHover = hover(px, py, pw, 24);
        float t = anim("bannerImport", btnHover ? 1 : 0, 14);
        Render2D.roundedRect(g, px, py, pw, 24, 12, c(Render2D.mix(TAB, ITEM_ACTIVE, t)));
        UiFont.SMALL.drawCentered(g, "导入账号", px + pw / 2, py + 12, c(TEXT));
        hit(px, py, pw, 24, () -> accountOpen = true, null);
    }

    /** User messages sit in a right-aligned bubble; assistant replies are plain text by the blossom. */
    private void drawChatMessage(GuiGraphicsExtractor g, CodexChat.Msg message, List<String> lines,
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
    private static List<String> planSteps(CodexChat.Msg message) {
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
    private void drawPlanCard(GuiGraphicsExtractor g, List<String> plan, float x, float y) {
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

    private Module paneModule;

    private void drawPane(GuiGraphicsExtractor g, float x, float open) {
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

    private float rowHeight(Value<?> value) {
        return value instanceof NumberValue ? 64 : 48;
    }

    private void drawValueRow(GuiGraphicsExtractor g, Value<?> value, float x, float y, float w, float fade) {
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

    private void drawBindRow(GuiGraphicsExtractor g, Module module, float x, float y, float w, float fade) {
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

    private void drawComposer(GuiGraphicsExtractor g, float x, float y, float w, float h) {
        // Context chips on a bar tucked behind the composer's top edge.
        float barX = x + 17, barW = w - 34;
        Render2D.roundedRect(g, barX, 920, barW, 64, 12, c(CHIP_BAR));
        UiFont.ICON_S.icon(g, Icons.FOLDER, x + 43, 944, c(0xFFE0E0DC));
        UiFont.BODY.drawMiddle(g, Client.name, x + 60, 944, c(TEXT));
        float chip2 = x + 60 + UiFont.BODY.width(Client.name) + 32;
        UiFont.ICON_S.icon(g, Icons.LAPTOP, chip2, 944, c(0xFFE0E0DC));
        UiFont.SEMIBOLD.drawMiddle(g, "Minecraft " + Client.ver, chip2 + 18, 944, c(TEXT));
        iconButton(g, Icons.SETTINGS, x + w - 39, 944, 26, ICON, () -> navigate(new Place(View.ALL, null)));

        boolean focused = !input.isEmpty() || binding == null;
        float typing = anim("typing", System.currentTimeMillis() - typedAt < 600 ? 1 : 0, 10);
        Render2D.shadow(g, x, y, w, h, 20, 32, 12, c(0x60000000));
        Render2D.roundedRect(g, x, y, w, h, 20, c(COMPOSER));
        Render2D.roundedOutline(g, x, y, w, h, 20, 1, c(Render2D.mix(COMPOSER_BORDER, COMPOSER_FOCUS, typing)));

        // Input line with a softly blinking caret.
        float textX = x + 16, textY = 998;
        float placeholder = anim("placeholder", input.isEmpty() ? 1 : 0, 20);
        if (placeholder > 0.01f) {
            String hint = CodexChat.state() == CodexChat.State.NO_ACCOUNT ? "先点击左下角头像导入账号 JSON" : "向 Codex 发消息…";
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

        // Model name in white, effort level in grey; clicking opens the picker, as Codex's does.
        boolean linked = CodexChat.state() != CodexChat.State.NO_ACCOUNT;
        String model = linked ? CodexChat.modelDisplay() : "未导入账号";
        String effort = linked ? " " + CodexChat.effortLabel(CodexChat.effort()) : "";
        float modelRight = x + w - 127;
        UiFont.SEMIBOLD.drawRight(g, effort, modelRight, rowY, c(ICON));
        UiFont.SEMIBOLD.drawRight(g, model, modelRight - UiFont.SEMIBOLD.width(effort), rowY, c(linked ? TEXT : ICON));
        UiFont.ICON_S.icon(g, Icons.CHEVRON_DOWN, modelRight + 15, rowY + 1, c(ICON));
        if (linked) {
            float modelLeft = modelRight - UiFont.SEMIBOLD.width(effort) - UiFont.SEMIBOLD.width(model);
            hit(modelLeft - 10, rowY - 14, modelRight + 26 - modelLeft, 28, () -> modelOpen = true, null);
        }
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

    private void drawCompletions(GuiGraphicsExtractor g, float x, float bottom, float w) {
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

    private void drawToast(GuiGraphicsExtractor g, float cx) {
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
    private void drawAccountPopup(GuiGraphicsExtractor g) {
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
        float x = ACCOUNT_PANEL_X, y = ACCOUNT_PANEL_Y + (1 - shown) * 12, w = ACCOUNT_PANEL_W, h = ACCOUNT_PANEL_H;
        Render2D.shadow(g, x, y, w, h, 16, 40, 16, c(Render2D.fade(0xB0000000, shown)));
        Render2D.roundedRect(g, x, y, w, h, 16, c(Render2D.fade(POPUP, shown)));
        Render2D.roundedOutline(g, x, y, w, h, 16, 1, c(Render2D.fade(POPUP_BORDER, shown)));
        UiFont.TITLE.drawMiddle(g, "Codex 账号", x + 28, y + 42, c(Render2D.fade(TEXT, shown)));
        iconButton(g, Icons.X, x + w - 42, y + 42, 32, ICON, () -> {
            accountOpen = false;
            accountFieldFocused = false;
        });
        Render2D.rect(g, x + 24, y + 68, w - 48, 1, c(Render2D.fade(HEADER_DIVIDER, shown)));

        CodexAccount acc = CodexChat.account();
        // Collected before the field's hit: later hits win, so controls still receive clicks.
        hit(x, y, w, h, () -> accountFieldFocused = false, null);

        float rowY = y + 96;
        if (acc == null) {
            Render2D.circle(g, x + 34, rowY, 4, c(Render2D.fade(0xFF5E6068, shown)));
            UiFont.SEMIBOLD.drawMiddle(g, "未导入账号", x + 48, rowY, c(Render2D.fade(TEXT_SIDEBAR, shown)));

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
            float clearW = 160, logoutW = 160;
            boolean clearHover = hover(x + 28, btnY, clearW, btnH);
            float clearT = anim("accountClearChat", clearHover ? 1 : 0, 14);
            Render2D.roundedRect(g, x + 28, btnY, clearW, btnH, 10,
                    c(Render2D.fade(Render2D.mix(BUTTON, CARD_HOVER, clearT), shown)));
            Render2D.roundedOutline(g, x + 28, btnY, clearW, btnH, 10, 1,
                    c(Render2D.fade(Render2D.mix(POPUP_BORDER, TEXT_SIDEBAR, clearT), shown)));
            UiFont.SEMIBOLD.drawCentered(g, "清空对话", x + 28 + clearW / 2, btnY + btnH / 2,
                    c(Render2D.fade(TEXT_SIDEBAR, shown)));
            hit(x + 28, btnY, clearW, btnH, this::clearChatAction, null);

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

    /** The model list and the pink effort slider, opened from the composer's model label. */
    private void drawModelPopup(GuiGraphicsExtractor g) {
        float shown = anim("modelPopup", modelOpen ? 1 : 0, 20);
        if (shown < 0.01f) return;
        g.nextStratum();
        float x = MODEL_POPUP_X, y = MODEL_POPUP_Y + (1 - shown) * 10, w = MODEL_POPUP_W;
        Render2D.shadow(g, x, y, MODEL_POPUP_W, MODEL_POPUP_H, 12, 30, 12, c(Render2D.fade(0x90000000, shown)));
        Render2D.roundedRect(g, x, y, w, MODEL_POPUP_H, 12, c(Render2D.fade(POPUP, shown)));
        Render2D.roundedOutline(g, x, y, w, MODEL_POPUP_H, 12, 1, c(Render2D.fade(POPUP_BORDER, shown)));
        UiFont.SMALL.drawMiddle(g, "选择模型", x + 22, y + 26, c(Render2D.fade(TEXT_SECTION, shown)));

        float rowY = y + 46;
        for (CodexChat.Model candidate : CodexChat.MODELS) {
            boolean active = candidate.slug().equals(CodexChat.model());
            boolean hovered = hover(x + 8, rowY, w - 16, 32);
            float t = anim("mrow" + candidate.slug(), active ? 1 : hovered ? 0.6f : 0, 18);
            Render2D.roundedRect(g, x + 8, rowY, w - 16, 32, 8, c(Render2D.fade(ITEM_ACTIVE, t * shown)));
            UiFont.SEMIBOLD.drawMiddle(g, candidate.name(), x + 22, rowY + 16,
                    c(Render2D.fade(active ? TEXT : TEXT_SIDEBAR, shown)));
            if (active) {
                UiFont.ICON_S.icon(g, Icons.CIRCLE_CHECK, x + w - 30, rowY + 16, c(Render2D.fade(0xFF4ADE80, shown)));
            }
            hit(x + 8, rowY, w - 16, 32, () -> {
                CodexChat.setModel(candidate.slug());
                showToast("模型: " + candidate.name());
            }, null);
            rowY += 34;
        }

        rowY += 6;
        Render2D.rect(g, x + 22, rowY, w - 44, 1, c(Render2D.fade(HEADER_DIVIDER, shown)));
        rowY += 18;
        UiFont.SMALL.drawMiddle(g, "推理力度", x + 22, rowY, c(Render2D.fade(TEXT_SECTION, shown)));
        UiFont.SEMIBOLD.drawCentered(g, CodexChat.effortLabel(CodexChat.effort()), x + w / 2, rowY,
                c(Render2D.fade(PINK, shown)));
        UiFont.SMALL.drawRight(g, CodexChat.effort(), x + w - 22, rowY, c(Render2D.fade(TEXT_MUTED, shown)));

        float trackX = x + 22, trackW = w - 44, trackY = rowY + 30;
        Render2D.roundedRect(g, trackX, trackY - 2, trackW, 4, 2, c(Render2D.fade(TRACK, shown)));
        int index = CodexChat.effortIndex();
        float step = trackW / (CodexChat.EFFORTS.length - 1);
        float fill = step * index;
        if (fill > 1) Render2D.roundedRect(g, trackX, trackY - 2, fill, 4, 2, c(Render2D.fade(PINK, shown)));
        for (int i = 0; i < CodexChat.EFFORTS.length; i++) {
            Render2D.circle(g, trackX + step * i, trackY, i <= index ? 3 : 2,
                    c(Render2D.fade(i <= index ? PINK : TRACK, shown)));
        }
        boolean onTrack = hover(trackX - 10, trackY - 16, trackW + 20, 32);
        Render2D.circle(g, trackX + fill, trackY, onTrack ? 10 : 9, c(Render2D.fade(0xFFF8F8F2, shown)));
        hit(trackX - 10, trackY - 16, trackW + 20, 32, () -> {
            int target = Math.round((mouseX - trackX) / step);
            target = Math.max(0, Math.min(CodexChat.EFFORTS.length - 1, target));
            CodexChat.setEffort(CodexChat.EFFORTS[target]);
            showToast("推理力度: " + CodexChat.effortLabel(CodexChat.effort()));
        }, null);
    }

    // ---- usage popup --------------------------------------------------------------------------

    /** Quota card from the x-codex-* headers, opened from the sidebar's bell. */
    private void drawUsagePopup(GuiGraphicsExtractor g) {
        float shown = anim("usagePopup", usageOpen ? 1 : 0, 20);
        if (shown < 0.01f) return;
        g.nextStratum();
        float x = USAGE_POPUP_X, y = USAGE_POPUP_Y + (1 - shown) * 10, w = USAGE_POPUP_W, h = USAGE_POPUP_H;
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
    private void refreshUsage() {
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

    private static String planName(String plan) {
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

    private void drawMenuDropdown(GuiGraphicsExtractor g) {
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

    private int lastMenu = -1;

    // ---- actions ------------------------------------------------------------------------------

    private void navigate(Place target) {
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

    private void goBack() {
        if (back.isEmpty()) return;
        forward.push(place);
        setPlace(back.pop());
    }

    private void goForward() {
        if (forward.isEmpty()) return;
        back.push(place);
        setPlace(forward.pop());
    }

    private void setPlace(Place target) {
        place = target;
        placeChangedAt = System.currentTimeMillis();
        targetScroll = 0;
        if (target.view() == View.RECENT) recentOpen = true;
    }

    private void select(Module module) {
        selected = module;
        binding = null;
    }

    private void toggleModule(Module module) {
        module.toggle();
        RECENT.remove(module);
        RECENT.addFirst(module);
        while (RECENT.size() > 8) RECENT.removeLast();
        if (!module.isMomentaryAction()) showToast(module.getName() + (module.isEnabled() ? " 已启用" : " 已关闭"));
        Client.instance.getConfigManager().saveAllConfig();
    }

    private void submit() {
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

    /** Non-command input with no module match becomes a Codex chat turn. */
    private void sendToCodex(String text) {
        CodexChat.ensureLoaded();
        if (CodexChat.state() == CodexChat.State.NO_ACCOUNT) {
            accountOpen = true;
            showToast("先导入账号 JSON 才能对话");
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

    private void runCommand(String text) {
        boolean known = Client.instance.getCommandManager().execute(text.substring(CommandManager.PREFIX.length()));
        showToast(known ? "已执行 " + text : "未知指令 " + text);
    }

    private void showToast(String text) {
        toastError = false;
        toast = text;
        toastAt = System.currentTimeMillis();
    }

    private void showToastError(String text) {
        toastError = true;
        toast = text;
        toastAt = System.currentTimeMillis();
    }

    private void updateCompletions() {
        completions = input.startsWith(CommandManager.PREFIX)
                ? Client.instance.getCommandManager().getCompletions(input)
                : List.of();
        completionIndex = Math.min(completionIndex, Math.max(0, completions.size() - 1));
    }

    private void applyCompletion() {
        if (completions.isEmpty()) return;
        String choice = completions.get(completionIndex);
        int space = input.lastIndexOf(' ');
        input = space < 0 ? choice + " " : input.substring(0, space + 1) + choice + " ";
        completionIndex = 0;
        updateCompletions();
    }

    private boolean isSearching() {
        return !input.isBlank() && !input.startsWith(CommandManager.PREFIX);
    }

    private List<Module> visibleModules() {
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

    private int count(Category category) {
        return Client.instance.getModuleManager().getModulesByCategory(category).size();
    }

    private long enabledCount() {
        return Client.instance.getModuleManager().getModules().stream()
                .filter(m -> m.isEnabled() && !m.isMomentaryAction()).count();
    }

    private void dragValue(NumberValue number) {
        float fraction = Math.max(0, Math.min(1, (mouseX - dragX) / dragW));
        number.set(number.getMin() + (number.getMax() - number.getMin()) * fraction);
    }

    /** Greedy line breaking that works for both spaced Latin text and Chinese. */
    private static List<String> wrap(UiFont font, String text, float maxWidth, int maxLines) {
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

    private float toDesignX(double guiX) {
        // Same half-pixel term the render-frame mouseX uses, so clicks and hover agree.
        return (float) ((guiX * minecraft.getWindow().getGuiScale() + minecraft.getWindow().getGuiScale() / 2f - originX) / s);
    }

    private float toDesignY(double guiY) {
        return (float) ((guiY * minecraft.getWindow().getGuiScale() + minecraft.getWindow().getGuiScale() / 2f - originY) / s);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        float x = toDesignX(event.x()), y = toDesignY(event.y());
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (accountOpen && (x < ACCOUNT_PANEL_X || x >= ACCOUNT_PANEL_X + ACCOUNT_PANEL_W
                || y < ACCOUNT_PANEL_Y || y >= ACCOUNT_PANEL_Y + ACCOUNT_PANEL_H)) {
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
        if (modelOpen && (x < MODEL_POPUP_X || x >= MODEL_POPUP_X + MODEL_POPUP_W
                || y < MODEL_POPUP_Y || y >= MODEL_POPUP_Y + MODEL_POPUP_H)) {
            modelOpen = false;
        }
        if (usageOpen && (x < USAGE_POPUP_X || x >= USAGE_POPUP_X + USAGE_POPUP_W
                || y < USAGE_POPUP_Y || y >= USAGE_POPUP_Y + USAGE_POPUP_H)) {
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

    private boolean isMenuHit(Hit hit) {
        return hit.y() < 46 && hit.x() < 420;
    }

    /**
     * Geometric hit-test of the account panel's controls, mirroring the draw code's numbers
     * exactly. Returns the action for the control under the cursor, or null when the click
     * lands on panel background or outside the panel.
     */
    private Runnable accountPanelAction(float px, float py) {
        if (!accountOpen) return null;
        float x = ACCOUNT_PANEL_X, y = ACCOUNT_PANEL_Y, w = ACCOUNT_PANEL_W, h = ACCOUNT_PANEL_H;
        if (px < x || px >= x + w || py < y || py >= y + h) return null;
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
            if (px >= x + 28 && px < x + 188) return this::clearChatAction;
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
    private void pasteClipboard() {
        String clip = minecraft.keyboardHandler.getClipboard();
        if (clip == null || clip.isBlank()) {
            showToastError("剪贴板是空的，先复制账号 JSON 内容");
            return;
        }
        appendAccountInput(clip);
        showToast("已粘贴 " + clip.length() + " 个字符");
    }

    /** Clears the login field; emptying an empty field says so instead of staying silent. */
    private void clearAccountInput() {
        if (accountInput.isEmpty()) {
            showToast("输入框本来就是空的");
            return;
        }
        accountInput = "";
        accountFieldFocused = false;
        showToast("已清空输入");
    }

    /** Clears the current conversation; empty list says so instead of appearing dead. */
    private void clearChatAction() {
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
    private void typeInto(String text) {
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

    private void appendAccountInput(String text) {
        if (accountInput.length() + text.length() > 200_000) {
            showToastError("内容过长");
            return;
        }
        accountInput += text;
        typedAt = System.currentTimeMillis();
    }

    private void deleteAccountChar(boolean wholeWord) {
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
                    modelOpen = false;
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
    private static String displayClip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    /** Clips a single-line field from the head so it plus {@code suffix} fits; one linear pass. */
    private static String fitLine(String text, String suffix, float maxWidth, UiFont font) {
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
    private void importAccountText(String text) {
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
    private int c(int color) {
        return Render2D.fade(color, alpha);
    }

    private boolean hover(float x, float y, float w, float h) {
        if (openMenu >= 0 && !(y < 46)) {
            // An open menu covers what is under it; only the menu itself and the title bar react.
            float mx = MENU_X[openMenu] - 8;
            if (mouseX >= mx && mouseX < mx + 220 && mouseY >= 44 && mouseY < 200) return false;
        }
        return mouseX >= Math.max(x, clipX0) && mouseY >= Math.max(y, clipY0)
                && mouseX < Math.min(x + w, clipX1) && mouseY < Math.min(y + h, clipY1);
    }

    /** Records a clickable area, cut to the current clip so hidden parts cannot be clicked. */
    private void hit(float x, float y, float w, float h, Runnable left, Runnable right) {
        float x0 = Math.max(x, clipX0), y0 = Math.max(y, clipY0);
        float x1 = Math.min(x + w, clipX1), y1 = Math.min(y + h, clipY1);
        if (x1 > x0 && y1 > y0) hits.add(new Hit(x0, y0, x1 - x0, y1 - y0, left, right));
    }

    private void pushClip(GuiGraphicsExtractor g, float x, float y, float w, float h) {
        clips.push(new float[]{clipX0, clipY0, clipX1, clipY1});
        clipX0 = Math.max(clipX0, x);
        clipY0 = Math.max(clipY0, y);
        clipX1 = Math.min(clipX1, x + w);
        clipY1 = Math.min(clipY1, y + h);
        g.enableScissor((int) Math.floor(clipX0), (int) Math.floor(clipY0),
                (int) Math.ceil(Math.max(clipX0, clipX1)), (int) Math.ceil(Math.max(clipY0, clipY1)));
    }

    private void popClip(GuiGraphicsExtractor g) {
        g.disableScissor();
        float[] previous = clips.pop();
        clipX0 = previous[0];
        clipY0 = previous[1];
        clipX1 = previous[2];
        clipY1 = previous[3];
    }

    private float anim(String key, float target, float speed) {
        return anim(key, null, target, speed);
    }

    /**
     * A value per key that eases towards {@code target}, keyed by a name plus the owner's identity.
     * It steps at most once per frame, so it can be read from several places while drawing.
     */
    private float anim(String name, Object owner, float target, float speed) {
        Object key = owner == null ? name : List.of(name, System.identityHashCode(owner));
        float[] state = animations.computeIfAbsent(key, k -> new float[]{target, -1});
        if (state[1] != frame) {
            state[1] = frame;
            float next = state[0] + (target - state[0]) * (1 - (float) Math.exp(-speed * dt));
            state[0] = Math.abs(next - target) < 0.001f ? target : next;
        }
        return state[0];
    }

    private float approach(float current, float target, float speed) {
        float next = current + (target - current) * (1 - (float) Math.exp(-speed * dt));
        return Math.abs(next - target) < 0.001f ? target : next;
    }

    private static float ease(float t) {
        float inv = 1 - Math.max(0, Math.min(1, t));
        return 1 - inv * inv * inv;
    }
}
