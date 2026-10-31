package wtf.carnation.ui.codex;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import wtf.carnation.Client;
import wtf.carnation.codex.CodexChat;
import wtf.carnation.graphics.Icons;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.manager.command.CommandManager;
import wtf.carnation.manager.command.impl.CommandBind;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.Value;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The ClickGUI as a one-to-one copy of the ZCode desktop app's home screen.
 *
 * <p>Coordinates are written in the pixels of a 1960x1003 screenshot of ZCode at 125% and
 * scaled by {@link #p} into the 1568x802 logical window, so every number can be checked against
 * the reference. Behaviour comes from {@link CodexScreen}: the composer, commands, IME, chat,
 * account and quota popups and the reasoning slider are the same.
 *
 * <p>Mapping: New task starts a conversation, the 对话 project lists conversations and every
 * other project is a module category whose tasks are its modules (click for settings, right-click
 * to toggle), Automations and the composer's orange badge show only enabled modules, typing
 * filters the projects, and the four chips under the composer fill in a prompt.
 */
public class ZCodeScreen extends CodexScreen {
    // Colours sampled from the screenshot.
    private static final int WINDOW = 0xFF2B2B2B, WINDOW_EDGE = 0xFF4A4A4A, CONTENT = 0xFF161616;
    private static final int DIVIDER = 0xFF363636, ROW_HOVER = 0xFF333333, ROW_ACTIVE = 0xFF383838;
    private static final int Z_TEXT = 0xFFD4D4D4, Z_MUTED = 0xFF909090, Z_DIM = 0xFF5E5E5E, Z_ARROW = 0xFF7F7F7F;
    private static final int Z_ORANGE = 0xFFFF8A30;
    private static final int COMPOSER_TOP = 0xFF222222, COMPOSER_BOX = 0xFF2B2B2B, COMPOSER_EDGE = 0xFF4B4B4B;
    private static final int CHIP_EDGE = 0xFF2E2E2E, CHIP_TEXT = 0xFF9C9C9C, RING = 0xFF454545, Z_SEND = 0xFF959595;
    private static final int Z_POPUP = 0xFF262626, Z_POPUP_EDGE = 0xFF3A3A3A;

    private static final String CHATS = "对话";
    private static final int CHILDREN_SHOWN = 5;
    private static final float CONTENT_X = 366, CONTENT_Y = 9, CONTENT_R = 1951, CONTENT_B = 994;
    private static final float PANE_W = 470;
    private static final float PROJECTS_TOP = 312, PROJECTS_BOTTOM = 932;

    private static final String[][] CHIPS = {
            {"Weekly Summary", "总结一下我这周的对话和常开的模块"},
            {"Error Fix", "帮我排查客户端最近一次报错的原因"},
            {"PPT Creation", "把 " + Client.name + " 的功能整理成一份演示大纲"},
            {"Idle-time task", "挂机的时候开哪些模块比较合适？"},
    };
    private static final char[] CHIP_ICONS = {Icons.ALARM_CLOCK_CHECK, Icons.BUG, Icons.PRESENTATION, Icons.MOON};

    /** Projects whose task lists are open; kept across openings like the app does. */
    private static final Set<String> OPEN = new HashSet<>(Set.of(CHATS));
    private static final Set<String> SHOW_ALL = new HashSet<>();
    private static boolean onlyEnabled;
    private static String currentProject = CHATS;

    private float sidebarScroll, sidebarTarget, sidebarMax;

    public ZCodeScreen() {
        super(1568, 802);
        usagePopupX = p(24);
        usagePopupY = p(930) - 252;
    }

    /** Screenshot pixels to logical pixels (the screenshot was taken at 125%). */
    private static float p(float v) {
        return v * 0.8f;
    }

    @Override
    protected EffortStyle effortStyle() {
        return new EffortStyle(1.0f, UiFont.Z_MEDIUM, UiFont.Z_SMALL, UiFont.SMALL, UiFont.Z_ICON, UiFont.Z_ICON_XS,
                Z_POPUP, Z_POPUP_EDGE, Z_TEXT, 0xFFA8A8A8, Z_MUTED, ROW_ACTIVE, 0xFF3C3C3C, 0xFFEDEDED);
    }

    // ---- frame --------------------------------------------------------------------------------

    @Override
    protected void drawWindow(GuiGraphicsExtractor g) {
        Render2D.shadow(g, 0, 0, W, H, 8, 56, 18, c(0x90000000));
        Render2D.shadow(g, 0, 0, W, H, 8, 8, 2, c(0x60000000));
        Render2D.roundedRect(g, 0, 0, W, H, 8, c(WINDOW));

        drawZSidebar(g);

        float cx0 = p(CONTENT_X), cy0 = p(CONTENT_Y);
        Render2D.roundedRect(g, cx0, cy0, p(CONTENT_R) - cx0, p(CONTENT_B) - cy0, p(10), c(CONTENT));
        drawZMain(g);
        drawTopRight(g);

        Render2D.roundedOutline(g, 0, 0, W, H, 8, 1, c(WINDOW_EDGE));
        drawAccountPopup(g);
        drawEffortPopup(g, effortStyle());
        drawUsagePopup(g);
    }

    // ---- sidebar ------------------------------------------------------------------------------

    private void drawZSidebar(GuiGraphicsExtractor g) {
        // The ZCode mark: a black tile with a white Z.
        Render2D.roundedRect(g, p(24), p(25), p(26), p(26), p(5), c(0xFF050505));
        Render2D.roundedOutline(g, p(24), p(25), p(26), p(26), p(5), 1, c(0xFF3C3E3E));
        UiFont.Z_BOLD.drawCentered(g, "Z", p(37), p(38), c(0xFFFFFFFF));
        navArrow(g, Icons.ARROW_LEFT, 75, !back.isEmpty(), this::goBack);
        navArrow(g, Icons.ARROW_RIGHT, 115, !forward.isEmpty(), this::goForward);

        menuRow(g, 95, Icons.MESSAGE_CIRCLE_PLUS, "New task", "Ctrl+N", mainView == MainView.CHAT
                && CodexChat.messages().isEmpty(), this::newTask);
        menuRow(g, 140, Icons.CALENDAR_CLOCK, "Automations", onlyEnabled ? String.valueOf(enabledCount()) : null,
                onlyEnabled, () -> onlyEnabled = !onlyEnabled);
        menuRow(g, 185, Icons.BLOCKS, "Plugin Marketplace", null, false,
                () -> minecraft.gui.setScreen(new CodexScreen()));
        Render2D.rect(g, p(1), p(220), p(364), 1, c(DIVIDER));

        UiFont.SMALL.drawMiddle(g, "Task list", p(24), p(251), c(Z_MUTED));
        smallIconButton(g, Icons.SEARCH, 298, 251, () -> {
            input = "";
            updateCompletions();
            showToast("直接输入即可搜索模块");
        });
        smallIconButton(g, Icons.LIST_FILTER, 334, 251, () -> onlyEnabled = !onlyEnabled);
        UiFont.SMALL.drawMiddle(g, "Projects", p(24), p(293), c(Z_DIM));

        drawProjects(g);
        drawProfile(g);
    }

    private void navArrow(GuiGraphicsExtractor g, char icon, float cx, boolean enabled, Runnable action) {
        float size = p(30), x = p(cx) - size / 2, y = p(38) - size / 2;
        float t = anim("zArrow" + icon, enabled && hover(x, y, size, size) ? 1 : 0, 18);
        Render2D.roundedRect(g, x, y, size, size, p(6), c(Render2D.fade(ROW_HOVER, t)));
        UiFont.Z_ICON.icon(g, icon, p(cx), p(38), c(enabled ? Render2D.mix(0xFFB0B0B0, Z_TEXT, t) : Z_ARROW));
        if (enabled) hit(x, y, size, size, action, null);
    }

    private void smallIconButton(GuiGraphicsExtractor g, char icon, float cx, float cy, Runnable action) {
        float size = p(28), x = p(cx) - size / 2, y = p(cy) - size / 2;
        float t = anim("zSmall" + icon, hover(x, y, size, size) ? 1 : 0, 18);
        Render2D.roundedRect(g, x, y, size, size, p(6), c(Render2D.fade(ROW_HOVER, t)));
        UiFont.Z_ICON.icon(g, icon, p(cx), p(cy), c(Render2D.mix(Z_MUTED, Z_TEXT, t)));
        hit(x, y, size, size, action, null);
    }

    /** New task / Automations / Plugin Marketplace: icon at 34, label at 54, shortcut right at 336. */
    private void menuRow(GuiGraphicsExtractor g, float cy, char icon, String label, String trailing, boolean active,
                         Runnable action) {
        float x = p(12), w = p(342), h = p(38), y = p(cy) - h / 2;
        float t = anim("zMenu" + label, active ? 1 : hover(x, y, w, h) ? 0.7f : 0, 18);
        Render2D.roundedRect(g, x, y, w, h, p(8), c(Render2D.fade(active ? ROW_ACTIVE : ROW_HOVER, t)));
        UiFont.Z_ICON.icon(g, icon, p(34), p(cy), c(Z_TEXT));
        UiFont.SMALL.drawMiddle(g, label, p(54), p(cy), c(Z_TEXT));
        if (trailing != null) UiFont.Z_SMALL.drawRight(g, trailing, p(336), p(cy), c(Z_DIM));
        hit(x, y, w, h, action, null);
    }

    private record Task(String label, String trailing, boolean bright, Runnable left, Runnable right, Object key) {
    }

    private record Project(String name, char icon, List<Task> tasks) {
    }

    private List<Project> projects() {
        String query = isSearching() ? input.trim().toLowerCase(Locale.ROOT) : null;
        List<Project> out = new ArrayList<>();

        List<Task> chats = new ArrayList<>();
        for (CodexChat.SessionInfo session : CodexChat.sessions()) {
            if (query != null && !session.title().toLowerCase(Locale.ROOT).contains(query)) continue;
            Path file = session.file();
            chats.add(new Task(session.title(), age(session.lastModified()),
                    file.equals(CodexChat.currentSessionFile()) && mainView == MainView.CHAT, () -> {
                renamePending = null;
                CodexChat.openSession(file);
                mainView = MainView.CHAT;
                chatFollow = true;
            }, () -> {
                renamePending = file;
                input = "";
                updateCompletions();
                showToast("在下方输入新标题，回车确认");
            }, file));
        }
        if (query == null || !chats.isEmpty()) out.add(new Project(CHATS, Icons.MESSAGE_CIRCLE, chats));

        for (Category category : Category.values()) {
            List<Task> tasks = new ArrayList<>();
            for (Module module : Client.instance.getModuleManager().getModulesByCategory(category)) {
                if (onlyEnabled && !module.isEnabled()) continue;
                if (query != null && !module.getName().toLowerCase(Locale.ROOT).contains(query)
                        && !module.getDescription().toLowerCase(Locale.ROOT).contains(query)) continue;
                String trailing = module.isEnabled() && !module.isMomentaryAction() ? "on"
                        : module.getKey() > 0 ? CommandBind.keyName(module.getKey()) : null;
                tasks.add(new Task(module.getName(), trailing, selected == module,
                        () -> select(selected == module ? null : module), () -> toggleModule(module), module));
            }
            if ((query != null || onlyEnabled) && tasks.isEmpty()) continue;
            out.add(new Project(category.getName(), Icons.FOLDER, tasks));
        }
        return out;
    }

    private static String age(long millis) {
        long minutes = Math.max(0, (System.currentTimeMillis() - millis) / 60_000);
        if (minutes < 1) return "now";
        if (minutes < 60) return minutes + "m";
        if (minutes < 60 * 24) return minutes / 60 + "h";
        return minutes / (60 * 24) + "d";
    }

    /** Folders at 40px steps; an open folder lists up to five tasks at 42.5px, then "Show more". */
    private void drawProjects(GuiGraphicsExtractor g) {
        boolean searching = isSearching();
        float top = p(PROJECTS_TOP), bottom = p(PROJECTS_BOTTOM);
        sidebarScroll = approach(sidebarScroll, sidebarTarget, 18);
        pushClip(g, 0, top, p(CONTENT_X), bottom - top);
        float y = p(331) - sidebarScroll;
        float start = y;
        for (Project project : projects()) {
            boolean open = searching || OPEN.contains(project.name());
            boolean empty = project.tasks().isEmpty();
            char icon = open && !empty ? Icons.FOLDER_OPEN : project.icon();
            int color = empty ? Z_DIM : Z_MUTED;
            String name = project.name();
            sidebarItem(g, y, p(34), icon, color, p(54), name, color, null, false, project, () -> {
                if (!OPEN.remove(name)) OPEN.add(name);
                currentProject = name;
            }, null);
            y += p(40);
            if (!open) continue;
            List<Task> tasks = project.tasks();
            boolean all = searching || SHOW_ALL.contains(name);
            int count = all ? tasks.size() : Math.min(CHILDREN_SHOWN, tasks.size());
            if (tasks.isEmpty() && CHATS.equals(name)) {
                UiFont.SMALL.drawMiddle(g, "No tasks yet", p(53), y + p(5), c(Z_DIM));
                y += p(42.5f);
            }
            for (int i = 0; i < count; i++) {
                Task task = tasks.get(i);
                String label = UiFont.SMALL.ellipsize(task.label(), p(240));
                sidebarItem(g, y + p(5), 0, (char) 0, 0, p(53), label, Z_TEXT, task.trailing(), task.bright(),
                        task.key(), task.left(), task.right());
                y += p(42.5f);
            }
            if (tasks.size() > CHILDREN_SHOWN && !searching) {
                String more = all ? "Show less" : "Show more";
                sidebarItem(g, y, 0, (char) 0, 0, p(54), more, Z_DIM, null, false, name + "#more", () -> {
                    if (!SHOW_ALL.remove(name)) SHOW_ALL.add(name);
                }, null);
                y += p(36);
            }
        }
        popClip(g);
        sidebarMax = Math.max(0, (y + sidebarScroll - start) - (bottom - top) + p(20));
        sidebarTarget = Math.max(0, Math.min(sidebarTarget, sidebarMax));
    }

    private void sidebarItem(GuiGraphicsExtractor g, float cy, float iconX, char icon, int iconColor, float textX,
                             String label, int textColor, String trailing, boolean active, Object key,
                             Runnable left, Runnable right) {
        float x = p(12), w = p(342), h = p(38), y = cy - h / 2;
        float t = anim("zItem", key, active ? 1 : hover(x, y, w, h) ? 0.7f : 0, 18);
        Render2D.roundedRect(g, x, y, w, h, p(8), c(Render2D.fade(active ? ROW_ACTIVE : ROW_HOVER, t)));
        if (icon != 0) UiFont.Z_ICON.icon(g, icon, iconX, cy, c(iconColor));
        UiFont.SMALL.drawMiddle(g, label, textX, cy, c(textColor));
        if (trailing != null) UiFont.Z_SMALL.drawRight(g, trailing, p(334), cy, c(Z_MUTED));
        hit(x, y, w, h, left, right);
    }

    private void drawProfile(GuiGraphicsExtractor g) {
        String name = minecraft.getUser().getName();
        String initials = name.length() >= 2 ? name.substring(0, 2).toUpperCase(Locale.ROOT) : "MC";
        float cx = p(40), cy = p(963), r = p(19);
        Render2D.circle(g, cx, cy, r, c(0xFFFDE3ED));
        Render2D.circleOutline(g, cx, cy, r, 1, c(0x40FFFFFF));
        UiFont.Z_SMALL.drawCentered(g, initials, cx, cy, c(0xFF7C4756));
        UiFont.Z_BOLD.drawMiddle(g, name, p(71), cy, c(Z_TEXT));
        hit(p(18), p(940), p(200), p(46), () -> accountOpen = !accountOpen, null);
        smallIconButton(g, Icons.TABLET, 276, 963, () -> {
            usageOpen = !usageOpen;
            if (usageOpen) refreshUsage();
        });
        smallIconButton(g, Icons.SETTINGS, 324, 963, () -> accountOpen = !accountOpen);
    }

    // ---- top-right controls -------------------------------------------------------------------

    private void drawTopRight(GuiGraphicsExtractor g) {
        topIcon(g, Icons.CIRCLE_HELP, 1737, () -> runCommand(".help"));
        topIcon(g, Icons.SQUARE_TERMINAL, 1774, () -> {
            input = CommandManager.PREFIX;
            typedAt = System.currentTimeMillis();
            updateCompletions();
        });
        topIcon(g, Icons.PANEL_RIGHT, 1812, () -> {
            if (selected != null) selected = null;
            else {
                List<Module> modules = Client.instance.getModuleManager().getModules();
                if (!modules.isEmpty()) select(modules.getFirst());
            }
        });
        topIcon(g, Icons.MINUS, 1849, this::onClose);
        topIcon(g, Icons.SQUARE, 1887, () -> selected = null);
        float x = p(1924) - p(18), y = p(38) - p(18), size = p(36);
        float t = anim("zClose", hover(x, y, size, size) ? 1 : 0, 20);
        Render2D.roundedRect(g, x, y, size, size, p(6), c(Render2D.fade(CodexTheme.CLOSE_HOVER, t)));
        UiFont.Z_ICON.icon(g, Icons.X, p(1924), p(38), c(Render2D.mix(Z_TEXT, 0xFFFFFFFF, t)));
        hit(x, y, size, size, this::onClose, null);
    }

    private void topIcon(GuiGraphicsExtractor g, char icon, float cx, Runnable action) {
        float size = p(34), x = p(cx) - size / 2, y = p(38) - size / 2;
        float t = anim("zTop" + icon, hover(x, y, size, size) ? 1 : 0, 18);
        Render2D.roundedRect(g, x, y, size, size, p(6), c(Render2D.fade(ROW_HOVER, t)));
        UiFont.Z_ICON.icon(g, icon, p(cx), p(38), c(Z_TEXT));
        hit(x, y, size, size, action, null);
    }

    // ---- main area ----------------------------------------------------------------------------

    private void drawZMain(GuiGraphicsExtractor g) {
        float pane = ease(anim("zPane", selected != null ? 1 : 0, 13));
        float right = p(CONTENT_R) - p(PANE_W) * pane;
        float cx = (p(CONTENT_X) + right) / 2;
        float composerW = Math.min(p(840), right - p(CONTENT_X) - p(60));
        float composerX = cx - composerW / 2;
        boolean chat = mainView == MainView.CHAT;
        float chatT = ease(anim("zChat", chat ? 1 : 0, 12));

        if (chatT < 0.99f) {
            float fade = 1 - chatT;
            drawMark(g, cx, fade);
            UiFont.Z_HERO.drawCentered(g, greeting(), cx, p(381), c(Render2D.fade(Z_TEXT, fade)));
        }
        // On the home screen the composer sits under the greeting; in a chat it drops to the bottom.
        float composerY = p(459) + (p(CONTENT_B - 30 - 182) - p(459)) * chatT;
        if (chatT > 0.01f) {
            pushClip(g, p(CONTENT_X), p(60), right - p(CONTENT_X), composerY - p(70));
            drawChat(g, composerX, composerW, p(80), composerY - p(16));
            popClip(g);
        }
        drawZComposer(g, composerX, composerY, composerW);
        if (chatT < 0.99f) drawChips(g, cx, composerY + p(212), 1 - chatT);
        if (pane > 0.002f) drawZPane(g, right, pane);
        drawToast(g, cx);
    }

    private static String greeting() {
        int hour = LocalTime.now().getHour();
        String part = hour < 5 ? "evening" : hour < 12 ? "morning" : hour < 18 ? "afternoon" : "evening";
        return "Good " + part + "! Leave the rest to me.";
    }

    /** The faint Z behind the greeting: a bar with a rounded right end over a long slanted stroke. */
    private void drawMark(GuiGraphicsExtractor g, float cx, float fade) {
        float dx = cx - p(1158.5f);
        int top = Render2D.fade(0xFF1D1D1D, fade), edge = Render2D.fade(0xFF262626, fade);
        int clear = Render2D.withAlpha(CONTENT, 0);
        // Bar: (1005,154) to (1168,154), curving down to (1090,206), back to (964,206).
        float[] bar = {1005, 154, 1168, 154, 1158, 172, 1140, 189, 1118, 200, 1090, 206, 964, 206};
        fillConvex(g, bar, dx, 154, 206, top, Render2D.mix(top, CONTENT, 0.25f));
        outline(g, bar, dx, edge);
        // Stroke: the top edge 1210..1377 at y 154, sides sloping 0.743 px left per px down, fading out by 345.
        float[] stroke = {1210, 154, 1377, 154, 1235, 345, 1068, 345};
        float[] ys = {154, 200, 250, 300, 345};
        for (int i = 0; i < ys.length - 1; i++) {
            float y0 = ys[i], y1 = ys[i + 1];
            float l0 = 1210 - (y0 - 154) * 0.743f, r0 = 1377 - (y0 - 154) * 0.743f;
            float l1 = 1210 - (y1 - 154) * 0.743f, r1 = 1377 - (y1 - 154) * 0.743f;
            float f0 = (y0 - 154) / 191f, f1 = (y1 - 154) / 191f;
            int c0 = c(Render2D.mix(top, CONTENT, f0)), c1 = c(Render2D.mix(top, CONTENT, f1));
            Render2D.quad(g, p(l0) + dx, p(y0), p(r0) + dx, p(y0), p(r1) + dx, p(y1), p(l1) + dx, p(y1), c0, c0, c1, c1);
            int e0 = c(Render2D.mix(edge, clear, f0));
            Render2D.line(g, p(l0) + dx, p(y0), p(l1) + dx, p(y1), 1, e0);
            Render2D.line(g, p(r0) + dx, p(y0), p(r1) + dx, p(y1), 1, e0);
        }
        Render2D.line(g, p(stroke[0]) + dx, p(154), p(stroke[2]) + dx, p(154), 1, c(edge));
    }

    /** A convex polygon (screenshot coordinates) with a vertical gradient between y0 and y1. */
    private void fillConvex(GuiGraphicsExtractor g, float[] pts, float dx, float y0, float y1, int top, int bottom) {
        int n = pts.length / 2;
        for (int i = 1; i < n - 1; i++) {
            int a = c(Render2D.mix(top, bottom, (pts[1] - y0) / (y1 - y0)));
            int b = c(Render2D.mix(top, bottom, (pts[i * 2 + 1] - y0) / (y1 - y0)));
            int d = c(Render2D.mix(top, bottom, (pts[i * 2 + 3] - y0) / (y1 - y0)));
            Render2D.quad(g, p(pts[0]) + dx, p(pts[1]), p(pts[i * 2]) + dx, p(pts[i * 2 + 1]),
                    p(pts[i * 2 + 2]) + dx, p(pts[i * 2 + 3]), p(pts[i * 2 + 2]) + dx, p(pts[i * 2 + 3]), a, b, d, d);
        }
    }

    private void outline(GuiGraphicsExtractor g, float[] pts, float dx, int color) {
        int n = pts.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            Render2D.line(g, p(pts[i * 2]) + dx, p(pts[i * 2 + 1]), p(pts[j * 2]) + dx, p(pts[j * 2 + 1]), 1, c(color));
        }
    }

    // ---- composer -----------------------------------------------------------------------------

    /** The 840x182 composer: a project chip on a #222 band, then the bordered input box. */
    private void drawZComposer(GuiGraphicsExtractor g, float x, float y, float w) {
        float r = p(18), boxY = y + p(50), boxH = p(132);
        Render2D.roundedRect(g, x, y, w, p(182), r, c(COMPOSER_TOP));

        // Project chip and the add button.
        float chipY = y + p(25);
        String project = currentProject;
        float chipX = x + p(33);
        float chipW = p(24) + UiFont.SMALL.width(project) + p(26);
        float ct = anim("zProject", hover(chipX - p(10), chipY - p(14), chipW, p(28)) ? 1 : 0, 18);
        Render2D.roundedRect(g, chipX - p(10), chipY - p(14), chipW, p(28), p(6), c(Render2D.fade(ROW_HOVER, ct)));
        UiFont.Z_ICON.icon(g, Icons.FOLDER, chipX, chipY, c(0xFF8C8C8C));
        UiFont.SMALL.drawMiddle(g, project, chipX + p(16), chipY, c(Z_TEXT));
        float chevronX = chipX + p(16) + UiFont.SMALL.width(project) + p(12);
        UiFont.Z_ICON_XS.icon(g, Icons.CHEVRON_DOWN, chevronX, chipY, c(0xFF8C8C8C));
        hit(chipX - p(10), chipY - p(14), chipW, p(28), () -> {
            if (!OPEN.remove(project)) OPEN.add(project);
        }, null);
        smallIconButton(g, Icons.PLUS, (chevronX + p(34)) / 0.8f, (chipY) / 0.8f, () -> {
            input = CommandManager.PREFIX;
            typedAt = System.currentTimeMillis();
            updateCompletions();
        });

        float typing = anim("typing", System.currentTimeMillis() - typedAt < 600 ? 1 : 0, 10);
        Render2D.roundedRect(g, x, boxY, w, boxH, r, c(COMPOSER_BOX));
        Render2D.roundedOutline(g, x, boxY, w, boxH, r, 1, c(Render2D.mix(COMPOSER_EDGE, 0xFF5E5E5E, typing)));

        // Input line.
        float textX = x + p(16), textY = boxY + p(28);
        float placeholder = anim("placeholder", input.isEmpty() ? 1 : 0, 20);
        if (placeholder > 0.01f) {
            String hint = renamePending != null ? "Type a new title for this task, Enter to save"
                    : CodexChat.state() == CodexChat.State.NO_ACCOUNT
                    ? "Import a Codex account from the avatar to start, . for commands"
                    : "Ask Codex anything, type to search modules, . for commands";
            UiFont.SMALL.drawMiddle(g, hint, textX + (1 - placeholder) * 6, textY,
                    c(Render2D.fade(Z_DIM, placeholder)));
        }
        String shown = fitLine(input, preedit, w - p(40), UiFont.SMALL);
        UiFont.SMALL.drawMiddle(g, shown, textX, textY, c(Z_TEXT));
        float afterInput = textX + UiFont.SMALL.width(shown);
        if (!preedit.isEmpty()) {
            UiFont.SMALL.drawMiddle(g, preedit, afterInput + 2, textY, c(0xBFD4D4D4));
            Render2D.rect(g, afterInput + 2, textY + 9, UiFont.SMALL.width(preedit), 1, c(0x66D4D4D4));
        }
        if (binding == null && !accountFieldFocused) {
            float caretX = afterInput + (preedit.isEmpty() ? 1 : UiFont.SMALL.width(preedit) + 4);
            double phase = (System.currentTimeMillis() - Math.max(openedAt, typedAt)) / 1060.0 * Math.PI * 2;
            float blink = System.currentTimeMillis() - typedAt < 500 ? 1 : (float) (0.5 + 0.5 * Math.cos(phase));
            Render2D.rect(g, caretX, textY - 8, 1.5f, 17, c(Render2D.fade(Z_TEXT, blink)));
        }
        float guiScale = minecraft.getWindow().getGuiScale();
        minecraft.textInputManager().setTextInputArea(
                Math.round((originX + textX * s) / guiScale), Math.round((originY + (textY - 12) * s) / guiScale),
                Math.round(w * s / guiScale), Math.round(24 * s / guiScale));

        // Bottom row: add, the orange badge, the context ring, model and send.
        float rowY = boxY + p(98);
        smallIconButton(g, Icons.PLUS, (x + p(34)) / 0.8f, rowY / 0.8f, () -> {
            input = CommandManager.PREFIX;
            typedAt = System.currentTimeMillis();
            updateCompletions();
        });
        String badge = enabledCount() + " modules on";
        float badgeX = x + p(68), badgeW = p(22) + UiFont.SMALL.width(badge) + p(24);
        float bt = anim("zBadge", hover(badgeX - p(8), rowY - p(15), badgeW, p(30)) || onlyEnabled ? 1 : 0, 18);
        Render2D.roundedRect(g, badgeX - p(8), rowY - p(15), badgeW, p(30), p(6),
                c(Render2D.fade(0x1FFF8A30, bt)));
        UiFont.Z_ICON.icon(g, Icons.SHIELD_ALERT, badgeX + p(9), rowY, c(Z_ORANGE));
        UiFont.SMALL.drawMiddle(g, badge, badgeX + p(24), rowY, c(Z_ORANGE));
        UiFont.Z_ICON_XS.icon(g, Icons.CHEVRON_DOWN, badgeX + p(24) + UiFont.SMALL.width(badge) + p(12), rowY,
                c(Z_ORANGE));
        hit(badgeX - p(8), rowY - p(15), badgeW, p(30), () -> onlyEnabled = !onlyEnabled, null);

        float sendX = x + w - p(34), sendR = p(15.5f);
        drawEffortTrigger(g, sendX - p(30), rowY, UiFont.Z_MEDIUM, UiFont.Z_ICON_XS, p(8), p(34), p(8),
                Z_TEXT, Z_MUTED, ROW_ACTIVE);
        effortAnchorRight = x + w;
        effortAnchorBottom = rowY - p(24);
        drawUsageRing(g, effortTriggerX - p(26), rowY);

        boolean busy = CodexChat.thinkingHere();
        float hov = anim("send", hover(sendX - sendR, rowY - sendR, sendR * 2, sendR * 2) ? 1 : 0, 20);
        float ready = anim("zSendReady", input.isBlank() ? 0 : 1, 16);
        float busyT = anim("sendBusy", busy ? 1 : 0, 16);
        int fill = Render2D.mix(Render2D.mix(Z_SEND, 0xFFE6E6E6, ready), CodexTheme.CLOSE_HOVER, busyT);
        Render2D.circle(g, sendX, rowY, sendR + hov * 0.8f, c(fill));
        if (busyT > 0.5f) UiFont.Z_ICON.icon(g, Icons.SQUARE, sendX, rowY, c(0xFFFFFFFF));
        else UiFont.Z_ICON.icon(g, Icons.ARROW_UP, sendX, rowY, c(0xFF2B2B2B));
        hit(sendX - sendR, rowY - sendR, sendR * 2, sendR * 2, busy ? CodexChat::cancel : this::submit, null);

        drawCompletions(g, x + p(17), y - p(8), w - p(34));
    }

    /** The context ring: grey track, filled clockwise by the quota window's used share once known. */
    private void drawUsageRing(GuiGraphicsExtractor g, float cx, float cy) {
        float r = p(9);
        Render2D.circleOutline(g, cx, cy, r, p(2), c(RING));
        CodexChat.Usage usage = CodexChat.usage();
        if (usage != null && usage.primaryUsedPercent() > 0) {
            int steps = Math.max(1, Math.round(usage.primaryUsedPercent() * 0.6f));
            for (int i = 0; i <= steps; i++) {
                double a = -Math.PI / 2 + Math.PI * 2 * usage.primaryUsedPercent() / 100.0 * i / steps;
                Render2D.circle(g, cx + (float) Math.cos(a) * (r - p(1)), cy + (float) Math.sin(a) * (r - p(1)),
                        p(1.1f), c(Z_MUTED));
            }
        }
        float t = anim("zRing", hover(cx - r - 4, cy - r - 4, r * 2 + 8, r * 2 + 8) ? 1 : 0, 18);
        if (t > 0.01f) Render2D.circleOutline(g, cx, cy, r + p(4), 1, c(Render2D.fade(ROW_ACTIVE, t)));
        hit(cx - r - 4, cy - r - 4, r * 2 + 8, r * 2 + 8, () -> {
            usageOpen = !usageOpen;
            if (usageOpen) refreshUsage();
        }, null);
    }

    /** Four outlined suggestion chips, 40px tall with 20px gaps, centred under the composer. */
    private void drawChips(GuiGraphicsExtractor g, float cx, float y, float fade) {
        float gap = p(20), h = p(40);
        float[] widths = new float[CHIPS.length];
        float total = -gap;
        for (int i = 0; i < CHIPS.length; i++) {
            widths[i] = p(43) + UiFont.SMALL.width(CHIPS[i][0]) + p(17);
            total += widths[i] + gap;
        }
        float x = cx - total / 2;
        for (int i = 0; i < CHIPS.length; i++) {
            float w = widths[i];
            float t = anim("zChip" + i, hover(x, y, w, h) ? 1 : 0, 18);
            Render2D.roundedRect(g, x, y, w, h, p(7), c(Render2D.fade(Render2D.mix(CONTENT, 0xFF1F1F1F, t), fade)));
            Render2D.roundedOutline(g, x, y, w, h, p(7), 1, c(Render2D.fade(Render2D.mix(CHIP_EDGE, 0xFF3D3D3D, t), fade)));
            int color = Render2D.mix(CHIP_TEXT, Z_TEXT, t);
            UiFont.Z_ICON.icon(g, CHIP_ICONS[i], x + p(26), y + h / 2, c(Render2D.fade(color, fade)));
            UiFont.SMALL.drawMiddle(g, CHIPS[i][0], x + p(43), y + h / 2, c(Render2D.fade(color, fade)));
            final String prompt = CHIPS[i][1];
            if (fade > 0.5f) {
                hit(x, y, w, h, () -> {
                    input = prompt;
                    typedAt = System.currentTimeMillis();
                    updateCompletions();
                }, null);
            }
            x += w + gap;
        }
    }

    // ---- settings pane ------------------------------------------------------------------------

    private void drawZPane(GuiGraphicsExtractor g, float x, float open) {
        if (selected != null) paneModule = selected;
        Module module = paneModule;
        if (module == null) return;
        float top = p(66), bottom = p(CONTENT_B), w = p(CONTENT_R) - x;
        pushClip(g, x, top, w, bottom - top);
        Render2D.shadow(g, x, top, w, bottom - top, 0, 40, 0, c(Render2D.fade(0x70000000, open)));
        Render2D.roundedRect(g, x, top, w + p(20), bottom - top, p(10), c(0xFF1B1B1B));
        Render2D.rect(g, x, top, 1, bottom - top, c(CHIP_EDGE));

        float px = x + p(28), pw = p(PANE_W) - p(56);
        float fade = open;
        float y = top + p(40);
        Render2D.roundedRect(g, px, y - p(20), p(40), p(40), p(8), c(Render2D.fade(ROW_ACTIVE, fade)));
        UiFont.Z_ICON.icon(g, CodexTheme.icon(module.getCategory()), px + p(20), y, c(Render2D.fade(Z_TEXT, fade)));
        UiFont.Z_BOLD.drawMiddle(g, module.getName(), px + p(54), y - p(8), c(Render2D.fade(Z_TEXT, fade)));
        UiFont.Z_SMALL.drawMiddle(g, module.getCategory().getName() + " · " + module.getCategory().getDisplayName(),
                px + p(54), y + p(12), c(Render2D.fade(Z_MUTED, fade)));
        float closeX = x + w - p(40);
        float ct = anim("zPaneClose", hover(closeX - p(16), y - p(16), p(32), p(32)) ? 1 : 0, 18);
        Render2D.roundedRect(g, closeX - p(16), y - p(16), p(32), p(32), p(6), c(Render2D.fade(ROW_HOVER, ct * fade)));
        UiFont.Z_ICON.icon(g, Icons.X, closeX, y, c(Render2D.fade(Z_MUTED, fade)));
        hit(closeX - p(16), y - p(16), p(32), p(32), () -> selected = null, null);

        y += p(44);
        for (String line : wrap(UiFont.Z_SMALL, module.getDescription(), pw, 3)) {
            UiFont.Z_SMALL.drawMiddle(g, line, px, y, c(Render2D.fade(0xFFB0B0B0, fade)));
            y += p(22);
        }

        y += p(14);
        float rowH = p(60);
        Render2D.roundedRect(g, px, y, pw, rowH, p(10), c(Render2D.fade(0xFF222222, fade)));
        Render2D.roundedOutline(g, px, y, pw, rowH, p(10), 1, c(Render2D.fade(CHIP_EDGE, fade)));
        UiFont.SMALL.drawMiddle(g, module.isMomentaryAction() ? "打开" : "启用模块", px + p(18), y + rowH / 2,
                c(Render2D.fade(Z_TEXT, fade)));
        if (module.isMomentaryAction()) {
            float bw = p(76), bx = px + pw - p(18) - bw, by = y + rowH / 2 - p(17);
            float t = anim("zPaneRun", hover(bx, by, bw, p(34)) ? 1 : 0, 18);
            Render2D.roundedRect(g, bx, by, bw, p(34), p(17), c(Render2D.fade(Render2D.mix(ROW_ACTIVE, 0xFFE6E6E6, t), fade)));
            UiFont.Z_SMALL.drawCentered(g, "运行", bx + bw / 2, by + p(17),
                    c(Render2D.fade(Render2D.mix(Z_TEXT, 0xFF1B1B1B, t), fade)));
            hit(bx, by, bw, p(34), () -> toggleModule(module), null);
        } else {
            drawSwitch(g, px + pw - p(18) - 36, y + rowH / 2 - 10, module.isEnabled(), module, fade,
                    () -> toggleModule(module));
        }
        y += rowH + p(26);

        List<Value<?>> values = module.getValues().stream().filter(Value::isAvailable).toList();
        UiFont.Z_SMALL.drawMiddle(g, "设置", px + 2, y, c(Render2D.fade(Z_MUTED, fade)));
        y += p(16);
        float blockH = 8;
        for (Value<?> value : values) blockH += rowHeight(value);
        blockH += 52;
        Render2D.roundedRect(g, px, y, pw, blockH, p(10), c(Render2D.fade(0xFF222222, fade)));
        Render2D.roundedOutline(g, px, y, pw, blockH, p(10), 1, c(Render2D.fade(CHIP_EDGE, fade)));
        float valueY = y + 4;
        for (Value<?> value : values) {
            drawValueRow(g, value, px + 16, valueY, pw - 32, fade);
            valueY += rowHeight(value);
            Render2D.rect(g, px + 16, valueY - 0.5f, pw - 32, 1, c(Render2D.fade(CHIP_EDGE, fade)));
        }
        drawBindRow(g, module, px + 16, valueY, pw - 32, fade);
        popClip(g);
    }

    // ---- actions & input ----------------------------------------------------------------------

    private void newTask() {
        CodexChat.newSession();
        renamePending = null;
        mainView = MainView.CHAT;
        chatFollow = true;
        currentProject = CHATS;
        OPEN.add(CHATS);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (toDesignX(x) < p(CONTENT_X) && !accountOpen) {
            sidebarTarget = Math.max(0, Math.min(sidebarMax, sidebarTarget - (float) scrollY * 40));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean ctrl = (event.modifiers() & InputConstants.MOD_CONTROL) != 0;
        if (ctrl && event.key() == InputConstants.KEY_N && binding == null && !accountFieldFocused) {
            newTask();
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE && binding == null && !accountFieldFocused
                && !modelOpen && !usageOpen && !accountOpen && input.isEmpty() && selected == null
                && mainView == MainView.CHAT) {
            mainView = MainView.MODULES;
            return true;
        }
        return super.keyPressed(event);
    }
}
