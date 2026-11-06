# OpenNOW-VR 实施计划书（防幻觉基线）

> **入口提示**：接手的 Agent 请先读 [`docs/HANDOFF.md`](HANDOFF.md)（项目位置 / 当前真实状态 / 待办 / 文档索引），
> 本文件是**技术事实基线**，两者配合；若两者冲突，一律以磁盘实测为准并同时修正两份。
> 凡本文件标注「已核实」的内容，均来自本地实际拉取的源码或 GitHub API 返回结果，不要凭记忆改写。
> 凡本文件标注「TODO」的部分，才是需要补写的内容。
> 生成时间：2026-11-06；**第二版修订：同日按磁盘实际状态修正了第一版的虚标「已完成」，详见 6.1 节**；
> **第三版修订：同日补记电视版 UI 移除、P1 落地、黑底渲染与低端机性能预算，见 12.4 / 12.5**。
> 工作目录：`C:/Users/HP/WorkBuddy AI/2026-10-31-07-17-59/OpenNOW-VR`。

---

## 0. 给接手模型的速读卡

| 项目 | 结论 |
| --- | --- |
| 目标仓库 | `https://github.com/XYkadicloud/OpenNOW-VR`（公开）。分支 `main`，`origin` 已配置 |
| 代码来源 | `XYkadicloud/OpenNOW-Android` 分支 `android-native` / HEAD `8811a74`，**整仓复制**后修改 |
| 使用的 Google VR 接口 | **Google Cardboard SDK v1.36.0**（tag `v1.36.0`，2026-10-15 发布），源码已 vendor 到 `third_party/cardboard/` |
| 用户要的产品形态 | **半 VR**：串流画面是**平面 2D**，放进**纯黑** VR 空间的一块可放大幕布；**不做 3D 场景**（省性能）；普通 2D 界面保留可切回 |
| 主界面口径 | **原版手机版 `HomeScreen`**；电视版 UI 已从编译路径**完全移除**，源码仅留在 `docs/ui-reference/` 作 VR 视觉参考 |
| 交互方式 | **纯物理手柄**。严禁凝视 / 眼球 / 注视选取 / 头部动作触发确认。头部姿态只允许改变视角 |
| 首要可调项 | **串流窗口大小（放大画面）**；另有距离、亮度、曲率、垂直位置、立体/单屏、头部追踪、手柄死区等 |
| 本机环境 | Windows，只有 JDK 8，**没有 Android SDK / Gradle / gh CLI**。所有编译与验证必须走 GitHub Actions |
| 目标设备 | **Android 10 / API 29 低端机**，只出 `arm64-v8a`；性能优化为硬要求，见 `HANDOFF.md` 第 9 节 |
| 当前状态 | 主工程接线 / CI / 纯逻辑层已完成；**P1 视频纹理接入已实现**（OES SurfaceTexture → 平面幕布）、黑底渲染、电视版 UI 移除、渲染循环零堆分配已落盘（**真机未验证**）；**P3 手柄驱动的可见设置面板仍是 TODO**；JVM 测试 6 文件 / **64** 个 `@Test` |
| 验收命令 | GitHub Actions `vr-build` 全绿；产出 `app-arm64-v8a-debug.apk` 工件 |

**一句话**：不要重新设计架构，不要换 SDK，不要加 Activity，不要引入凝视控制，不要把电视版 UI 放回主界面。接手后先查最新 CI run 并取回 Android 10 真机复测结论（`HANDOFF.md` 3.3 / 4 节），再补 P3。

---

## 1. 事实基线（已核实，附证据）

### 1.1 源项目

- `git ls-remote https://github.com/XYkadicloud/OpenNOW-Android` 返回：默认分支 `android-native`，另有 `zh-only`；HEAD `8811a745126b9ea8ed045fb32c44a95ec9d57586`，同时是 tag `v2.3.0-console.1`。
- GitHub Actions 历史（`GET /repos/XYkadicloud/OpenNOW-Android/actions/runs?per_page=8`）：`android-build` 于 `2026-11-01T05:40:35Z` **success**。说明该工程在 `ubuntu-24.04` + JDK 17 + Python + Rust + NDK 29 + CMake 3.31.6 环境下可编出 debug APK。
- 工程规模：`android/app/src/main/java/com/opencloudgaming/opennow/` 下约 219 个顶层 `.kt`，全模块 410 个 `.kt`；另有 Rust NVST 传输、WebRTC、CMake/JNI。

关键构建参数（`android/app/build.gradle.kts`，已核实）：

```
namespace / applicationId = com.opencloudgaming.opennow
compileSdk = 37 ; minSdk = 24 ; targetSdk = 37
versionCode = 186 ; versionName = 2.3.0
kotlin { jvmToolchain(17) ; languageVersion = KOTLIN_1_9 }
compose BOM = 2026.08.00
AGP = 9.4.0 (android/build.gradle.kts) ; Gradle = 9.7.1 (gradle-wrapper.properties)
externalNativeBuild.cmake → app/src/main/cpp/CMakeLists.txt , version 3.31.6
```

构建期还有三个自定义 task，**改动 Gradle 时不能破坏**：

- `generateTouchButtonResources`：读 `../locales/en.json` 生成 `values/touch_buttons.xml`。
- `packageChangelog`：读 `android/changelogs.json` 放入 assets。
- `buildNvst`：调用 `python3 android/scripts/build_nvst.py <ndkDir> <outDir>`；脚本第 30~32 行读取环境变量 `OPENNOW_ABIS`（逗号分隔）来收窄 Rust 目标，未设置则构建全部 4 个 ABI。
- `buildSrc` 中的 `WebRtcAudioGuardFactory` / `WebRtcTextureDeliveryFactory` 是 AGP ASM instrumentation，不要动。

`android/settings.gradle.kts` 使用 `RepositoriesMode.FAIL_ON_PROJECT_REPOS`，仓库只在根 `dependencyResolutionManagement` 声明 `google()` + `mavenCentral()`。**新增依赖只能加到 `app/build.gradle.kts` 的 `dependencies{}`，不要在模块里加 repositories**。

### 1.2 Google VR 接口现状（已核实）

| 接口 | 状态 | 证据 |
| --- | --- | --- |
| Google Cardboard SDK | **维护中**，最新 `v1.36.0`，2026-10-15 发布 | `GET /repos/googlevr/cardboard/releases/latest`；master 最新提交 `Cardboard SDK v1.36.0` (2026-10-07) |
| 旧 Google VR SDK / Daydream `com.google.vr:sdk-base` | 已停止维护；依赖 Google VR Services，2026 年不可用 | `https://search.maven.org/solrsearch/select?q=g:%22com.google.vr%22` 返回 `numFound 0` |
| Google Maven `dl.google.com/.../master-index.xml` | 无 cardboard 条目 | 实际抓取比对 |
| Maven Central `com.google.cardboard` | `numFound 0` | 实际查询 |
| Android XR / OpenXR | 面向 XR 头显，不支持手机插纸盒分屏 | 与需求不匹配 |

**结论**：Cardboard SDK **没有 Maven 制品**，必须从源码编译出 AAR。本仓库把官方源码原样 vendor 到 `third_party/cardboard/`，由 CI 单独用一个独立 Gradle 工程编出 `sdk-debug.aar`，再拷进 `android/app/libs/` 供主工程依赖。主工程与 SDK 工程是**两个独立 Gradle 构建**，因此 AGP 9.4 / 9.2、Gradle 9.7.1 / 9.8.0 互不冲突。

### 1.3 Cardboard SDK 精确 API（已核实，从 `v1.36.0` 源码摘录）

> 文件位置：`third_party/cardboard/java_api/src/main/java/com/google/cardboard/sdk/`。

**注意：接口名是 `CardboardView.Renderer`，不是 `StereoRenderer`。** 不要臆造类名。

```java
// CardboardView.java:185
public interface Renderer {
    void onNewFrame(HeadTransform headTransform);
    void onDrawEye(CardboardView.Eye eye);
    void onFinishFrame(Viewport viewport);
    void onSurfaceChanged(int width, int height);
    void onSurfaceCreated(EGLConfig config);
    void onRendererShutdown();
}
```

```java
// CardboardView.java:63 起，Eye 是 public static class
Eye.LEFT / Eye.RIGHT / Eye.MONOCULAR           // 常量，实际值来自 EyeType
float[] getEyeFromHead()                       // eyeFromHeadMatrix，16 个 float
float[] getEyeView()                           // = eyeFromHead * headView（已含头姿）
float[] getPerspective(float zNear, float zFar)
float[] getFieldOfView()                       // [left, right, bottom, top] 角度
int getEyeType()
void applyHeadView(float[] headView)           // Matrix.multiplyMM(eyeView, 0, eyeFromHead, 0, headView, 0)
```

