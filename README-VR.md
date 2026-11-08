# OpenNOW-VR — 手机「半 VR」模式使用与开发说明

本仓库是 `XYkadicloud/OpenNOW-Android`（分支 `android-native`）的**独立 fork**，不是对原仓库的修改。
目标是在普通安卓手机上，用 **Google Cardboard SDK v1.36.0** 把现有的云游戏串流放进一个可调节的 VR 影院空间。

---

## 1. 这个「半 VR」到底是什么

用户的需求原文是「串流肯定一定是平面的，因为基本都串流 PC/传统游戏」。因此：

- **串流画面永远是平面 2D**。它是一张纹理，贴到 VR 空间里的一块幕布上。
- 左右眼看到的是**同一块幕布**，只是视点不同（这就是普通立体视觉）。**不会**把视频做成左右眼不同内容的 3D，不做 SBS、不加视差。
- VR 负责的只有：立体空间、幕布大小/位置、设置面板、视角跟随头部。
- 普通 2D 界面**完整保留**，随时可以切回。

**交互是纯物理手柄。** Cardboard 纸盒没有专业 VR 手柄，所以用蓝牙/USB 游戏手柄。
**没有**凝视、眼球追踪、注视计时、准星选中、眨眼确认，也**没有**任何用头部动作触发的选择。
头部姿态只进视角矩阵。

---

## 2. 当前状态（请按此理解，勿高估）

| 部分 | 状态 |
| --- | --- |
| Cardboard SDK v1.36.0 源码 vendor + 必要补丁 | 已完成 |
| 纯逻辑层（设置、范围、键码、矩阵、幕布几何、四元数、菜单模型、手柄状态机、**手机端设置规格表**） | 已完成，有 JVM 单测（覆盖 IPD 缩放/钳制、四元数相对旋转、菜单长按触发与 latch、全项范围/步长） |
| **VR 设置集成进原生设置页** | 已实现，**真机未验证**；`设置 → Interface → VR 模式`，用上游原生 `SettingSwitch`/`NumberSlider`；**已删除**常驻浮层「VR 设置」按钮 |
| 景深 / 空间固定感、真实帧宽高比 | 已修复，**真机未验证**；view 矩阵改用 SDK 语义的逆旋转，幕布按真实解码帧比例重建 |
| 无缝切回手机 / 软键盘 | 已实现，**真机未验证**；VR 内按系统 BACK 或手柄快捷键退出，串流不重连 |
| Android 骨架层（VR 状态、Surface 注册、GL program、Renderer、CardboardView、Compose 覆盖层） | 已完成 |
| 主工程接线（Gradle 依赖、MainActivity 输入与覆盖层、Streaming 解码 Surface hook、Manifest） | 已完成 |
| `.github/workflows/vr-build.yml` 两阶段 CI（编 AAR → 单测 + 出 APK）+ AAR/APK 二进制守卫 | 已完成 |
| **电视版 UI 完全移除**，主界面回到原版手机版 `HomeScreen` | 已完成；源码仅留 `docs/ui-reference/` 作 VR 视觉参考 |
| **P1 视频纹理接入**（`GL_TEXTURE_EXTERNAL_OES` + SurfaceTexture → 平面幕布） | **已实现**（零拷贝），**真机未验证** |
| 纯黑背景 + 无串流时的 2D 状态面板（不再是空白屏） | 已实现，**真机未验证** |
| 低端机性能优化（渲染循环零堆分配、顶点 50→4、面板节流、官方单目省电档） | 已实现，详见 `HANDOFF.md` 第 9 节 |
| **P3 手柄驱动的可见 VR 设置面板** | **已实现（2026-11-08 重做）**：**长按** `START+SELECT` 或**长按** `HOME/MODE` 打开**现代化二级页面菜单**（5 个分类页 / 共 23 行）：`↑↓` 选择、`LB/RB` 换页（`←→` 置空）、`A` 进入数值编辑或切换开关、左摇杆调数值、`B` 返回。数值**必须先按 `A` 才能调**，避免误触 |
| **单纯放大 / 上下填满（双目）** | **已实现，待真机复测**：`VrScreenFillMode` + `zoomScale`。**完全保留** Cardboard 原版双目 + 镜头畸变，只在 `onFinishFrame()` 把最终合成画面拷成纹理、用一次居中等比放大贴回，把上下黑边顶出屏幕；左右允许留小黑边。双眼仍重叠、不退化单目、不改 FOV / 投影 / 几何 / shader / SDK C++。`DISTORTED` 或 `1.00x` 一键回到原版。⚠️ 早期「方形 / 左右半屏重画」方案已被真机判定为错误并废弃 |
| **等效瞳距 / 左右眼视差** | **已实现**：`ipdScale` 0.5~1.6x（默认 1.0 = 零改动），对左右眼最终裁剪坐标施加等大反向水平位移（`VrMatrix4.applyIpdShiftInto`）。**已放弃**旧做法「缩放 `viewMatrix[12]`」：SDK 的 `eyeFromHead`、离轴投影、畸变 mesh 同源于 `inter_lens_distance`，只改其一会让相机与视锥失配、左右眼无法融合。不改 shader、不改 SDK 参数 |
| **重置中心点（头部追踪校准）** | **已实现**：把当前头朝向记为新的正前方；`VrHeadPose` 计算 `current * conj(center)` 相对旋转；VR 菜单动作项 + 手机设置页按钮均可触发 |
| **可选 360° 全景背景 + 用户导入图片** | **已实现**（默认关闭）：低多边形球体环境贴图、内置星空兜底、系统文件选择器导入 2:1 全景图 |
| **VR 期间强制横向 + 真全屏** | **已实现**：锁 `SENSOR_LANDSCAPE`；隐藏 status/nav/systemGestures/displayCutout；`FLAG_LAYOUT_NO_LIMITS` + `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`。**注意：window flags 只解决系统栏/刘海，消不掉截图里那种上下大黑边** —— 那来自 Cardboard 畸变 mesh 的 FOV 截断，由上面的 `screenFillMode` 解决 |
| 真机 / 纸盒 / GFN 串流效果 | **真机反馈确认可用后又反馈失败，已第二轮修复**：`f77f482` 真机 logcat 定案「进 VR 啥也没有」是 `VrModeHost` 竞态，层级修复后用户确认「现在的确能使用了」。但随后 `7a9abfb` 版真机截图暴露：基础状态面板被 180° 反转、串流朝向不对、辅助线贴在 VR 面板里跟头转、状态栏仍显示。**这些画面效果已重新实现（见 §4 与 `HANDOFF.md` §13），全部待真机复测**。CI 只能证明「能编译 + 单测通过 + 能出 APK」，GLSL 运行时编译与视觉朝向 CI 抓不到 |

