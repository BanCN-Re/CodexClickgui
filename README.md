<div align="center">

# CodexClickgui

**Minecraft 26.3 · Fabric Loader 0.19.5 · Java 25**  
Codex 桌面端风格的客户端界面，内置 Lua 模块系统

<br>

## 💬 QQ 交流群：**737086340**

安装、使用、报 Bug、提需求 —— 进群直接说。  
QQ 搜索 **737086340** 即可加入。

</div>

---

## 这是什么

一个**可直接构建、可直接运行**的完整 Fabric 客户端工程。打开游戏就能用：ClickGUI、分类浏览、模块卡片、右侧设置面板、搜索、`.` 命令全部接好，
并且内置 **Lua 模块系统** —— 不写 Java 也能往客户端里加功能。

- **67 个内置模块**，覆盖战斗 / 移动 / 渲染 / 杂项
- **三套界面**：Codex / ZCode / Naven，由 `ClickGui` 的 `Style` 选项切换
- **Lua 模块**与 Java 模块完全同权：出现在分类里、有设置面板、能绑键、配置自动保存
- **封包层**开放到 Lua：收发 26.3 协议包，可读字段、可取消、可重建
- **客户端游戏测试**：`runClientGameTest` 真实启动客户端、跑断言、截图

| 项 | 值 |
|---|---|
| Minecraft | `26.3`（无混淆，无需 mappings） |
| Fabric Loader | `0.19.5` |
| Fabric API | `0.165.0+26.3` |
| Java | `25` |
| 图形后端 | Vulkan（游戏测试走 OpenGL） |
| 模组版本 | `0.1.0` |

---

## 目录