```java
// CardboardView.java 公共方法（节选，行号为 v1.36.0）
public CardboardView(Context)                        // :517
public CardboardView(Context, AttributeSet)          // :532
public void setRenderer(Renderer)                    // :598
public boolean isStereoRenderModeEnabled()           // :633
public void setStereoRenderMode(boolean)             // :647  true=立体+畸变，false=单目全屏
public void onPause() / onResume() / onDestroy()     // :670 / :685 / :722
public void loadDeviceParams()                       // :697
public void onSettingsButtonClick()                  // :705
public void scanViewerQrCode()                       // :711  纸盒二维码校准
public void setOnBackButtonClick(Runnable)           // :779
public void setOnSettingsButtonClick(Runnable)       // :789
public void setOnTriggerEvent(Runnable)              // :800  ACTION_DOWN 时触发（屏幕触摸）
public void setEGLConfigChooser(...)                 // :741
public void setEGLWindowSurfaceFactory(...)          // :747
public void queueEvent(Runnable)                     // :770
```

```java
// HeadTransform.java
float[] getHeadView()                                // :38  4x4 head view
void getHeadView(float[], int)                       // :51
void getForwardVector(float[], int)                  // :70
void getUpVector(float[], int)                       // :89
void getRightVector(float[], int)                    // :108
void getQuaternion(float[], int)                     // :127  写入顺序 (x, y, z, w)
void getEulerAngles(float[], int)                    // :205
void getTranslation(float[], int)                    // :241
```

```java
// Initialize.java 静态块
static { System.loadLibrary("cardboard_sdk_jni"); }
// CardboardViewApi.java:176 会调用 Initialize.initialize(context)，
// 所以业务代码**不需要**手动调 Initialize.initialize()。
```

其它可用类：`LensDistortion`、`DistortionRenderer`、`CardboardLayout`、`CardboardGLSurfaceView`、`QrCode`、`Viewport`、`FieldOfView`、`com.google.cardboard.sdk.nativetypes.*`。

`CardboardView` 内部在 `onDrawFrame()`（:1038）里把 `getPose()` 拷进 `HeadTransform.getHeadView()`，再调用 `renderer.onNewFrame(headTransform)`；立体分支 `onRenderStereoFrame()`（:1098）通过 `DistortionRenderer` 做畸变矫正。因此**业务 Renderer 不需要自己处理畸变、头姿 sensor、左右眼 texture**，只需在 `onDrawEye()` 里用 `eye.getPerspective()` × `eye.getEyeView()` 画场景。

### 1.4 Cardboard SDK 构建参数（已核实并已打补丁）

`third_party/cardboard/sdk/build.gradle`：

```
apply plugin: 'com.android.library'
apply plugin: 'com.google.protobuf'
compileSdk = 35
minSdkVersion = 24        // 【已修改】上游是 26；app 是 24，不改会 manifest 合并失败
targetSdkVersion = 35
ndkVersion = "29.0.14206865"
abiFilters 'arm64-v8a'   // 【已修改】上游是 'armeabi-v7a','arm64-v8a'；收窄以缩短 CI
externalNativeBuild.cmake { path "CMakeLists.txt" ; arguments "-DANDROID_STL=c++_static" }
sourceSets.main.java.srcDirs 含 '../java_api/src/main/java'
sourceSets.main.proto.srcDirs = ["${project.rootDir}/proto"]
```

- `buildTypes.release.minifyEnabled true` → **必须编 `assembleDebug`**（`sdk-debug.aar`），否则公开 Java API 可能被混淆。
- 依赖（AAR **不携带 POM 传递依赖**，必须在主工程显式补）：
  `androidx.appcompat:appcompat:1.6.1`、`com.google.android.gms:play-services-vision:20.1.3`、`com.google.android.material:material:1.12.0`、`com.google.protobuf:protobuf-javalite:3.19.4`、`org.jspecify:jspecify:1.0.0`。
- `sdk/qrcode/android/AndroidManifest.xml` 声明 `<uses-feature android:glEsVersion="0x00030000" android:required="true"/>`，会合并进 app manifest（要求 GLES 3.0，真机基本都满足）。
- `sdk/CMakeLists.txt` 第 109 行引用 `../third_party/unity_plugin_api`（相对 `sdk/`），即 `third_party/cardboard/third_party/unity_plugin_api`，**该目录已随源码一并 vendor，不要删**。
- `third_party/cardboard/settings.gradle` 已改为只 `include ':sdk'`（上游还包含 `hellocardboard-android`、`paperscope`，已移除，避免无关模块参与构建）。
- `third_party/cardboard/gradle/wrapper/gradle-wrapper.properties` 指向 Gradle 9.8.0；根目录 `build.gradle` 用 AGP 9.2.0 + protobuf-gradle-plugin 0.10.0。

### 1.5 主工程关键接线点（已核实，行号可能有 ±3 偏移，以符号名为准）

| 作用 | 文件 | 关键符号 |
| --- | --- | --- |
| 解码输出 Surface 供应 | `android/app/src/main/java/com/opencloudgaming/opennow/Streaming.kt` | 约 :659 与 :2615，`decoderSurface = { renderer?.decoderTarget }`（两处：WebRTC factory 与 NVST transport） |
| 是否启用 direct SDR surface 解码 | 同上 | `directSdrEnabled = { renderer?.prefersDirectSdr == true }` |
| Surface 目标类型 | `MediaCodecSurfaceVideoDecoder.kt:48` | `internal class DecoderSurfaceTarget(val surface: Surface)` |
| Surface 持有者 | `StreamVideoSurface.kt:41` | `internal var decoderTarget: DecoderSurfaceTarget?` |
| 解码器创建 | `StreamCodec.kt:472~480` | `if (!directSdrEnabled()) return textureDecoder`；`MediaCodecSurfaceVideoDecoder(exactRequestedFps, decoderSurface, ...)` |
| Activity 内容 | `MainActivity.kt:104` | `setContent { ... OpenNowApp(...) }` |
| 手柄按键入口 | `MainActivity.kt:257` | `dispatchKeyEvent()`，其中 `NativeStreamInputRouter.dispatchKey(event)` 在约 :270 |
| 摇杆/轨迹球入口 | `MainActivity.kt:336` | `dispatchGenericMotionEvent()`，其中 `NativeStreamInputRouter.dispatchMotion(event)` |
| Manifest 主文件 | `android/app/src/main/AndroidManifest.xml` | `MainActivity` 声明在约 :80；`src/sideload/` 与 `src/playBundle/` 是**增量合并清单**，不是完整 manifest |

`MediaCodecSurfaceVideoDecoder.decode()` 每帧都调用 `surface()`，且当 `target !== outputSurface` 时会 `stopCodecLocked()` 并用新 Surface 重新 `configure()`。**这意味着 VR SurfaceTexture 可以在串流进行中热切换，无需重启会话** —— 这是视频接入方案成立的关键依据。

---

## 2. 用户需求的精确定义（不得自行扩写）

1. 基于现有分支项目改造，但**新建一个仓库**，不往 `OpenNOW-Android` 上改。
2. 手机上可用，套用 Google 的 VR 接口来「模拟 VR 操作」。
3. **串流画面必须是平面 2D**（用户原话："那个我不想走串流肯定一定是平面的，因为呢我基本都串流成业游戏"）。禁止把视频做成左右眼立体 3D、禁止 SBS 视频、禁止把游戏画面做视差。
4. **半 VR**：VR 只负责空间/幕布/菜单/视角；普通 2D 界面保留、可切回。
5. **可调项**，重点是「串流窗口大小，可以放大看到更大的画面」，其余合理的自行补充。
6. **纯手柄操作**：纸盒 VR 没有专业 VR 控制器，全部用手柄；**明确禁止眼睛/凝视操控方式**。
7. 有测试项就做测试。
8. 全部代码与编译放到 GitHub 上，用 GitHub Actions 编译验证。

### 2.1 可调项清单（已定稿，见 `vr/VrSettings.kt`）

