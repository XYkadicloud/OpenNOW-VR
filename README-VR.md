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
| **P3 手柄驱动的可见 VR 设置面板** | **已实现（2026-11-08 重做，2026-11-09 加滚动）**：**长按** `START+SELECT` 或**长按** `HOME/MODE` 打开**现代化二级页面菜单**（5 个分类页 / 共 34 行，含「环境」页 13 项摄像头透视设置）：`↑↓` 选择、`LB/RB` 换页（`←→` 置空）、`A` 进入数值编辑或切换开关、左摇杆调数值、`B` 返回。数值**必须先按 `A` 才能调**，避免误触。**面板位图固定 768×432，每页最多画 10 行；超出时随选中行自动下翻，右侧显示滚动条，选中项永远可见** —— 以后再加多少设置项都不会被裁掉（见 §3.4） |
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
> **设备差异（第二轮真机修复）**：不少蓝牙手柄把 `Select` / `Back` 映射成 Android 的
> `KEYCODE_BACK`。这种**来自物理手柄**的 BACK 会被识别为普通游戏键直接送给游戏，
> 不再触发退出 VR；只有手机自身的系统返回键（非手柄来源）才走上面两条退出路径。

### 3.3 打开设置菜单
VR 内**长按** `START + SELECT` ≥ 500 ms，或**长按** `HOME/MODE` ≥ 500 ms。
短按一律原样透传给游戏，防止游戏里误触。计时从组合成立的第一次按下开始，触发后带 latch，
松开任意键才允许再次触发。

### 3.4 菜单内操作（5 个分类页 / 共 34 行：画面 10 / 观看 5 / 操作 3 / 环境 13 / 快捷 3）
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

> **菜单可以向下翻页（2026-11-09 真机修复）**：面板是一张固定 **768×432** 的位图，早期版本
> 把该页所有行一次性往下画，环境页涨到 13 行后底部几行直接超出位图被裁掉，**没有滚动条、
> 也没有任何下滑手段，看不到下面的设置项**。现在 `VrMenuModel.visibleWindowFor()` 只返回
> 当前可见的 **10 行**窗口：选中行走到窗口底部时窗口自动下移一行，往上走自动回卷；
> 渲染器只画这 10 行，并在右侧 744–768 px 槽里画**轨道 + 滑块**，banner 右侧显示
> `1-10/13 ↓到底自动下翻` 这样的计数。窗口是**纯函数、无持久滚动状态**，换页 / 退出重进 /
> 进程重启都不会错位，选中行永远在可见区。以后环境页涨到 20 / 50 / 200 行也无需改布局。
> 2D 手机设置页本来就在外层 `Column.verticalScroll()` 里，可正常上下滑，不需要再加嵌套滚动。

### 3.5 菜单关闭时（游戏中）

所有未被 VR 层消费的按键 / 摇杆 / 鼠标事件，走**专用的 VR 游戏直通通道**
（`NativeStreamInputRouter.dispatchVrGameKey` / `dispatchVrGameMotion`）直接发给串流游戏，
**绕过**原版 `dispatchKey()` 里所有「打开 2D 串流 Quick Menu / 键盘快捷键 / Android TV Guide /
长按 START 菜单」的本地分支。

> **为什么必须绕开（第二轮另一台设备真机反馈）**：那些 2D 面板在 VR 画面下完全不可见。
> 某些设备把 `Select` / `Home` 键映射到会被本地分支当作「打开菜单」的键码，一次普通的
> 游戏内按键就会悄悄打开看不见的 2D 菜单，把 `streamUiActive` 置为 `true`；此后
> `dispatchKey()` 对**每一个**按键都返回 false、`dispatchMotion()` 也不再转发摇杆，
> 游戏看起来「输入焦点丢了」，而且因为菜单不可见根本关不掉。现在 VR 期间本地菜单
> 永远不会被手柄按键打开，从根上消除这个死锁。

因此：

