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
| 纯逻辑层（设置、范围、键码、矩阵、幕布几何、四元数、菜单模型、手柄状态机） | 已完成，有 JVM 单测 |
| Android 骨架层（VR 状态、Surface 注册、GL program、Renderer、CardboardView、Compose 覆盖层） | 已完成骨架 |
| 主工程接线（Gradle 依赖、MainActivity 输入与覆盖层、Streaming 解码 Surface hook、Manifest） | 已完成 |
| `.github/workflows/vr-build.yml` 两阶段 CI（编 AAR → 单测 + 出 APK） | 已完成 |
| **P1 视频纹理真正接入**（`GL_TEXTURE_EXTERNAL_OES` + SurfaceTexture 采样） | **TODO，尚未实现**，当前 VR 里是纯色占位幕布 |
| **P3 VR 设置面板渲染**（Canvas Bitmap → 贴图 quad） | **TODO，尚未实现**，菜单模型与手柄命令已就绪但还没有可见面板 |
| 真机 / 纸盒 / GFN 串流效果 | **未验证**。CI 只能证明「能编译 + 单测通过 + 能出 APK」 |

> 一句话：**框架和构建链已完成，视频接入和面板渲染还没做**。
> 唯一事实来源是 [`docs/VR_IMPLEMENTATION_PLAN.md`](docs/VR_IMPLEMENTATION_PLAN.md)，接手开发前请先完整读完。

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

1. CI 只能证明可编译 + JVM 单测通过 + 能出 APK。**串流画面能否正确显示、手柄手感、纸盒畸变观感、陀螺仪表现，都必须真机验证**，本仓库尚未验证。
2. VR APK 只出 `arm64-v8a`；x86 模拟器跑不了 Cardboard 原生库。
3. Cardboard SDK 的 manifest 要求 OpenGL ES 3.0，极老设备会被过滤。
4. HDR 路径未针对 VR SurfaceTexture 单独验证；VR 模式下 `directSdrEnabled` 会强制为 true 走 SDR。
5. 视频宽高比目前固定 16:9，P1 需要把 `Streaming.kt` 的帧尺寸回调写进 `VrSession.videoAspect`。
6. 上游 SDK 的 `minSdkVersion` 从 26 补丁到 24 以匹配主工程；低版本设备的运行时行为未真机验证。

---

## 7. 开发者入口

- 计划 / 防幻觉基线：[`docs/VR_IMPLEMENTATION_PLAN.md`](docs/VR_IMPLEMENTATION_PLAN.md)
- VR 模块源码：`android/app/src/main/java/com/opencloudgaming/opennow/vr/`
- JVM 单测：`android/app/src/test/java/com/opencloudgaming/opennow/vr/`
- CI：[`.github/workflows/vr-build.yml`](.github/workflows/vr-build.yml)