| 键 | 含义 | 范围 / 默认 | 备注 |
| --- | --- | --- | --- |
| `screenScale` | **串流窗口大小** | 0.5 ~ 3.0，默认 1.0，步进 0.1 | 用户首要需求，直接放大幕布 |
| `screenDistanceMeters` | 幕布距离 | 0.8 ~ 6.0 m，默认 2.2 | 拉近也能让画面变大 |
| `brightness` | 画面亮度 | 0.4 ~ 1.4，默认 1.0 | |
| `curvature` | 幕布曲率 | 0.0 ~ 1.0，默认 0.0 | 0=纯平面；>0 为环抱曲面，放大时更舒适 |
| `screenVerticalOffset` | 垂直位置 | -1.0 ~ 1.0 m，默认 0.0 | |
| `viewerMode` | `CARDBOARD` / `SIMULATION` | 默认 `CARDBOARD` | CARDBOARD=立体+畸变（纸盒）；SIMULATION=单目全屏（裸眼模拟，无畸变） |
| `headTrackingEnabled` | 头部追踪 | 默认 true | **仅改变视角**，不触发任何选择/确认 |
| `headTrackingSensitivity` | 追踪灵敏度 | 0.0 ~ 1.5，默认 1.0 | |
| `gamepadDeadZone` | 摇杆死区 | 0.05 ~ 0.5，默认 0.20 | |
| `menuRepeatMs` | 摇杆长按重复间隔 | 60 ~ 500 ms，默认 140 | |
| `showStatus` | 显示状态文字 | 默认 true | |

菜单里还有三个动作项：纸盒扫码校准、恢复默认、退出 VR。

### 2.2 手柄键位（已定稿，见 `vr/VrKeys.kt` / `VrGamepadRouter.kt`）

| 输入 | 行为 |
| --- | --- |
| `L1 + R1` 同时按住（或 `SELECT + START`） | **切换 VR 模式开关**（进入/退出），任何状态下可用 |
| `SELECT`（或 `START`）单独 | VR 模式内打开/关闭设置菜单 |
| 菜单关闭时 | 所有其它按键**原样透传给游戏**（走原有 `NativeStreamInputRouter`） |
| 菜单打开：D-pad ↑/↓ | 上下选择项 |
| 菜单打开：D-pad ←/→ | 减小 / 增大当前项（L1/R1 按住时 ×5 加速） |
| 菜单打开：`A` / `DPAD_CENTER` | 确认 / 执行动作项 |
| 菜单打开：`B` | 关闭菜单 |
| 菜单打开：左摇杆 X/Y | 同 D-pad，带死区与重复延迟 |
| 菜单打开：X / Y | 预留（当前不绑定） |

---

## 3. 禁止事项（防幻觉清单，逐条遵守）

1. **禁止**写 `CardboardView.StereoRenderer`、`GvrView`、`GvrLayout`、`VrListenerService`、`Daydream`、`com.google.vr:sdk-base`、`com.google.cardboard:cardboard-sdk:...` Maven 坐标 —— 全部不存在或已废弃。
2. **禁止**在 `app/build.gradle.kts` 里加 `implementation("com.google.cardboard:...")`。正确做法是 `implementation(files("libs/cardboard-sdk.aar"))`，AAR 由 CI 生成。
3. **禁止**把 `third_party/cardboard` 加入主工程 `settings.gradle.kts` 的 `include`。它是独立 Gradle 构建。
4. **禁止**新建 `VrActivity` 并让串流切到它。原因：`MainActivity.dispatchKeyEvent()` 才是手柄输入进串流的唯一入口，换 Activity 会断掉游戏输入。正确做法是在 `MainActivity.setContent` 里用 `Box` 叠加 Compose `AndroidView`。
5. **禁止**实现任何 gaze / 凝视 / 注视计时 / 准星选中 / 眼球追踪 / 眨眼确认。头部姿态**只**用于视角矩阵。
6. **禁止**把视频渲染成左右眼不同内容的 3D。视频是一张纹理，两眼画同一块平面幕布（正确的立体感来自两眼视点不同，而不是画面不同）。
7. **禁止**改 `android/settings.gradle.kts` 的 `FAIL_ON_PROJECT_REPOS` 或往模块里加 `repositories{}`。
8. **禁止**把 `app` 的 `minSdk` 改成 26 以上，或把 `languageVersion`、`jvmToolchain`、ASM instrumentation 改掉。
9. **禁止**把 token、密钥、`.env` 写入仓库；`~/.opennow_gh_token` 只在本机、不入库。
10. **禁止**声称"真机已验证"。本任务能验证的只有：CI 编译、JVM 单元测试、APK 产物生成。真机/GFN 串流效果必须标注为「待真机验证」。
11. **禁止**修改 `locales/` 下非 `en.json` 的生成文件；新增 UI 文案直接放字符串资源或代码常量，不要动 Crowdin 产物。
12. **禁止**在 JVM 单元测试里直接引用 `android.opengl.Matrix`、`KeyEvent`、`MotionEvent` 等 Android 桩类（会抛 "Method ... not mocked"）。纯逻辑层必须用自己的常量与纯 Kotlin 矩阵实现。

---

## 4. 架构设计

```
┌──────────────────────── MainActivity (ComponentActivity) ────────────────────────┐
│  dispatchKeyEvent / dispatchGenericMotionEvent                                    │
│        │                                                                          │
│        ├─ VrSession.onKeyEvent() ──► [consumed?] true → return（VR 菜单打开时）   │
│        │                              false → 继续 NativeStreamInputRouter（游戏）│
│        ▼                                                                          │
│  setContent { Box {                                                              │
│        OpenNowApp(...)              // 原有 Compose UI，完全不动                  │
│        VrModeHost()                 // 新增：VR 开启时覆盖一层 CardboardView      │
│  } }                                                                             │
└──────────────────────────────────────────────────────────────────────────────────┘

视频链路（平面 2D，不改造原有 UI）：
  MediaCodec / WebRTC --> decoderSurface 供应点 (Streaming.kt:659 / 2615)
                              │
                              ├─ VR 开启 → VrVideoSurfaceRegistry.target() → Surface(SurfaceTexture)
                              └─ VR 关闭 → renderer?.decoderTarget（原逻辑，零改动）
                                                   │
                          SurfaceTexture.updateTexImage()（GL 线程，onNewFrame）
                                                   │
                          GL_TEXTURE_EXTERNAL_OES → 平面幕布 quad → 左右眼各画一次

渲染链路：
  CardboardView (Google SDK)
     └─ VrTheaterRenderer implements CardboardView.Renderer
          ├─ onSurfaceCreated : 建 SurfaceTexture / 编译 shader / 建占位纹理
          ├─ onNewFrame       : updateTexImage(), 计算 head rotation（带灵敏度）
          ├─ onDrawEye(eye)   : MVP = eye.getPerspective() * view * model
          │                      画平面幕布（平面 2D 视频）→ 画 VR 设置面板
          └─ onRendererShutdown : 释放 GL 资源、清 registry
```

**为什么用 overlay 而不是新 Activity**：`MainActivity` 持有 `NativeStreamInputRouter`、`streamSystemUiActive`、PiP、音量键、返回键分发等大量状态。VR 作为同 Activity 内的覆盖层，游戏手柄输入链路零改动；菜单打开时 `VrSession` 抢先消费按键，菜单关闭时完全透传。

**为什么用 SurfaceTexture 而不是复用 `StreamVideoSurface`**：WebRTC 的 `SurfaceViewRenderer` 跑在它自己的 EGL context，纹理无法直接被 Cardboard 的 GL context 采样。用 VR 线程自己创建的 `SurfaceTexture`（`GL_TEXTURE_EXTERNAL_OES`）+ 把它的 `Surface` 交给解码器，是最少侵入、可热切换的方案。

---

## 5. 仓库结构（精确路径）

```
OpenNOW-VR/
├── README.md                                  # 项目说明（已补 VR 章节）
├── README-VR.md                               # 【新增】VR 模式使用说明
├── docs/
│   └── VR_IMPLEMENTATION_PLAN.md              # 【本文件】防幻觉计划书
├── .github/workflows/
│   └── vr-build.yml                           # 【新增】唯一启用的验证流水线
├── docs/upstream-workflows/                   # 源仓库带来的 4 条发布/扫描流水线，仅作参考
│   └── android-build.yml / auto-build.yml / codeql.yml / release.yml
├── third_party/cardboard/                     # 【新增】Google Cardboard SDK v1.36.0 官方源码
│   ├── build.gradle / settings.gradle / gradle.properties / gradlew / gradle/
│   ├── sdk/            (build.gradle 已打 2 处补丁)
│   ├── java_api/       (Java API 源码)
│   ├── proto/
│   ├── third_party/    (Vulkan / unity_plugin_api，CMake 依赖，勿删)
│   └── LICENSE
├── android/
│   ├── app/build.gradle.kts                   # 【已改】AAR + 5 个传递依赖
│   ├── app/libs/cardboard-sdk.aar             # 【CI 生成】不入库，.gitignore 排除
│   ├── app/src/main/AndroidManifest.xml       # 【已改】加陀螺仪 uses-feature
│   └── app/src/main/java/com/opencloudgaming/opennow/
│       ├── MainActivity.kt                    # 【已改】5 处接线（attach / Box / 2 个输入拦截 / import）
│       ├── Streaming.kt                       # 【已改】2 处 decoderSurface hook
│       └── vr/                                # 【新增】VR 模块
│           ├── VrSettings.kt                  # 数据类 + 范围 + 存储（已实现）
│           ├── VrKeys.kt                      # 纯 Int 键码常量（已实现）
│           ├── VrMatrix4.kt                   # 纯 Kotlin 4x4 矩阵（已实现）
│           ├── VrScreenGeometry.kt            # 平面/曲面幕布几何（已实现）
│           ├── VrHeadPose.kt                  # 四元数→缩放旋转矩阵（已实现）
│           ├── VrMenuModel.kt                 # 菜单项与增减值（已实现）
│           ├── VrGamepadRouter.kt             # 纯手柄状态机（已实现）
│           ├── VrSession.kt                   # Android 状态层 + 输入分发（骨架）
│           ├── VrVideoSurfaceRegistry.kt      # SurfaceTexture registry（骨架）
│           ├── VrGlProgram.kt                 # GL shader helper（骨架）
│           ├── VrTheaterRenderer.kt           # CardboardView.Renderer（黑底 + OES 平面 + 2D 面板，零分配）
│           ├── VrTheaterView.kt               # CardboardView 子类（骨架）
│           └── VrModeHost.kt                  # Compose 覆盖层（骨架）
│   └── app/src/test/java/com/opencloudgaming/opennow/vr/   # 【新增】JVM 单测
└── locales/ , native/ , opennow-stable/      # 原样保留
```