> 一句话：**框架、构建链、主界面回退、视频纹理、黑底渲染、手机端与 VR 内手柄设置面板、
> 可导入的 360° 背景、瞳距调节、重置中心点、强制横向真全屏均已完成；串流朝向 / 基础 UI 正立 /
> 辅助线层级 / 全屏铺满已按真机反馈逐轮重做，剩下的是这些画面效果的最新一轮真机复测**。
> 接手的 Agent 请先读 [`docs/HANDOFF.md`](docs/HANDOFF.md)（项目位置 / 状态 / 待办 / 文档索引），
> 技术事实基线见 [`docs/VR_IMPLEMENTATION_PLAN.md`](docs/VR_IMPLEMENTATION_PLAN.md)。

### 骨架刻意做成的「零影响」特性

VR 默认关闭。关闭时：`VrVideoSurfaceRegistry.target()` 返回 `null`、`directSdrEnabled` 走原逻辑、
`VrSession` 不消费任何按键（除进入 VR 的组合键）、`VrModeHost` 不创建 GL 视图。
**因此原 App 的行为基本不变**，骨架可以独立合入并通过 CI。

---

## 3. 完整操作方式（手柄 / 手机）

### 3.1 进入 VR（三选一）
① 外部主页面顶栏的原生「进入 VR」按钮；② 串流内 Quick Menu 的「进入 VR」项（点击会先收起菜单）；
③ 手柄 **`L1 + R1` 同时按**。设置页的 VR 模式开关也保留。
VR 开启后画面上**没有任何浮层控件**（右下角常驻按钮已删，防误触）。

### 3.2 退出 VR（只有两种，都是显式操作）
| 方式 | 操作 |
| --- | --- |
| **手机系统返回键**（推荐） | 菜单开着 → 先关菜单；菜单关着 → 直接退出 VR。整个 DOWN+UP 由 VR 层独占，不会再误结束 Activity |
| **VR 设置菜单里退出** | 长按 `START+SELECT` 或 `HOME/MODE` 打开菜单 → `RB` 翻到「快捷」页 → 选「退出 VR」→ 按 `A` |

> `L1+R1` 现在**只负责进入**：在 VR 里按这对肩键不会被用来退出，避免误触把人踢出会话。
> 手柄 `B` / `SELECT` / `START` / `MODE` 在菜单里只是「关闭菜单」，不会结束应用。
> 这是按用户偏好定的：`L1+R1` 的退出能力已移除，退出统一走手机返回键或设置里的「退出 VR」。

### 3.3 打开设置菜单
VR 内**长按** `START + SELECT` ≥ 500 ms，或**长按** `HOME/MODE` ≥ 500 ms。
短按一律原样透传给游戏，防止游戏里误触。计时从组合成立的第一次按下开始，触发后带 latch，
松开任意键才允许再次触发。

### 3.4 菜单内操作（5 个分类页 / 共 23 行：画面 / 观看 / 操作 / 环境 / 快捷）
| 输入 | 行为 |
| --- | --- |
| 十字键 ↑ / ↓ | **选行**：上下移动选中条目，可按住连选 |
| **左摇杆 ↑ / ↓（未进入编辑态）** | **同样选行** ← 本轮修复。旧版摇杆只发 `Adjust`，未编辑时被静默丢弃，真机表现就是「摇杆上下滑不动」 |
| 十字键 ← / →、摇杆 ← / →（未进入编辑态） | 置空：不改数值、不换页，也不会漏到底层 2D 界面 |
| `LB`(L1) / `RB`(R1) | 上一页 / 下一页（按住不连翻） |
| `A` / 十字键中间 | 数值行：进入 / 退出「编辑中」；开关 / 枚举行：立即切换；动作行：执行 |
| **编辑中**的左摇杆 | 改数值：← / → = 1 步细调，↑ / ↓ = 5 步粗调，带死区与重复延迟；按 `A` 完成 |
| `B` / `SELECT` / `START` / `MODE` | 关闭菜单（没有 B 键也不会被困住） |
| 其它任何按键 | 菜单独占，全部吞掉 |

> **数值防误触**：必须先按 `A` 进入「编辑中」，摇杆才会改数值；没按 `A` 时摇杆只用来选行。
> 切换编辑态会重置摇杆的重复节奏，让新模式立刻响应；十字键的 HAT 物理边沿状态**故意保留**，
> 否则按 `A` 时仍按着十字键会在下一个事件里多走一行。
> **手柄兼容**：不少蓝牙手柄把十字键报成 `AXIS_HAT_Y` 而不是 `KEYCODE_DPAD_*`，
> 现在由 `VrGamepadRouter.onHat()` 处理，按下一次走一行、按住不连发；
> VR 开启时底层 2D Compose 的按键 / 触摸 / 摇杆焦点全部隔离，不会再弹窗。