- `SELECT`、`HOME/MODE`、`START` 等**短按全部原样进游戏**（含 OS key-repeat）；
- 只有 `L1+R1`（进入 VR）与 `START+SELECT` / `HOME/MODE`（**长按 ≥ 500 ms** 开菜单）
  仍由 VR 层监听；
- 手柄 `Select` 若被映射成 `KEYCODE_BACK`，同样作为普通游戏键进游戏，不会误退出 VR。

**关闭设置菜单时**，`VrSession.onVrMenuClosed` 会主动调用
`NativeStreamInputRouter.restoreVrGameInput()`：取消可能残留的「长按 START 开 2D 菜单」
定时器、把卡住的 `streamUiActive` 复位为 `false`。进入 VR 时也会做同样一次复位。
这就是真机反馈「关闭设置页时，无法正常地将输入焦点对焦至游戏」的直接修复。

### 3.6 装了最新 APK 却「依然不能全屏」？按这个顺序查

真机反馈「依然好像是不能全屏的」常见原因不是渲染坏了，而是**放大链路被静默跳过**。
`d09f94f` 起，每个跳过原因都会打一条一次性日志，照下面查：

1. **先确认装的是最新 CI 产出的 APK**。之前那次反馈本人已说明是「上一个 CI 的编译中、
   最新的 CI 还没编译完」——`d09f94f` 之前的版本只有 RGBA8 单一快照格式，在 RGB565
   默认 framebuffer 上会 `GL_INVALID_OPERATION` 并永久降级，画面完全不变。
2. **确认 2D 设置页的「画面上下填满（单纯放大）」是开着的**。`screenFillMode` 会被
   `SharedPreferences` 记住；旧版本手动切成「原版（可回退）」后，升级装机仍是
   `DISTORTED`，`onFinishFrame()` 会整段跳过放大。这是最容易被误判成「改了没用」的一条。
3. **确认「整体放大」不在 `1.00x`**。默认 `1.35x`，范围 `1.00 ~ 2.50x`，步进 `0.02x`。
4. **确认 VR 设置菜单是关着的**。菜单面板是铺满屏幕的 quad，放大它会裁掉外圈行和分类
   chip，所以菜单打开时**故意不放大**；关掉菜单即恢复。
5. **看 logcat 直接判定**：`adb logcat -s VrTheaterRenderer:V`
   - 出现 `zoom FILL active: 1.35x on <w>x<h> ...` → 放大已真实运行，黑边还在就只是倍率
     不够，把「整体放大」继续往上调（QR / 纸盒参数越窄，需要的倍率越大，上限 `2.50x`）。
   - 出现 `zoom FILL skipped: <原因>` → 原因已经写得很直白（模式 / 倍率 / 菜单 /
     `panel` 未就绪 / 快照失败 / `all snapshot strategies failed` 永久降级），按它改。
   - 出现 `zoom snapshot: copy-tex format N accepted` 或 `using glReadPixels fallback` →
     说明三格式探测 / 兜底已生效，快照确实拿到了最终合成图。
6. **状态面板也能看**：无串流且菜单关闭时，第一张卡片第二行会显示
   `曲面 …   偏移 …   <填充模式> <倍率>x`，一眼可确认当前模式和倍率。

技术事实（避免再走回头路）：Cardboard SDK 的 `onDrawFrame()` 顺序是
`onNewFrame → onRenderStereoFrame（含 renderEyeToDisplay）→ onFinishFrame`，且
`onFinishFrame` 收到的 viewport 是 `(0, 0, screenParamsWidth, screenParamsHeight)`
**整屏**而非半屏。所以放大作用的是 SDK 已经完成桶形畸变合成之后的最终画面，
不是单目、不是去畸变、不是重画左右眼、也不是改 FOV / 投影。上下优先顶满，
左右允许留小黑边——这正是「单纯放大」的定义。

### 3.7 点击屏幕重置中心点视角

VR 开启期间，**短按一下屏幕**（按住 ≤ 400 ms、位移 ≤ 24 px）会自动执行一次
「重置中心点 / 重置视角」：把当前头朝向记为新的正前方，画面立刻摆正。
整个触摸手势仍然被 VR 层**完整吞掉**，不会让底下不可见的 2D 界面收到点击、弹窗。