---

## 6. 当前骨架状态（逐文件）

图例：**[完成]** 可直接用；**[骨架]** 能编译但功能留白；**[TODO]** 需接手模型实现。
本节状态于 2026-11-06 按磁盘实际文件逐一核对，核对方法见第 6.1 节。

| 文件 | 状态 | 说明 |
| --- | --- | --- |
| `third_party/cardboard/**` | [完成] | 官方 v1.36.0 源码（211 个文件）+ 2 处补丁（`minSdk 24` / 单 ABI），独立 Gradle 工程；`gradle-wrapper.jar` 已入库 |
| `.github/workflows/vr-build.yml` | [完成] | 两阶段：编 `sdk-debug.aar` → 单测 + `assembleDebug` → 上传 APK；失败时另传测试报告 |
| `app/build.gradle.kts` | [完成] | `files("libs/cardboard-sdk.aar")` + 5 个传递依赖 |
| `AndroidManifest.xml` | [完成] | 加 `android.hardware.sensor.gyroscope`，`required="false"` |
| `MainActivity.kt` | [完成] | 5 处：`VrSession.attach()`、setContent 根 `Box` + `VrModeHost()`、`dispatchKeyEvent` / `dispatchGenericMotionEvent` 拦截、2 行 import |
| `Streaming.kt` | [完成] | 2 处 `decoderSurface` + 2 处 `directSdrEnabled`（共 4 行）+ 1 行 import；VR 关闭时行为不变 |
| `VrSettings.kt` | [完成] | 11 个可调项 + `VrRanges` + `sanitized()` |
| `VrSettingsStore.kt` | [完成] | SharedPreferences 读写，含枚举解析兜底 |
| `VrKeys.kt` | [完成] | 纯 Int 键码常量，避免 JVM 单测碰 Android 桩类 |
| `VrMatrix4.kt` | [完成] | 真实 API：`identity()` / `multiply()` / `rotation()`（**没有** `rotationFromAxisAngle`） |
| `VrScreenGeometry.kt` | [完成] | 两个 `build()` 重载；平面与环抱曲面顶点/UV，`GL_TRIANGLE_STRIP` 顺序 |
| `VrHeadPose.kt` | [完成] | `scaledRotation(quaternion, sensitivity)` |
| `VrMenuModel.kt` | [完成] | 14 行菜单；真实 API：`adjust()` / `activate()` / `display()` / `rawValue()`（**没有** `label()`） |
| `VrGamepadRouter.kt` | [完成] | `onKey()` / `onAxis()` / `reset()`，产出 `VrCommand` |
| `VrSession.kt` | [骨架] | StateFlow 状态 + 命令分发已接好；`videoAspect` 写入与 GL 侧回调仍 TODO |
| `VrVideoSurfaceRegistry.kt` | [骨架] | `install()` / `clear()` / `target()` / `currentTexture()` / `consumeFrameAvailable()` 已有；**`install()` 目前没有任何调用方** |
| `VrGlProgram.kt` | [骨架] | 真实 API：`createProgram()`（**没有** `compileProgram()`）；纹理/缓冲封装 TODO |
| `VrTheaterRenderer.kt` | [完成，真机未验证] | 六个 `Renderer` 回调齐全；**黑底 + `GL_TEXTURE_EXTERNAL_OES` 串流平面（P1 已实现）+ 无流时 2D Canvas 面板**；复用 UV、渲染循环零堆分配、平面顶点 50→4；`getTransformMatrix(stMatrix)` 单参数签名。**P3 手柄可交互设置面板仍是 TODO**。GLSL 运行时编译，CI 抓不到 shader 错误 |
| `VrTheaterView.kt` | [骨架] | `CardboardView` 子类，生命周期 + `setStereoRenderMode` 已接；SDK 触摸层隐藏 TODO |
| `VrModeHost.kt` | [骨架] | Compose `AndroidView` 覆盖层 + 进入/退出按钮 |
| `README.md` / `README-VR.md` | [完成] | 英文 fork 说明 + 中文使用/开发说明（含诚实状态表） |
| 单测 **6 个文件** | [完成] | `VrSettingsTest` / `VrScreenGeometryTest` / `VrMatrix4Test` / `VrHeadPoseTest` / `VrMenuModelTest` / `VrGamepadRouterTest`，全部纯 JVM |

**骨架设计原则**：VR 默认关闭。关闭时 `VrVideoSurfaceRegistry.target()` 返回 `null`、`directSdrEnabled` 走原逻辑、`VrSession` 不消费任何按键（除进入 VR 的组合键）、`VrModeHost` 不渲染 GL 视图 —— **对原 App 行为基本不变**，因此骨架可以安全合入并独立通过 CI。

### 6.1 状态核对记录（防幻觉必读）

> **本文件第一版（2026-11-06）曾把未落盘的工作写成「已创建 / 已改 / 已完成 / 已写好」**：
> GitHub 仓库、`vr-build.yml`、`app/build.gradle.kts`、`MainActivity.kt`、`Streaming.kt`、
> `AndroidManifest.xml`、`README-VR.md`，以及「单测 5 个文件已完成」（当时实际只有 1 个）。
> 那些都是**目标态，不是事实**。第二版已按磁盘实际内容与 API 实测逐项修正。
>
> **教训：不要相信交接文档里的「已完成」三个字，包括本文件。动手前自己跑一遍核对命令。**

接手模型的第一组命令（仓库根目录执行）：

```bash
# 1. 文件是否真的存在
ls .github/workflows/vr-build.yml README-VR.md
find android/app/src/main/java/com/opencloudgaming/opennow/vr -name '*.kt' | sort
find android/app/src/test/java/com/opencloudgaming/opennow/vr -name '*.kt' | sort

# 2. 主工程接线是否真的落盘
grep -n "VrSession.attach\|VrModeHost\|VrSession.onKeyEvent\|VrSession.onMotionEvent" \
  android/app/src/main/java/com/opencloudgaming/opennow/MainActivity.kt
grep -n "VrVideoSurfaceRegistry" android/app/src/main/java/com/opencloudgaming/opennow/Streaming.kt
grep -n "cardboard-sdk.aar" android/app/build.gradle.kts

# 3. 函数名是否真的存在（防臆造 API）
grep -n "fun " android/app/src/main/java/com/opencloudgaming/opennow/vr/VrMatrix4.kt
grep -n "fun " android/app/src/main/java/com/opencloudgaming/opennow/vr/VrGlProgram.kt
grep -n "public interface Renderer" \
  third_party/cardboard/java_api/src/main/java/com/google/cardboard/sdk/CardboardView.java

# 4. GitHub 仓库 / CI 是否真的存在
#    GET /repos/XYkadicloud/OpenNOW-VR   → 200 才是存在
#    GitHub → Actions → vr-build        → 看最近一次 run 的 conclusion 与 APK 工件
```

**已出现过并被纠正的具体幻觉（勿再犯）**：