### 3.5 菜单关闭时
其它所有按键**原样透传给串流游戏**（走原有 `NativeStreamInputRouter`）；
VR 层只保留 `L1+R1`（进入）与 `START+SELECT` / `HOME/MODE`（长按开菜单）的监听。

---

## 4. 可调项

**设置入口**：`设置 → Interface → 「VR 模式（半 VR 影院）」`，与其它设置项完全一样的原生控件。
不进入 VR 也能改全部参数；改动通过 `VrSession.updateSettings()` 立即写入 `SharedPreferences`
并同步给 VR 渲染，杀掉 App 重开后仍然保留。**画面上不再有常驻的 VR 设置浮层按钮。**

**进入 VR**：主页面顶栏原生按钮、串流 Quick Menu、或手柄 `L1+R1`（只负责进入）。
VR 开启后画面上**没有任何浮层控件**；退出只有两条显式路径 —— 手机**系统返回键**，
或 VR 设置菜单「快捷 → 退出 VR」按 `A`（详见 §3.2）。串流不中断，
退出后即可用手机软键盘打字。单目省电模式对低端机最有效，
但纸盒 VR 请保持 `CARDBOARD` 双目。

重点是第一项「串流窗口大小」，可以直接把画面放大。

| 项 | 含义 | 范围（默认） |
| --- | --- | --- |
| `screenScale` | **串流窗口大小** | 0.5 ~ 3.0，步进 0.1（1.0） |
| `screenDistanceMeters` | 幕布距离，拉近也能让画面变大 | 0.8 ~ 6.0 m（2.2） |
| `brightness` | 画面亮度 | 0.4 ~ 1.4（1.0） |
| `curvature` | 幕布曲率，0 = 纯平面 | 0.0 ~ 1.0（0.0） |
| `screenVerticalOffset` | 画面垂直位置 | -1.0 ~ 1.0 m（0.0） |
| `ipdScale` | **等效瞳距 / 左右眼视差**：调小左右眼画面靠拢更易重合、调大分离立体感更强；1.00x = 完全不改动 SDK 原版渲染（幕布与 360° 背景一起生效；单目 SIMULATION 模式不生效） | 0.5 ~ 1.6（1.0） |
| `screenFillMode` | **画面上下填满（单纯放大）**：`FILL`（默认）= 保留 SDK 原版双目 + 镜头畸变，只对**最终合成画面**做一次等比放大，把上下黑边顶出屏幕；`DISTORTED` = 回到 Cardboard 原版输出（有黑边、适配纸盒镜片）。**不会把画面变成方形、不会去掉桶形校正、双眼始终重叠**。2D 设置页开关 + VR 菜单「画面填充」行 + 持久化 + 恢复默认 | `FILL` |
| `zoomScale` | **整体放大（上下填满）**：`FILL` 生效时的等比放大倍数，范围 `1.00~2.50x`、步长 `0.02`。`1.00x` = 完全不放大（逐像素原版输出）；往上调逐步把上下黑边顶满，左右仍可留一点黑边。纯最终画面后处理，不改 FOV / 投影 / 几何 / shader。**设置菜单打开时自动不放大**，保证菜单行不会被裁。2D 设置页滑块 + VR 菜单「整体放大」行 + 持久化 + 恢复默认 | `1.35` |
| `viewerMode` | `CARDBOARD`（立体+畸变，插纸盒）/ `SIMULATION`（单目全屏，裸眼） | `CARDBOARD` |
| `headTrackingEnabled` | 头部追踪开关（**仅影响视角**） | true |
| `headTrackingSensitivity` | 追踪灵敏度 | 0.0 ~ 1.5（1.0） |
| `gamepadDeadZone` | 摇杆死区 | 0.05 ~ 0.5（0.20） |
| `menuRepeatMs` | 摇杆长按重复间隔 | 60 ~ 500 ms（140） |
| `showStatus` | 显示状态文字 | true |
| `showCenterAlignmentLine` | **中心对齐辅助线**：整块物理屏幕正中央一条从底部到中心的白色细竖线，固定在 2D/屏幕层（`onFinishFrame` 叠加），不进 VR 幕布、不分左右眼、不随头转动 | false |
| `horizontalMirror` | **串流左右镜像**：只翻转串流画面，状态面板/菜单/360° 背景不受影响；纯纹理坐标变换，不改 shader | false |
| `verticalFlip` | **串流上下翻转**：只翻转串流画面，与左右镜像独立、可叠加（两项同开 = 旋转 180°） | false |
| `backgroundEnabled` | **360° 全景背景**；关闭时为纯黑、零额外显存与绘制 | false |
| `backgroundImageUri` | 导入的全景图 URI；留空则用内置程序化星空（无需任何资源文件） | 空 |
| `backgroundBrightness` | 背景独立亮度（不影响幕布亮度） | 0.2 ~ 1.5（1.0） |

菜单里还有四个动作项：**重置中心点**、纸盒扫码校准、恢复默认、退出 VR。设置用 `SharedPreferences` 持久化。

**重置中心点**：VR 菜单选「重置中心点」按 `A`，或在手机设置页点「重置中心点（头部追踪校准）」，
把当前头朝向存为新的正前方。渲染时 view 矩阵改用 `current * conj(center)` 的相对旋转
（`VrHeadPose.viewRotationInto` 四参重载），幕布与 360° 球体共用该矩阵，纯 Kotlin 四元数运算、
不改 shader。戴上纸盒后歪着头也能一键把画面摆正；退出 VR 后重置。