- 拖动、长按（> 400 ms）、`ACTION_CANCEL` 都**不会**触发，避免调整手机 / 纸盒位置时
  视角被误重置；
- 与菜单里的「重置中心点」动作项、手机设置页的「重置中心点」按钮走的是同一条
  `VrSession.recenter() → VrTheaterView.recenter() → VrTheaterRenderer.recenter()` 链路；
- 纯视角校准，不改瞳距 / FOV / 投影 / 几何 / shader，退出 VR 后重置。

### 3.8 摄像头透视 / Passthrough（默认是关的）

把手机摄像头的**实时画面**放进 VR，不摘纸盒也能看见周围。手机不是 360° 摄像头（只有一个
约 60–80° 的单目视角），所以它**不是**全景环境：画面是一扇**居中、固定横向 4:3 / 16:9、
绝不竖屏**的窗口，窗口外仍是纯黑。Camera2 的真实预览宽高**只决定居中裁掉多少像素，
不决定窗口形状**，因此画面永远不会被拉伸或压扁。窗口用 `eyeFromHead + perspective` 产生
双眼视差，**默认与串流幕布共用同一个空间变换**，转头时两层一起移动、不互相滑动。

> **⚠️ 2026-11-09 第二轮真机反馈：v0.6.0（r6）方向仍然错，窗口被强制拉伸成竖屏 9:16**
> r6 把 AUTO 基准改成「传感器安装角」（多数机型 = 90°），渲染器又对 90°/270° 把预览比例
> **取倒数**当窗口比例：`16:9 → 9:16`，于是默认就画出一扇竖屏窗口、画面被硬塞进去。
> 用户实测原话：「没蒙住啊，依然是有错误的，依然是被强制拉伸成了竖屏的 9:16 比例」。
> **v0.7.0（r7）三条修正**：
> 1. **AUTO 回到 0°**：`SENSOR_ORIENTATION` 只是传感器相对**竖屏自然方向**的安装角，不是还要
>    额外施加的旋转；VR 强制横屏时 Camera2 预览缓冲本来就是正立的横向画面。v0.5.0 用
>    `JPEG_ORIENTATION` 拍照公式、v0.6.0 用裸传感器角，两版都凭空多转了 90°，这才是「方向错」
>    与「竖屏 9:16」的共同根因。手动 `0° / 90° / 180° / 270°` 覆盖保留给特殊机型 / 纸盒装配。
> 2. **窗口形状与旋转彻底解耦**：窗口固定为用户选的横向 `4:3` / `16:9`，**任何旋转都不再取
>    倒数**。真实预览比例只进 `VrPassthroughGeometry.centreCrop()` 算 UV 居中裁切量，
>    `cropUv()` 把多余那一轴对称裁掉、像素宽高比原样保留，不再挤压或拉长。90°/270° 手动覆盖
>    只改变采样哪部分画面，窗口仍是横向。纯几何规则独立成 `VrPassthroughGeometry.kt`
>    （无 Android / GL 依赖），由 JVM 单测锁定。
> 3. **Camera2 不再回报竖屏尺寸**：`choosePreviewSize()` 优先挑 `width >= height` 的横向条目，
>    只提供竖屏条目的机型也经 `normaliseLandscape()` 归一化后再上报，从源头杜绝
>    「720×1280 让窗口变竖屏」。
>
> **同轮另修：VR 设置菜单滚动**（见 §3.4）—— 环境页 13 行原本超出固定 768×432 面板、没有
> 滚动条也无法下滑，看不到「透视帧率」以下的设置项；现在 10 行窗口 + 滚动条 + 计数全部可达。
>
> **v0.6.0（r6）已修且仍然有效的部分**：① 分辨率 `360P / 480P / 720P / 1080P`、
> 帧率 `15 / 30 / 60` 档位与真实尺寸上报；② 窗口深度 `0.8 m → 0.12 m`、相对大小
> `2.2 → 1.45`；③ 默认 `passthroughHeadLocked=false`，摄像头窗口与幕布共用基础 MVP，
> 转头零相对滑动（消除「不协调感」）；④ 旋转 / 镜像手动覆盖与状态面板诊断。