- [快速开始](#快速开始)
- [客户端模块](#客户端模块)
- [界面接入](#界面接入)
  - [在现有客户端里打开界面](#在现有客户端里打开界面)
  - [接到你自己的 Module 系统](#接到你自己的-module-系统)
  - [依赖与渲染前提](#依赖与渲染前提)
  - [命令与配置](#命令与配置)
- [Lua 模块开发](#lua-模块开发)
  - [脚本放哪、怎么加载](#脚本放哪怎么加载)
  - [第一个模块](#第一个模块)
  - [设置项](#设置项)
  - [生命周期与错误处理](#生命周期与错误处理)
  - [完整示例：自动搭路](#完整示例自动搭路)
  - [.lua 命令速查](#lua-命令速查)
- [Lua API 参考](#lua-api-参考)
  - [module 定义接口](#module-定义接口)
  - [api：环境与输出](#api环境与输出)
  - [api：战斗](#api战斗)
  - [api：视角与移动](#api视角与移动)
  - [api：快捷栏与方块](#api快捷栏与方块)
  - [api：封包层](#api封包层)
  - [进阶：直接调 Java](#进阶直接调-java)
- [样式与主题](#样式与主题)
- [构建与测试](#构建与测试)
- [仓库内容](#仓库内容)
- [常见问题](#常见问题)
- [免责声明](#免责声明)
- [许可证](#许可证)

---

## 快速开始

```bash
./gradlew runClient
```

进游戏后按 **Right Shift** 打开 ClickGUI（模块名 `ClickGui`，分类 Render）。关掉 GUI 会自动存配置。

想换外观：在 ClickGUI 里找到 `ClickGui` 模块自己的 `Style` 选项，可选 **Codex** / **ZCode** / **Naven**。

---

## 客户端模块

共 **67 个**，按分类列出（ClickGUI 侧栏同一分组）：

| 分类 | 数量 | 模块 |
|---|---:|---|
| **Combat** 战斗 | 10 | AimAssist, AntiBots, AutoClicker, AutoHeal, AutoThrow, Criticals, CrystalAura, GhostHand, KillAura, Velocity |
| **Movement** 移动 | 17 | AutoMLG, Blink, FastCobweb, FastWeb, Flight, InventoryMove, LegitSpeed, LongJump, NoFall, NoJumpDelay, NoSlow, SafeWalk, Speed, Sprint, StopMove, Stuck, TargetStrafe |
| **Render** 渲染 | 32 | AntiBlindness, AntiNausea, BedPlates, ChestESP, ClickGui, Compass, EffectDisplay, FullBright, Glow, HotKeys, Interface, Island, ItemPhysics, ItemTags, LowFire, ModuleList, MotionBlur, NameProtect, NameTags, NoFOV, NoHurtCam, NoRender, PearlInfo, PostProcess, Projectile, Rotation, Scoreboard, ScoreboardSpoof, TargetInfo, TimeChanger, ViewClip, Watermark |
| **Misc** 杂项 | 8 | AntiFireball, AutoTools, ClientFriend, FastPlace, KillSay, MidPearl, Spammer, Teams |

> Lua 模块会追加到对应分类中，和 Java 模块并列显示。

---

## 界面接入

核心入口只有两层：

1. **模块开关**：`wtf.carnation.module.impl.render.ClickGui` 在 `onEnable` 里打开界面，然后立刻关掉自己（瞬时动作）。
2. **界面本身**：`wtf.carnation.ui.codex.CodexScreen`，继承原版 `Screen`，不暂停游戏。

### 在现有客户端里打开界面

任意地方（按键绑定、命令、别的模块）执行：

```java
mc.execute(() -> mc.gui.setScreen(new wtf.carnation.ui.codex.CodexScreen()));
```

本仓库的真实实现在 `ClickGui.java`：`Style` 是 `ModeValue`，按选项分派到三套界面；`isMomentaryAction()` 让模块开关随开随关。

```java
// ClickGui.java
setKey(InputConstants.KEY_RSHIFT);

@Override
public boolean isMomentaryAction() {
    return true;
}

@Override
protected void onEnable() {
    String mode = style.get();
    mc.execute(() -> mc.gui.setScreen(switch (mode) {
        case "ZCode" -> new ZCodeScreen();
        case "Naven" -> new NavenScreen();
        default -> new CodexScreen();
    }));
    setState(false); // 打开后立刻关闭模块开关，避免一直"启用"
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
| `wtf.carnation.graphics.UiFont` | Figtree / MiSans / Lucide 图标的烘焙 TrueType |
| `wtf.carnation.graphics.Icons` | Lucide 码点常量 |
| `lol.mixin.MixinTrueTypeGlyphProvider*` | 让自定义 TTF 能正确出字形 |
| `carnation.accesswidener` | 访问绘制所需的内部字段 |

字体资源在 `src/main/resources/assets/carnation/font/`（`figtree-*.ttf`、`misans-gb2312.ttf`、`lucide.ttf` 与各号字 json，中文再回退系统微软雅黑）。迁 GUI 时请整目录带走，否则侧栏/卡片文字和图标会对不齐。

### 命令与配置

- 界面底部输入框：普通文字会过滤模块；以 `.` 开头走 `CommandManager`。内置命令：`.bind`、`.toggle`、`.module`、`.config`、`.lua`、`.help`。
- 关闭界面时调用 `ConfigManager#saveAllConfig`，写入游戏目录下的 `Carnation/modules.json`。

只想要外观、不要命令系统时，可删掉 composer 里对 `CommandManager` 的分支，保留搜索过滤即可。

---

## Lua 模块开发

内置 [luaj](https://github.com/luaj/luaj)（`luaj-jse 3.0.1`，已 `include` 进 jar，使用方零依赖）。一个 `.lua` 文件就是一个模块：注册后和 Java 写的模块完全同权——出现在 ClickGUI 分类里、有设置面板、能被 `.bind` 绑键、配置自动保存。

### 脚本放哪、怎么加载

- 脚本目录：`<游戏目录>/Carnation/scripts/`，不存在会自动创建。
- 客户端启动时自动加载目录里全部 `*.lua`（按文件名排序）。
- 同名文件重复 `load` 就是**热重载**：旧模块先卸载、新版本重新注册，不用重启游戏。
- 模块名不能和已有模块（含 Java 模块）重名，重名会被拒绝并提示。

### 第一个模块

脚本里有两个现成全局对象，都用**冒号调用**：`module` 负责声明，`api` 负责操作游戏。最小可用的自动攻击：

```lua
module:name("KillAura")
module:category("Combat")
module:description("Lua 示例：自动攻击范围内最近的生物")

local range = module:number("Range", 3.0, 1.0, 6.0, 0.1)
local playersOnly = module:bool("PlayersOnly", false)

module:onEnable(function()
    api:chat("KillAura.lua 已启用")
end)

module:onUpdate(function()
    if not api:isInGame() then return end
    local target = api:nearestLiving(range:getFloat(), playersOnly:get())
    if target ~= nil and api:canAttack() then
        api:lookAt(target)
        api:attack(target)
    end
end)
```

放进 `Carnation/scripts/KillAura.lua`，聊天里输入 `.lua load KillAura.lua`（或 `.lua reload`），ClickGUI 的 Combat 分类里就出现了。

> 注意：示例里的模块名 `KillAura` 与内置 Java 模块同名，实际使用请改名（如 `MyKillAura`），否则会被重名检查拒绝。

### 设置项

声明即出现在模块的设置面板里，配置随 `ConfigManager` 自动持久化：

| 写法 | 生成控件 | 返回值读取 |
|------|----------|-----------|
| `module:bool("Name", default)` | 开关 | `:get()` |
| `module:number("Name", default, min, max, step)` | 滑条 | `:get()` / `:getFloat()` / `:getInt()` |
| `module:mode("Name", {"A", "B"}, default)` | 多选一 | `:get()` |

在回调里**每次都重新读**（如 `range:getFloat()`），用户在面板上改的值才能实时生效。

### 生命周期与错误处理

| 回调 | 触发时机 | 参数 |
|------|----------|------|
| `module:onEnable(fn)` | 模块被开启 | — |
| `module:onDisable(fn)` | 模块被关闭 | — |
| `module:onUpdate(fn)` | 每个客户端 Tick | — |
| `module:onRender2D(fn)` | 每帧 2D 渲染 | `graphics`, `partialTicks` |
| `module:onKey(fn)` | 按键事件 | `key`, `state` |
| `module:onPacketReceive(fn)` | 收到服务端封包（Netty 线程） | `event` |
| `module:onPacketReceiveOf(fn, 过滤)` | 同上，只接收指定包名 | `event` |
| `module:onPacketSend(fn)` | 发出客户端封包 | `event` |
| `module:onPacketSendOf(fn, 过滤)` | 同上，只接收指定包名 | `event` |

任何回调抛错（包括调了不存在的 API）：模块**自动关闭**、聊天栏提示错误原因，不影响客户端和其他模块。修好脚本后 `load` 同名文件即可再来。

### 完整示例：自动搭路

这是仓库作者实际在用的 Scaffold 脚本，覆盖了"找方块 → 切槽位 → 放置 → 还原状态"的完整套路：

```lua
module:name("Scaffold")
module:category("Movement")
module:description("自动搭路：脚下有空隙时自动放置快捷栏中的方块")

local delay = module:number("DelayTicks", 2, 1, 10, 1)
local sneak = module:bool("SneakGuard", true)
local ticks = 0
local lastStatus

module:onUpdate(function()
    if not api:isInGame() then return end
    ticks = ticks + 1
    if ticks < delay:getInt() then return end
    ticks = 0

    local player = api:player()
    local below = player:blockPosition():below()
    local state = api:world():getBlockState(below)
    if not state:canBeReplaced() then return end

    local blockSlot = api:findHotbarBlock()
    if blockSlot < 0 then return end
    local previous = api:hotbarSlot()
    if sneak:get() then api:setSneaking(true) end
    api:setHotbarSlot(blockSlot)
    local status = api:placeBlockAdjacent(below:getX(), below:getY(), below:getZ())
    api:setHotbarSlot(previous)
    if not sneak:get() then api:setSneaking(false) end
    if status ~= "placed" and status ~= lastStatus then
        lastStatus = status
        api:log("Scaffold: " .. status)
    end
end)

module:onEnable(function() ticks = 0; lastStatus = nil; api:chat("Scaffold 已启用") end)
module:onDisable(function()
    ticks = 0
    if api:isInGame() and api:isSneaking() then api:setSneaking(false) end
end)
```

### .lua 命令速查

| 命令 | 作用 |
|------|------|
| `.lua list` | 列出已加载的 Lua 模块及开关状态 |
| `.lua reload` | 全部卸载重载（并重读配置） |
| `.lua load <文件>` | 加载/热重载一个脚本（相对 `Carnation/scripts/` 或绝对路径） |
| `.lua unload <模块名>` | 卸载模块 |
| `.lua <源码>` | 直接执行一段 Lua 源码并注册为 `ChatScript` 模块 |

---

## Lua API 参考

### module 定义接口

| 调用 | 说明 |
|------|------|
| `module:name(s)` | 模块名（必填；缺省用文件名去掉 `.lua`） |
| `module:category(s)` | `Combat` / `Movement` / `Player` / `World` / `Render` / `Misc`（缺省 `Misc`） |
| `module:description(s)` | 模块描述（设置面板里显示） |

设置与回调见[上文表格](#设置项)。

### api：环境与输出

| 调用 | 返回 / 说明 |
|------|-------------|
| `api:isInGame()` | `bool`，玩家与世界都就绪才为 true；回调第一行建议先判这个 |
| `api:minecraft()` | `Minecraft` 实例 |
| `api:player()` | `LocalPlayer`，未进世界为 nil |
| `api:world()` | `ClientLevel`，未进世界为 nil |
| `api:chat(text)` | 往聊天栏发一条本地消息（只给自己看） |
| `api:log(text)` | 写进 `logs/latest.log`，前缀 `[Lua]` |

### api：战斗

| 调用 | 返回 / 说明 |
|------|-------------|
| `api:nearestLiving(range, playersOnly)` | 范围内最近的 `LivingEntity`，没有为 nil |
| `api:canAttack()` | 攻击冷却是否已恢复满 |
| `api:lookAt(entity)` | 视角转向目标眼睛 |
| `api:attack(entity)` | 发起攻击并挥手（走 `gameMode.attack`，封包合法） |

### api：视角与移动

| 调用 | 返回 / 说明 |
|------|-------------|
| `api:lookAtPos(x, y, z)` | 视角转向世界坐标（放置方块前先对准） |
| `api:boostHorizontal(multiplier, maxSpeed)` | 按倍率放大当前水平速度（保留垂直分量），返回加速后速度；原速低于 0.02 时不动 |
| `api:setSneaking(bool)` | 潜行开关。26.3 的输入每 Tick 重建，**要持续潜行需在 onUpdate 里反复应用** |
| `api:isSneaking()` | 当前是否潜行 |

### api：快捷栏与方块

| 调用 | 返回 / 说明 |
|------|-------------|
| `api:hotbarSlot()` | 当前选中槽位 0-8；未进世界为 -1 |
| `api:setHotbarSlot(i)` | 切换槽位（自动夹到 0-8）；脚本结束**记得换回原槽位** |
| `api:hotbarStack(i)` | 该槽的 `ItemStack` |
| `api:findHotbarBlock()` | 快捷栏里第一个方块物品的槽位，没有为 -1 |
| `api:placeBlockAdjacent(x, y, z)` | 把当前选中方块放进 (x,y,z) 处的可替换方块（空气/水/草），自动找相邻坚硬面、发合法放置封包。返回 `placed` / `rejected:…` / `no_block` / `no_face` / `not_replaceable` / `no_player` |

### api：封包层

26.3 的协议封包可以直接在 Lua 里收发，不需要写 Java。

```lua
-- 只接收指定包；onPacketReceive / onPacketSend 不带过滤时接收全部
module:onPacketReceiveOf(function(event)
    if event.entity_id == api:player():getId() then
        event.cancel = true          -- 丢弃这次击退
    end
end, "set_entity_motion")

module:onPacketSendOf(function(event)
    api:log(event.packet .. " 0x" .. string.format("%02X", event.packet_id))
end, {"player_input", "move_player_pos_rot"})
```

| 事件字段 | 说明 |
|------|------|
| `event.packet` | 规范名，如 `set_entity_motion` |
| `event.packet_id` | 数字 id，未登记为 `-1` |
| `event.packet_class` | Java 类简名 |
| `event.direction` | `send` / `receive` |
| `event.cancel` | 置 `true` 丢弃该封包 |
| 其余键 | 该封包字段（snake_case），如 `x` / `y` / `z` / `yaw` / `pitch` |

常用字段：`set_entity_motion` 的 `entity_id` / `motion_x/y/z`，`explode` 的 `x/y/z` / `strength`，`container_set_slot` 的 `window_id` / `slot` / `item_count`。

26.3 的封包字段是 `final`，改字段后由构造器重建封包；不能重建的包仍可读取和取消。

| 调用 | 说明 |
|------|------|
| `api:send_packet(id_or_name, table)` | 发送服务端向封包 |
| `api:send_packet_no_event(...)` | 同上，但不触发 `on_packet_send` |
| `api:packet_id(name)` / `api:packet_name(id)` | 出站名 ↔ id |
| `api:packet_id_in(name)` / `api:packet_name_in(id)` | 入站名 ↔ id |
| `api:packet_list("send")` / `api:packet_list("receive")` | 打印完整 id 表 |

```lua
api:send_packet("move_player_pos_rot", { x = 0, y = 64, z = 0, yaw = 90, pitch = 0, on_ground = true })
api:send_packet("chat_command", { command = "spawn" })
```

> `on_packet_receive` 在 Netty 线程执行，回调里不要直接改世界或渲染状态；需要操作游戏对象时用 `api:minecraft():execute(function() ... end)`。

### 进阶：直接调 Java

环境是标准 luaj JSE globals，可以用 `luajava` 拿任何客户端类：

```lua
local BlockPos = luajava:bindClass("net.minecraft.core.BlockPos")
local pos = BlockPos:containing(api:player():getX(), api:player():getY(), api:player():getZ())
```

两条 26.3 的实战注意：

- `Vec3` 的坐标读字段 **`.x` / `.y` / `.z`**，这个版本没有 `getX()/getY()/getZ()`。
- 不要用 luajava 自己构造 `BlockHitResult` 或直接调 `gameMode:useItemOn`——封包细节已经封在 `api:placeBlockAdjacent` 里，直接用。

---

## 样式与主题

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

---

## 构建与测试

```bash
# 编译并打包
./gradlew build

# 启动客户端（Vulkan）
./gradlew runClient

# 客户端游戏测试：真实启动客户端、跑断言、截图
./gradlew runClientGameTest
```

游戏测试产出的截图在 `build/run/clientGameTest/screenshots/`，日志在 `run/logs/latest.log`。

几点约定：

- `runClient` 固定 `--graphicsBackend vulkan`，壁纸走 Vulkan 渲染路径。
- `runClientGameTest` 固定 `--graphicsBackend opengl`：测试会反复改窗口尺寸、快速换帧，Vulkan 在驱动叠加层上容易触发 `VK_ERROR_DEVICE_LOST`。
- 游戏测试的断言必须通过 `context.computeOnClient(...)` / `runOnClient(...)` 执行，测试线程直接调 `Minecraft.getInstance()` 会抛异常。

---

## 仓库内容

**包含**：`src/`（主源码 + 游戏测试）、Gradle 包装器与构建脚本、`MIGRATION-STATUS.md`（模块迁移记录）。

**不包含**（已用 `.gitignore` 挡掉）：

- `.gradle/`、`build/`、`run/`（游戏运行目录，含 `logs/`、崩溃转储、配置、本地脚本目录 `run/Carnation/scripts/`）
- 内置示例 Lua 脚本资源（本地自留；jar 里没有它时 `installExample` 静默跳过，不影响任何功能）
- `.vscode/`、`.idea/`、本地素材与临时产物

---

## 常见问题

**Q：按 Right Shift 没反应？**  
确认模块 `ClickGui` 已启用（分类 Render），或者绑定被别的模组抢了——用 `.bind` 重新绑一个键。

**Q：Lua 脚本加载失败，提示模块名已存在？**  
Lua 模块不能与任何已注册模块（含 Java 模块）重名。给脚本换个名字再 `.lua load`。

**Q：启动日志里有一堆 Realms / SignedJWT 报错？**  
开发环境用离线账号，连不上 Realms 是正常的，不影响单机与 ClickGUI。

**Q：启动黑屏 / 崩溃在图形初始化？**  
本工程固定 Vulkan 后端。请确认显卡驱动支持 Vulkan；需要 OpenGL 时自行改 `build.gradle` 里 `runs.client.programArgs`。

**Q：改了模块设置没保存？**  
配置在关闭 ClickGUI 时统一写盘，位置是游戏目录下的 `Carnation/`。

---

## 免责声明

**本仓库内源码与所有内容均为 Vibecoding 产出**——不是逐行手写，而是人与 AI 结对迭代出来的。参与模型（排名不分先后）：

| 模型 |
|------|
| Claude Opus 5.5 |
| Kimi K3.1 Pro |
| GLM 5.3 |
| GLM 5.3 FlashX |
| DeepSeek V4.1 Flash |
| GPT 6.1 SOL |

在此声明：

1. **仅供学习与技术交流**。本项目用于研究 Minecraft 26.3 客户端 UI、Lua 集成与 Fabric 模组开发，不得用于商业用途，也不建议在多人服务器上使用可能影响游戏公平的功能。
2. **按"原样"提供**。不对代码的可用性、正确性、安全性作任何明示或暗示的担保；因使用或滥用本仓库内容产生的任何直接或间接后果，由使用者自行承担。
3. **与上述模型厂商无关**。各模型的输出由仓库作者驱动并采纳，与 Anthropic、Moonshot、智谱、DeepSeek、OpenAI 等公司无任何隶属、背书关系。
4. **不喜勿喷**。Vibecoding 的产物难免有瑕疵，欢迎提 Issue 指正，但请保持基本礼貌；看不惯 AI 写代码的话，右上角关了就是。

---

## 许可证

与上游工程一致；字体授权见 `src/main/resources/META-INF/licenses/`。

<br>

<div align="center">

**遇到问题？加群聊：737086340**

</div>