**360° 背景怎么用**：在 `设置 → Interface → VR 模式` 打开「360° 背景图」，点
「导入 360° 全景图」从系统文件选择器选一张 **2:1 等距圆柱全景图**（推荐 ≤2048×1024），
URI 会持久化，重启 App 仍在；不导入也能用（内置程序化星空）。背景是静态环境贴图，
只出现在幕布四周，**不参与串流解码**，默认关闭时完全不占显存、不额外绘制。
VR 内手柄菜单同样有「360° 背景图 / 背景亮度 / 串流左右镜像 / 串流上下翻转 / 中心辅助线」五项，随时可调回。

**朝向不对怎么办**：`PANEL_FLIP_Y` 只作用于 `GL_TEXTURE_2D` 状态面板/菜单，串流走的是
`samplerExternalOES + uSTMatrix`，根本没有 `uFlip`——所以早期把它设成 `1.0f` 只把基础 UI
翻了 180°、串流一点没修（这是 `5542a60` 的错误尝试，已回退为 `0.0f`）。现在串流的左右镜像与
上下翻转都由**只给视频用的 `videoTexCoordBuffer`**（`u→1-u` / `v→1-v`）实现，不动 `stMatrix`、
不动任何 GLSL；状态面板、菜单、360° 球体继续用原 `texCoordBuffer`，永远正立。
两项默认都关、随时可恢复，真机若方向相反就分别试开或叠加。

**辅助线层级**：`VrTheaterView` 的 GL SurfaceView 被 `setZOrderOnTop(true)` 置顶，普通 Compose
2D View 会被盖住，因此无法直接复用 Cardboard SDK 旧的 `ui_alignment_marker`（那是 VR 尚未正常
工作时 2D-only 界面上的 View）。现在改在 `onFinishFrame(viewport)` 里、SDK 完成畸变合成之后，
用默认 framebuffer + 整屏 viewport + identity MVP 画一条 NDC `x=0, y=-1..0` 的白色细线：
一条、正中央、笔直、不跟头转。

**单纯放大 / 上下填满（`screenFillMode` + `zoomScale`）**：截图里那种「两块带圆角的桶形眼窗 +
上下大黑边」**不是** Activity window flag 造成的，而是 Cardboard SDK 的畸变合成：
`renderEyeToDisplay()` 用畸变 mesh 把 FBO 重投影到屏幕，而该 mesh 的 FOV 在
`lens_distortion.cc:189-194` 被 `std::min(几何角, device_fov)` 截断，只覆盖屏幕中间一块。
`FLAG_LAYOUT_NO_LIMITS` / 隐藏系统栏解决不了这个。

> ⚠️ **走过的弯路**：`6cac3e3` 曾经的做法是「缓存左右眼 MVP，`onFinishFrame()` 清掉畸变结果，
> 再把两眼各重画到 `w/2 × 全高` 矩形 viewport」。真机反馈证明方向错了 —— 用户要的是
> 「依然是跟之前那样的」「双眼能够这样重叠」「不需要左右填充，只要上下填充完整」。
> 那套方案去掉了桶形校正、破坏了双眼融合，也没能真正填满，**已整体废弃**。

现在的做法就是用户说的「**单纯放大**」：SDK 的双目渲染、头部姿态、IPD 位移、360° 背景、
镜头畸变合成、OES 零拷贝视频通路**全部原样保留、一行不改**。在 `onFinishFrame()` —— SDK 已经
把最终画面合成到默认 framebuffer 之后 —— 用 `glCopyTexSubImage2D` 把这张成品图拷进一张
RGBA 纹理，清黑，然后把 viewport 设成比屏幕大 `zoomScale` 倍并居中（GLES2 允许 viewport
超出 framebuffer，超出部分直接被裁掉），用 identity MVP 把整张图贴回去。上下黑边就这样被推出
屏幕，左右允许留一点黑边；两个眼窗一起长大，**重叠关系和桶形校正都不变**。中心辅助线在这
之后以整屏 viewport 画，不随放大缩放。

不改 FOV、不改 projection、不改 geometry、不改 shader、不改 SDK C++、不重渲染左右眼、
不退化成单目。`zoomScale = 1.00x` 是严格 no-op；`DISTORTED` 完全跳过这段后处理，**一键回到
逐像素一致的旧渲染**。VR 菜单「画面 → 画面填充 / 整体放大」和 2D 设置页的开关 + 滑块都能调，
「恢复默认」可复位。

**为什么上一个版本「依然不能全屏」（已修）**：旧实现只分配了一张 **RGBA8** 纹理去
`glCopyTexSubImage2D`。而多数手机给 Cardboard 的默认 framebuffer 是 **RGB565**，GLES2 会直接
以 `GL_INVALID_OPERATION` 拒绝这次格式不兼容的拷贝；旧代码为了避免「清屏后贴未定义纹理变全黑」，
一旦校验失败就**永久降级回 SDK 原版输出** —— 于是真机上表现为「放大完全没生效，还是有黑边」。
现在改为：

1. 首帧 drain 旧 GL error 后，依次用 **RGBA8 → RGB565 → RGB8** 三种目标格式重新分配纹理并试拷，
   哪个驱动接受就固定用哪个（零每帧开销）；
2. 三种都被拒绝时，退到 `glReadPixels` 读成 RGBA 再 `glTexSubImage2D` 上传（有全屏读回开销，
   但兼容性最好，且带 32 MB 上限保护）；
3. 只有**连读回都失败**才永久降级回原版画面。任何失败路径都在 `glClear()` 之前 return，
   所以**绝不会出现全黑**，原 SDK 画面完好保留。GL context 重建时重新探测。
