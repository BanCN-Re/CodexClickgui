# CodexClickgui

Codex 桌面端风格的 Minecraft ClickGUI（Carnation 客户端模块面），目标版本 **Minecraft 26.3 / Fabric Loader 0.19.5 / Java 25**。

本仓库是可构建的完整工程：打开 GUI、分类浏览、模块卡片、右侧设置面板、搜索与 `.` 命令都已接好。下面说明怎么接到你自己的客户端里，以及配色/样式从哪里改。

## 快速运行（本仓库）

```bash
./gradlew runClient
```

进游戏后按 **Right Shift** 打开 ClickGUI（模块名 `ClickGui`，分类 Render）。关掉 GUI 会自动存配置。

## 接入用法

核心入口只有两层：

1. **模块开关**：`wtf.carnation.module.impl.render.ClickGui` 在 `onEnable` 里打开界面，然后立刻关掉自己（瞬时动作）。
2. **界面本身**：`wtf.carnation.ui.codex.CodexScreen`，继承原版 `Screen`，不暂停游戏。

### 在现有客户端里打开界面

任意地方（按键绑定、命令、别的模块）执行：

```java
mc.execute(() -> mc.gui.setScreen(new wtf.carnation.ui.codex.CodexScreen()));
```

本仓库的默认绑定示例：

```java
// ClickGui.java
setKey(InputConstants.KEY_RSHIFT);

@Override
protected void onEnable() {
    mc.execute(() -> mc.gui.setScreen(new CodexScreen()));
    setState(false); // 打开后立刻关闭模块开关，避免一直“启用”
}
```

### 接到你自己的 Module 系统

`CodexScreen` 通过 `Client.instance.getModuleManager()` 拉模块列表，并读写：

- `Module#getName` / `getDisplay` / `getCategory` / `getKey` / `isEnabled` / `toggle`
- `Module#getValues()` 里的 `BoolValue` / `ModeValue` / `NumberValue`

如果你的包名或 API 不同，需要二选一：

- 把本仓库的 `Module` / `Category` / `Value` 体系一起迁过去；或
- 改 `CodexScreen` 里读模块的地方，映射到你自己的类型。

分类枚举在 `wtf.carnation.module.Category`（Combat / Movement / Player / World / Render / Misc），侧栏显示用 `displayName`（中文）。

### 依赖与渲染前提

界面不是用原版 Widget 拼的，而是即时模式绘制，依赖本仓库这些图元与字体：

| 组件 | 作用 |
|------|------|
| `wtf.carnation.graphics.Render2D` | 圆角矩形、描边、阴影、裁剪、颜色混合 |
| `wtf.carnation.graphics.UiFont` | Inter / MiSans / Lucide 图标的烘焙 TrueType |
| `wtf.carnation.graphics.Icons` | Lucide 码点常量 |
| `lol.mixin.MixinTrueTypeGlyphProvider*` | 让自定义 TTF 能正确出字形 |
| `carnation.accesswidener` | 访问绘制所需的内部字段 |

字体资源在 `src/main/resources/assets/carnation/font/`（含 `inter-*.ttf`、`misans-gb2312.ttf`、`lucide.ttf` 与各号字 json）。迁 GUI 时请整目录带走，否则侧栏/卡片文字和图标会对不齐。

### 命令与配置（可选）

- 界面底部输入框：普通文字会过滤模块；以 `.` 开头走 `CommandManager`（如 `.bind`、`.toggle`）。
- 关闭界面时调用 `ConfigManager#saveAllConfig`；配置目录默认在游戏目录下的 `Carnation/`。

只想要外观、不要命令系统时，可删掉 composer 里对 `CommandManager` 的分支，保留搜索过滤即可。

## 样式说明

所有颜色集中在 `wtf.carnation.ui.codex.CodexTheme`，按 Codex 桌面端截图像素取样。改主题优先改这个类，不要在 `CodexScreen` 里散落硬编码。

### 表面

| 常量 | ARGB | 用途 |
|------|------|------|
| `CHROME` | `#22232D` | 顶栏/窗口铬合金区 |
| `SIDEBAR` | `#262733` | 侧栏与右侧设置面板 |
| `MAIN` | `#282A36` | 主内容区 |
| `PANEL_BORDER` / `WINDOW_BORDER` | `#2C2D38` / `#3A3B46` | 面板与窗口描边 |
| `SIDEBAR_DIVIDER` / `HEADER_DIVIDER` | `#33343D` / `#393B46` | 分割线 |
| `HUD` | `#EB282A36` | 游戏内 HUD 卡片（半透明 MAIN） |

### 控件

| 常量 | 用途 |
|------|------|
| `RAIL_ACTIVE` / `ITEM_HOVER` / `ITEM_ACTIVE` | 左侧 rail、侧栏行选中与悬停 |
| `TAB` / `TAB_BORDER` | 顶栏菜单标签 |
| `BUTTON` / `CHIP_BAR` | 按钮与芯片条 |
| `COMPOSER` / `COMPOSER_BORDER` / `COMPOSER_FOCUS` | 底部输入框与焦点态 |
| `CARD` / `CARD_HOVER` / `CARD_BORDER*` | 模块卡片 |
| `POPUP` / `POPUP_BORDER` | 弹出层 |
| `TRACK` | 开关轨道 |
| `CLOSE_HOVER` | 关闭按钮悬停（红） |

### 文字与强调色

| 常量 | 用途 |
|------|------|
| `TEXT` | 主文字 `#F8F8F2` |
| `TEXT_SIDEBAR` / `TEXT_SECTION` / `TEXT_MUTED` | 侧栏、分组标题、次要文字 |
| `PLACEHOLDER` / `ICON` / `LOGO` | 占位符、图标、Logo 环 |
| `ORANGE` | Codex 橙强调 `#F28149` |
| `AVATAR` | 头像紫 `#9B59B6` |
| `SEND` / `SEND_GLYPH` | 发送按钮底与字形 |

分类图标由 `CodexTheme.icon(Category)` 映射到 Lucide（剑、脚印、用户、地球、眼睛、省略号）。

### 布局刻度

`CodexScreen` 按 **1551×1115** 的 Codex 窗口像素坐标系写死布局，每帧再按 `UiFont` 烘焙比例缩放到游戏窗口，保证形状与 TrueType 同像素对齐。关键宽度：

- 左侧 rail：`64`
- 侧栏：`303`（可折叠）
- 右侧设置 pane：`404`
- 内容区圆角：`14`

改布局改这些常量；改观感改 `CodexTheme`。

## 仓库里有什么 / 不会推什么

**包含**：`src/`、Gradle 包装器与构建脚本。

**不包含**（已用 `.gitignore` 挡掉）：`.gradle/`、`build/`、`run/`（游戏运行目录，含 `logs/`、崩溃转储、配置）、所有 `*.log` / `hs_err_*.log` / `replay_*.log`。

## 许可证

与上游工程一致；字体授权见 `src/main/resources/META-INF/licenses/`。
