# Naven → Carnation 26.3 迁移状态

来源：`xiaozhuhou233/moon-rebase`（1.20.4 MCP）与 `Huty1337/NeoNaven`（1.21.10 NeoForge，同一客户端的新版，包名 `tech.naven`）。
基准以 **NeoNaven** 为准（API 更接近 26.3，且界面与参考截图同源）。

## 已完成

### 基础设施
- `utils/Vector2f` `utils/Rotation` `utils/RotationUtils` `utils/RotationManager`（转头核心，已在 `Client` 中注册）
- `utils/MoveUtil` `utils/Projection` `utils/EspRender` `utils/PacketUtil`
- 事件层：`MotionEvent` `MoveEvent` `StrafeEvent` `MoveInputEvent` `StuckInBlockEvent` `RunTickEvent` `RespawnEvent` `PacketEvent` `Phase`
- Mixin：`MixinLivingEntity` `MixinEntity` `MixinLocalPlayer` `MixinClientInput` `MixinMinecraft` `MixinCamera` `MixinConnection` `MixinChatComponent` `MixinLevel` `MixinItemEntity` `MixinObjective` `LivingEntityAccessor`
- 封包层（上一轮）：`LuaPackets` `LuaPacketDispatcher` + Lua 的 `onPacketReceive/onPacketSend`
- 字体：新增 3x/4x 烘焙档（28 个 json），`UiFont.useGuiScale()` 按 GUI 缩放取图集

### ClickGUI（Naven 模式）
`ui/naven/NavenScreen`：400×250 窗口（收起 100×185）、分类列 100 宽、模块行 120×25 圆角 5、启用色 `#3662EC`、
悬停白色 1/3、设置列 x+140 起、布尔 12×12、数值 5px 滑条 + 圆点、模式 10×10、
左键开关 / 右键设置 / 中键绑定、右下角拖拽改窗口大小、内容区 scissor 裁剪、窗口展开动画。

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
  目前用不透明深色矩形 `0xF01A1A22` 等价替代。
- Naven 的 `Velocity` 有完整的“延迟补偿 + 反打 + 跳跃重置”状态机，目前是 Cancel/Jump/Horizontal 三模式简化版。
- NeoNaven 的 `Blink` 用假玩家实体模拟位置，263 版是纯发包队列。