4. 默认 `zoomScale` 从 `1.18x` 提到 `1.35x`、上限从 `2.00x` 提到 `2.50x`，给窄畸变视窗留足余量。

代码层面另有 `snapshot-20261108-pre-refactor` tag、`backup/pre-fullscreen-refactor-20261108`
分支和一份 stash 作为整体回滚点。

---

## 5. 构建（全部在 GitHub 上做）

本机不需要 Android SDK / Gradle / JDK 17。推送到 `main` 会自动触发 `vr-build`，
也可以在 `Actions → vr-build → Run workflow` 手动触发。

两个 job：

1. `build-cardboard-sdk-aar`：从 `third_party/cardboard/` 用独立 Gradle 工程编出 `sdk-debug.aar`。
2. `vr-unit-tests-and-debug-apk`：把 AAR 下载到 `android/app/libs/cardboard-sdk.aar`，
   跑 `:app:assembleDebug` 与 `:app:testDebugUnitTest --tests "com.opencloudgaming.opennow.vr.*"`，
   产出 `opennow-vr-debug-arm64-v8a` APK 工件。

> **测试范围说明**：CI 只跑本 fork 新增的 6 个 VR 测试类。上游项目另外自带 167 个 JVM 测试，
> 它们引用了 `android.*` 框架类却既没有 Robolectric、也没有 `unitTests.returnDefaultValues`，
> 且上游 CI 从未执行过它们。把它们纳入会导致与本改动无关的失败，因此暂不启用。

### 5.1 真实 CI 运行记录