**开启方式（三选一，都会弹出一次 CAMERA 权限申请）**
1. 手机 2D 设置页 `设置 → Interface → VR 模式` → 打开「摄像头透视（Passthrough）」；
2. VR 内菜单 → `RB` 翻到「环境」页 → 选「摄像头透视」→ 按 `A`；
3. 游戏中**长按手柄 `L2 + R2`（LT / RT）≥ 500 ms** —— 只负责**开 / 关**这一个总开关，
   **不会**改动布局样式（用户明确要求「快捷键只负责开启和关闭，具体的样式调整放在设置里面调」）。

> 权限是集中管理的：`VrSession.updateSettings()` 在 `passthroughEnabled` 的 OFF→ON 边沿统一
> 调 `requestCameraPermission`，2D 设置页 / VR 菜单 / 快捷键三个入口共用一套逻辑，不会重复弹窗。
> 拒绝授权会把开关还原为关，renderer 回到黑底，**不会**卡在半开状态或反复重试。
> `L2/R2` 同时兼容两类手柄：发 `KEYCODE_BUTTON_L2/R2` 的走 `VrGamepadRouter` 长按边沿，
> 发模拟轴 `AXIS_LTRIGGER/RTRIGGER`（或旧式 `AXIS_BRAKE/GAS`）的走
> `VrSession.onTriggerEvent()` + 500 ms 延时时钟；两条路径之间 800 ms 去抖，
> 一个物理长按只产生一次翻转，且**事件本身不消费**，游戏照常收到扳机。

**三种布局（在设置里选，快捷键不切换）**，两种画面在三种模式下都各自保持比例、绝不拉伸：

| 模式 | 画面 |
| --- | --- |
| `OVERLAY` 叠加（默认） | 4:3 摄像头窗口居中、与幕布同变换；16:9 串流幕布原尺寸居中**盖在窗口之上**，游戏始终在最上层；四周窗口边缘 + 最外圈纯黑底 |
| `CAMERA_MAIN` 摄像头为主 | 摄像头窗口放大成接近顶满的主画面；游戏缩到**右下角小窗**、退到后面，游戏画面不再遮挡主视野 |
| `SIDE_BY_SIDE` 双窗口并排 | 左 4:3 摄像头窗口、右 16:9 游戏窗口，两者**互不重叠**，各占一半 |

**可调项**（2D 设置页 + VR 菜单「环境」页，全部持久化、可恢复默认，共 11 项）：

| 项 | 取值 / 默认 | 说明 |
| --- | --- | --- |
| 摄像头透视开关 | 关（默认）/ 开 | `L2+R2` 长按只翻转这一项 |
| 前 / 后置摄像头 | `BACK`（默认）/ `FRONT` | 前置会自动镜像 |
| 透视可见度 | `0.00~1.00`（1.00） | `uBrightness` 亮度乘法，不启 `GL_BLEND` |
| 透视窗口大小 | `0.30~1.50`（0.85） | 三种布局都生效 |
| 窗口比例 | `4:3`（默认）/ `16:9` | **窗口的最终形状，永远横向**。Camera2 真实预览比例只决定居中裁切多少，不会覆盖这一项，也绝不会把窗口变成竖屏 9:16 |
| 透视布局 | 上面三选一 | 快捷键不切换 |
| **透视分辨率** | **`360P / 480P（默认）/ 720P / 1080P`** | 按短边分档。从 `StreamConfigurationMap` 挑最接近且比例一致的**横向**实际预览尺寸；真实值只用于决定居中裁切量，窗口比例仍由上面的「窗口比例」决定，**绝不拉伸、绝不竖屏** |
| **透视帧率** | **`15（省电）/ 30（默认）/ 60`** | 写入 `CONTROL_AE_TARGET_FPS_RANGE`，三档循环。只支持 30 帧的手机选 60 会自动回落到 30，不黑屏；同类上限相同时优先固定区间 `[30,30]` 而不是 `[15,30]`，避免低光掉帧。15 帧适合透视只作粗略环境参考、想省电省发热的场景 |
| **透视画面旋转** | `AUTO（默认，0°）/ 0° / 90° / 180° / 270°` | AUTO = 0°，因为 VR 强制横屏时预览缓冲本来就正立；方向还不对就对照现实场景手动拨。旋转**只改变采样哪部分画面**，窗口永远横向、多余部分居中裁掉，**绝不取倒数、绝不拉伸** |
| 透视左右镜像 | 关（默认）/ 开 | 在前置自动镜像之外再做一次水平翻转，前后置都适用 |
| 透视钉在眼前 | **关（默认）** / 开 | 关＝与幕布同变换、无相对滑动（修复「不协调感」）；开＝恢复旧 head-locked 行为 |

