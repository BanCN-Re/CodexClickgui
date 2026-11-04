# Naven → Carnation 26.3 迁移状态

来源：`xiaozhuhou233/moon-rebase`（1.20.4 MCP）与 `Huty1337/NeoNaven`（1.21.10 NeoForge，同一客户端的新版，包名 `tech.naven`）。
基准以 **NeoNaven** 为准（API 更接近 26.3，且界面与参考截图同源）。

## 已完成

### 基础设施
- `utils/Vector2f` `utils/Rotation` `utils/RotationUtils` `utils/RotationManager`（转头核心，已在 `Client` 中注册）
- `utils/MoveUtil` `utils/Projection` `utils/EspRender` `utils/PacketUtil`
- 事件层：`MotionEvent` `MoveEvent` `StrafeEvent` `MoveInputEvent` `StuckInBlockEvent` `RunTickEvent` `RespawnEvent` `PacketEvent` `Phase`
- Mixin：`MixinLivingEntity` `MixinEntity` `MixinLocalPlayer` `MixinClientInput` `MixinMinecraft` `MixinCamera` `MixinConnection` `MixinChatComponent` `MixinLevel` `MixinItemEntity` `MixinObjective` `LivingEntityAccessor` `MixinLoadingOverlay`（莱茵生命启动动画）`MixinTitleScreen` `MixinScreen` `MixinAbstractWidget` `MixinLogoRenderer` `MixinSplashRenderer` `MixinEditBox` `MixinSliderButton` `MixinCheckbox` `MixinLevelLoadingScreen` `MixinMusicManager`（莱茵生命标题 / 子界面 / 加载界面 / 音乐循环）
- 封包层（上一轮）：`LuaPackets` `LuaPacketDispatcher` + Lua 的 `onPacketReceive/onPacketSend`
- 字体：新增 3x/4x 烘焙档（28 个 json），`UiFont.useGuiScale()` 按 GUI 缩放取图集

### ClickGUI（Naven 模式）
`ui/naven/NavenScreen`：**逐行移植** NeoNaven `tech.naven.ui.ClickGUI`——字段名、400×250 窗口、
100 宽分类列（图标 + 文字，hover 右移 5px）、模块行 120×25 圆角 5（启用 `#3662EC`、悬停白 1/3）、
设置列 x+140 起、布尔 12×12、数值 5px 滑条 + 10px 圆点、模式 10×10、中键绑定覆盖层、
标题栏面包屑、右下角 resize（最小 500×300）、滚动 target 缓动 + 1s 淡出（`#3763EC`）、
`moduleSwap` 驱动模块列淡入淡出；动画按原版 `AnimationUtils` 的
`dt * max(10, |diff|*40) * 0.4 / 1000` 线性步进。
收起高度 190 = `40 + 6 分类 × 25`（原版 4 分类 = 140，公式一致）。
窗口底色用单层 `rgba(0,0,0,94)` 合成原版两层黑（40 + 0x40），因 26.3 无 GUI blur 钩子。
输入：点击/悬停都按**事件坐标**命中绘制期登记的区域（CodexScreen 同款），不依赖
`extractRenderState` 传入的鼠标坐标；拖拽/滑条位置由 `mouseMoved` 跟踪。
**鼠标按钮码必须用 26.3 常量**（SDL：左=1、中=2、右=3、侧键=4/5），NeoNaven 的
GLFW 码（左=0、右=1、中=2）在 26.3 会把左键当右键，导致整屏无响应。

### 模块
| 分类 | 已迁移 |
|---|---|
| move | Sprint, SafeWalk, NoJumpDelay, StopMove, LegitSpeed, Speed, Flight, NoFall, FastCobweb, **FastWeb**, NoSlow, LongJump, Stuck, **Blink**, **AutoMLG**, InventoryMove, TargetStrafe |
| combat | **AutoClicker**, **AutoHeal**, **AntiBots**, **GhostHand**, **CrystalAura**, **AimAssist**, **Criticals**, **KillAura**, **Velocity**, **AutoThrow** |
| render | Watermark, ModuleList, Compass, EffectDisplay, PearlInfo, TargetInfo, HotKeys, NameTags, ItemTags, Projectile, Glow, BedPlates, ChestESP, FullBright, NoFOV, NoHurtCam, LowFire, ViewClip, AntiBlindness, AntiNausea, **MotionBlur**(PostChain), **PostProcess**(PostChain), **NameProtect**, **NoRender**, **Scoreboard**, **ScoreboardSpoof**, **TimeChanger**, **Island**, **Interface**, **ItemPhysics**, **Rotation** |
| misc | — |

## 待办（按优先级）
1. **Scaffold**（NeoNaven 42KB，依赖 placement + 转头）
2. **misc 14 个**：AntiFireball, AutoTools, ChestStealer, ClientFriend, Disabler, FastPlace, Helper,
   InventoryCleaner, ItemTracker, KillSay, MidPearl, PearlPrediction, Spammer, Teams
3. **render 辅助**：Animations, HUD, Render3DUtils, EspRenderTypes

## 已知偏差
- Naven 的面板背景依赖 BLUR/SHADOW 遮罩 pass（`onShader`），26.3 没有 GUI blur 钩子，
  目前用不透明深色矩形 `0xF01A1A22` 等价替代；resize 手柄图标用内置 `Icons.SQUARE` 代替
  Naven 字体图标的 RESIZE 字形（不新增图集码点）。
- Naven 的 `Velocity` 有完整的“延迟补偿 + 反打 + 跳跃重置”状态机，目前是 Cancel/Jump/Horizontal 三模式简化版。
- NeoNaven 的 `Blink` 用假玩家实体模拟位置，263 版是纯发包队列。