| run ID | commit | 结论 | 依据 |
| --- | --- | --- | --- |
| `42186000233` | `02ce545` | 失败 | AAR job 成功；app job 在 `:app:compileDebugKotlin` 报 `'public' function exposes its 'internal' return type DecoderSurfaceTarget`，已修为 `internal object` |
| `42187431788` | `1fc4bd7` | 成功 | 两个 job 全绿；日志含 `> Task :app:testDebugUnitTest` 与 `BUILD SUCCESSFUL in 7m 7s`；产物 `cardboard-sdk-aar` 378245 B、`opennow-vr-debug-arm64-v8a` 47554069 B |
| `42189848032` | `fcdb892` | 成功 | 测试数量断言生效：`6 suites / 58 tests / 0 failures / 0 errors / 0 skipped` |
| `42192555168` | `11a4d69` | 成功 | **进入 VR 闪退修复版**：AAR 与 APK 两层守卫确认 `libcardboard_sdk_jni.so` 已打进产物 |
| `42194578267` | `4c67196` | 成功 | docs-only push 回归验证，两个 job 全绿 |
| `42198812193` | `1a489e9` | **成功** | **主代码验证**：主界面回退 + 黑底/OES 渲染 + 低端机优化。两 job 全绿；`6 suites / 64 tests / 0 failures / 0 errors / 0 skipped`；人工解包 APK 确认两个必需 `.so` 存在 |
| `42200369358` | `bd0f6b4` | 成功 | docs + KDoc 注释改动的回归，两个 job 全绿 |
| `42202834080` | `e365e03` | 成功 | 手机端 VR 设置面板版 + 71 测试；两 job 全绿 |
| `42206935569` | `203e57a` | 失败 | `VrModeHost.kt` 漏 `setValue` import，已在后续提交修复 |
| `42208037616` | `b19f3ec` | 成功 | 原生设置页 VR 分区 + 景深修复 + 帧宽高比 + BACK 无缝退出；两 job 全绿；`7 suites / 75 tests / 0 failures` |
| `42219713375` | `3cfcade` | 成功 | 手柄 combo 修复 + 长按 D-pad repeat（4 新测试）+ `setZOrderOnTop` 尝试；**真机进 VR 反而显示非 VR 界面** |
| `42230809154` | `f77f482` | 成功 | 回退 `setZOrderOnTop` + 四文件加节流诊断日志；**用户据此抓 logcat，定位真正根因** |
| **`42236078232`** | **`9c9cbc7`** | **成功** | 修复 `VrModeHost` 的 `DisposableEffect` 竞态（见 `HANDOFF.md` §12）；两 job 全绿、AAR/APK 两道 JNI 守卫 success；artifacts 385622 / 24349 / 47543881 B |
| `42322518683` | `b7961f6` | 成功 | **层级修复**：`setZOrderOnTop(true)` 只在 `init` 调一次；两 job 全绿。用户实测确认 VR 场景正常显示 |
| `42340709719` | `5542a60` | 成功 | **错误尝试（已在 `9fb1d16` 回退）**：把 `PANEL_FLIP_Y` 改成 `1.0f` 想修串流上下翻转，但 `uFlip` 只存在于 `PANEL_FRAGMENT_SHADER`，串流走 `samplerExternalOES + uSTMatrix` 没有该 uniform。结果基础 UI 被翻 180°、串流没变。CI 能过但视觉是坏的 |
| `42342141378` | `9dffbb5` | 失败 | 镜像提交误删 `showCenterAlignmentLine` 字段 + 误用作用域外变量 `s`，Kotlin 编译失败；下一提交修复 |
| **`42346490577`** | **`c563391`** | **成功** | 编译修复 + 左右眼镜像一致性修复；两 job 全绿 |
| `42347655425` | `7a9abfb` | 成功 | 可选 360° 全景背景 + 用户导入图片，镜像/辅助线/背景亮度全部可调；两 job 全绿。**该版真机反馈画面效果失败，见下一行** |
| `42355583696` | `9fb1d16` | 成功 | 回退 `PANEL_FLIP_Y=1.0f`（修基础 UI 180° 反转）、新增 `verticalFlip` + 视频专用 `videoTexCoordBuffer`、辅助线移到 `onFinishFrame()` 物理屏幕层、VR 强制横向 + 沉浸式全屏、补齐 3 个开关持久化、菜单 18→19 行；两 job 全绿。**画面效果待真机复测** |
| `42359196869` | `54702f5` | 成功 | **真全屏铺满**：hide `systemGestures`/`displayCutout`、`FLAG_LAYOUT_NO_LIMITS`、`LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`。修用户反馈「状态栏隐藏了但不是全屏」；两 job 全绿 |
| `42359487355` | `27ceea1` | **失败** | 新增 `ipdScale` + 重置中心点；`VrModeHost` 跨类访问 `VrTheaterView.theaterRenderer`（`private`），Kotlin 编译报错。已在 `8294d61` 修复 |
| `42359823182` | `15315de` | **失败** | 菜单改长按 START+SELECT + HOME；同样因上一条的 private 访问失败（该提交基于含错误代码的 main） |
| `42361113685` | `8294d61` | **失败** | 修 private 编译错误（`VrTheaterView.recenter()` public 包装）、`START+SELECT` 移出 VR 开关组合、HOME/MODE 也改长按、菜单带 latch。Kotlin 编译已通过，但 JVM 单测 `VrGamepadRouterTest` 第 55 行断言失败（计时起点落在第一个 key-repeat）；已在 `9d4ddd2` 修复 |
| `42362976400` | `9d4ddd2` | 成功 | 长按计时起点前移到组合成立的初始 `ACTION_DOWN`、加 1499L 边界断言与注释订正；两 job 全绿（JVM 单测 + debug APK + AAR/APK JNI 守卫） |
| **`42366035113`** | **`d3a0f32`** | **成功** | 重做 IPD —— 放弃缩放 `viewMatrix[12]`（破坏 Cardboard `eyeFromHead` / 离轴投影 / 畸变 mesh 三者同源性，左右眼失配无法融合；真机反馈「ipd 调整不正常」），改为 `VrMatrix4.applyIpdShiftInto()` 在每眼最终 MVP 上做等大反向屏幕空间位移；`VrMatrix4Test` 删旧 2 例、新增 4 例。两 job 全绿。**效果待真机复测** |
| **`42438601098`** | **`6cac3e3`** | **成功** | **历史版本；下面 ① 的 FILL 实现已被 `b109061` 整体废弃替换（见下一节技术说明）**，其余 ②③④⑤ 仍有效：① **上下填满 / 方形 / 去遮罩**（`VrScreenFillMode`，默认 `FILL`）：`onFinishFrame()` 在默认 framebuffer 上用 `onDrawEye()` 缓存的左右眼各自 MVP，把两只眼重画到 `(0,0,w/2,h)` / `(w/2,0,w/2,h)` 全高矩形 viewport，覆盖 Cardboard 的畸变合成；`DISTORTED` 一键回到原版。**仍是双目立体，不是单目直显**；不改 shader / SDK C++ / 零拷贝视频通路。② VR 菜单重做为 **5 个二级分类页 / 22 行现代化 UI**（渐变背景、圆角卡片、分类 chip、编辑中 banner、数值进度条、开关胶囊）。③ 交互改为：十字键 ↑↓ **只选择**、←→ **只换页**、数值**必须先按 `A` 进入编辑态**且**仅左摇杆**可调（左右 1 步 / 上下 5 步）、开关/枚举行按 `A` 立即切换。④ 删除右下角常驻「进入 VR」按钮，改为外部主页面顶栏原生按钮 + 串流 Quick Menu 项 + `L1+R1`。⑤ `screenFillMode` 全程可调：2D 设置页开关、VR 菜单 ENUM 行、SharedPreferences 持久化、恢复默认。同步改动 `VrMenuModel` / `VrGamepadRouter` / `VrSession` / `VrSettings` / `VrSettingsStore` / `VrSettingSpec` / `VrSettingsSection` / `VrModeHost` / `OpenNowScreens` / `OpenNowStreamControls` / `OpenNowStreamSurface` 及 4 个测试文件；建立 `snapshot-20261108-pre-refactor` tag、`backup/pre-fullscreen-refactor-20261108` 分支与 stash 作为回滚点。两 job 全绿（JVM 单测 + debug APK + AAR/APK JNI 守卫）。**全部画面效果与新手柄交互待真机复测** |
| **`42444105242`** | **`b109061`** | **成功** | **当前版本**：① **「单纯放大」重做画面填充** —— 废弃上面的「方形 / 左右半屏重画」，改为保留 100% SDK 原版双目 + 桶形畸变，只在 `onFinishFrame()` 把最终合成图拷成纹理后做一次居中等比放大（`zoomScale` 默认 1.18x，1.00~2.00，步长 0.02），上下顶满、左右可留小黑边、双眼仍重叠；`glCopyTexSubImage2D` 失败时永久降级为原版而非黑屏。② **修 VR 菜单无法选行 / 换页**：取消 VR 开启时的 `isGamepadSource()` 门控（部分手柄报 `SOURCE_KEYBOARD`）。③ **修 B 误退出应用**：`KEYCODE_BACK` 整个 DOWN+UP 由 VR 层独占、只在 UP 动作。④ **输入隔离**：VR 开启时未消费按键 / motion 只转发给串流游戏并吞掉、跳过 hat 导航与 `super`，`dispatchTouchEvent` 吞掉所有触摸（合并进原有 override，未新增重复方法）。⑤ **LB / RB 换页**：`↑↓` 只选行、`←→` 置空、肩键换页且不连发；`L1+R1` 仍是 VR 开关但菜单内被抑制，并改为「组合仅在 0→1 边沿触发」，残留肩键及其 key-repeat 都不会误退出 VR。⑥ `zoomScale` 全链路可调（2D 滑块 / VR 菜单 RANGE 行 / 持久化 / 恢复默认），菜单 22→23 行、画面页 9→10 行、行高 30→27。⑦ 2D 设置页旧文案、README 与 `HANDOFF.md` §16 同步订正。两 job 全绿（AAR 构建 + JNI 守卫、JVM 单测 + debug APK）。**全部真机效果待复测** |
| **`42445539466`** | **`a52e910`** | **成功** | **当前版本**：收紧 `L1+R1` 退出 VR 的触发条件为「组合从 0→1 的边沿」——`b109061` 的 `shoulderDown` 守卫只挡住了无关键，若关菜单时 LB+RB 仍按着，这对肩键**自身的 OS key-repeat** 仍会命中 `comboPressed()` 把用户踢出 VR。现在先记 `comboBefore` 再 `pressed.add()` 得到 `comboNow`，只有 `!comboBefore && comboNow && shoulderDown` 才发 `ToggleVrMode`，陈旧组合与其 repeat 一律吞为 `Consumed`；松开重新按仍正常退出。回归用例补了 shoulder repeat 与「松开后可正常退出」断言，本机 Python 复刻状态机 79 条断言全绿。两 job 全绿（AAR + JNI 守卫、JVM 单测 + debug APK）。**真机效果仍待复测** |
| **`42448340812`** | **`d09f94f`** | **成功** | **当前版本**：① **修左摇杆不能选行** —— `VrGamepadRouter` 新增 `editing` 标志，未编辑态摇杆 ↑↓ 发 `MoveSelection`（旧版无论门控一律发 `Adjust`，被 `VrSession` 在未编辑态静默丢弃，真机就是「滑不动」），A 编辑态才 `Adjust`（↑↓ 5 步 / ←→ 1 步）；`VrSession.setEditMode()` 是唯一同步点。② **HAT 轴兼容**：蓝牙手柄把十字键报成 `AXIS_HAT_Y` 的事件新增 `onHat()` 边沿触发（按一次走一行、按住不连发），`resetAxisHold()` 只重置摇杆节流、保留 HAT 物理边沿，避免按 A 时仍按着的十字键多走一行。③ **退出方式收紧**：`ToggleVrMode` 只在 VR 关闭时执行，`L1+R1` 只负责进入；退出只剩手机系统返回键与 VR 菜单「快捷 → 退出 VR」按 A。④ **修「依然不能全屏」根因**：`b109061` 只分配 RGBA8 快照纹理，而多数手机给 Cardboard 的默认 framebuffer 是 RGB565，`glCopyTexSubImage2D` 直接 `GL_INVALID_OPERATION`，旧代码随即永久降级回原版 —— 放大根本没跑。现首帧依次探测 RGBA8 / RGB565 / RGB8，全失败退到 `glReadPixels` + `glTexSubImage2D`（32 MB 上限），只有全部失败才降级；任何失败都在 `glClear()` 前 return，绝不黑屏；surface 尺寸变化时重新分配纹理；菜单打开时跳过放大。⑤ `zoomScale` 默认 1.18→**1.35**、`ZOOM_MAX` 2.00→**2.50**，同步订正 `VrMenuModelTest` / `VrSettingsTest` 两处写死 `1.18f` 的必红断言。⑥ `README-VR.md §3` 重写为完整操作手册、`§4` 旧「进入 / 退出 VR」文案订正、`HANDOFF.md §17` 新增；2D 设置页两处文案与菜单 banner 同步更新。本机 Python 复刻状态机 37 条断言全绿。两 job 全绿（AAR + JNI 守卫、JVM 单测 + debug APK）。**全部真机效果待复测** |