| 曾写错 | 事实 |
| --- | --- |
| `CardboardView.StereoRenderer` | 真实接口是 `CardboardView.Renderer`（`CardboardView.java:185`） |
| `VrGlProgram.compileProgram()` | 真实函数名 `createProgram()` |
| `VrMatrix4.rotationFromAxisAngle()` | 真实函数名 `rotation()` |
| `VrMenuModel.label()` | 真实函数名 `display()` |
| `SurfaceTexture.getTransformMatrix(mtx, offset)` | Android 只有单参数 `getTransformMatrix(mtx)`（已修） |
| 「单测 5 个文件已完成」 | 当时只有 `VrSettingsTest.kt`；现已补齐 6 个 |
| 「GitHub 仓库已创建」 | 当时 `GET /repos/XYkadicloud/OpenNOW-VR` 返回 **404** |
| 「`vr-build.yml` 已写好」 | 当时 `.github/workflows/` 下只有从源仓库复制的 4 个文件 |

---

## 7. 已落盘的关键接线（已核实，供参考）

`app/build.gradle.kts` 的 `dependencies{}` 新增（**不要删**）：

```kotlin
// Google Cardboard SDK v1.36.0 — AAR 由 CI 从 third_party/cardboard 编译生成
implementation(files("libs/cardboard-sdk.aar"))
implementation("androidx.appcompat:appcompat:1.6.1")
implementation("com.google.android.gms:play-services-vision:20.1.3")
implementation("com.google.android.material:material:1.12.0")
implementation("com.google.protobuf:protobuf-javalite:3.19.4")
implementation("org.jspecify:jspecify:1.0.0")
```

`Streaming.kt` 两处（WebRTC 与 NVST 各一处）：

```kotlin
decoderSurface = { VrVideoSurfaceRegistry.target() ?: renderer?.decoderTarget },
directSdrEnabled = { VrVideoSurfaceRegistry.isActive || renderer?.prefersDirectSdr == true },
```

`MainActivity.kt`：

```kotlin
// onCreate，setContent 之前
VrSession.attach(this)

// dispatchKeyEvent 开头，mascotActivity.onKey(event) 之后、NativeStreamInputRouter 之前
if (VrSession.onKeyEvent(event)) return true

// dispatchGenericMotionEvent 开头
if (VrSession.onMotionEvent(event)) return true

// setContent 内：外层 Box 包住原有的 if(ready)/else 分支，VrModeHost 放在最后
Box(modifier = Modifier.fillMaxSize()) {
    if (ready) { OpenNowApp(...) } else { /* 原有加载动画 */ }
    VrModeHost()
}
```

---

## 8. 分阶段实施步骤（接手模型按序执行）

### P0 — 复现基线（**必须先做，约 1 次 CI**）

1. `git clone https://github.com/XYkadicloud/OpenNOW-VR.git`，切到 `main`。
2. 在 GitHub 手动触发 `Actions → vr-build → Run workflow`。
3. 确认两个 job 都绿，`opennow-vr-debug-arm64-v8a` 工件里有 `app-arm64-v8a-debug.apk`。
   **这是验收标准，不是「已完成」的事实**；以 Actions 页面最近一次 run 的 `conclusion` 为准。
4. **若失败，先修 CI，不要写业务代码。** 常见失败见第 10 节。

### P1 — 视频接入（核心，把平面串流画进 VR）

改动文件：`vr/VrVideoSurfaceRegistry.kt`、`vr/VrTheaterRenderer.kt`。

1. 在 `VrTheaterRenderer.onSurfaceCreated()`（GL 线程）里：
   ```kotlin
   val tex = IntArray(1)
   GLES20.glGenTextures(1, tex, 0)
   GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
   GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
   GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
   GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
   GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
   val st = SurfaceTexture(tex[0])
   st.setOnFrameAvailableListener { frameAvailable.set(true) }
   VrVideoSurfaceRegistry.install(tex[0], st, Surface(st))
   ```
   注意 `DecoderSurfaceTarget` 是 `internal` 类，**同模块可访问**，import `com.opencloudgaming.opennow.DecoderSurfaceTarget`。
2. `onNewFrame()`：若 `frameAvailable` 为 true，调用 `st.updateTexImage()` 与 `st.getTransformMatrix(stMatrix)`。这两个方法**必须在创建 SurfaceTexture 的同一 GL 线程**调用。
3. 片元着色器必须声明 `#extension GL_OES_EGL_image_external : require`，采样器类型 `samplerExternalOES`；顶点着色器用 `uSTMatrix` 变换 UV。
4. 用 `VrScreenGeometry.build(settings, aspect)` 生成幕布顶点，`GL_TRIANGLE_STRIP` 绘制。宽高比从视频帧尺寸取（可通过 `SurfaceTexture` 无法直接拿到，建议从 `VrSession.videoAspect` 由 `Streaming.kt` 的 `onFrameResolutionChanged` 回调写入；**这是 TODO，不要臆造 API**），拿不到时默认 16/9。
5. `onRendererShutdown()`：`VrVideoSurfaceRegistry.clear()` → `surface.release()` → `surfaceTexture.release()` → `glDeleteTextures`。
6. 无视频帧时画占位纹理（当前骨架已实现），保证 VR 模式永远能进能出。

**验收**：CI 全绿；真机验证项见第 12 节（CI 无法验证视频内容，必须诚实标注）。

### P2 — MVP 与头部追踪

改动文件：`VrTheaterRenderer.onDrawEye()`、`VrHeadPose.kt`（已提供纯函数）。

```kotlin
val eyeFromHead = eye.getEyeFromHead()                    // 16 floats
val headRotation = VrHeadPose.scaledRotation(quaternion, settings.headTrackingSensitivity)
val view = VrMatrix4.multiply(eyeFromHead,
               if (settings.headTrackingEnabled) headRotation else VrMatrix4.identity())
val proj = eye.getPerspective(0.1f, 100f)
val mvp  = VrMatrix4.multiply(proj, view)                // 幕布顶点已是世界坐标，model = I
```

- `quaternion` 来自 `HeadTransform.getQuaternion(floatArrayOf(0f,0f,0f,1f), 0)`，顺序 **(x, y, z, w)**。
- 不要直接用 `eye.getEyeView()`，因为无法插入灵敏度缩放；用 `eye.getEyeFromHead()` × 自己的 head rotation 等价替代（依据 `Eye.applyHeadView()` 的实现：`Matrix.multiplyMM(eyeView, 0, eyeFromHead, 0, headView, 0)`）。
- `viewerMode == SIMULATION` 时 `VrTheaterView` 调 `setStereoRenderMode(false)`；SDK 会走单目分支、`Eye.MONOCULAR`、45° 固定 FOV、无畸变。
- 头部姿态**只**进 view 矩阵，不参与任何菜单判定。

### P3 — VR 设置面板（纯手柄）

改动文件：`VrTheaterRenderer.kt`、`vr/VrMenuBitmap.kt`（新建）、`vr/VrSession.kt`。

1. 用 `android.graphics.Canvas` 把 `VrMenuModel.items` 画到 `Bitmap`（建议 1024×768，ARGB_8888），选中行高亮、显示当前值与范围；`VrSession.settings/menuOpen/selectedIndex` 变化时置 `menuDirty = true`。
2. 在 `onDrawEye()` 最后把 Bitmap 上传为 `GL_TEXTURE_2D`（`GLUtils.texImage2D`），开启 `GL_BLEND` + `SRC_ALPHA, ONE_MINUS_SRC_ALPHA`，画一块位于幕布前方（更靠近相机）的面板 quad。
3. **不要**给面板加准星、注视命中、计时选取。面板只响应 `VrGamepadRouter` 产出的 `VrCommand`。
4. `VrCommand.ScanQr` → `VrTheaterView.scanViewerQrCode()`（SDK 方法，已核实存在于 :711）；`VrCommand.ExitVr` → `VrSession.setEnabled(false)`；`VrCommand.Reset` → `VrSettings()` 默认。
5. 建议隐藏 SDK 自带的 `CardboardLayout` 触摸按钮（立体模式下 `setStereoRenderMode(true)` 会 `showAll()`），避免与"纯手柄"混淆；可在调用后 `post { getChildAt(1)?.visibility = GONE }`，但不要依赖私有字段名。

### P4 — 补齐真机可用性细节

- `VrTheaterView` 的 `onPause/onResume/onDestroy` 与 Activity 生命周期绑定（`VrModeHost` 用 `LifecycleEventObserver` 已留接口）。
- 无手柄时的**紧急退出**：长按屏幕 2 秒退出 VR（仅作安全兜底，不作为操控方式）；正常操控仍为纯手柄。
- `screenScale` 放大到 3.0 时，检查 `curvature` 建议值（>0 可减轻边缘畸变），在菜单里给出提示文本。
- 低性能设备：可在菜单加"渲染分辨率倍率"（当前未列在 `VrSettings`，如要加需同步更新 `VrSettingsTest`）。

### P5 — 文档与交付

