package wtf.carnation.ui.codex;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import wtf.carnation.codex.CodexChat;
import wtf.carnation.codex.provider.ApiModel;
import wtf.carnation.codex.provider.ApiProviderClient;
import wtf.carnation.codex.provider.ApiProviderConfig;
import wtf.carnation.codex.provider.ProviderProtocol;
import wtf.carnation.graphics.Icons;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static wtf.carnation.ui.codex.CodexTheme.*;

public final class ApiSettingsScreen extends Screen {
    private static final float W = 1180, H = 720;
    private static final int PAGE_SIZE = 6;

    private enum Field {NONE, API_KEY, BASE_URL, MODEL_ID, MODEL_NAME, MODEL_CONTEXT}

    private record Hit(float x, float y, float w, float h, Runnable action) {
        boolean contains(float px, float py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    private final Screen parent;
    private final ApiProviderConfig config;
    private final List<Hit> hits = new ArrayList<>();
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

    private int page;
    private Field field = Field.NONE;
    private int modelIndex = -1;
    private int cursor;
    private int selectionAnchor;
    private String status = "";
    private boolean statusError;
    private boolean fetching;
    private String preedit = "";
    private long typedAt;
    private float open = 1;
    private boolean closing;
    private float s = 1, mouseX = -1, mouseY = -1;
    private int originX, originY;

    public ApiSettingsScreen(Screen parent) {
        super(Component.literal("API 登录"));
        this.parent = parent;
        this.config = ApiProviderConfig.load();
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
        minecraft.onTextInputFocusChange(textAnchor, true);
    }

    @Override
    public void removed() {
        minecraft.onTextInputFocusChange(textAnchor, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int guiMouseX, int guiMouseY, float partialTick) {
        super.extractRenderState(g, guiMouseX, guiMouseY, partialTick);
        open = approach(open, closing ? 0 : 1, closing ? 20 : 12);
        if (closing && open < 0.02F) {
            saveIfConfigured();
            minecraft.gui.setScreen(parent);
            return;
        }
        float alpha = ease(open);
        int guiScale = minecraft.getWindow().getGuiScale();
        float fit = Math.min(minecraft.getWindow().getWidth() * 0.95F / W,
                minecraft.getWindow().getHeight() * 0.95F / H);
        // Draw at exactly the baked atlas scale: anything else resamples the font and blurs it.
        float baked = UiFont.useScale(fit);
        s = Math.min(baked, fit);
        originX = snap(Math.round((minecraft.getWindow().getWidth() - W * s) / 2F), guiScale);
        originY = snap(Math.round((minecraft.getWindow().getHeight() - H * s) / 2F), guiScale);
        mouseX = toDesignX(guiMouseX);
        mouseY = toDesignY(guiMouseY);
        hits.clear();

        g.pose().pushMatrix();
        g.pose().translate(originX / (float) guiScale, originY / (float) guiScale);
        g.pose().scale(s / guiScale, s / guiScale);
        draw(g, alpha);
        g.pose().popMatrix();
    }

    private void draw(GuiGraphicsExtractor g, float alpha) {
        int fade = Render2D.fade(0x94000000, alpha);
        Render2D.rect(g, -originX / s, -originY / s,
                minecraft.getWindow().getWidth() / s, minecraft.getWindow().getHeight() / s, fade);
        Render2D.shadow(g, 0, 0, W, H, 18, 52, 20, Render2D.fade(0xB0000000, alpha));
        Render2D.roundedRect(g, 0, 0, W, H, 18, Render2D.fade(CHROME, alpha));
        Render2D.roundedOutline(g, 0, 0, W, H, 18, 1, Render2D.fade(WINDOW_BORDER, alpha));

        UiFont.HERO.drawMiddle(g, "API 登录", 44, 52, Render2D.fade(TEXT, alpha));
        UiFont.SMALL.drawMiddle(g, "OpenAI · Anthropic · Gemini · OpenAI-compatible", 46, 82,
                Render2D.fade(TEXT_MUTED, alpha));
        iconButton(g, Icons.X, W - 44, 50, 34, alpha, this::onClose);

        drawProtocolPicker(g, alpha);
        drawInput(g, Field.API_KEY, "API Key", config.apiKey(), 44, 170, 520, true, alpha);
        drawInput(g, Field.BASE_URL, "Base URL", config.baseUrl(), 616, 170, 520, false, alpha);
        button(g, 44, 238, 148, 44, fetching ? "获取中…" : "获取模型", !fetching, alpha, this::fetchModels);
        button(g, 204, 238, 108, 44, "添加模型", true, alpha, this::addModel);

        drawModelTable(g, alpha);
        drawFooter(g, alpha);
    }

    private void drawProtocolPicker(GuiGraphicsExtractor g, float alpha) {
        UiFont.SEMIBOLD.drawMiddle(g, "协议", 44, 113, Render2D.fade(TEXT_SECTION, alpha));
        ProviderProtocol[] protocols = ProviderProtocol.values();
        float x = 118, y = 96, h = 34;
        for (ProviderProtocol protocol : protocols) {
            String label = shortProtocol(protocol);
            float w = UiFont.SEMIBOLD.width(label) + 28;
            boolean active = config.protocol() == protocol;
            boolean hovered = hover(x, y, w, h);
            float t = active ? 1 : hovered ? .45F : 0;
            Render2D.roundedRect(g, x, y, w, h, 10, Render2D.fade(Render2D.mix(CHROME, ITEM_ACTIVE, t), alpha));
            Render2D.roundedOutline(g, x, y, w, h, 10, 1,
                    Render2D.fade(active ? TAB_BORDER : Render2D.mix(PANEL_BORDER, CARD_BORDER_HOVER, t), alpha));
            UiFont.SEMIBOLD.drawCentered(g, label, x + w / 2, y + h / 2,
                    Render2D.fade(active ? TEXT : TEXT_SIDEBAR, alpha));
            hit(x, y, w, h, () -> {
                ProviderProtocol previous = config.protocol();
                config.protocol(protocol);
                if (config.baseUrl().isBlank() || config.baseUrl().equals(previous.defaultBaseUrl())) {
                    config.baseUrl(protocol.defaultBaseUrl());
                }
            });
            x += w + 8;
        }
    }

    private void drawInput(GuiGraphicsExtractor g, Field target, String label, String value,
                           float x, float y, float w, boolean secret, float alpha) {
        UiFont.SEMIBOLD.drawMiddle(g, label, x, y - 16, Render2D.fade(TEXT_SECTION, alpha));
        boolean focused = field == target;
        float typing = focused && System.currentTimeMillis() - typedAt < 600 ? 1 : 0;
        Render2D.roundedRect(g, x, y, w, 46, 12, Render2D.fade(CARD, alpha));
        Render2D.roundedOutline(g, x, y, w, 46, 12, 1,
                Render2D.fade(Render2D.mix(CARD_BORDER, COMPOSER_FOCUS, typing), alpha));
        String shown = secret ? "•".repeat(value.length()) : value;
        shown = fitSuffix(shown, w - 32);
        drawSelection(g, target, -1, shown, UiFont.BODY, x + 16, y + 25, alpha);
        UiFont.BODY.drawMiddle(g, shown, x + 16, y + 25, Render2D.fade(value.isEmpty() ? PLACEHOLDER : TEXT, alpha));
        if (focused) {
            int cursorIndex = Math.min(cursor, shown.length());
            float caretX = x + 18 + UiFont.BODY.width(shown.substring(0, cursorIndex));
            float blink = .5F + .5F * (float) Math.cos((System.currentTimeMillis() - typedAt) / 530.0);
            Render2D.rect(g, caretX, y + 12, 1.5F, 22, Render2D.fade(TEXT, blink));
        }
        hit(x, y, w, 46, () -> focusField(target, -1));
    }

    private void drawModelTable(GuiGraphicsExtractor g, float alpha) {
        float x = 44, y = 296, w = W - 88;
        Render2D.shadow(g, x, y, w, 320, 16, 34, 12, Render2D.fade(0x60000000, alpha));
        Render2D.roundedRect(g, x, y, w, 320, 16, Render2D.fade(SIDEBAR, alpha));
        Render2D.roundedOutline(g, x, y, w, 320, 16, 1, Render2D.fade(PANEL_BORDER, alpha));

        UiFont.SEMIBOLD.drawMiddle(g, "模型", x + 24, y + 32, Render2D.fade(TEXT, alpha));
        UiFont.SMALL.drawMiddle(g, "共 " + config.models().size() + " 个", x + 68, y + 32,
                Render2D.fade(TEXT_MUTED, alpha));
        UiFont.SMALL.drawRight(g, (page + 1) + "/" + (maxPage() + 1), x + w - 30, y + 32,
                Render2D.fade(TEXT_MUTED, alpha));
        smallButton(g, x + w - 168, y + 14, 30, 28, "<", page == 0, alpha, () -> page = Math.max(0, page - 1));
        smallButton(g, x + w - 132, y + 14, 30, 28, ">", page >= maxPage(), alpha,
                () -> page = Math.min(maxPage(), page + 1));

        float cols = x + 24;
        UiFont.SMALL.drawMiddle(g, "模型 ID", cols, y + 64, Render2D.fade(TEXT_SECTION, alpha));
        UiFont.SMALL.drawMiddle(g, "显示名称", cols + 438, y + 64, Render2D.fade(TEXT_SECTION, alpha));
        UiFont.SMALL.drawMiddle(g, "上下文", cols + 770, y + 64, Render2D.fade(TEXT_SECTION, alpha));
        UiFont.SMALL.drawMiddle(g, "操作", cols + 884, y + 64, Render2D.fade(TEXT_SECTION, alpha));
        Render2D.rect(g, x + 20, y + 82, w - 40, 1, Render2D.fade(HEADER_DIVIDER, alpha));

        int from = page * PAGE_SIZE;
        int visible = Math.min(PAGE_SIZE, Math.max(0, config.models().size() - from));
        for (int i = 0; i < visible; i++) {
            int index = from + i;
            ApiModel model = config.models().get(index);
            float rowY = y + 96 + i * 38;
            boolean selected = model.id().equals(config.selectedModel());
            boolean rowHover = hover(x + 16, rowY, w - 32, 34);
            float t = selected ? .65F : rowHover ? .3F : 0;
            Render2D.roundedRect(g, x + 16, rowY, w - 32, 34, 9,
                    Render2D.fade(Render2D.mix(SIDEBAR, ITEM_ACTIVE, t), alpha));
            drawCell(g, Field.MODEL_ID, index, model.id(), cols, rowY + 17, 402, alpha);
            drawCell(g, Field.MODEL_NAME, index, model.displayName(), cols + 438, rowY + 17, 300, alpha);
            drawCell(g, Field.MODEL_CONTEXT, index,
                    model.contextLength() > 0 ? model.contextLabel() : "未知",
                    cols + 770, rowY + 17, 86, alpha);
            smallButton(g, cols + 868, rowY + 3, 60, 28, selected ? "已选" : "选择", selected, alpha, () -> {
                config.selectedModel(config.models().get(index).id());
                status = "已选择 " + config.modelDisplay();
                statusError = false;
            });
            smallButton(g, cols + 936, rowY + 3, 52, 28, "删除", false, alpha, () -> deleteModel(index));
        }
        if (config.models().isEmpty()) {
            UiFont.BODY.drawCentered(g, "点击「获取模型」自动拉取，或手动添加模型", x + w / 2, y + 170,
                    Render2D.fade(TEXT_MUTED, alpha));
        }
    }

    private void drawCell(GuiGraphicsExtractor g, Field target, int index, String value,
                          float x, float centerY, float w, float alpha) {
        boolean focused = field == target && modelIndex == index;
        float y = centerY - 17;
        Render2D.roundedRect(g, x, y, w, 34, 8, Render2D.fade(focused ? CARD_HOVER : CARD, alpha));
        Render2D.roundedOutline(g, x, y, w, 34, 8, 1,
                Render2D.fade(focused ? COMPOSER_FOCUS : CARD_BORDER, alpha));
        String shown = fitSuffix(value, w - 20);
        drawSelection(g, target, index, shown, UiFont.SMALL, x + 10, centerY, alpha);
        UiFont.SMALL.drawMiddle(g, shown, x + 10, centerY, Render2D.fade(TEXT_SIDEBAR, alpha));
        if (focused) {
            int cursorIndex = Math.min(cursor, shown.length());
            float caretX = x + 11 + UiFont.SMALL.width(shown.substring(0, cursorIndex));
            float blink = .5F + .5F * (float) Math.cos((System.currentTimeMillis() - typedAt) / 530.0);
            Render2D.rect(g, caretX, y + 7, 1.2F, 20, Render2D.fade(TEXT, blink));
        }
        hit(x, y, w, 34, () -> focusField(target, index));
    }

    private void drawSelection(GuiGraphicsExtractor g, Field target, int index, String shown,
                               UiFont font, float x, float centerY, float alpha) {
        boolean focused = field == target && (target == Field.API_KEY || target == Field.BASE_URL || modelIndex == index);
        if (!focused || cursor == selectionAnchor) return;
        int start = Math.max(0, Math.min(shown.length(), Math.min(cursor, selectionAnchor)));
        int end = Math.max(0, Math.min(shown.length(), Math.max(cursor, selectionAnchor)));
        if (start == end) return;
        float left = font.width(shown.substring(0, start));
        float right = font.width(shown.substring(0, end));
        Render2D.rect(g, x + left, centerY - 10, Math.max(2, right - left), 20,
                Render2D.fade(0x995A8DDE, alpha));
    }

    private void focusField(Field target, int index) {
        field = target;
        modelIndex = index;
        String value = currentValue();
        cursor = value.length();
        selectionAnchor = cursor;
        typedAt = System.currentTimeMillis();
    }

    private void drawFooter(GuiGraphicsExtractor g, float alpha) {
        if (!status.isEmpty()) {
            UiFont.BODY.drawMiddle(g, status, 44, 630,
                    Render2D.fade(statusError ? 0xFFFF6F61 : TEXT_SIDEBAR, alpha));
        }
        button(g, W - 414, 646, 166, 46, "保存并启用", true, alpha, this::save);
        button(g, W - 232, 646, 188, 46, "关闭", false, alpha, this::onClose);
    }

    private void button(GuiGraphicsExtractor g, float x, float y, float w, float h, String label,
                        boolean primary, float alpha, Runnable action) {
        boolean hovered = hover(x, y, w, h);
        float t = hovered ? 1 : 0;
        int background = Render2D.mix(primary ? SEND : BUTTON, primary ? 0xFF7D7F8C : CARD_HOVER, t);
        Render2D.roundedRect(g, x, y, w, h, 12, Render2D.fade(background, alpha));
        UiFont.SEMIBOLD.drawCentered(g, label, x + w / 2, y + h / 2,
                Render2D.fade(primary ? SEND_GLYPH : TEXT_SIDEBAR, alpha));
        hit(x, y, w, h, action);
    }

    private void smallButton(GuiGraphicsExtractor g, float x, float y, float w, float h, String label,
                             boolean active, float alpha, Runnable action) {
        boolean hovered = hover(x, y, w, h);
        float t = active ? 1 : hovered ? .5F : 0;
        Render2D.roundedRect(g, x, y, w, h, 8, Render2D.fade(Render2D.mix(CHROME, ITEM_ACTIVE, t), alpha));
        UiFont.SMALL.drawCentered(g, label, x + w / 2, y + h / 2,
                Render2D.fade(active ? TEXT : TEXT_SIDEBAR, alpha));
        hit(x, y, w, h, action);
    }

    private void iconButton(GuiGraphicsExtractor g, char icon, float cx, float cy, float size,
                            float alpha, Runnable action) {
        boolean hovered = hover(cx - size / 2, cy - size / 2, size, size);
        float t = hovered ? 1 : 0;
        Render2D.roundedRect(g, cx - size / 2, cy - size / 2, size, size, 9,
                Render2D.fade(Render2D.mix(CHROME, ITEM_ACTIVE, t), alpha));
        UiFont.ICON_M.icon(g, icon, cx, cy, Render2D.fade(Render2D.mix(ICON, TEXT, t), alpha));
        hit(cx - size / 2, cy - size / 2, size, size, action);
    }

    private void addModel() {
        List<ApiModel> models = new ArrayList<>(config.models());
        String id = uniqueModelId(models);
        models.add(new ApiModel(id, id, 0));
        config.models(models);
        if (config.selectedModel().isBlank()) config.selectedModel(id);
        page = maxPage();
        focusField(Field.MODEL_ID, config.models().size() - 1);
        status = "已添加模型，请填写 ID";
        statusError = false;
    }

    private void deleteModel(int index) {
        List<ApiModel> models = new ArrayList<>(config.models());
        if (index < 0 || index >= models.size()) return;
        models.remove(index);
        config.models(models);
        if (config.models().stream().noneMatch(model -> model.id().equals(config.selectedModel()))) {
            config.selectedModel(config.models().isEmpty() ? "" : config.models().getFirst().id());
        }
        page = Math.min(page, maxPage());
        if (modelIndex == index) {
            field = Field.NONE;
            modelIndex = -1;
        }
    }

    private void fetchModels() {
        if (fetching) return;
        field = Field.NONE;
        modelIndex = -1;
        fetching = true;
        status = "正在获取模型…";
        statusError = false;
        ApiProviderConfig copy = config.copy();
        CompletableFuture.supplyAsync(() -> {
            try {
                return ApiProviderClient.fetchModels(copy);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((models, error) -> minecraft.execute(() -> {
            fetching = false;
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                status = "获取失败: " + cause.getMessage();
                statusError = true;
            } else if (models == null || models.isEmpty()) {
                status = "API 未返回任何模型";
                statusError = true;
            } else {
                config.models(models);
                if (config.selectedModel().isBlank()
                        || models.stream().noneMatch(model -> model.id().equals(config.selectedModel()))) {
                    config.selectedModel(models.getFirst().id());
                }
                page = 0;
                status = "已获取 " + models.size() + " 个模型";
                statusError = false;
            }
        }));
    }

    private void save() {
        if (config.apiKey().isBlank()) {
            fail("API Key 不能为空");
            return;
        }
        if (config.baseUrl().isBlank()) {
            fail("Base URL 不能为空");
            return;
        }
        if (config.selectedModel().isBlank()) {
            fail("请先获取或选择模型");
            return;
        }
        try {
            // A relay Base URL already names its protocol; picking the other one talks to a path
            // that only answers "404 page not found".
            ProviderProtocol detected = ProviderProtocol.detect(config.baseUrl());
            if (detected != null && detected != config.protocol()) config.protocol(detected);
            config.enabled(true);
            config.save();
            CodexChat.reloadProvider();
            status = "已保存并启用: " + config.modelDisplay();
            statusError = false;
        } catch (Exception e) {
            status = "保存失败: " + e.getMessage();
            statusError = true;
        }
    }

    private void fail(String message) {
        status = message;
        statusError = true;
    }

    private void saveIfConfigured() {
        if (!config.configured()) return;
        try {
            config.save();
            CodexChat.reloadProvider();
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        float x = toDesignX(event.x()), y = toDesignY(event.y());
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(x, y)) {
                hit.action.run();
                return true;
            }
        }
        field = Field.NONE;
        modelIndex = -1;
        cursor = 0;
        selectionAnchor = 0;
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (field == Field.NONE || !event.isAllowedChatCharacter()) return false;
        type(event.codepointAsString());
        return true;
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        preedit = event == null ? "" : event.fullText();
        return true;
    }

    @Override
    protected void insertText(String text, boolean narrate) {
        if (field == Field.NONE || text == null || text.isEmpty()) return;
        type(text.replace("\n", "").replace("\r", ""));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean shift = event.hasShiftDown();
        if (event.isEscape()) {
            if (field != Field.NONE) {
                field = Field.NONE;
                modelIndex = -1;
            } else onClose();
            return true;
        }
        if (field == Field.NONE) return super.keyPressed(event);
        String value = currentValue();
        int length = value.length();
        cursor = Math.max(0, Math.min(length, cursor));
        selectionAnchor = Math.max(0, Math.min(length, selectionAnchor));

        if (event.isSelectAll()) {
            selectionAnchor = 0;
            cursor = length;
            return true;
        }
        if (event.isCopy()) {
            copySelection(value);
            return true;
        }
        if (event.isCut()) {
            copySelection(value);
            replaceSelection("");
            return true;
        }
        if (event.isPaste()) {
            replaceSelection(minecraft.keyboardHandler.getClipboard().replaceAll("[\\r\\n]", " "));
            return true;
        }

        switch (key) {
            case InputConstants.KEY_BACKSPACE -> {
                if (hasSelection()) replaceSelection("");
                else if (cursor > 0) {
                    int start = wholeWordStart(value, cursor);
                    applyText(value.substring(0, start) + value.substring(cursor));
                    cursor = start;
                    selectionAnchor = start;
                }
                typedAt = System.currentTimeMillis();
            }
            case InputConstants.KEY_DELETE -> {
                if (hasSelection()) replaceSelection("");
                else if (cursor < length) {
                    int end = value.offsetByCodePoints(cursor, 1);
                    applyText(value.substring(0, cursor) + value.substring(end));
                }
                typedAt = System.currentTimeMillis();
            }
            case InputConstants.KEY_LEFT -> moveCursor(-1, shift, value);
            case InputConstants.KEY_RIGHT -> moveCursor(1, shift, value);
            case InputConstants.KEY_HOME -> {
                cursor = 0;
                if (!shift) selectionAnchor = cursor;
            }
            case InputConstants.KEY_END -> {
                cursor = length;
                if (!shift) selectionAnchor = cursor;
            }
            default -> {
            }
        }
        return true;
    }

    private void type(String text) {
        if (text == null || text.isEmpty()) return;
        if (field == Field.MODEL_CONTEXT && !text.matches("\\d+")) return;
        replaceSelection(text);
        typedAt = System.currentTimeMillis();
    }

    private boolean hasSelection() {
        return cursor != selectionAnchor;
    }

    private void copySelection(String value) {
        int start = Math.max(0, Math.min(value.length(), Math.min(cursor, selectionAnchor)));
        int end = Math.max(0, Math.min(value.length(), Math.max(cursor, selectionAnchor)));
        if (start != end) minecraft.keyboardHandler.setClipboard(value.substring(start, end));
    }

    private void replaceSelection(String insert) {
        String value = currentValue();
        int start = Math.max(0, Math.min(value.length(), Math.min(cursor, selectionAnchor)));
        int end = Math.max(0, Math.min(value.length(), Math.max(cursor, selectionAnchor)));
        applyText(value.substring(0, start) + insert + value.substring(end));
        cursor = start + insert.length();
        selectionAnchor = cursor;
    }

    private void moveCursor(int direction, boolean select, String value) {
        if (!select && hasSelection()) {
            cursor = direction < 0
                    ? Math.min(cursor, selectionAnchor) : Math.max(cursor, selectionAnchor);
            selectionAnchor = cursor;
            return;
        }
        if (direction < 0 && cursor > 0) cursor = value.offsetByCodePoints(cursor, -1);
        if (direction > 0 && cursor < value.length()) cursor = value.offsetByCodePoints(cursor, 1);
        if (!select) selectionAnchor = cursor;
    }

    private static int wholeWordStart(String value, int cursor) {
        int index = cursor;
        while (index > 0 && Character.isWhitespace(value.charAt(index - 1))) index--;
        while (index > 0 && !Character.isWhitespace(value.charAt(index - 1))) index--;
        return index;
    }

    private String currentValue() {
        if (field == Field.API_KEY) return config.apiKey();
        if (field == Field.BASE_URL) return config.baseUrl();
        if (modelIndex < 0 || modelIndex >= config.models().size()) return "";
        ApiModel model = config.models().get(modelIndex);
        return switch (field) {
            case MODEL_ID -> model.id();
            case MODEL_NAME -> model.displayName();
            case MODEL_CONTEXT -> model.contextLength() > 0 ? String.valueOf(model.contextLength()) : "";
            default -> "";
        };
    }

    private void applyText(String value) {
        if (value.length() > 8192) return;
        switch (field) {
            case API_KEY -> config.apiKey(value);
            case BASE_URL -> config.baseUrl(value);
            case MODEL_ID -> updateModel(new ApiModel(value, currentModel().displayName(),
                    currentModel().contextLength()));
            case MODEL_NAME -> updateModel(new ApiModel(currentModel().id(), value,
                    currentModel().contextLength()));
            case MODEL_CONTEXT -> {
                int context;
                try {
                    context = value.isBlank() ? 0 : Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    context = 0;
                }
                updateModel(new ApiModel(currentModel().id(), currentModel().displayName(), context));
            }
            default -> {
            }
        }
    }

    private ApiModel currentModel() {
        if (modelIndex < 0 || modelIndex >= config.models().size()) return new ApiModel("", "", 0);
        return config.models().get(modelIndex);
    }

    private void updateModel(ApiModel model) {
        if (modelIndex < 0 || modelIndex >= config.models().size()) return;
        List<ApiModel> models = new ArrayList<>(config.models());
        models.set(modelIndex, model);
        config.models(models);
    }

    private boolean hover(float x, float y, float w, float h) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    private void hit(float x, float y, float w, float h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    private float toDesignX(double guiX) {
        return (float) (guiX * minecraft.getWindow().getGuiScale() - originX) / s;
    }

    private float toDesignY(double guiY) {
        return (float) (guiY * minecraft.getWindow().getGuiScale() - originY) / s;
    }

    private int maxPage() {
        return Math.max(0, (Math.max(0, config.models().size() - 1)) / PAGE_SIZE);
    }

    private String fitSuffix(String text, float maxWidth) {
        if (UiFont.BODY.width(text) <= maxWidth) return text;
        String tail = text.length() > 18 ? "…" + text.substring(text.length() - 17) : text;
        while (tail.length() > 1 && UiFont.BODY.width(tail) > maxWidth) tail = "…" + tail.substring(2);
        return tail;
    }

    private static String shortProtocol(ProviderProtocol protocol) {
        return switch (protocol) {
            case OPENAI_CHAT -> "OpenAI";
            case OPENAI_RESPONSES -> "Responses";
            case ANTHROPIC_MESSAGES -> "Anthropic";
            case GEMINI -> "Gemini";
        };
    }

    private static String uniqueModelId(List<ApiModel> models) {
        String base = "new-model";
        int index = 2;
        while (true) {
            String candidate = index == 2 ? base : base + "-" + index;
            if (models.stream().noneMatch(model -> model.id().equals(candidate))) return candidate;
            index++;
        }
    }

    private static float approach(float current, float target, float speed) {
        // Time-based, like the Codex window: a fixed per-frame factor of (1 - .05 * speed) hits
        // exactly zero at speed 20 and the value then never moves, which stranded closing windows.
        float dt = 1 / 60f;
        float next = current + (target - current) * (1 - (float) Math.exp(-speed * dt));
        return Math.abs(next - target) < .001F ? target : next;
    }

    private static float ease(float t) {
        float inverse = 1 - Math.max(0, Math.min(1, t));
        return 1 - inverse * inverse * inverse;
    }
}