> **真机提示（已由用户实测确认）**：run `42189848032` 之前的 APK 点击「进入 VR」会因缺少
> `libcardboard_sdk_jni.so` 闪退（`UnsatisfiedLinkError`），`42192555168` 之后已解决；
> 「进 VR 啥也没有」由 `9c9cbc7` 修复 `VrModeHost` 竞态解决，用户已确认 VR 场景能正常显示。
> **当前剩余的是真机复测（`d09f94f`）**：① 左摇杆上下（未按 `A`）能否顺畅选行、按住连选节奏是否合适；
> ② 十字键若为 `AXIS_HAT_Y` 能否选行且不连发；③ 按 `A` 后摇杆是否只改数值、按 `B` 返回后
> 是否立刻恢复选行；④ 退出：`L1+R1` 在 VR 内**不再退出**、手机返回键与菜单「快捷 → 退出 VR」
> 两条是否都可用；⑤ **全屏**：`zoomScale = 1.35` 时上下黑边是否真的被顶掉；若仍有，继续上调到
> 2.00~2.50；菜单打开时画面是否不裁切；驱动走 `glReadPixels` 兜底时是否流畅。若仍完全无变化，
> 请确认 2D 设置页「画面上下填满（单纯放大）」开关是开着的、且「整体放大」滑块不在 `1.00x`
> （`screenFillMode` 会被 SharedPreferences 记住，旧版本手动关过的话升级后仍是 `DISTORTED`）。
> ⑥ `DISTORTED` 是否逐像素回到旧渲染。其余遗留项：基础 UI 是否正立、串流左右/上下朝向、
> 辅助线是否固定在屏幕正中、瞳距调小/调大、重置中心点、顶栏 / Quick Menu 三个入口、
> 长按 500 ms 与 key-repeat 可靠性、退出 VR 后方向与系统栏 flags 恢复。
> 详见第 6 节与 `docs/HANDOFF.md` §13 / §14 / §16 / §17（§15 为已作废方案的历史记录）。