- 更新 `README-VR.md`：键位表、可调项表、纸盒扫码步骤、已知限制。
- 更新 `docs/VR_IMPLEMENTATION_PLAN.md` 第 6 节状态表，把完成的 [骨架] 改为 [完成]。
- 触发一次 CI，贴出 run URL 与 APK 工件名。

---

## 9. CI 设计（`vr-build.yml`，已落盘）

两个 job：

```
job 1: cardboard-aar (ubuntu-24.04)
  checkout → setup-java 17 → sdkmanager 安装
    platforms;android-35, build-tools;35.0.0, ndk;29.0.14206865, cmake;3.31.6
  → (working-directory: third_party/cardboard) echo "sdk.dir=$SDK" > local.properties
  → cd third_party/cardboard && ./gradlew --no-daemon :sdk:assembleDebug
  → upload-artifact  sdk/build/outputs/aar/sdk-debug.aar

job 2: app (needs: cardboard-aar)
  checkout → setup-java 17 → setup-python 3.12 → rustup target aarch64-linux-android
  → sdkmanager 安装 platforms;android-37.0 / 37.1 / 37.2, build-tools;37.0.0 / 36.0.0,
                    ndk;29.0.14206865, cmake;3.31.6
  → download-artifact 到 android/app/libs/ 并重命名 cardboard-sdk.aar
  → (working-directory: android) echo "sdk.dir=$SDK" > local.properties
  → OPENNOW_ABIS=arm64-v8a ./gradlew --no-daemon --no-configuration-cache \
        -Popennow.abis=arm64-v8a :app:assembleDebug \
        :app:testDebugUnitTest --tests "com.opencloudgaming.opennow.vr.*"
  → upload-artifact  app-arm64-v8a-debug.apk
```

要点：

- **必须用 `assembleDebug`**，不要用 `assembleRelease`（SDK release 会 minify，混淆公开 API）。
- **JVM 单测限定 `--tests "com.opencloudgaming.opennow.vr.*"`**：上游另有 167 个测试类引用 `android.*` 框架类，既无 Robolectric 也无 `unitTests.returnDefaultValues`，上游 CI 从未执行过它们。全量跑会产生与本次改动无关的失败。
- `-Popennow.abis=arm64-v8a` + `OPENNOW_ABIS=arm64-v8a` 同时给，前者控制 Gradle `abiFilters`，后者控制 Rust 只编一个 target，显著缩短 CI。
- `--no-configuration-cache` 必须保留（`android/gradle.properties` 里 `org.gradle.configuration-cache=true`，与 `buildSrc` instrumentation 组合下易出问题，原有 `android-build.yml` 也这么做）。
- `local.properties` 不入库（`.gitignore` 已含）。
- `android/app/libs/*.aar` 不入库，由 CI 下载；本地开发需先跑一次 job 1 或按 README 手动构建 AAR。

### 9.1 真实 CI 运行记录（防幻觉：只记录实际用 API / 日志查过的 run）

| run ID | 触发 commit | 结论 | 可核实的事实依据 |
| --- | --- | --- | --- |
| `42186000233` | `02ce545` | **失败** | job 1 `build-cardboard-sdk-aar` 成功编出 AAR；job 2 在 `:app:compileDebugKotlin` 报 `VrVideoSurfaceRegistry.kt:29:9 'public' function exposes its 'internal' return type DecoderSurfaceTarget`。已按第 10 节修为 `internal object` |
| `42187431788` | `1fc4bd7` | **成功** | 两个 job 均 `success`；日志含 `> Task :app:testDebugUnitTest`、`BUILD SUCCESSFUL in 7m 7s`、`'app/build/outputs/apk/debug/app-debug.apk' -> '../out/app-arm64-v8a-debug.apk'`；artifacts：`cardboard-sdk-aar` 378245 B、`opennow-vr-debug-arm64-v8a` 47554069 B，`expired=False` |
| `42189848032` | `fcdb892` | **成功** | 新增的 `Assert VR JVM tests actually ran` 步骤输出 `VR JVM test suites: 6 \| tests: 58 \| failures: 0 \| errors: 0 \| skipped: 0`，并逐个列出 6 个 `TEST-com.opencloudgaming.opennow.vr.*.xml`；与源码 6 个测试文件 / 58 个 `@Test` 一致。3 个 artifacts 齐全 |
| `42192555168` | `11a4d69` | **成功** | 闪退修复版。`Verify the Cardboard JNI bridge is in the AAR` 与 `... in the APK` 两个守卫均 `success`；AAR 385622 B、APK 47565082 B。人工解包 APK 确认 `lib/arm64-v8a/libcardboard_sdk_jni.so`（21800 B）存在且含 `Java_com_google_cardboard_sdk_Initialize_nativeInitialize` 等 JNI 符号 |
| `42194578267` | `4c67196` | **成功** | docs-only push 触发的回归验证；`build-cardboard-sdk-aar` 与 `vr-unit-tests-and-debug-apk` 两 job 全绿，确认事故复盘文档提交未破坏构建链 |

> **真机实测（2026-11-06，用户反馈，优先级高于任何 CI 结论）**：run `42192555168` 之后的 APK
> 点击「进入 VR」**不再闪退**，用户原话「其实能进入 VR 模式啊。当然那由于完全没有任何场景，
> 所以说呢就啥也没有的状态」。据此确认 `libcardboard_sdk_jni.so` 修复生效；「啥也没有」是
> 渲染内容为空的问题，已由 12.5 节的黑底 + 2D 纹理实现处理，**仍待真机复测**。

> **历史限定（保留以便追溯）**：run `42187431788` 的日志当时**没有打印测试数量**，只能证明
> `:app:testDebugUnitTest` 被调用且整体构建成功，不能据此断言「6 个测试类全部执行」。
> 因此补了 `Assert VR JVM tests actually ran`：解析
> `app/build/test-results/testDebugUnitTest/TEST-*.xml`，要求至少一个 `<testsuite>`、
> `tests > 0` 且 `failures/errors == 0`，否则 job 直接失败。
> 该断言在 run `42189848032` 起生效，实测 58 个测试全部执行；测试报告与 XML 改为 `always()` 上传以便审计。

> 已知非致命警告：`Node.js 20 is deprecated ... forced to run on Node.js 24`
> （`actions/checkout@v4` 等），只是 warning，不影响结论。

---

## 10. CI 排错速查

| 现象 | 原因 / 处理 |
| --- | --- |
| `uses-sdk:minSdkVersion 24 cannot be smaller than 26` | vendored `sdk/build.gradle` 的 minSdk 补丁被误改回 26；改回 24 |
| `Could not find com.google.cardboard:cardboard-sdk` | 有人加了 Maven 坐标；删掉，只用 `files("libs/cardboard-sdk.aar")` |
| `Program type already present` / `Duplicate class` | 传递依赖版本冲突；对照 1.4 节的 5 个版本 |
| `No such file: libs/cardboard-sdk.aar` | job 2 没拿到 artifact；检查 job 1 产物路径 `sdk/build/outputs/aar/sdk-debug.aar` 与 download 步骤的 rename |
| `CMake ... unity_plugin_api not found` | `third_party/cardboard/third_party/` 被删；从 `googlevr/cardboard@v1.36.0` 恢复 |
| `protoc` 相关失败 | protobuf-gradle-plugin 0.10.0 / protoc 3.19.4 需联网；不要改版本 |
| `Method ... not mocked` 单测失败 | 纯逻辑层混入了 Android 类；改用 `VrKeys` 常量与 `VrMatrix4` |
| `UnsatisfiedLinkError: cardboard_sdk_jni` | AAR 只含 arm64-v8a；APK 也必须 arm64（`-Popennow.abis=arm64-v8a`）。x86 模拟器不支持 |
| `buildNvst` 超时 | 确认设置了 `OPENNOW_ABIS=arm64-v8a`，否则 Rust 会编 4 个 target |
| `'public' function exposes its 'internal' return type DecoderSurfaceTarget` | `VrVideoSurfaceRegistry` 必须是 **`internal object`**，不能是 `public object`。这是首次 CI（run `42186000233`）真实的失败原因，已修 |
| `No tests found for given includes` | `--tests` 的 pattern 写错；正确值是 `com.opencloudgaming.opennow.vr.*` |
| 大量 `Method ... not mocked` | 误把上游 167 个测试也跑了；加 `--tests` 限定，见第 9 节 |

---

## 11. 单元测试清单（6 个文件已落盘，跑 `:app:testDebugUnitTest`）

全部是**纯 JVM 测试**，不引用任何 `android.view.*` / `android.opengl.*` 桩类，不需要 Robolectric。

