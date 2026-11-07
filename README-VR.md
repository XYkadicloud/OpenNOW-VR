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
| 纯逻辑层（设置、范围、键码、矩阵、幕布几何、四元数、菜单模型、手柄状态机、**手机端设置规格表**） | 已完成，有 JVM 单测（7 文件 / 71 个 `@Test`） |
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
| **P3 手柄驱动的可见 VR 设置面板** | **已实现**：`SELECT`/`START` 打开双列菜单（现 19 行），`↑↓` 选择、`←→` 调整、`A` 确认；含背景/镜像/翻转/辅助线等全部可调项 |
| **可选 360° 全景背景 + 用户导入图片** | **已实现**（默认关闭）：低多边形球体环境贴图、内置星空兜底、系统文件选择器导入 2:1 全景图 |
| **VR 期间强制横向 + 沉浸式全屏** | **已实现**：监听 `VrSession.enabled`，VR 开启时锁 `SENSOR_LANDSCAPE` 并隐藏状态栏/导航栏，退出后恢复原方向与系统栏策略；待真机复测 |
| 真机 / 纸盒 / GFN 串流效果 | **真机反馈确认可用后又反馈失败，已第二轮修复**：`f77f482` 真机 logcat 定案「进 VR 啥也没有」是 `VrModeHost` 竞态，层级修复后用户确认「现在的确能使用了」。但随后 `7a9abfb` 版真机截图暴露：基础状态面板被 180° 反转、串流朝向不对、辅助线贴在 VR 面板里跟头转、状态栏仍显示。**这些画面效果已重新实现（见 §4 与 `HANDOFF.md` §13），全部待真机复测**。CI 只能证明「能编译 + 单测通过 + 能出 APK」，GLSL 运行时编译与视觉朝向 CI 抓不到 |

> 一句话：**框架、构建链、主界面回退、视频纹理、黑底渲染、手机端与 VR 内手柄设置面板、
> 可导入的 360° 背景、强制横向全屏均已完成；串流朝向 / 基础 UI 正立 / 辅助线层级已按真机反馈重做，
> 剩下的是这些画面效果的最新一轮真机复测**。
> 接手的 Agent 请先读 [`docs/HANDOFF.md`](docs/HANDOFF.md)（项目位置 / 状态 / 待办 / 文档索引），
> 技术事实基线见 [`docs/VR_IMPLEMENTATION_PLAN.md`](docs/VR_IMPLEMENTATION_PLAN.md)。

### 骨架刻意做成的「零影响」特性

VR 默认关闭。关闭时：`VrVideoSurfaceRegistry.target()` 返回 `null`、`directSdrEnabled` 走原逻辑、
`VrSession` 不消费任何按键（除进入 VR 的组合键）、`VrModeHost` 不创建 GL 视图。
**因此原 App 的行为基本不变**，骨架可以独立合入并通过 CI。

---

## 3. 手柄键位

| 输入 | 行为 |
| --- | --- |
| `L1 + R1` 同时按住，或 `SELECT + START` | 进入 / 退出 VR（任何状态下可用，按住期间只触发一次） |
| `SELECT` / `START` / `MODE` 单独按 | VR 模式内打开 / 关闭设置菜单 |
| 菜单关闭时其它所有按键 | **原样透传给游戏**（走原有 `NativeStreamInputRouter`） |
| 菜单打开：十字键 ↑ / ↓ | 上下选择项 |
| 菜单打开：十字键 ← / → | 减小 / 增大当前项；按住 L1/R1/L2/R2 时 ×5 加速 |
| 菜单打开：`A` / 十字键中间 | 确认 / 执行动作项 |
| 菜单打开：`B` | 关闭菜单 |
| 菜单打开：左摇杆 | 同十字键，带死区与重复延迟 |

屏幕右下角的小按钮只是**进入/退出 VR 的兜底入口**（没接手柄时用），不是操控方式。

---

## 4. 可调项

**设置入口**：`设置 → Interface → 「VR 模式（半 VR 影院）」`，与其它设置项完全一样的原生控件。
不进入 VR 也能改全部参数；改动通过 `VrSession.updateSettings()` 立即写入 `SharedPreferences`
并同步给 VR 渲染，杀掉 App 重开后仍然保留。**画面上不再有常驻的 VR 设置浮层按钮。**

**进入 / 退出 VR**：2D 模式右下角有一个小「进入 VR」入口（也可用手柄快捷键）；
VR 开启后画面上**没有任何浮层控件**，按**系统返回键**或手柄快捷键退出，
串流不中断，退出后即可用手机软键盘打字。单目省电模式对低端机最有效，
但纸盒 VR 请保持 `CARDBOARD` 双目。

重点是第一项「串流窗口大小」，可以直接把画面放大。

| 项 | 含义 | 范围（默认） |
| --- | --- | --- |
| `screenScale` | **串流窗口大小** | 0.5 ~ 3.0，步进 0.1（1.0） |
| `screenDistanceMeters` | 幕布距离，拉近也能让画面变大 | 0.8 ~ 6.0 m（2.2） |
| `brightness` | 画面亮度 | 0.4 ~ 1.4（1.0） |
| `curvature` | 幕布曲率，0 = 纯平面 | 0.0 ~ 1.0（0.0） |
| `screenVerticalOffset` | 画面垂直位置 | -1.0 ~ 1.0 m（0.0） |
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

菜单里还有三个动作项：纸盒扫码校准、恢复默认、退出 VR。设置用 `SharedPreferences` 持久化。

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
| **`42355583696`** | **`9fb1d16`** | **成功** | **当前版本**：回退 `PANEL_FLIP_Y=1.0f`（修基础 UI 180° 反转）、新增 `verticalFlip` + 视频专用 `videoTexCoordBuffer`、辅助线移到 `onFinishFrame()` 物理屏幕层、VR 强制横向 + 沉浸式全屏、补齐 3 个开关持久化、菜单 18→19 行；两 job 全绿。**画面效果待真机复测** |

> **真机提示（已由用户实测确认）**：run `42189848032` 之前的 APK 点击「进入 VR」会因缺少
> `libcardboard_sdk_jni.so` 闪退（`UnsatisfiedLinkError`），`42192555168` 之后已解决；
> 「进 VR 啥也没有」由 `9c9cbc7` 修复 `VrModeHost` 竞态解决，用户已确认 VR 场景能正常显示。
> **当前剩余的是画面效果复测**：基础 UI 是否正立、串流左右/上下朝向、辅助线是否固定在屏幕正中、
> 强制横向与系统栏隐藏、退出 VR 后方向与系统栏是否恢复。详见第 6 节与 `HANDOFF.md` §13。

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

---

## 7. 开发者入口

- **交接给下一个 Agent 的入口：[`docs/HANDOFF.md`](docs/HANDOFF.md)**（项目位置 / 真实状态 / 待办 / 文档索引 / 性能预算 / 防幻觉清单）
- 计划 / 技术事实基线：[`docs/VR_IMPLEMENTATION_PLAN.md`](docs/VR_IMPLEMENTATION_PLAN.md)
- VR 模块源码：`android/app/src/main/java/com/opencloudgaming/opennow/vr/`
- JVM 单测：`android/app/src/test/java/com/opencloudgaming/opennow/vr/`
- CI：[`.github/workflows/vr-build.yml`](.github/workflows/vr-build.yml)