> 状态面板在透视开启时会显示 `实际宽高 · 实际帧率 · 生效旋转角 · 窗口 4:3 / 16:9 · 镜像状态`，
> 以及 `是否有帧`、窗口是「跟随幕布」还是「钉在眼前」，不用 adb 就能看出当前档位是否真的生效、窗口比例有没有跑偏。

**失败一律安全降级**：无摄像头、权限被拒、摄像头被占用（例如串流正在录制）、GL 驱动建不出
第二个 `GL_TEXTURE_EXTERNAL_OES`、或无预览帧时，窗口直接不绘制，回到原来的纯黑底 / 360° 背景，
VR **不会黑屏、不会崩溃**。透视与 360° 全景背景互斥（都是背景层，不叠加），
有实时画面时 360° 背景自动让位。相机全部走独立 Camera2 + 私有 `HandlerThread`，
不复用串流视频的 `stMatrix`、不碰 WebRTC / CameraX、不改 shader、不开 `GL_BLEND`、
不改 Cardboard SDK 的 GL 状态；可见度用 `uBrightness` 在纯黑 clear 上做亮度乘法实现。

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
| `passthroughEnabled` | **摄像头透视总开关**：把手机实时摄像头画面放进 VR（居中窗口，非 360°）。开启时会统一申请 CAMERA 权限；无摄像头 / 拒权 / 被占用时自动降级回黑底。快捷键 `L2+R2` 长按只翻转这一项 | false |
| `passthroughCameraFacing` | 透视用哪个镜头：后置（朝纸盒外侧看环境，默认）/ 前置（纸盒无后置开孔时用） | `BACK` |
| `passthroughOpacity` | 透视可见度：在纯黑底上用 `uBrightness` 做亮度乘法，`0.00` = 完全看不见、`1.00` = 全亮；不启 `GL_BLEND`、不改 shader | 0.00 ~ 1.00，步进 0.05（1.00） |
| `passthroughWindowScale` | 透视窗口大小：相对基准整体缩放，三种布局都生效 | 0.30 ~ 1.50，步进 0.05（0.85） |
| `passthroughAspect` | 窗口比例：`4:3` 是多数手机传感器原生比例、裁切最少且垂直视野更大（默认）；`16:9` 可选。这是**窗口最终形状，永远横向**；Camera2 真实预览宽高只决定居中裁切多少，不改变窗口形状，也绝不变竖屏 9:16 | `FOUR_THREE` |
| `passthroughMode` | 三种布局：`OVERLAY` 叠加（游戏在上，默认）/ `CAMERA_MAIN` 摄像头为主（游戏右下角小窗）/ `SIDE_BY_SIDE` 双窗口并排。快捷键**不**切换此项，只在设置里选 | `OVERLAY` |
| `passthroughResolution` | 透视分辨率档（短边）：`P360 / P480 / P720 / P1080`。从 `StreamConfigurationMap` 挑实际支持且比例最接近的**横向**尺寸；真实宽高经 `VrPassthroughGeometry` 算居中裁切，窗口形状仍由 `passthroughAspect` 决定，不拉伸 | `P480` |
| `passthroughFrameRate` | 透视帧率：`FPS_15 / FPS_30 / FPS_60`，写入 `CONTROL_AE_TARGET_FPS_RANGE`；设备不支持则自动回落并在状态面板显示实际值 | `FPS_30` |
| `passthroughRotation` | 透视画面旋转：`AUTO`（= 0°，横屏预览已正立）/ `R0 / R90 / R180 / R270` 手动覆盖。旋转只改变采样哪部分画面，窗口永远横向、多余部分居中裁掉，**不再取倒数、不再拉伸** | `AUTO` |
| `passthroughMirror` | 额外左右镜像，在前置摄像头自动镜像之外再做一次水平翻转 | false |
| `passthroughHeadLocked` | 窗口是否钉在眼前。**默认 false**：与串流幕布共用基础 MVP，转头时两层一起动，消除「不协调感」；true 恢复旧 head-locked 行为 | false |