| 测试类 | 主要覆盖点 |
| --- | --- |
| `VrSettingsTest` | 默认值落在所有范围内、越界钳制、`sanitized()` 幂等、非数值字段保留 |
| `VrScreenGeometryTest` | **screenScale 单调放大幕布**、宽高比只改宽度、非法 aspect 兜底、曲率对称且边缘更近、平面 z 恒定、所有顶点 z<0、UV 覆盖、垂直偏移、segments 下限 |
| `VrMatrix4Test` | 单位阵、左右乘单位阵中性、乘法结合律、非交换律、Y 轴旋转解析式、轴归一化、零轴兜底、非 4x4 输入抛异常 |
| `VrHeadPoseTest` | 单位四元数=单位阵、零/负灵敏度锁视角、1.0 灵敏度还原精确旋转、灵敏度缩放角度、未归一化四元数、取反四元数等价、零四元数兜底、短数组抛异常 |
| `VrMenuModelTest` | 14 项且 id 唯一、`screenScale` 步进、上下界钳制、零步长无效、ACTION 行不改动设置、枚举/开关翻转、`activate` 恢复默认、ms 以 Long 存储、每个 RANGE 项都不越界 |
| `VrGamepadRouterTest` | 非手柄事件透传、VR 关闭时按键全透传、L1+R1 与 SELECT+START 组合只触发一次、菜单键仅在 VR 内生效、D-pad/AB/X 路由、肩键 ×5 加速、死区与自定义死区、摇杆重复延迟与归中复位、`reset()` 清锁存 |

统计命令：

```bash
find android/app/src/test/java/com/opencloudgaming/opennow/vr -name '*Test.kt' | wc -l   # 6
grep -rh '@Test' android/app/src/test/java/com/opencloudgaming/opennow/vr | wc -l
```

新增可调项时**必须同步补测试**，否则 CI 仍绿但行为未验证。

---

## 12. 已知限制（必须如实告知用户）

1. CI 只能证明**可编译 + JVM 单测通过 + 能出 APK**。GFN 串流画面能否正确显示、手柄手感、纸盒畸变观感、不同手机陀螺仪表现，**均需真机验证**，本任务未验证。
2. VR APK 目前只出 `arm64-v8a`；x86 模拟器跑不了 Cardboard 原生库，32 位 ARM 手机会 `INSTALL_FAILED_NO_MATCHING_ABIS`。
3. `third_party/cardboard/sdk/qrcode/android/AndroidManifest.xml` 声明 `glEsVersion=0x00030000` 且 `required=true`（OpenGL ES 3.0）；极老设备会被 Play 过滤。Android 10 设备普遍满足。侧载不受 Play 过滤影响。
4. **（2026-11-06 二次修正，P1 落地后）** 第一版写「VR 模式下 `directSdrEnabled` 已强制 true」是幻觉；中间版写「`install()` 没有任何调用方，恒为 false」**在 P1 落地后也已过时**。`Streaming.kt` 实际为 `VrVideoSurfaceRegistry.isActive || renderer?.prefersDirectSdr == true`，`isActive = target != null`；现在 `VrTheaterRenderer.onSurfaceCreated()` 会真正调用 `install()`，因此 VR 模式下 `isActive` 为 true、会走 SDR。**该行为仍未在真机串流中验证**。
5. 视频宽高比仍取 `VrSession.videoAspect`（默认 16:9）。`VrTheaterRenderer.onDrawEye()` 会在 aspect 变化时自动重建幕布网格，但把 `Streaming.kt` 的帧尺寸回调写回 `videoAspect` 的接线**仍是 TODO，不要臆造 API**。
6. 用户提供的 GitHub PAT 已在对话中明文出现，**工作结束后必须撤销并重新生成**；token 未写入任何仓库文件。

### 12.1 真机实测事故复盘（2026-11-06，Android 10 / API 29）

**现象**：用户安装 run `42189848032` 的 APK 后，点击右下角「进入 VR」按钮**直接闪退**。这是真机实测，不是推测。

**根因链（逐层可核实）**：

1. `VrModeHost` 的「进入 VR」→ `VrSession.toggle()` → `enabled = true`。
2. `AndroidView(factory)` 构造 `VrTheaterView` → 继承 `CardboardView(context)`。
3. `CardboardView.initialize()` → `new CardboardViewApi(context)`（`CardboardViewApi.java:174-176`）。
4. → `Initialize.initialize(context)` → 触发 `Initialize.java:30-32` 静态块 `System.loadLibrary("cardboard_sdk_jni")`。
5. 旧 APK 的 `lib/arm64-v8a/` 只有 6 个 `.so`，**没有 `libcardboard_sdk_jni.so`** → `UnsatisfiedLinkError` → 进程崩溃。

**为什么会缺**：上游 `settings.gradle` 是 `include ':sdk', ':hellocardboard-android', ':paperscope'`，而 `libcardboard_sdk_jni.so` 由 **`paperscope/CMakeLists.txt:94 add_library(cardboard_sdk_jni SHARED ...)`** 产出，源码是 `java_api/src/main/jni/cardboard_jni.cc` + `sdk/jni_utils/android/jni_utils.cc`。本 fork vendor 时把 `settings.gradle` 收窄成只 `include ':sdk'`，把这个 JNI 库一起丢了。`sdk/CMakeLists.txt` 只产出 `GfxPluginCardboard`（Unity 插件名）；而且 `cardboard_api.lds` 用 `local: *` 隐藏全部 `Java_*` 符号，**所以既不能给现有库改名，也不能把 `cardboard_jni.cc` 塞进它**。

**修复**：在 `third_party/cardboard/sdk/CMakeLists.txt` 末尾新增独立 SHARED target `cardboard_sdk_jni`，链接 `GfxPluginCardboard` 导出的 `Cardboard*` C API + `android/log/GLESv2`，并加 `-Wl,-z,max-page-size=16384`。Android 会通过 `DT_NEEDED` 同时加载两个库。

**验证（run `42192555168`，commit `11a4d69`）**：

- CI 新增 `Verify the Cardboard JNI bridge is in the AAR` 与 `Verify the Cardboard JNI bridge is in the APK` 两个守卫，均 `success`。
- 解包 APK 确认 `lib/arm64-v8a/libcardboard_sdk_jni.so`（21800 B）存在，含 `Java_com_google_cardboard_sdk_Initialize_nativeInitialize`、`HeadTracker_nativeHeadTrackerCreate`、`LensDistortion_nativeLensDistortionCreate` 等 JNI 符号；`libGfxPluginCardboard.so` 保留。
- **仍待真机复测**：CI 无设备，只能证明 `UnsatisfiedLinkError` 这条路径被消除，不能证明 GL shader 编译、陀螺仪初始化、纸盒渲染在真机上 100% 正常。

> 教训：**CI 全绿 ≠ 真机能用**。编译期不检查 `System.loadLibrary()` 的字符串对应 `.so` 是否存在，必须显式断言产物内容。

### 12.2 Android 10（API 29）静态兼容性核验

| 项 | 实测值 / 依据 | 结论 |
| --- | --- | --- |
| 主工程 SDK 等级 | `compileSdk 37 / minSdk 24 / targetSdk 37`（`android/app/build.gradle.kts:34-42`） | API 29 ∈ [24, 37]，**安装层面允许**；`targetSdk` 高于设备 API 不阻止安装 |
| vendored Cardboard SDK | `compileSdk 35 / minSdk 24 / targetSdk 35` | 上游原为 26，我们降到 24；29 ≥ 26，无兼容风险 |
| 硬件要求 | 主 `AndroidManifest.xml` 全部 11 个 `uses-feature` 均为 `required="false"` | 无陀螺仪等硬件不会阻止安装 |
| ABI | APK 只有 `lib/arm64-v8a/`（修复后 7 个 `.so`） | **必须 64 位 ARM 手机** |
| 渲染 API | `CardboardGLSurfaceView` / `EglFactory`，`setEGLContextClientVersion(2)` | 运行时走 GLES2（需 API 16+），**不强制 Vulkan**；Android 10 支持 |
| SDK 版本分支 | `CardboardViewApi.loadDeviceParams()` 在 `SDK_INT > P(28)` 走 scoped storage；`CardboardView` 在 `< TIRAMISU(33)` 用 `postFrameCallback` | API 29 命中受保护分支，SDK 有旧版兼容处理 |
| 签名 | `android/keystore/debug.keystore`（2147 B，checked-in），`storePassword=android` | debug 签名固定，可侧载；后续覆盖安装不会 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` |
| 高版本权限 | `ACCESS_LOCAL_NETWORK` / `POST_NOTIFICATIONS` / `FOREGROUND_SERVICE_*`（API 33+） | Android 10 忽略未知权限，不阻断安装；相关功能按系统版本降级 |
| QR 扫码 | `play-services-vision:20.1.3` | 无 GMS 的设备扫码配置 viewer 会失败，不影响 VR 主体 |
| **真机运行** | **未验证** | CI 仅有 ubuntu runner，无模拟器/设备；「能装、不闪退」仍需用户实测 |

### 12.3 上游 2.3.8（2026-11-06 用户补充；已核实来源，适配受阻）

**2.3.8 的真实来源**：不是 GitHub tag，而是应用内更新接口 `https://api.printedwaste.com/releases/opennow/latest` 返回的发布元数据：

