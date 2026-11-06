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
| **P3 手柄驱动的可见 VR 设置面板** | **TODO**，菜单模型与手柄命令已就绪，当前面板只显示静态状态文字 |
| 真机 / 纸盒 / GFN 串流效果 | **未验证**。CI 只能证明「能编译 + 单测通过 + 能出 APK」；GLSL 是运行时编译，CI 抓不到 |

> 一句话：**框架、构建链、主界面回退、视频纹理、黑底渲染、手机端 VR 设置面板已完成；
> 还没做的是「戴着头显时在 VR 内部用手柄操作的设置面板」，画面效果也仍待真机验证**。
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

菜单里还有三个动作项：纸盒扫码校准、恢复默认、退出 VR。设置用 `SharedPreferences` 持久化。

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
| **`42202834080`** | **`e365e03`** | **成功** | **最终代码验证**：手机端 VR 设置面板 + 71 测试。两 job 全绿无失败步骤；`7 suites / 71 tests / 0 failures / 0 errors / 0 skipped`；解包 APK 确认两个必需 `.so` |

> **真机提示（已由用户实测确认）**：run `42189848032` 之前的 APK 点击「进入 VR」会因缺少
> `libcardboard_sdk_jni.so` 直接闪退（`UnsatisfiedLinkError`）；**`42192555168` 之后已不再闪退，
> 用户反馈「其实能进入 VR 模式啊」**。当前剩余问题是 VR 内此前「啥也没有」，
> 已改为黑底 + 2D 平面面板 / 串流纹理，**仍需用最新 APK 复测**，详见第 6 节与计划书 12.1 / 12.5 节。

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
7. 若真机上 2D 面板中文上下颠倒，把 `VrTheaterRenderer.PANEL_FLIP_Y` 由 `0.0f` 改为 `1.0f`。
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