菜单里还有四个动作项：**重置中心点**、纸盒扫码校准、恢复默认、退出 VR。设置用 `SharedPreferences` 持久化。
当前共 **31 个设置项**（r4 的 20 个 + 透视 11 个），其中 2D 原生数字滑块 13 个、
VR 手柄菜单 5 页 34 行（环境页 13 行）。**VR 菜单不要求行数 ≤ 10**：每页只渲染可见的
10 行窗口并随选中行滚动（见 §3.4），所以继续增加设置项不会再出现「超出页面看不到下面」的问题。

**重置中心点**：VR 菜单选「重置中心点」按 `A`，或在手机设置页点「重置中心点（头部追踪校准）」，
把当前头朝向存为新的正前方。渲染时 view 矩阵改用 `current * conj(center)` 的相对旋转
（`VrHeadPose.viewRotationInto` 四参重载），幕布与 360° 球体共用该矩阵，纯 Kotlin 四元数运算、
不改 shader。戴上纸盒后歪着头也能一键把画面摆正；退出 VR 后重置。
**第三种触发方式**：VR 期间短按一下屏幕也会自动重置中心点（见 §3.7），拖动 / 长按不触发。

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
| **`42450863219`** | **`b4fe277`** | **成功** | **当前版本**：① **SDK 源码静态复核** —— 确认 `CardboardView.onDrawFrame()` 顺序为 `onNewFrame → onRenderStereoFrame（含 renderEyeToDisplay）→ onFinishFrame`，且 `onFinishFrame` 收到的 viewport 是 `(0, 0, screenParamsWidth, screenParamsHeight)` **整屏**而非左右半屏；所以「单纯放大」作用在 SDK 已完成桶形畸变合成之后的最终画面，时机与尺寸都成立，不必再改 FOV / 投影 / geometry / shader / SDK C++，也不要退回 `6cac3e3` 的半屏重画方向。② **把「放大被静默跳过」变成可查**：`VrTheaterRenderer` 新增 `logLastZoomSkip` / `logLastZoomActive` 与 `noteZoomSkip()` / `noteZoomActive()` 两个 edge-triggered 诊断；`onFinishFrame()` 的 `DISTORTED` 分支与 `drawZoomFill()` 的 7 个 early return（viewport 无效 / 快照永久降级 / `zoomScale<=1.00` / 菜单打开 / `panel` 未就绪 / 快照失败等）各记一条一次性 `Log.w`，成功画完记一条 `Log.i` 并清掉 skip；`onRendererShutdown()` 重置，健康循环保持日志静默。③ **修一个确定的过时文案 bug**：`drawStatus()` 原写「L1+R1 或系统返回键：退出 VR」，但 `d09f94f` 已把 `L1+R1` 收紧为只进入、不退出；改为「系统返回键 / 设置「退出 VR」退出 · L1+R1 只进入」，与 §3.2 / `VrSettingsSection` / `VrKeys` 一致。④ 状态面板第二行补显示 `zoomScale` 倍率（`%s %.2fx`），不改卡片布局与 dirty 重绘链路。⑤ `README-VR.md` 新增 **§3.6「装了最新 APK 却『依然不能全屏』？按这个顺序查」** 六步清单（含 `adb logcat -s VrTheaterRenderer:V` 关键字），`HANDOFF.md` 新增 **§18**。**零渲染路径改动**：默认值 1.35 / `ZOOM_MAX` 2.50 / 三格式快照探测 / readPixels 兜底 / 菜单不放大策略 / 输入路由 / 设置架构 / `VrSettings.DEFAULT` / JVM 单测全部未动。两 job 全绿（AAR + JNI 守卫、JVM 单测 + debug APK）。**真机效果仍待复测** |
| **`42460189808`** | **`e179e34`** | **成功** | **当前版本**：① **修「另一台设备 Select / Home 键不能给游戏用」**：根因是 VR 分支仍走 `NativeStreamInputRouter.dispatchKey()`，而它内部含「长按 START 开 2D Quick Menu / 键盘快捷键 / Android TV Guide / MODE 菜单」等本地分支；这些分支把 `streamUiActive=true`，此后每个按键与摇杆 motion 都被静默丢弃，而 2D Quick Menu 被 `setZOrderOnTop` 的 GL SurfaceView 完全遮住、看不见也关不掉，表现就是「输入焦点回不到游戏」。新增 VR 专用直通通道 `dispatchVrGameKey()` / `dispatchVrGameMotion()`，直接 `client?.dispatchKey/dispatchMotion`，绕过全部本地 UI / shortcut / `streamUiActive` 分支。② **修手柄 Select 误退出 VR**：部分蓝牙手柄把 Select/Back 报成 `KEYCODE_BACK`，`VrSession.onKeyEvent()` 会吞掉并在 `ACTION_UP` 退出 VR；现在 VR enabled、菜单关闭且事件来源为控制器时，`KEYCODE_BACK` 直通游戏，非手柄的手机系统返回键仍保留「返回键退出 VR」。③ **修关闭 VR 设置页后焦点不回游戏**：新增 `restoreVrGameInput(reason)`（取消 pending 2D 菜单 hold Runnable、清卡住的 `streamUiActive`），在 `VrSession.onVrMenuClosed` 开→关边沿与进入 VR 时各调一次；`setEnabled(false)` 正常退出 VR 不触发，避免干扰 2D UI 恢复。④ **新增点击屏幕重置中心点视角**：VR 期间触摸仍完整吞掉，但在吞掉前用 `handleVrTapRecenter()` 判定短按（≤`VR_TAP_TIMEOUT_MS=400` ms、位移 ≤`VR_TAP_SLOP_PX=24` px）触发 `VrSession.recenter()`，拖动 / 长按 / `ACTION_CANCEL` 不触发；未新增可调项。⑤ `VrGamepadRouter` 状态机本身**未改**，仅补 2 个回归用例（SELECT/MODE 短按含 OS repeat 必须 `PassThrough`、关菜单后立刻变回游戏键）；`README-VR.md §3.2 / §3.5 / §3.7`、`docs/HANDOFF.md §19` 同步。本机 Python 复刻状态机 36 条断言全绿。两 job 全绿（AAR + JNI 守卫、JVM 单测 + debug APK）。**三条真机效果待那台设备复测** |
| **`42560700441`** | **`2008d48`** | **成功** | **当前版本**：① **新增摄像头透视 / Passthrough** —— 手机不是 360° 摄像头，所以不把单目画面铺满背景，而做成一扇 head-locked、居中、保持原生 4:3 的实时摄像头窗口（MVP 用 `eyeFromHead + perspective` 但去掉头部旋转，左右眼仍有视差），窗口外纯黑；`VrPassthroughCamera.kt`（303 行，Camera2 + 私有 `HandlerThread` + 独立 `GL_TEXTURE_EXTERNAL_OES`）不复用串流 `stMatrix`、不碰 WebRTC / CameraX、不改 shader / `GL_BLEND` / SDK GL 状态。② **三种可切换布局**：`OVERLAY`（游戏幕布盖在窗口之上，默认）/ `CAMERA_MAIN`（摄像头为主 + 游戏右下角小窗）/ `SIDE_BY_SIDE`（左摄像头右游戏），两种画面各自保持比例、绝不拉伸，串流 quad 用 `gameModelMatrix` 变换而不重建几何。③ **双扳机快捷键**：长按 `L2+R2` ≥ 500 ms **只翻转 `passthroughEnabled` 总开关**、不切换布局（用户明确要求「快捷键只负责开启和关闭，样式在设置里调」）；同时兼容 keycode 型 `BUTTON_L2/R2` 与模拟轴型 `AXIS_LTRIGGER/RTRIGGER`（旧式 `AXIS_BRAKE/GAS`），两路径 800 ms 去抖、一次长按只翻转一次、事件本身不消费。④ **6 个新设置项**：`passthroughEnabled` / `passthroughCameraFacing` / `passthroughOpacity` / `passthroughWindowScale` / `passthroughAspect` / `passthroughMode`，全部可调 / 持久化 / 可恢复默认；设置项 20→26、2D 数字滑块 11→13、VR 菜单 23→29 行（环境页 2→8 行）。⑤ **4:3 默认**（传感器原生、不裁切），16:9 可切；预览缓冲 `1280×960` / `1280×720` 与窗口 quad 同源，切换重启 Camera2 会话而不拉伸；可见度用 `uBrightness` 在纯黑 clear 上做亮度乘法，等价 `mix(black,camera,opacity)`。⑥ **CAMERA 权限集中管理**：`VrSession.updateSettings()` 在 OFF→ON 边沿统一申请，2D 设置页 / VR 菜单 / 快捷键三入口共用一套 launcher，拒权自动把开关还原为关；无镜头 / 拒权 / 被占用 / 无预览帧时窗口不绘制，安全降级回黑底或 360° 背景，VR 不黑屏，透视与 360° 背景互斥。⑦ 4 个 JVM 单测补 Passthrough 断言；`README-VR.md §3.8`、`docs/HANDOFF.md §20` 同步。本机 Python 复刻状态机 / 菜单 / 配置 42 条断言全绿。两 job 全绿（AAR + JNI 守卫、JVM 单测 + debug APK）。**全部透视真机效果待复测** |