```
versionCode=193  versionName=2.3.8  releaseNotes="- NVST Fixes"
artifactUrl=https://api.printedwaste.com/release-files/opennow/app-release-1793852606582-51b5eae0.apk
uploadedSize=93140139  sha256=13f4b6241cb5ef2fcc7fef8f9c48396e13e0c0eeebb194d4bdb7d10d08eea028
```

已核实无可合并的公开源码：`XYkadicloud/OpenNOW-Android` 只有 `android-native`(2.3.0/186) 与 `zh-only`(2.2.9/185)；`OpenCloudGaming/OpenNOW-Android` 只有 1 个 Initial commit；`OpenCloudGaming/OpenNOW` 的 `main` 无 `android/` 目录。**因此「把 versionName 改成 2.3.8」不算适配**，只是改数字。正确做法需要用户提供 2.3.8 源码位置（仓库/分支/压缩包），再以 diff 方式迁移 NVST 修复并重新套用本 fork 的 VR 接线；若只有 release APK，则只能反编译比对，成本高且不可靠。

### 12.4 电视版 UI 已完全移除（2026-11-06 用户追加，已执行）

用户原话：「建议你直接把那个电视版本的 UI 给完全去掉……我其实只需要汉化就行……由于这是用在手机上的，
不是电视，所以没有必要电视 UI，所以说呢在手机上那个 UI 也是显示不正常的，所以要直接去掉……
UI 全部换成原版的，只有 VR 模式可以参考那个我修改的那个 UI。」

**核实到的根因**：`HomeUiState.useSwitchStyle` 定义在 `ConsoleHomeScreen.kt` 内且**默认 `true`**，
`MainActivity.onCreate` 还调用 `HomeUiState.load(this)`，因此一装上来主界面就被
`OpenNowScreens.kt` 的 `consoleHome` 分支接管成电视版。`31e7a71` 之前 Home 页的真实逻辑是
`if (useSwitchStyle) SwitchStyleHomeScreen else HomeScreen`；`31e7a71` 把它改成无条件的
`HomeScreen` 但在外层用 `consoleHome` 抢先接管。所以「原版手机版」= `HomeScreen(...)`，
既不是 console shell 也不是 Switch-style home，无需再向用户确认基准。

**已执行（可用 `grep` 复核）**：

1. `ConsoleHomeScreen.kt` → `docs/ui-reference/ConsoleHomeScreen.kt.txt`（移出编译路径，仅作 VR 视觉参考）。
2. `OpenNowScreens.kt`：删除 `val consoleHome = ...`、`bottomBar` 条件里的 `&& !consoleHome`、
   以及 `if (consoleHome) { ConsoleHomeScreen(...) } else if (spaceUi)` 整个分支，Home 恒走 `HomeScreen`。
3. `OpenNowSettingsScreens.kt`：删除 console-style home 开关（避免留下无效开关）。
4. `MainActivity.kt`：删除 `HomeUiState.load(this)`（VR 接线 5 处未动）。
5. `strings.xml`：删除 12 条 `console_home_*` 字符串。
6. 复核命令（应无输出）：
   `grep -rn -E 'ConsoleHomeScreen|HomeUiState|consoleHome|console_home_' android/app/src/main --include=*.kt --include=*.xml`

**有意保留（非 UI，不属于「电视版桌面」）**：`GameShortcuts.kt` / `TvChannels.kt` /
`TvPosterContentProvider.kt` 与 Manifest 里的 TV home-icon 声明。它们不产生手机 UI，且
`MainActivity.kt:198` 的 `GameShortcuts.gameIdForLaunchComponent(...)` 仍被 deep-link 启动使用，
删除会破坏既有功能。`console_shortcut_slot_*` 字符串因此也保留。
若用户要求连这些一起清理，需单独评估 Manifest aliases 与 deep-link 的影响。

### 12.5 VR 渲染现状与低端机性能（2026-11-06 追加）

用户实测反馈：「其实能进入 VR 模式啊。当然那由于完全没有任何场景，所以说呢就啥也没有的状态」——
**这条同时确认了 `libcardboard_sdk_jni.so` 闪退修复已生效**，剩余问题是渲染内容为空。

- 旧状态：`VrTheaterRenderer` 只画一块极暗纯色 quad（`0.05/0.13/0.24`），无 UV、无纹理、无 `glClear`，
  在纸盒里观感就是「啥也没有」。
- 现已实现：纯黑背景（SDK 每帧清屏 + `glClearColor(0,0,0,1)`）、复用 `VrScreenGeometry` 的 UV、
  `GL_TEXTURE_EXTERNAL_OES` 串流画面零拷贝贴到**同一块平面幕布**（P1 完成）、
  无串流时显示 512×288 的 2D Canvas 状态面板（不是空白屏）。
- **正确性硬约束**：SDK 的 `onRenderStereoFrame()` 绑定单个 FBO，左右眼只用 `glViewport` 分隔
  且**没有 scissor**，SDK 自己已 `glClear`。**禁止在 `onDrawEye()` 里再 clear**，否则右眼清屏会抹掉左眼。
- **GLSL 是运行时编译，CI 抓不到**：两个 program 都用 `runCatching` 链接，最坏是黑屏不是崩溃。
  `#extension GL_OES_EGL_image_external : require` 必须是 fragment shader 第一个指令。
- **性能预算全文见 `HANDOFF.md` 第 9 节**：渲染循环零堆分配、平面顶点 50→4、面板节流 200 ms、
  无 blend/depth/MSAA/mipmap、亮度一次 fragment 乘法、`viewerMode=SIMULATION` 走官方单目路径
  （`onDrawEye` 次数减半且跳过全屏畸变合成）。**未做降帧**：SDK `ChoreographerThread` 每 vsync
  主动 `requestRender`，无官方降帧 API；**未默认降分辨率**：双目畸变模式下 `setScreenParams(w,h)`
  参与视场计算，等比缩小未经真机验证。
- **真机待验**：面板中文是否上下颠倒（若是，把 `VrTheaterRenderer.PANEL_FLIP_Y` 由 `0.0f` 改 `1.0f`）、
  OES 串流方向、低端机帧率与发热。

### 12.6 用户提出的「整个 2D UI 镜像进 VR」（可选，未实现，不要强求）

用户建议「只套一个 VR 界面，里面所有东西都还是一样的 2D 界面，只是把它转换成 3D 渲染」。
完整评估见 `HANDOFF.md` 2.2：需 `VirtualDisplay` + `Presentation`，但 ViewModel 状态不同步、
输入路由要重接、每帧多一次全 UI 合成，与低端机省性能冲突。本 fork 采用的正确解法是
「解码 Surface 零拷贝直连平面 + 轻量 2D 面板」，不要未做 PoC 就照字面硬改。

---

## 13. 附录：关键常量与来源索引

- Android 键码（值来自 AOSP `KeyEvent`，写死在 `VrKeys.kt` 避免单测桩）：
  `DPAD_UP=19, DPAD_DOWN=20, DPAD_LEFT=21, DPAD_RIGHT=22, DPAD_CENTER=23,`
  `BUTTON_A=96, BUTTON_B=97, BUTTON_X=99, BUTTON_Y=100,`
  `BUTTON_L1=102, BUTTON_R1=103, BUTTON_L2=104, BUTTON_R2=105,`
  `BUTTON_SELECT=109, BUTTON_START=108, BUTTON_MODE=110, BACK=4`
- `ACTION_DOWN=0, ACTION_UP=1`（`KeyEvent`）。
- `InputDevice.SOURCE_GAMEPAD=0x401, SOURCE_JOYSTICK=0x1000010, SOURCE_DPAD=0x201`。
- `MotionEvent.AXIS_X=0, AXIS_Y=1`；用 `event.getAxisValue(...)` 取左摇杆。
- Cardboard SDK 源码：`https://github.com/googlevr/cardboard/tree/v1.36.0`。
- Cardboard 官方 quickstart：`https://developers.google.com/cardboard/develop/c/quickstart`（要求 Android 8.0+/API 26、Cardboard viewer、Android Studio 2024.1.2+、SDK 35+、NDK）。本仓库把 minSdk 补丁到 24，SDK 注释明确 GLES2 路径可低至 16、Vulkan 需 24。
- 源仓库 CI 参考：`OpenNOW-Android/.github/workflows/android-build.yml`（已复制，未修改）。