测试数量断言解析 `app/build/test-results/testDebugUnitTest/TEST-*.xml`，要求 `tests > 0`
且 `failures/errors == 0`，否则 job 直接失败。完整记录见 `docs/VR_IMPLEMENTATION_PLAN.md` 第 9.1 节。

要点：

- **只能用 `assembleDebug` 编 SDK**：上游 `release` 开了 `minifyEnabled true`，会混淆公开 Java API。
- Cardboard SDK **没有 Maven 坐标**，所以主工程用 `implementation(files("libs/cardboard-sdk.aar"))`，
  并显式补上 AAR 不带 POM 的 5 个传递依赖。
- `android/app/libs/*.aar` **不入库**，由 CI 生成；本地要编需先跑 job 1 再手动拷过去。
- 两个 Gradle 构建互相独立：主工程 AGP 9.4.0 / Gradle 9.7.1，SDK 工程 AGP 9.2.0 / Gradle 9.8.0。
- `-Popennow.abis=arm64-v8a` + 环境变量 `OPENNOW_ABIS=arm64-v8a` 同时给，用来收窄 Rust NVST 目标。

从源仓库带过来的 4 条发布/扫描流水线已移到 `docs/upstream-workflows/` 作为参考保留，
避免在 `main` 上自动触发并与 `vr-build` 冲突。

---

## 6. 已知限制

1. CI 只能证明可编译 + JVM 单测通过 + 能出 APK；**GLSL shader 是运行时编译，CI 完全抓不到**。
   **串流画面、2D 面板文字方向、手柄手感、纸盒畸变观感、陀螺仪、低端机帧率与发热都必须真机验证**。
2. VR APK 只出 `arm64-v8a`；x86 模拟器跑不了 Cardboard 原生库。
3. Cardboard SDK 的 manifest 要求 OpenGL ES 3.0，极老设备会被过滤。
4. HDR 路径未针对 VR SurfaceTexture 单独验证。`Streaming.kt` 的
   `directSdrEnabled = { VrVideoSurfaceRegistry.isActive || renderer?.prefersDirectSdr == true }`：
   P1 落地后 `VrTheaterRenderer.onSurfaceCreated()` 会真正 `install()`，因此 VR 模式下
   `isActive` 为 true，会走 SDR；**此行为尚未在真机串流中验证**。
5. 视频宽高比仍取 `VrSession.videoAspect`（默认 16:9），**尚未**把 `Streaming.kt` 的帧尺寸回调写回；
   渲染器会在 aspect 变化时自动重建幕布网格，但回调接线仍是 TODO。
6. 上游 SDK 的 `minSdkVersion` 从 26 补丁到 24 以匹配主工程；低版本设备的运行时行为未真机验证。
7. **不要再改 `PANEL_FLIP_Y`**：`uFlip` 只存在于 `PANEL_FRAGMENT_SHADER`，串流走
   `samplerExternalOES + uSTMatrix`，根本没有这个 uniform。`5542a60` 曾把它设成 `1.0f`
   想修串流上下翻转，结果**只把基础 UI 翻了 180°、串流一点没修**（见 `HANDOFF.md` §13），
   现已回退 `0.0f`。串流朝向请用「串流左右镜像 / 串流上下翻转」两个开关调整。
8. 上游 2.3.8 来自应用内更新接口而非 GitHub tag，目前无可合并公开源码，适配受阻（见计划书 12.3）。
9. **串流开始后才进入 VR（中途切换）暂不可用**：解码器类型在会话创建时一次性选定，
   `SurfaceOrTextureVideoDecoder` 只支持 surface→texture 单向回退，VR 的 `SurfaceTexture`
   此时没有解码器消费，只剩 2D 状态面板。**先进 VR 再开始串流**的路径可用。
   完整成因与推荐修复方案见 `docs/HANDOFF.md` 8.5 与计划书 12.8；本机无法真机验证，故未擅自改动上游解码器。
10. **菜单长按依赖手柄的 OS key-repeat，但计时起点不是 repeat**：`VrGamepadRouter` 是纯 JVM
    状态机、无定时器，**500 ms 计时从「组合键成立的初始 `ACTION_DOWN`」开始**（第二个
    START/SELECT 按下、或 MODE 按下那一刻），OS key-repeat 的 `ACTION_DOWN` 只用来「兑现」到点检查。
    早期实现把计时起点放在第一个 key-repeat，会把手柄的 repeat 初始延迟（~400 ms，因设备而异）
    叠加到 500 ms 之上，导致实际要按近 1 秒、且 `VrGamepadRouterTest` 第 55 行断言失败
    （run `42361113685`）；已在 `9d4ddd2` 修正。若某手柄对 `START`/`SELECT`/`MODE` 完全不发
    key-repeat，长按仍将无法触发，需改为基于 `Handler.postDelayed` 的方案（会引入 Android 框架依赖）。
    真机验证时请一并确认。

---

## 7. 开发者入口

- **交接给下一个 Agent 的入口：[`docs/HANDOFF.md`](docs/HANDOFF.md)**（项目位置 / 真实状态 / 待办 / 文档索引 / 性能预算 / 防幻觉清单）
- 计划 / 技术事实基线：[`docs/VR_IMPLEMENTATION_PLAN.md`](docs/VR_IMPLEMENTATION_PLAN.md)
- VR 模块源码：`android/app/src/main/java/com/opencloudgaming/opennow/vr/`
- JVM 单测：`android/app/src/test/java/com/opencloudgaming/opennow/vr/`
- CI：[`.github/workflows/vr-build.yml`](.github/workflows/vr-build.yml)