| `42570810255` | `38378f6` | **失败（非代码）** | r6 修复首次推送：Cardboard AAR job 在 wrapper 下载 `gradle-9.8.0-bin.zip` 时 `SocketTimeoutException: Read timed out`（默认 `networkTimeout=10000`、`retries=0`）；app job 被 `needs:` 跳过，**Kotlin 代码根本没走到编译** |
| `42571202478` | `9401ef0` | **失败（非代码）** | 15 帧档推送：AAR job 已全绿，但 app job 下载 `gradle-9.7.1-bin.zip` 同样 10 秒超时；仍**未**编译 r6 的业务代码。两次失败促成 `e00287a` 的 CI 加固 |
| **`42572527294`** | **`e00287a`** | **成功** | **当前版本 / r6 首次真正编译通过**。① CI 加固：两个 wrapper 改 `networkTimeout=120000` / `retries=5` / `retryBackOffMs=2000`，`vr-build.yml` 两个 job 各加「发行包预热」步骤（先带重试跑 `./gradlew --version`，缓存好再编译）；两次下载超时不再拖红整条流水线。② 由此首次完整验证 `38378f6` + `9401ef0` 的 r6 业务代码：摄像头方向 / 拉伸 / 不协调修复、分辨率 4 档、帧率 15/30/60 三档、旋转 / 镜像 / 钉在眼前、31 个设置项、34 行菜单、状态面板诊断全部 Kotlin 编译通过。③ 两 job 全绿：AAR 构建 + JNI 守卫、JVM 单测 + debug APK；`7 suites / 125 tests / 0 failures / 0 errors / 0 skipped`，`BUILD SUCCESSFUL in 9m 11s`。产物 `opennow-vr-debug-arm64-v8a` 47603818 B、`cardboard-sdk-aar` 385622 B。**透视真机效果仍待复测**（见 §3.8） |

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
