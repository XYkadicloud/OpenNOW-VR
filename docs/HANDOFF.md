# OpenNOW-VR 交接文档（给接手的 Agent）

> 生成时间：2026-11-06（GMT+8）
> **本文是唯一入口。先读完第 0、2、3 节，再动手。**
> 编写原则：只记录已用磁盘文件 / GitHub API 核实的事实。凡未验证的一律标注「未验证」或「待真机」，
> 不得把「计划做」「应该可以」写成「已完成」。本文所有路径、run ID、常量均可自行复核。

---

## 0. 三十秒速读

1. **这是什么**：从 `XYkadicloud/OpenNOW-Android` fork 出来的独立公开仓库 `OpenNOW-VR`，
   在手机 Google Cardboard（纸盒 VR）里把**平面 2D 串流画面**放进黑色 VR 空间。不是 3D 游戏，
   不是立体视频。
2. **现在到哪一步**：框架 + 主界面回退 + VR 黑底/2D 纹理渲染 + 低端机性能优化 + 全套 CI 守卫
   已落盘；**每次改动都必须靠 GitHub Actions 验证，本机没有 Android 编译工具链**。
3. **最重要的一条经验**：**CI 全绿 ≠ 真机能用**。曾经出现 AAR 缺 `libcardboard_sdk_jni.so`，
   CI 全绿但用户点「进入 VR」直接闪退（已修）；更隐蔽的一次是 `VrModeHost` 的 Compose
   `DisposableEffect` 竞态把刚创建的 VR view 误销毁，真机「进 VR 啥也没有」（§12，已修）。
   结论：**渲染/生命周期问题只能靠真机 logcat 定案，别靠猜**。
4. **绝对不能做**：3D/SBS/左右眼不同内容、gaze/眼球/凝视/dwell/头部动作确认、
   把电视版 UI 放回主界面、把 2.3.8 只改个版本号冒充适配、在本机编译、
   **把 `DisposableEffect` 清理逻辑改回读共享 `viewRef` 的写法**（§12.3）。
5. **接手后最优先的三件事**：① 查最新 CI run 是否全绿、读 §12 结案记录；
   ② 请用户按 §12.6 在真机复测 VR 画面 / 景深 / 软键盘 / BACK 无缝退出；
   ③ 按第 4 节补 P3 与处理 P2 阻塞项。

---

## 1. 项目在哪里（精确路径）

### 1.1 本地主仓库（所有改动都在这里）

```
C:\Users\HP\WorkBuddy AI\2026-10-31-07-17-59\OpenNOW-VR
```

Git Bash 等价路径：

```
/c/Users/HP/WorkBuddy AI/2026-10-31-07-17-59/OpenNOW-VR
```

- 分支：`main`，远端：`origin`
- 提交身份：`XYkadicloud <XYkadicloud@users.noreply.github.com>`
- 进入方式（Git Bash）：`cd "/c/Users/HP/WorkBuddy AI/2026-10-31-07-17-59/OpenNOW-VR"`

### 1.2 GitHub 远端

```
https://github.com/XYkadicloud/OpenNOW-VR
```

- 公开仓库，默认分支 `main`
- 唯一启用的工作流：`.github/workflows/vr-build.yml`（`vr-build`）
- Actions 页面：`https://github.com/XYkadicloud/OpenNOW-VR/actions`
- APK 工件名：`opennow-vr-debug-arm64-v8a`（外层 zip 内是 `app-arm64-v8a-debug.apk`）

**上游项目链接（2026-11-06 用 GitHub API 逐一核实可访问）**：

| 用途 | 链接 |
| --- | --- |
| **本 fork 的直接来源**（整仓复制的基线） | `https://github.com/XYkadicloud/OpenNOW-Android`，默认分支 `android-native`，HEAD `8811a74`，`2.3.0 / versionCode 186` |
| 更上游的官方 Android 客户端仓库 | `https://github.com/OpenCloudGaming/OpenNOW-Android`，默认分支 `main`（内容极简） |
| 上游组织主项目 | `https://github.com/OpenCloudGaming/OpenNOW`，默认分支 `main`，描述 `Custom GeForce Now Client Named OpenNOW` |

> 2.3.8 **不是**这些仓库里的 tag，来自应用内更新 API，见 2.3 / 4.2 节。

### 1.3 只读参考副本（**不要改，不要提交**）

| 路径（相对工作区根目录） | 内容 |
| --- | --- |
| `_src_opennow/` | 上游 `XYkadicloud/OpenNOW-Android` 克隆，HEAD `8811a74`，默认分支 `android-native`，`versionCode=186 / versionName=2.3.0` |
| `_src_cardboard/` | `googlevr/cardboard` tag `v1.36.0` 源码 |
| `NanaPlay/`、`opennow-atom-port-archive.zip` | 与本任务无关的其他资料，忽略 |

主仓库内 `docs/ui-reference/ConsoleHomeScreen.kt.txt` 是**从编译路径移出的电视版 UI**，
仅作为 VR 界面视觉参考，`.txt` 后缀确保不会被 Kotlin 编译。

### 1.4 认证与推送（容易踩坑）

- PAT 暂存于 `$HOME/.opennow_gh_token`（Git Bash 下即 `C:\Users\HP\.opennow_gh_token`）。
  **绝不写入仓库任何文件、绝不打印明文。**
- GitHub REST API：用 `Authorization: Bearer $TOKEN`。
- `git push` over HTTPS：**必须用 Basic**，Bearer 会报 `invalid credentials`：

  ```bash
  TOKEN=$(tr -d '\r\n' < "$HOME/.opennow_gh_token")
  BASIC=$(printf 'x-access-token:%s' "$TOKEN" | base64 -w0)
  git -c credential.helper= -c http.extraheader="AUTHORIZATION: basic $BASIC" push origin main
  ```

- Actions artifact 下载端点是 `/repos/XYkadicloud/OpenNOW-VR/actions/artifacts/{id}/zip`。
  REST 文档里的 `/archive` 在本环境返回 `404 Not Found`。
- 工作全部结束后**提醒用户撤销并重新生成该 PAT**（它曾在对话中明文出现）。

### 1.5 本机环境限制

本机只有 JDK 8，**没有** Android SDK / Gradle 9.x / JDK 17 / NDK / Rust Android target / `gh` CLI。
用户明确要求「本机不编译，全部放 GitHub，改完在 GitHub 上编译」。
所以：**不要尝试 `gradlew assembleDebug`，不要 `./gradlew test`，只看 CI。**

---

## 2. 用户需求的硬约束（不可自行扩写）

### 2.1 必须保持

| 约束 | 说明 |
| --- | --- |
| 串流是**平面 2D** | 一块平面幕布贴进 VR 空间；不做 SBS、不加视差、不给左右眼不同内容。用户主要串流 PC / 传统游戏 |
| **纯物理手柄** | 纸盒 VR 没有专业控制器。所有 VR 菜单 / 设置只用手柄 |
| **严禁眼睛操控** | 用户原话：「不要使用眼睛的操控方式，纯手柄罢了。」禁止 gaze / eye tracking / dwell / 注视确认 / 眨眼 / reticle 选择 / 头部动作确认。头部姿态**只**用于视角矩阵 |
| 主界面 = **原版手机版** | 用户原话：「主界面不要使用我更改的电视版的桌面，使用原版的手机版界面就好」。电视版 UI 已从编译路径删除 |
| VR 背景 = **纯黑，不做 3D 场景** | 用户原话：「场景的话应该是不用做，直接做黑底，方便节省性能」 |
| **低端机全套性能优化** | 用户原话：「说明一下要做好全套的性能优化哦。毕竟我也就一个低端机」 |
| 窗口大小可调 | 用户特别强调「串流的窗口大小」可以放大，其余可调项由助手补充 |
| **VR 设置必须在原生设置页里** | 用户原话：「把它做到原本的设置里面，不是在屏幕的画面上再叠加一个一直都会有的按钮」。入口为 `设置 → Interface → VR 模式`，用上游原生 `SettingSwitch`/`NumberSlider`；**严禁**再加浮层设置按钮。不进入 VR 也要能改全部参数，改动即时保存生效 |
| **手机 / VR 无缝切换** | 用户原话：「串流的时候直接换成手机模式……需要键盘时退出 VR 用手机软键盘打字」。VR 内按系统返回键或手柄快捷键退出，串流不重连；**不要在 VR 里做软键盘** |
| 目标设备 | Android 10（API 29），只出 `arm64-v8a`，必须 64 位 ARM |
| 新仓库、公开 | 不要改原仓库 `OpenNOW-Android` |

### 2.2 用户提出的「省事方案」（**可选项，未实现，不要强求**）

用户说：「如果图省事的话，你也可以试一下选择只套一个 VR 界面，然后呢里面所有东西都还是一样的，
2D 界面，只是把它转换成 3D 渲染。在 3D 场景中，当然 3D 场景直接黑色就可以……直接套用原本的
界面，以及叠加一个 VR 渲染就行。」

**技术评估（务必先读，别直接照字面硬做）**：把整个 Compose 手机 UI 镜像进 GLES 纹理，
在 Android 上需要 `VirtualDisplay` + `Presentation` 承载一份 UI 副本输出到 `SurfaceTexture`。
存在三个实质问题：

1. `OpenNowViewModel` 是 `AndroidViewModel` 且与 Activity 生命周期 / 输入路由强耦合，
   `Presentation` 是独立 Window/Context，副本会拿到**不同状态**，VR 里看到的界面与主界面不同步；
2. 输入要重新转发，而现有 `NativeStreamInputRouter` / `VrSession` 路径会绕开；
3. 每帧多一次全 UI 合成 + 一次纹理上传，低端机发热掉帧，与「省性能」目标冲突。

**本 fork 采用的正确解法**（已实现）：串流解码器的 `Surface` 通过
`GL_TEXTURE_EXTERNAL_OES` **零拷贝**直连 VR 平面幕布；手机 UI 保持原生 2D 不变；
VR 内只需要界面时用轻量 Canvas 2D 面板。若后续仍要做完整 UI 镜像，
建议先做 PoC 验证 ViewModel 共享与输入转发，再评估是否值得。

### 2.3 明确不要

- 不要把电视版 / console-style 桌面放回主界面或设置。
- 不要为了「适配 2.3.8」只把 `versionName` 改成 `2.3.8`（这是冒充，不是适配）。
- 不要在本机编译。
- 用户没要的功能不要强求，只**提出来**并给出正确解法（用户的自定义指令）。

---

## 3. 当前真实状态（逐项，附证据）

### 3.1 已完成且有证据

| 项目 | 状态 | 证据 |
| --- | --- | --- |
| 独立公开仓库 + `main` 分支 | 完成 | `https://github.com/XYkadicloud/OpenNOW-VR` |
| Google Cardboard SDK v1.36.0 vendor + 两阶段 CI 编 AAR | 完成 | `third_party/cardboard/`，CI job `build-cardboard-sdk-aar` |
| 真机「进入 VR」闪退修复 | 完成（用户实测确认） | `libcardboard_sdk_jni.so` 已进 AAR/APK；用户反馈「其实能进入 VR 模式啊」 |
| AAR / APK 两层 `.so` 二进制守卫 | 完成 | `vr-build.yml` 中两个 `Verify the Cardboard JNI bridge...` 步骤 |
| VR JVM 测试数量断言 | 完成 | 解析 `TEST-*.xml`，要求 `tests>0`、`failures/errors==0` |
| 平面 2D 幕布几何、可调项、手柄状态机、设置持久化 | 完成 | `vr/VrSettings.kt` / `VrScreenGeometry.kt` / `VrGamepadRouter.kt` 等 |
| `MainActivity` 同 Activity overlay 接线（5 处） | 完成 | `MainActivity.kt`：`VrSession.attach`、根 `Box` 叠加 `VrModeHost()`、`dispatchKeyEvent`、`dispatchGenericMotionEvent` |
| **电视版 UI 完全移除** | 完成 | `ConsoleHomeScreen.kt` 移到 `docs/ui-reference/*.txt`；`OpenNowScreens.kt` 的 `consoleHome` 分支、设置开关、`HomeUiState.load`、`console_home_*` 字符串全部删除；`grep` 编译路径零残留 |
| **VR 黑底 + 2D 纹理正常渲染（层级修复）** | **代码完成 + CI 验证** | `vr/VrTheaterView.kt`：`init` 中一次性 `promoteGlSurface()` 调用 `setZOrderOnTop(true)`，只在表面创建前应用一次（**不**在 `onAttachedToWindow` 重新应用），使 VR GL SurfaceView 置顶于 2D SurfaceView 之上。CI run `42322518683`（commit `b7961f6`）双 job 全绿，AAR/APK JNI 守卫 success。真机仍待 §12.6 验证。 |
| **上下翻转 / 左右镜像修复 + 中心辅助线** | **真机反馈失败后已重做**（`9fb1d16`，详见 §13） | 旧方案三件套全部作废：`PANEL_FLIP_Y = 1.0f` 只翻基础 UI、`stMatrix[0]` 取负会推偏画面、辅助线画在状态面板 Canvas 会跟头转。现改为 `PANEL_FLIP_Y = 0.0f` + 视频专用 `videoTexCoordBuffer`（`u→1-u` / `v→1-v`，只作用串流、不改任何 GLSL）+ `onFinishFrame()` 物理屏幕层单条白色竖线。**画面效果待真机复测** |
| **VR 期间强制横向 + 真全屏铺满** | 代码完成（待真机复测） | `MainActivity` 用 `lifecycleScope` 收集 `VrSession.enabled`：VR 开启时 `vrFullscreenActive=true`，锁 `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`、强制 immersive；退出后恢复。用户后续反馈「状态栏隐藏了但不是全屏」，追加 `FLAG_LAYOUT_NO_LIMITS`、`hide(systemGestures + displayCutout)`、`LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`（`OpenNowSystemBars.kt`），让内容铺满含刘海/手势区的整个物理屏。见 §13.3 / §14 |
| **可选 360° 全景背景 + 用户导入图片** | 代码完成 + CI 验证 | 新增 `backgroundEnabled` / `backgroundImageUri` / `backgroundBrightness`，**默认关闭**（保持纯黑外观与零拷贝视频通路）。低多边形等距圆柱球体（16×24、2304 顶点）在幕布**之前**绘制，复用现有 panel program（`uFlip=0`），无需深度测试、无需新 shader；内置程序化星空兜底（不依赖任何资源文件）；图片经 `ACTION_OPEN_DOCUMENT` 导入并 `takePersistableUriPermission`，`>2048px` 自动降采样。所有 GL 创建/上传/释放都在 GL 线程，`applySettings` 只置标志。CI run `42347655425`（`7a9abfb`）双 job 全绿。真机画面待复测 |
| **等效瞳距 / 左右眼视差** | 代码完成（真机反馈「不正常」后已重写，待复测） | `VrSettings.ipdScale`（0.5~1.6，默认 1.0）。**不再**缩放 `viewMatrix[12]`；改为 `VrMatrix4.applyIpdShiftInto()` 在每眼最终 MVP 上做等大反向的屏幕空间水平位移（`clip.x += k·clip.w`），保持 SDK 的 eyeFromHead / 离轴投影 / 畸变 mesh 原样自洽。默认 1.00x 严格零改动，单目模式不生效。2D 滑块 + VR 菜单行 + 持久化 + JVM 单测齐全。见 **§14.8** |
| **重置中心点** | 代码完成（待真机复测） | `VrHeadPose.viewRotationInto(out, current, center, sensitivity)` 新增四参重载，算 `current * conj(center)` 相对旋转；`VrTheaterRenderer` 存 `centerOffsetQuaternion`，`recenter()` 抓当前头朝向。`VrSession.onRecenter` 由 `VrModeHost` 接到 `VrTheaterView.recenter()`（renderer 是 private，须经此 public 方法）。VR 菜单「重置中心点」动作项 + 手机设置页按钮。见 §14 |
| **菜单触发改长按** | 代码完成（待真机复测） | 用户要求 `START/SELECT` 不能单按触发设置、且 `HOME/MODE` 也要长按。现为长按 `START+SELECT` 或长按 `HOME/MODE` ≥ `MENU_LONG_PRESS_MS=500ms`；`START+SELECT` 从 `VR_TOGGLE_COMBOS` 移除（VR 开关只留 `L1+R1`）；短按全部透传给游戏；触发带 latch 防连发。见 §14 |
| **低端机性能优化** | 代码完成，**真机未验证** | 见第 9 节 |
| **VR 设置集成进原生设置页** | 代码完成，**真机未验证** | `设置 → Interface → 「VR 模式（半 VR 影院）」`：`vr/VrSettingsSection.kt` 复用上游原生 `SettingSwitch` / `NumberSlider`。**已删除**常驻浮层「VR 设置」按钮与 `VrPhoneSettings.kt`（用户明确要求）。含 VR 总开关、8 个滑块、3 个开关、恢复默认 |
| **景深 / 空间固定感修复** | 代码完成，**真机未验证** | 已核实 SDK `HeadTransform.getHeadView()` 是**逆旋转**（camera-to-head）。旧代码把四元数的**正向**旋转当 view 矩阵用，幕布会跟着头转、贴脸、无空间深度。现改 `VrHeadPose.viewRotationInto()`（转置 3x3 求逆）+ 2 个 JVM 测试 |
| **幕布宽高比接真实帧** | 代码完成，**真机未验证** | `Streaming.kt.onFrameResolutionChanged` → `VrSession.setVideoAspect(w,h)`；渲染器检测到 aspect 变化自动重建幕布网格，不再固定 16:9 |
| **无缝切回手机 / 软键盘** | 代码完成，**真机未验证** | VR 内按系统 **BACK** 或手柄快捷键退出；`VrVideoSurfaceRegistry.clear()` 让解码器回退 2D surface，不重连会话；退出后 2D UI 与软键盘恢复。VR 开启时画面上**没有任何浮层控件** |
| Android 10 / API 29 静态兼容性核验 | 完成（静态） | 计划书 12.2；`minSdk 24 / targetSdk 37` |

### 3.2 已核实但未实现

| 项目 | 说明 |
| --- | --- |
| ~~P3：VR 内部、戴头显时用手柄操作的设置面板~~ | **已于 `9fb1d16` 之后完成并持续扩充**：VR 内菜单现 21 行（含瞳距、重置中心点、扫码、恢复默认、退出 VR），由 `VrGamepadRouter` 手柄驱动，长按 `START+SELECT` / `HOME/MODE` 打开。见 3.1 与 §14 |
| **串流开始后才进入 VR（中途切换）** | **受解码器架构限制，未实现无缝**，详见 **8.5**。已核实：先进 VR 再开始串流可用；串流中才进 VR 时，解码器仍是纯 texture 路径，VR 的 `SurfaceTexture` 收不到画面 |
| 完整 2D UI 镜像进 VR | 见 2.2，用户「可以试一下」的可选项，未实现 |
| 上游 2.3.8 适配 | 阻塞：见 4.2 |
| 汉化 | 用户说「我其实只需要汉化就行」，语义未确认，**未执行**。不要擅自全量改文案 |
| 上游 167 个 JVM 测试 | 未启用（引用 `android.*` 且无 Robolectric）。CI 只跑 `--tests "com.opencloudgaming.opennow.vr.*"` |

### 3.3 必须由用户真机复测（不能靠 CI 证明）

1. 点击「进入 VR」不闪退（**已由用户实测确认**修复生效）。
2. VR 内能看到**纯黑背景 + 一块 2D 平面面板**（此前是「啥也没有」）。
3. 面板中文文字是否正立。**不要把 `VrTheaterRenderer.PANEL_FLIP_Y` 从 `0.0f` 改成 `1.0f`**：
   `uFlip` 只存在于 `PANEL_FRAGMENT_SHADER`，改它只会把基础 UI 翻 180°，串流（`samplerExternalOES
   + uSTMatrix`）根本没有这个 uniform。串流朝向请用「串流左右镜像 / 串流上下翻转」两个可调开关。
4. 串流开始后，画面是否出现在平面幕布上（OES 纹理、`uSTMatrix` 方向、亮度）。
5. 手柄调整设置后面板数值是否更新、窗口是否变大。
6. 低端机发热 / 掉帧情况；`viewerMode=SIMULATION`（单目）是否明显更流畅。
7. **不进入 VR**，点「VR 设置」→ 拖动「串流窗口大小」→ 关闭对话框 → 再进 VR，幕布是否确实变大；
   杀掉 App 重开后设置是否仍在（SharedPreferences 持久化）。

> **GLSL shader 是运行时编译，CI 完全抓不到 shader 错误。** 两个 program 都用
> `runCatching` 包住，最坏情况是黑屏而不是崩溃；真机若黑屏先查 shader 编译日志。

---

## 4. 你要做什么（按优先级）

### P0 — 接手立刻做

1. `git status` / `git log --oneline -6` 确认本地与 `origin/main` 一致、工作区 clean。
2. 查 GitHub Actions 最新 `vr-build` run：两个 job 全绿，`Assert VR JVM tests actually ran`
   应显示 `7 suites / 71 tests / 0 failures / 0 errors / 0 skipped`
   （原 58 个 + 6 个零分配矩阵测试 + 7 个手机端设置规格测试）。数量以解包
   `vr-unit-test-report` 的 XML 为准，不要只信文档。
3. 请用户用**最新** APK 在 Android 10 真机按 3.3 清单复测，拿回结论再继续改渲染。

### P1 — 剩余功能（按序）

1. **P3 可见设置面板**：把 `VrMenuModel` 渲染进 2D 面板纹理（Canvas 绘制），
   接 `VrGamepadRouter` 的上下/确认/返回，保持纯手柄、无 gaze。当前面板只是静态状态显示。
   **注意**：手机端的 VR 设置已完成，入口在**原生设置页**（`VrSettingsSection.kt`，见 12.8），
   旧的 `VrPhoneSettings.kt` 浮层已按用户要求删除；这里说的是戴头显时在 VR 内部
   用手柄操作的那个面板，两者不要混为一谈。
2. **串流中面板叠加**：串流时是否仍显示半透明设置面板，由用户确认后再做，不要自作主张。
3. 真机发现文字方向 / OES 方向问题时，只改 `PANEL_FLIP_Y` 或 `uSTMatrix`，不要重写渲染器。

### P2 — 阻塞项（**必须问用户，不要猜**）

| 阻塞 | 需要用户提供 |
| --- | --- |
| 2.3.8 适配 | 2.3.8 的源码位置（仓库 / 分支 / diff / 压缩包）。**已核实**：上游 GitHub 只有 `android-native` 2.3.0/186 与 `zh-only` 2.2.9/185；2.3.8 来自应用内更新接口 `https://api.printedwaste.com/releases/opennow/latest`（`versionCode=193`、`releaseNotes="- NVST Fixes"`），目前只有 release APK 元数据，**没有可合并的公开源码** |
| 汉化 | 是否要执行；若要，是否以 `zh-only` 分支文案为准 |
| 完整 UI 镜像 | 是否坚持 2.2 的方案（需先做 PoC） |

### P3 — 可选（提出即可，不要强求）

- 降 GL surface 分辨率（仅 SIMULATION 单目模式安全，见 9.2）。
- 启用上游全量 JVM 测试（需 Robolectric / `returnDefaultValues` / 测试隔离）。
- 隐藏 Cardboard SDK 自带的 `CardboardLayout` 触摸按钮（`VrTheaterView.kt` 的 TODO）。

---

## 5. 该看什么文档（索引）

| 文档 | 解决什么问题 | 何时读 |
| --- | --- | --- |
| **`docs/HANDOFF.md`（本文）** | 项目位置、状态、待办、防幻觉总入口 | **第一个读** |
| `docs/VR_IMPLEMENTATION_PLAN.md` | 最详细的事实基线、API 摘录、架构、分阶段步骤、CI 排错、事故复盘 | 动手改代码前必读第 0/1/2/3/6/9/12 节 |
| `README-VR.md` | 中文使用说明、手柄键位、可调项、CI 记录、已知限制 | 回答用户「现在能做什么」时 |
| `README.md` | 原项目 README + fork 说明 | 了解上游功能 |
| `docs/ui-reference/ConsoleHomeScreen.kt.txt` | 电视版 UI 源码，**仅作 VR 视觉参考** | 只在设计 VR 界面时 |
| `docs/upstream-workflows/` | 上游 4 条发布/扫描流水线，**已从 `.github/workflows/` 移出以免误触发** | 排查 CI 触发异常时 |
| `.github/workflows/vr-build.yml` | 唯一真实构建流程与全部守卫 | 改 CI / 查失败时 |
| `android/app/src/test/java/.../vr/*Test.kt` | 6 个纯 JVM 测试文件、64 个 `@Test` | 改纯逻辑层后 |
| `third_party/cardboard/` | vendor 的 Cardboard SDK 源码（含本 fork 的 CMake 修复） | 查 SDK 真实 API 时，**以源码为准，不要凭记忆** |

---

## 6. 关键文件地图（相对 `OpenNOW-VR/`）

```
.github/workflows/vr-build.yml          唯一 CI（两阶段：AAR → 单测 + APK）
third_party/cardboard/
  settings.gradle                     只 include ':sdk'
  sdk/build.gradle                    minSdk 24 / arm64-v8a only
  sdk/CMakeLists.txt                  ★新增 cardboard_sdk_jni target（闪退修复核心）
  java_api/.../CardboardView.java     真实 Renderer / Eye API 来源
android/app/build.gradle.kts          compileSdk 37 / minSdk 24 / targetSdk 37
android/app/libs/cardboard-sdk.aar    CI 产物，.gitignore 排除
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/com/opencloudgaming/opennow/
  MainActivity.kt                    ★VR overlay 接线（5 处）
  Streaming.kt                       ★decoderSurface → VrVideoSurfaceRegistry；onFrameResolutionChanged → VrSession.setVideoAspect
  OpenNowScreens.kt                  ★主界面：Home 恒为原版 HomeScreen（电视版分支已删，用户已确认要做）
  OpenNowSettingsScreens.kt          ★Interface 分类下挂 VrSettingsSection（VR 设置的原生入口）
  ConsoleHomeScreen.kt               ✗已从编译路径移除 → docs/ui-reference/*.txt
android/app/src/main/java/com/opencloudgaming/opennow/vr/
  VrTheaterRenderer.kt               ★黑底 + OES 串流平面 + 2D 面板 + 零分配渲染循环
  VrTheaterView.kt                   CardboardView 子类；viewerMode → setStereoRenderMode
  VrModeHost.kt                      VR 渲染宿主；2D 时仅一个「进入 VR」小按钮，VR 开启后无任何浮层
  VrSettingsSection.kt               ★原生设置页内的 VR 分区（上游 SettingSwitch / NumberSlider）
  VrSettingSpec.kt                   纯 Kotlin 设置规格表（范围/步进/读写），UI 与 JVM 测试共用
  （已删除）VrPhoneSettings.kt       原浮层设置对话框，按用户要求移除，勿重新引入
  VrSession.kt / VrSettings.kt       全局状态与可调项；updateSettings() = 校验 + 持久化 + 路由同步
  VrVideoSurfaceRegistry.kt          解码器 Surface ↔ GL 线程桥（internal object）
  VrMatrix4.kt / VrHeadPose.kt       纯 Kotlin 数学，含 *Into 零分配变体
  VrScreenGeometry.kt / VrGlProgram.kt / VrMenuModel.kt / VrGamepadRouter.kt / ...
docs/                                 见第 5 节
```

---

## 7. 防幻觉清单（本对话真实踩过的坑，逐条避免）

1. 接口是 **`CardboardView.Renderer`**，不存在 `CardboardView.StereoRenderer`。
2. `SurfaceTexture.getTransformMatrix(float[])` 是**单参数**；两参数形式不存在，会编译失败。
3. 主工程是 `compileSdk 37 / minSdk 24 / targetSdk 37`；`35/24/35` 只属于 vendored Cardboard SDK。
4. `VrVideoSurfaceRegistry` 必须是 `internal object`（返回 `internal DecoderSurfaceTarget`）。
5. JVM 测试只跑 `--tests "com.opencloudgaming.opennow.vr.*"`；上游 167 个测试类会 `not mocked`。
6. **CI 全绿 ≠ 真机可用**：编译期不检查 `System.loadLibrary()` 是否有对应 `.so`。
7. `libGfxPluginCardboard.so` 因 `cardboard_api.lds` 的 `local: *` 隐藏了所有 `Java_*` 符号，
   **不能**靠改名代替 `libcardboard_sdk_jni.so`。
8. 2.3.8 **不是 GitHub tag**，是应用内更新 API；不要改 `versionName` 冒充适配。
9. `git push` 用 **Basic**，不是 Bearer；artifact 下载用 `/zip`，不是 `/archive`。
10. **不要在 `onDrawEye()` 里 `glClear()`**：SDK 的 `onRenderStereoFrame()` 只绑一个 FBO、
    用 `glViewport` 分左右眼且**没有 scissor**，右眼的 clear 会抹掉左眼。黑底靠 `glClearColor()`。
11. GLSL shader **运行时才编译**，CI 抓不到；`#extension GL_OES_EGL_image_external : require`
    必须是 fragment shader 第一个指令。
12. Kotlin `const val` 不能调用 `.toInt()` 等函数（不是编译期常量表达式）。
13. `HomeUiState.useSwitchStyle` 原先默认 `true` 且定义在电视版 UI 文件里，
    这才是一装上来就显示电视版的根因；该状态与电视版 UI 一起已删除。
14. `VrVideoSurfaceRegistry.isActive` 只有在 `install()` 被调用后才为 true；
    现在 `VrTheaterRenderer.onSurfaceCreated()` 会真正 `install()`。
15. 上游 31e7a71 之前 Home 页是 `if (useSwitchStyle) SwitchStyleHomeScreen else HomeScreen`；
    31e7a71 之后改成 `consoleHome` 分支接管。现在的正确基准是**原版 `HomeScreen`**，
    既不是 console shell 也不是 Switch-style home。
16. **`vr/` 目录在 3cfcade 之前一条 `Log.*` 都没有**：日志里「零 CardboardView 生命周期日志」
    是「代码本来就不打日志」，**不能**据此推断「renderer 没画帧」。2026-11-06 起补了诊断日志。
17. 诊断导出里的 `UnsatisfiedLinkError: libcardboard_sdk_jni.so` 常是 **「Retained Java crash —
    previous process」**（上一次运行残留），要按 timestamp/uptime 判断是不是当前这次测试；
    该 so 缺库闪退早在 `11a4d69` 已修复、且用户实测确认「能进入 VR」，不要再当成当前根因。
18. **`setZOrderOnTop(true)` 是双刃剑**：它让 GL 层成为不透明置顶层，但必须在 surface 创建前
    设置；从 `init`/`onAttachedToWindow` 遍历子 view 调用不可靠。`3cfcade` 加了它之后真机反而
    显示「原本的非 VR 界面」（VR 层未出现/透明）。已回退，见 §12。
19. **Compose `DisposableEffect(key)` 的 onDispose 不要读「会被新 composition 覆盖的共享状态」**：
    `VrModeHost` 曾用 `viewRef`（VrModeHost 作用域）+ `DisposableEffect(enabled)`，false→true
    切换时旧 effect 的 onDispose 会把 `AndroidView.factory` 刚建好的新 view 误销毁（onResume
    后 3ms 就 onPause/onDestroy）。修复见 §12.3/12.4 —— 用「仅 enabled 时存在的子 composable」
    + `DisposableEffect(Unit)` + 局部引用。**这是「进 VR 啥也没有」的真正根因**。

---

## 8. 构建与验证操作手册

### 8.1 提交与推送

```bash
cd "/c/Users/HP/WorkBuddy AI/2026-10-31-07-17-59/OpenNOW-VR"
git add -A && git status --short          # 确认没有 token / .aar / build 产物
git commit -m "..."
TOKEN=$(tr -d '\r\n' < "$HOME/.opennow_gh_token")
BASIC=$(printf 'x-access-token:%s' "$TOKEN" | base64 -w0)
git -c credential.helper= -c http.extraheader="AUTHORIZATION: basic $BASIC" push origin main
```

push 到 `main` 会自动触发 `vr-build`（`on: push`），**不是手动点编译**。每次代码改动都要跑一次并记录 run ID。

> **提交纪律（用户 2026-11-06 指出「为什么隔一段就编译一次」后的改进，务必遵守）**：
> 一次完整构建约 8–10 分钟。本轮有 2 次**纯文档提交**也触发了全量双阶段构建，属于浪费。
> 后续请：① 把「补记上一个 run ID」「修注释/文案」与下一次**代码改动合并**成一次提交；
> ② 确实只改文档时，commit message 里带 `[skip ci]`（GitHub Actions 会跳过 push 触发的工作流）；
> ③ 不要为了「记录 run ID」单独再 push 一次。
> 本次交接的最后一次文档提交已按此规则使用 `[skip ci]`，代码验证结论以 `42202834080` 为准。

### 8.2 查 CI

```bash
TOKEN=$(tr -d '\r\n' < "$HOME/.opennow_gh_token")
curl -sS -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/XYkadicloud/OpenNOW-VR/actions/runs?per_page=8" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); [print(r['id'], r['head_sha'][:7], r['status'], r['conclusion']) for r in d['workflow_runs']]"
```

必须全绿的步骤：`build-cardboard-sdk-aar`、`Verify the Cardboard JNI bridge is in the AAR`、
`Run VR JVM unit tests and build the debug APK`、`Assert VR JVM tests actually ran`、
`Verify the Cardboard JNI bridge is in the APK`。

### 8.3 下载 APK（仅用于自主核验，用户已自行下载过）

从 Actions API 拿 artifact `id`，用 `/actions/artifacts/{id}/zip`，外层 zip 内含
`app-arm64-v8a-debug.apk`。用 Python `zipfile` 解包（本机没有 `file` 命令）。

### 8.4 已核实的 CI 运行记录

| run ID | commit | 结论 |
| --- | --- | --- |
| `42186000233` | `02ce545` | 失败：`'public' function exposes its 'internal' return type DecoderSurfaceTarget` |
| `42187431788` | `1fc4bd7` | 成功：改为 `internal object` + 限定测试包 |
| `42189848032` | `fcdb892` | 成功：测试数量断言生效，`6 suites / 58 tests` |
| `42192555168` | `11a4d69` | 成功：闪退修复版，AAR/APK 均含 `libcardboard_sdk_jni.so` |
| `42194578267` | `4c67196` | 成功：docs-only push 回归验证，两个 job 全绿 |
| **`42198812193`** | **`1a489e9`** | **成功：主代码验证**。电视版 UI 移除 + 黑底/OES 渲染 + 低端机优化 + 64 测试；两个 job 全绿，AAR/APK 两道 JNI 守卫均 success；artifacts：`cardboard-sdk-aar` 385622 B、`vr-unit-test-report` 21304 B、`opennow-vr-debug-arm64-v8a` 47525871 B |
| `42200369358` | `bd0f6b4` | 成功：docs + KDoc 注释改动的回归；两个 job 全绿 |
| **`42202834080`** | **`e365e03`** | **成功：手机端 VR 设置面板版**。手机 2D 端 VR 设置面板 + 7 个新增测试；两个 job 全绿、无失败步骤；artifacts：`cardboard-sdk-aar` 385622 B、`vr-unit-test-report` 23811 B、`opennow-vr-debug-arm64-v8a` 47548120 B |
| `42206935569` | `203e57a` | **失败（已定位并修复）**：重写 `VrModeHost.kt` 时漏了 `import androidx.compose.runtime.setValue`，`:app:compileDebugKotlin` 报 `'MutableState<VrTheaterView?>' has no method 'setValue'`。**教训**：删掉 `by remember { mutableStateOf(...) }` 之外的 UI 时，别忘了它仍需要 `getValue` + `setValue` 两个 import |
| **`42208037616`** | **`b19f3ec`** | **成功：交接版本**。原生设置页 VR 分区 + 景深逆旋转修复 + 真实帧宽高比 + BACK 无缝退出；两 job 全绿、无失败步骤；人工解包 `7 suites / 75 tests / 0 failures / 0 errors / 0 skipped`，APK 含两个必需 `.so`；artifacts 385622 / 23969 / 47535533 B |
| `42211859296` | `4f27e07` | 失败：串流中途切 surface + 手柄菜单 |
| `42212285185` | `fb64a9a` | 失败：切 surface 后请求关键帧 |
| `42215232161` | `72e803c` | 失败：`menuKeyClosesThePanelWhenItIsAlreadyOpen` 单测与 combo 键冲突 |
| **`42219713375`** | **`3cfcade`** | **成功**：修复 combo 冲突 + 手柄长按 D-pad repeat（含 4 新测试）+ `setZOrderOnTop(true)` 层级尝试；两 job 全绿。**但真机实测进入 VR 显示的是「原本的非 VR 手机界面」，VR 层没出现** |
| **`42230809154`** | **`f77f482`** | **成功**：回退 `setZOrderOnTop` + 四个 VR 文件加节流诊断日志（渲染逻辑不变）。用户装此版抓 logcat，据此定位真正根因（见 §12） |
| **`42236078232`** | **`9c9cbc7`** | **成功：竞态修复版**。修复 `VrModeHost` 的 `DisposableEffect` 竞态（§12.4）；两 job 全绿、AAR/APK 两道 JNI 守卫 success、测试断言 success；artifacts：`cardboard-sdk-aar` 385622 B、`vr-unit-test-report` 24349 B、`opennow-vr-debug-arm64-v8a` 47543881 B.**画面效果待用户真机复测（§12.6）** |
| `42322518683` | `b7961f6` | **成功：层级修复验证**。`VrTheaterView.kt` 一次性 `setZOrderOnTop(true)` 在 `init` 中应用；CI 双 job 全绿，AAR/APK JNI 守卫 success。代码修复见 §3.1/§12。 |
| `42340709719` | `5542a60` | 成功但**结论错误（已在 `9fb1d16` 回退）**：`PANEL_FLIP_Y` 由 `0.0f` 改为 `1.0f` 想修串流上下翻转，但 `uFlip` 只存在于 `PANEL_FRAGMENT_SHADER`，串流走 `samplerExternalOES + uSTMatrix` 没有该 uniform；实际只把基础 UI 翻了 180°、串流没变。CI 双 job 全绿但视觉是坏的，见 §13.2 |
| `42342141378` | `9dffbb5` | **失败（已定位并修复）**：加 `horizontalMirror` 时误把 `VrSettings.showCenterAlignmentLine` 字段删掉，但 `sanitized()` 与 `VrTheaterRenderer.drawStatus()` 仍引用它；同时 `onDrawEye` 里误用了作用域外的 `s.horizontalMirror`（该处只有 `current`）。Kotlin `Unresolved reference` 导致 `:app:compileDebugKotlin` 失败 |
| **`42346490577`** | **`c563391`** | **成功：编译修复版**。恢复 `showCenterAlignmentLine` 字段（与 `horizontalMirror` 共存）；镜像改用 `current.horizontalMirror`，并在 `glUniformMatrix4fv` 之后复位 `stMatrix[0]` 符号，保证左右眼都镜像。两 job 全绿 |
| **`42347655425`** | **`7a9abfb`** | **成功：可选 360° 背景 + 图片导入版**。新增 3 个设置项、球体渲染、2D 侧导入 UI、VR 菜单扩到 2×9 行、单测补到 18 行断言与新开关用例；两 job 全绿。**该版真机反馈画面效果失败（基础 UI 反转 / 串流朝向 / 辅助线层级 / 状态栏），见 §13** |
| **`42355583696`** | **`9fb1d16`** | **成功：真机反馈修复版（§13）**。回退 `PANEL_FLIP_Y` 为 `0.0f`；新增 `verticalFlip` 与视频专用 `videoTexCoordBuffer`（镜像/翻转只作用串流，不改 GLSL、不动 `stMatrix`）；辅助线从 panel Canvas 移到 `onFinishFrame()` 的物理屏幕层；`MainActivity` 强制 `SENSOR_LANDSCAPE` + immersive；补 3 个开关持久化；菜单 18→19 行；2D 设置页加上下翻转开关；单测同步。两 job 全绿。**画面效果待真机复测（§13.5）** |

**`42202834080` 人工二进制核验（已执行）**：解包测试报告 7 个 XML →
`7 suites / 71 tests / 0 failures / 0 errors / 0 skipped`；解包 APK →
`lib/arm64-v8a/libcardboard_sdk_jni.so`(21800 B) 与 `lib/arm64-v8a/libGfxPluginCardboard.so` 均存在，
共 7 个原生库。**这是本机可给出的最高验证级别；画面与真机行为仍未验证。**

**`42198812193` 的人工二进制核验（已执行，非推测）**：

- 解包 `vr-unit-test-report` 的 6 个 XML：`6 suites / 64 tests / 0 failures / 0 errors / 0 skipped`。
- 解包 APK：`lib/arm64-v8a/libcardboard_sdk_jni.so`(21800 B) 与
  `lib/arm64-v8a/libGfxPluginCardboard.so`(857144 B) 均存在。
- 结论只到「可编译 + 单测通过 + 产物完整」为止；**shader、画面、串流切换、真机帧率仍未验证**。

### 8.5 串流接入的真实边界与「中途切 VR」的正确修复方案（必读）

**用户质疑**：「为什么你 VR 界面是直接把整个串流界面铺到 VR 界面下了，也有可能你根本没做」。
核实结论如下，不要凭文档猜测：

1. **VR 里显示的是解码后的视频帧，不是整个 App UI**。`Streaming.kt:660` 的
   `decoderSurface = { VrVideoSurfaceRegistry.target() ?: renderer?.decoderTarget }` 让
   `MediaCodec` 直接输出到 `VrTheaterRenderer` 创建的 `SurfaceTexture`；GL 用
   `GL_TEXTURE_EXTERNAL_OES` 把这一帧贴到**唯一一块平面 quad**。手机 Compose UI 仍在底层
   （被全屏 `CardboardView` 盖住），并没有被镜像进 VR。串流始终是平面 2D，无 SBS、无视差。
2. **但用户的质疑戳中了一个真实缺陷**：`StreamCodec.kt:471`
   `if (!directSdrEnabled()) return textureDecoder` 是在 **WebRTC 创建 decoder 时一次性决定**解码器类型；
   `SurfaceOrTextureVideoDecoder` 只实现了 **surface → texture 单向**回退，没有 texture → surface。
   于是：
   - **先进 VR，再开始串流** → `directSdrEnabled` 当时为 true → 创建 surface decoder →
     `MediaCodecSurfaceVideoDecoder.decode()` 每帧读 `surface()`，检测到
     `target !== outputSurface` 会 `stopCodecLocked()` 并用新 Surface 重新 `configure()` →
     **VR 能收到画面，且进/出 VR 都能在同一会话内切换 target**。这条路径可用。
   - **先开始串流（VR 未开），串流中途才进 VR** → decoder 已是纯 `textureDecoder`，
     `VrVideoSurfaceRegistry.target()` 虽然返回 VR 的 SurfaceTexture，**没有任何解码器消费它**，
     VR 里只剩 2D 状态面板。**这条路径目前不可用**。
3. **为什么本轮没有硬改解码器**：`directSdrEnabled` 恒 true 会绕过 `prefersDirectSdr` 的
   `!hdr && !textureRequired` 保护，破坏 HDR、锐化、录制等上游功能；而
   `SurfaceOrTextureVideoDecoder` 的 `switchToTexture()` 是「一次失败就永久回退」的设计。
   本机无 Android 工具链、无法真机验证关键帧恢复与 WebRTC 状态机，**贸然改动违背「确认可用」**。
   因此把完整方案写在这里，交给能在真机上验证的 Agent。

**正确修复方案（按风险排序，推荐 A）**：

- **A（真正无缝，需在真机验证）**：
  1. `StreamCodec.kt`：`createDecoder` 在硬件 surface decoder 可用时**总是**创建
     `SurfaceOrTextureVideoDecoder`（去掉 `if (!directSdrEnabled()) return textureDecoder` 的早退），
     `useSurface` 保持**动态** lambda
     `{ VrVideoSurfaceRegistry.isActive || renderer?.prefersDirectSdr == true }`。
     `initDecode` 时 `usingTexture = !useSurface()`，所以普通 2D 会话行为不变，只是多一层包装。
  2. `SurfaceOrTextureVideoDecoder.decode()`：新增反向分支
     `if (usingTexture && useSurface() && surfaceFailure() == null) switchToSurface()`；
     `switchToSurface()` = `textureDecoder.release()` → `surfaceDecoder.initDecode(settings, callback)`
     → `active = surfaceDecoder; usingTexture = false`。
  3. `MediaCodecSurfaceVideoDecoder.initDecode()` 会置 `needsKeyFrame = true`，切换后的**非关键帧**
     应返回 `VideoCodecStatus.NO_OUTPUT`（不要 `ERROR`），等下一个关键帧自然恢复，避免 WebRTC
     判定解码失败而重连。
  4. **保护条件**：HDR、锐化、录制（`textureRequired`）时必须让 `useSurface` 为 false，
     否则会破坏这些功能；`directSdrEnabled` 里已有 `settings.hdrEnabled` 可取。
  5. 真机验证：串流中进 VR → 画面出现；按 BACK 退出 → 手机继续播放且软键盘可唤起；
     HDR / 锐化 / 录制会话不受影响。
- **B（零风险但不无缝）**：设置页的「VR 模式」开关在检测到当前会话是 texture decoder 时，
  提示「需要重新开始串流才能在 VR 中看到画面」，或要求先开 VR 再开串流。
- **C（不推荐）**：VR 侧镜像 2D SurfaceView 输出（VirtualDisplay / SurfaceControl），
  需要系统权限或额外 render pass，与低端机省性能目标冲突。

**另一个可选改进（用户明确说「可以，不强求」）**：VR 内显示帧率等只读信息。
现成位置是 `VrTheaterRenderer.uploadPanel()` 的 Canvas 面板；加一个由 `onNewFrame` 统计的
FPS 计数器即可，不要在 VR 里做软键盘（用户明确禁止）。

---

## 9. 低端机性能预算（用户明确要求「全套性能优化」）

### 9.1 已实施（代码在 `VrTheaterRenderer.kt` / `VrMatrix4.kt` / `VrHeadPose.kt` / `VrTheaterView.kt`）

| 优化 | 量化 / 机制 |
| --- | --- |
| 渲染循环**零堆分配** | 矩阵改用 `multiplyInto` / `scaledRotationInto` / `identityInto` / `rotationInto`；复用 direct `FloatBuffer`、Bitmap、Canvas、Paint；SDK 的 `Eye` 矩阵本身是预分配并返回引用。原本每眼每帧约 3–4 个 `float[16]`，现在为 0 |
| 平面幕布顶点 50 → **4** | `curvature <= 0.001` 时 `segments = 1`；只有开启曲面才用 24 段 |
| 视频**零拷贝** | 解码器 `Surface` → `SurfaceTexture` → `GL_TEXTURE_EXTERNAL_OES`，不经过 Bitmap / 额外 FBO |
| 2D 面板轻量 | 512×288（0.59 MB），仅在设置变化且距上次 ≥ 200 ms 时重绘，用 `texSubImage2D` 更新，不重新分配纹理 |
| 不重复清屏 | SDK 每帧已 clear 一次；`onDrawEye` 不再 clear（既是正确性要求，也省一次全屏带宽） |
| 关闭多余 GL 特性 | 无 blending / depth test / MSAA / mipmap；`glDisable(GL_DITHER)`；`LINEAR` + `CLAMP_TO_EDGE` |
| 亮度 | fragment shader 里一次乘法，不增加 render pass |
| 状态抖动 | 几何只在 `screenScale/distance/verticalOffset/curvature` 变化时重建；aspect 变化才重建 |
| 健壮性 | 两个 shader program 都用 `runCatching` 链接，驱动不支持 OES 时降级为 2D 面板，不崩 GL 线程 |
| **官方单目省电档** | `VrTheaterView.applySettings()` 已把 `viewerMode` 接到 `setStereoRenderMode()`。`SIMULATION` 模式 SDK 只调 **1 次** `onDrawEye`（双目是 2 次），且**完全跳过 `renderEyeToDisplay()` 全屏畸变合成**。这是低端机收益最大的一档 |

### 9.2 明确未实施（附理由，别自作主张加上）

| 未做 | 原因 |
| --- | --- |
| 降低帧率 | SDK 的 `ChoreographerThread` 每个 vsync 主动 `requestRenderOnVsyncEvent()`；虽然设为 `RENDERMODE_WHEN_DIRTY`，实际仍是满帧率双目渲染，**没有官方降帧 API**。改 vendor SDK 的线程逻辑风险高，收益不确定 |
| 降低 GL surface 分辨率 | `setFixedSize()` 会让 `CardboardView.onSurfaceChanged(w,h)` → `CardboardViewApi.setScreenParams(w,h)` 收到缩小值。在 **CARDBOARD 双目畸变模式**下，像素尺寸参与畸变/视场计算，等比缩小未经真机验证，可能改变 FOV。仅在 **SIMULATION 单目**（无畸变）下可安全使用，列为可选 |
| 改 EGL config 去掉 depth/stencil | SDK 自己调用 `glClear(GL_DEPTH_BUFFER_BIT)` 且畸变网格由 native 绘制，去掉 depth buffer 有黑屏风险，收益小于风险 |

### 9.3 结论

在**不修改 vendor SDK 渲染循环**的前提下，可优化的部分已全部覆盖：CPU 侧每帧分配归零、
几何与纹理上传降到最低、GPU 状态最简、并提供官方单目省电档。
剩余最大开销来自 SDK 固定的 vsync 双目畸变，**只能通过 `SIMULATION` 模式规避**。

---

## 10. 已知风险与未验证项（如实告知用户）

- 本轮所有渲染改动**只经过 Kotlin 编译与 JVM 单测（CI）验证**，GLSL、OES 纹理、
  中文文字方向、串流切换、真机帧率**均未真机验证**。
- CI 能证明：代码可编译、64 个 JVM 测试通过、AAR/APK 含两个必需 `.so`。
  CI **不能**证明：画面正常、shader 编译通过、串流切换不黑屏、低端机不发热。
- `PANEL_FLIP_Y = 0.0f` 是基于 `VrScreenGeometry` 的 `top v=0` 与 `GLUtils` 上传约定的推理；
  真机若文字上下颠倒，改这一个常量即可。
- 上游 2.3.8 无可合并源码，适配处于阻塞状态。
- PAT 曾明文出现在对话中，任务结束后应撤销重建。

---

## 11. 收尾检查清单

- [x] **当前代码 run `42236078232` 全绿**，两 job 全绿、AAR/APK 两道 JNI 守卫 success、测试断言 success、三个 artifact 齐全（见 8.4）
- [x] run ID 已补进 `README-VR.md` 5.1、本文 8.4、§12.4
- [x] 提交纪律已写入 8.1（纯文档提交用 `[skip ci]`，不重复触发构建）
- [ ] 用户在新 APK（`9c9cbc7`）上按 §12.6 真机复测并反馈（**唯一剩下的关键验证**）
- [ ] 工作区 `git status` clean，仓库内无 PAT 字面串残留
- [ ] 提醒用户撤销并重新生成 PAT

---

## 12. 2026-11-06 结案：「进 VR 啥也没有」= VrModeHost 的 DisposableEffect 竞态（已修复）

### 12.1 现象与已否决的猜测

- 用户测过 `3cfcade`（带 `setZOrderOnTop`）与 `f77f482`（回退置顶 + 诊断日志），现象一致：
  进入 VR 后看到**「原本的 no-VR 界面」（底层 2D UI 透出，VR 层没出现）**，或「啥也没有」。
- 诊断导出 4 MB 日志里那条 `UnsatisfiedLinkError: libcardboard_sdk_jni.so` 是
  **`[Retained Java crash — previous process]` timestamp 08:56**（当前测试是 13:00–13:14），
  且该缺库闪退早在 `11a4d69` 已修复、用户实测确认「能进 VR」。**不是本次根因**。
- 「零 CardboardView 生命周期日志」曾是误判：`vr/` 原本一条 `Log.*` 都没有（§7.16）。
- shader 编译、GLSL 黑屏、JNI/SDK、`setZOrderOnTop` 层级——**全部经真机 logcat 排除**。
- `docs/VR_IMPLEMENTATION_PLAN.md` §12.5 早已写明剩余问题是「渲染内容为空 / 啥也没有」。

### 12.2 真机 logcat 的决定性证据（commit `f77f482`，run `42230809154` 全绿）

用户执行 `adb logcat -s VrSession,VrModeHost,VrTheaterView,VrTheaterRenderer`，
每次进入 VR 序列完全复现（以 22:26:32 那次为例）：

```
setEnabled(true) → AndroidView.factory → init(childCount=2) → onAttachedToWindow
→ VrTheaterView: start: onResume()        22:26:32.615
→ VrTheaterView: stop: onPause()          22:26:32.618   ← 仅 3ms 后，期间没有 setEnabled(false)
→ VrTheaterView: release: onDestroy()     22:26:32.637
[9 秒后用户退出] setEnabled(false) → onDetachedFromWindow（此时已无 stop/release 日志）
```

- `view.stop()` / `view.release()` 全仓库**仅在 `VrModeHost.DisposableEffect(enabled).onDispose`
  调用**（grep 复核），所以那次 `onPause()/onDestroy()` 必来自此 effect，而当时 enabled 仍为 true。
- 整份日志**零条 `VrTheaterRenderer` 日志** → `onSurfaceCreated` / `onDrawEye` 从没执行，
  GL surface 刚 `onResume()` 就被销毁 → 画面当然「啥也没有」。
- `childCount=2`、`hiddenNonSurface=1` 证明 CardboardView 构造、JNI、SDK 都正常。

### 12.3 根因（Compose 竞态）

旧 `VrModeHost` 在**自身作用域**持有 `var viewRef by remember { mutableStateOf(...) }`，并在
`DisposableEffect(enabled).onDispose` 里 `viewRef?.stop()/release()`。
当 `enabled: false→true`：Compose 会 dispose 掉 key=false 的**旧** effect；但该 `onDispose` 在
commit 阶段执行时，`AndroidView.factory` 已经把**新建的** VrTheaterView 写进了同一个 `viewRef`。
于是旧 effect 的 onDispose 把**刚创建的新 view** 给 pause + destroy 了 —— 这就是 3ms 竞态，
也解释了之前「有时黑屏、有时非 VR 界面」的不稳定症状。

### 12.4 修复（commit `9c9cbc7`，CI run `42236078232` 全绿）

把 VR 视图封装进**仅在 `enabled==true` 时存在**的私有子 composable `VrTheaterHost`：

- 用 `remember { VrTheaterHolder() }` 保存**局部** view 引用（不再是 VrModeHost 的共享状态）；
- 用 `DisposableEffect(Unit)`（key 与 enabled 无关）管理该子 composable 的生命周期；
- `enabled=false` 时子 composable 离开组合 → 它的 onDispose 只清理**属于它自己的那个 view**，
  不存在「旧 effect 读到新 view」的交错窗口。

`VrModeHost()` 在 `MainActivity.setContent` 根 Box 里无条件调用（已核实），不会被整体移除，
所以这是唯一的生命周期来源。回退的 `setZOrderOnTop` 保持不恢复：日志证明「原本非 VR 界面」
是 view 被误销毁后底层 UI 透出，**不是**独立的 SurfaceView 层级问题；若真机修复后再出现
「VR 显示成全屏 2D 串流」，再评估 `setZOrderMediaOverlay` / Compose `zIndex`。

### 12.5 诊断日志（保留，便于回归）

`VrSession` / `VrModeHost` / `VrTheaterView` / `VrTheaterRenderer` 四个 TAG。渲染热路径仅
每 120 帧一条节流心跳，正常帧零分配（§9）。抓取命令：

```
adb logcat -s VrSession,VrModeHost,VrTheaterView,VrTheaterRenderer
```

修复后的**健康序列**应为：`start: onResume()` 之后**不再**有 `stop/release`，随后
`VrTheaterRenderer: onSurfaceCreated` → 「onDrawEye: draw frame#」心跳；退出时才有
`VrTheaterHost.onDispose` + `stop/release`。

### 12.6 待用户真机确认（CI 无法验证画面）

1. 覆盖安装新 APK，进 VR 应能看到黑色房间 + 2D 状态面板/串流画面（不再是原手机 UI）。
2. BACK 退出 VR 应无重连、软键盘恢复。
3. 若仍有问题，按 §12.5 抓 logcat 回传。

---

## 13. 真机反馈失败 → 第二轮修复（commit `9fb1d16`）

### 13.1 用户反馈（`7a9abfb` 版三张真机截图）

用户确认 VR 场景能正常显示后，反馈：

- 「串流界面还是不正常」；
- 「最基础的介面也都变成反转了」——状态面板 `OpenNOW VR` 与中文状态文字 180° 倒置；
- 辅助线「在 VR 界面里、会跟头转动」，用户明确要求它属于 **2D 界面层级**；
- 截图里 Android 状态栏（`3:06`、电量 `37`）仍显示，要求**强制横向全屏**；
- 用户补充旧截图澄清：「这个是之前不能正常使用 vr 场景时的界面」——旧图中间白线是
  Cardboard SDK `CardboardLayout.java` / `cardboard_ui.xml` 的 `ui_alignment_marker`
  （`layout_gravity="center_horizontal|bottom"`，与左上 X、右上齿轮同一 2D View 层），
  **不是**当前 VR 场景里的线。

### 13.2 根因

1. **基础 UI 反转**：`5542a60` 把 `PANEL_FLIP_Y` 从 `0.0f` 改成 `1.0f`，想修串流上下翻转。
   但 `uFlip` **只存在于 `PANEL_FRAGMENT_SHADER`**，只作用于 `GL_TEXTURE_2D` 状态面板/菜单；
   串流走 `VIDEO_FRAGMENT_SHADER`（`samplerExternalOES + uSTMatrix`），**根本没有 `uFlip`**。
   所以那次改动没碰到视频，只把基础 UI 翻了 180°。CI 能过、视觉是坏的。
2. **串流朝向不对**：旧左右镜像用 `stMatrix[0] = -stMatrix[0]`，只改 x-scale，没处理
   `SurfaceTexture.getTransformMatrix()` 可能携带的 crop / translation，会把画面推偏；
   且 `onDrawEye()` 每眼各跑一次，共享 `stMatrix` 被反复取负。
3. **辅助线错位**：旧实现画在 `drawStatus(canvas, s)` 的 panel `Canvas` 上，因此成为
   VR quad 纹理的一部分——每只眼各一条、随透视变形、跟头转动。
4. **状态栏**：VR 开启时没走 immersive，`VrSession.enabled` 没有驱动窗口策略。
5. **持久化漏项**：`VrSettingsStore` 之前没保存 `showCenterAlignmentLine` /
   `horizontalMirror`，开关重启会丢。

### 13.3 修复（全部不改任何 GLSL 源码）

| 文件 | 改动 |
| --- | --- |
| `VrTheaterRenderer.kt` | `PANEL_FLIP_Y` 回退 `0.0f`；新增 `videoTexCoordBuffer`，`horizontalMirror` 用 `u→1-u`、`verticalFlip` 用 `v→1-v`，**只给视频 quad**，panel/menu/360° 球体继续用原 `texCoordBuffer`；视频路径不再 mutate `stMatrix` |
| `VrTheaterRenderer.kt` | 删除 panel Canvas 里的辅助线；新增 `linePositionBuffer` / `lineTexCoordBuffer` / `lineTextureId` / `createLineResources()` / `drawScreenSpaceAlignmentLine()` |
| `VrTheaterRenderer.kt` | 菜单 18→19 行（`MENU_ROWS_PER_COLUMN=10`、`MENU_ROW_HEIGHT=30f`）；`onRendererShutdown` 释放线纹理；`createLineResources()` 幂等（surface 重建先删旧纹理） |
| `VrSettings.kt` | 新增 `verticalFlip`；`sanitized()` 保留；三个朝向/辅助线开关默认全关 |
| `VrSettingsStore.kt` | 补 `K_ALIGN_LINE` / `K_H_MIRROR` / `K_V_FLIP` 的 load / save |
| `VrSettingsSection.kt` | 新增「串流画面上下翻转」开关，更新镜像/辅助线说明 |
| `VrMenuModel.kt` | 新增 `VERTICAL_FLIP` 枚举项，rawValue / display / activate / toggle 全部补齐 |
| `MainActivity.kt` | `vrFullscreenActive`，`lifecycleScope` 收集 `VrSession.enabled`；VR 开时 `SENSOR_LANDSCAPE` + `applyOpenNowSystemBars(immersive=true)`，退出后恢复；`updateSpaceUiSystemBars` 不再因 hidden 相同而 early-return |
| 两个 JVM 测试 | 菜单行数 18→19，新增 `VERTICAL_FLIP` 可逆性与三开关默认值用例 |

### 13.4 辅助线为什么放在 `onFinishFrame()` 而不是 `onDrawEye()`

`VrTheaterView` 的 GL SurfaceView 被 `setZOrderOnTop(true)` 置顶，普通 Compose 2D View
会被盖住，**无法直接复用 SDK 旧的 `ui_alignment_marker`**。

Cardboard SDK 的 `onRenderStereoFrame()` 把左右眼分别画到 FBO 的两个半屏 viewport，
再用 `renderEyeToDisplay()` 做畸变合成到默认 framebuffer；`onFinishFrame(viewport)` 在
**合成之后**调用，且传入的是整屏 viewport。因此把辅助线画在这里：

- 显式 `glBindFramebuffer(GL_FRAMEBUFFER, 0)` + `glViewport(整屏)`；
- identity MVP，NDC `x=0`（物理屏幕正中）、`y=-1..0`（底边到中间）、宽 `0.003`；
- 关闭 depth / scissor 测试，避免被 SDK 畸变 pass 的残留状态裁剪；
- 复用 panel program + 1×1 纯白纹理，`uFlip=0`、`uBrightness=1`。

结果：**一条**笔直白色细线、固定在物理屏幕正中央、不分左右眼、不受畸变弯曲、不跟头转。
这正是用户旧截图里那条 2D marker 的位置与层级。

### 13.5 CI 与待复测

- CI run `42355583696`（commit `9fb1d16`）：**两个 job 全绿**（`build-cardboard-sdk-aar`
  + `vr-unit-tests-and-debug-apk`；单测断言、APK 收集、JNI 守卫全部 success）。
  见 §5.1 / `README-VR.md` §5.1 记录。
- **全部画面效果仍需用户真机复测**：基础 UI 是否正立、左右镜像 / 上下翻转是否分别正确、
  两项叠加是否等于 180° 旋转、辅助线是否固定在屏幕正中且不跟头转、强制横向与状态栏/导航栏
  是否隐藏、退出 VR 后方向与系统栏是否恢复。
- 若串流朝向仍相反，在 2D 设置页或 VR 菜单里切换「串流左右镜像 / 串流上下翻转」即可确认方向，
  两个开关独立、可叠加、默认都关、随时可恢复。
- 未实现：把整个手机 2D 界面放进 VR 场景（见 §2.2 可选项）。
  **瞳距 / IPD 调整已于后续提交实现**，见 §14。

---

## 14. 2026-11-07 第三轮：真全屏、IPD、重置中心点、菜单长按

### 14.1 用户反馈与需求（按顺序）

1. 「有 IPD/瞳距调整吗？」→ 已提交版本里确实没有，补做。
2. 「vr界面不是全屏ah，虽然上面状态栏是隐藏了但不是全屏」→ 状态栏隐藏已生效，但画面未铺满。
3. 「然后在加一下重置中心点」→ 头部追踪校准。
4. 「为什么start/selat键要做为触发设置键，要的话就用长按start+selat触发再改一下」→ 菜单改长按。
5. 「编译报错了，home键也改成长按」→ 修 CI 失败，且 HOME/MODE 也改长按。

### 14.2 真全屏铺满（commit `54702f5`，CI run `42359196869` 成功）

`9fb1d16` 只做了「隐藏 bars」，用户真机看到仍有黑边 / 未铺满。补三处：

- `OpenNowSystemBars.applyOpenNowSystemBars()`：immersive 时 `controller.hide()` 增加
  `WindowInsets.Type.systemGestures()` 与 `WindowInsets.Type.displayCutout()`；
  `window.attributes.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`；
  非 immersive 时恢复 `LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT`。
- `MainActivity.applyVrFullscreen(active)`：VR 开启 `window.addFlags(FLAG_LAYOUT_NO_LIMITS)`，
  关闭时 `clearFlags`，让 decor 内容延伸到状态栏 / 手势区 / 刘海区。
- `styles.xml` 保持 `windowFullscreen=false` 不动（运行时 insets controller 已接管，避免影响非 VR 原版 UI）。

### 14.3 瞳距 / IPD（初版 `27ceea1`；**渲染方式已重写，见 §14.8**）

- `VrRanges` 增 `IPD_SCALE_MIN=0.5 / MAX=1.6 / STEP=0.05`；`VrSettings.ipdScale=1.0f`，
  `sanitized()` 钳制；`VrSettingsStore` 加 `K_IPD_SCALE` 读写；`VrSettingSpecs.ALL` 加 2D 滑块；
  `VrMenuModel` 加「瞳距 / IPD」RANGE 行；`VrSettingsSection` 底部说明补用法。
- **初版（已废弃）**：`VrMatrix4.scaleIpdInto()` 只缩放 `viewMatrix[12]`（`eyeFromHead` 的
  ±IPD/2 平移）。用户真机反馈「ipd 调整不正常」，根因与修复见 **§14.8**。
- 数值通路（菜单 → `VrSession.updateSettings` → StateFlow → `VrModeHost` → `VrTheaterView
  .applySettings` → renderer）经核对完整，问题不在设置传递，而在渲染数学。

### 14.4 重置中心点（commit `27ceea1`）

- `VrHeadPose` 新增四参重载 `viewRotationInto(out, current, center, sensitivity)`：
  先算 `current * conj(center)` 相对四元数（`quatMultiplyConj`），再复用原 `viewRotationInto`
  生成逆旋转 view 矩阵。
- `VrTheaterRenderer` 存 `centerOffsetQuaternion`（单位四元数=未校准）；`recenter()` 拷贝当前
  `headQuaternion`；`onDrawEye` 改调四参重载。
- 触发链：`VrSession.onRecenter: (() -> Unit)?` → `VrModeHost` 里 `VrSession.onRecenter =
  { view.recenter() }` → `VrTheaterView.recenter()` → `theaterRenderer.recenter()`。
- 入口：VR 菜单 `VrMenuItemId.RECENTER`（ACTION 行，`activate()` 里调 `VrSession.recenter()`），
  以及手机设置页「重置中心点（头部追踪校准）」按钮。菜单 20 → 21 行。

### 14.5 菜单触发改长按（commit `15315de` → 编译修复 `8294d61`）

- `VrKeys`：`VR_TOGGLE_COMBOS` **只留 `L1+R1`**（`START+SELECT` 原是退出 VR 组合键，与长按
  菜单直接冲突，必须移除）；新增 `MENU_LONG_PRESS_COMBO=[START, SELECT]`、
  `MENU_LONG_PRESS_MS=500L`、`MENU_CLOSE_KEYS=[SELECT, START, MODE]`；删除 `MENU_KEYS`。
- `VrGamepadRouter`：用 `menuHoldStartMs` + `menuHoldFired` latch 统一计时；
  `menuHoldActive()` = 同时按住 START+SELECT **或** 按住 MODE。**计时从组合键成立的初始
  `ACTION_DOWN` 开始**（在 `alreadyDown==false` 分支里 arm），OS key-repeat 的 `ACTION_DOWN`
  只负责检查是否已过 500 ms 并发一次 `ToggleMenu`；松开任意键 `clearMenuHold()`，防连发。
  短按全部 `PassThrough` 给游戏。菜单打开时单按 B / SELECT / START / MODE → `CloseMenu`。
  > 计时起点**不能**放在第一个 key-repeat：手柄 repeat 初始延迟约 400 ms、且因设备而异，
  > 会叠加到 500 ms 阈值之上（用户实际要按近 1 秒）。见 §14.7 的 CI 失败记录。
- **编译失败根因（两个 run `42359487355`、`42359823182` 均 failure）**：
  `VrModeHost.kt` 写了 `view.theaterRenderer.recenter()`，但 `VrTheaterView.theaterRenderer`
  是 `private val`，跨类访问 Kotlin 编译报错。修复：给 `VrTheaterView` 加 public
  `fun recenter() = theaterRenderer.recenter()`，`VrModeHost` 改调 `view.recenter()`。
  教训：新增跨类调用前先确认目标成员可见性；本机无 Android 工具链，只能靠 CI 兜底。
- 测试同步：`VrGamepadRouterTest` 改 `startSelectDoesNotToggleVrButLongPressOpensMenu`、
  新增 `homeKeyRequiresLongPress`、`menuLongPressOpensOnlyInsideVr`、`homeKeyClosesThePanel...`；
  `VrMenuModelTest` 数量 20→21 并加 RECENTER 断言；`VrMatrix4Test` / `VrSettingsTest`
  补 IPD 缩放与钳制断言。

### 14.6 仍待用户真机复测（CI 无法证明）

1. VR 画面是否**铺满整个物理屏**（无上下/左右黑边、刘海与手势区无留黑）。
2. 长按 500 ms 手感是否合适；目标手柄是否确实发送 OS key-repeat `ACTION_DOWN`
   （**到点触发**依赖它，与菜单内长按十字键同一机制；**计时本身从初始按下开始，不依赖 repeat 延迟**）。
3. 「瞳距 / IPD」调小/调大是否让左右眼画面靠拢 / 分离且**能正常融合**（旧版是错位重影）、可随时恢复 1.00x。
4. 「重置中心点」是否把歪头时的画面摆正；退出 VR 后是否重置。
5. §13.5 列出的第二轮画面项（基础 UI 正立、串流镜像/翻转、辅助线固定屏幕中央）。
6. 退出 VR 后方向 / 系统栏 / 全屏 flags 是否恢复原状。

### 14.7 run `42361113685` 的 JVM 单测失败与修复（commit `9d4ddd2`）

`8294d61` 解决了 Kotlin 编译错误（private renderer），但 `vr-unit-tests-and-debug-apk`
的 step 9 仍失败：**87 tests completed, 1 failed** ——
`VrGamepadRouterTest > startSelectDoesNotToggleVrButLongPressOpensMenu FAILED`
（`AssertionError at VrGamepadRouterTest.kt:55`）。日志见根目录 `vr_ci_42361113685_job2.log`。

**根因（不是测试写错数值，是计时起点设计错）**：`menuHoldStartMs` 原先只在第一个 OS
key-repeat 的 `ACTION_DOWN`（`alreadyDown==true`）分支里赋值。测试里组合在 `1000L` 成立、
第一个 repeat 是 `1200L`（arm），`1600L` 的 repeat 只过了 `400 ms < 500 ms`，返回
`PassThrough` 而测试期望 `ToggleMenu`。真机上这还意味着手柄的 repeat 初始延迟（~400 ms，
各设备不同）被叠加到用户设定的 500 ms 之上，长按体感接近 1 秒。

**修复**：把计时 arm 移到 `alreadyDown==false` 的初始按下分支 —— 组合键一成立就记
`menuHoldStartMs = timeMs`；repeat 分支只做 `timeMs - menuHoldStartMs >= 500` 的到点检查
（纯 JVM 状态机没有定时器，仍必须靠 repeat 事件来兑现）。同时：

- `VrGamepadRouterTest.startSelectDoesNotToggleVrButLongPressOpensMenu` 补 `1499L` 边界断言
  （499 ms 不触发）与 `1600L` 触发断言，把「从初始按下计时」这个契约锁死；
- `menuLongPressOpensOnlyInsideVr` / `homeKeyRequiresLongPress` 的注释同步订正；
- 提交前用 Python 精确复刻状态机跑完 20 个 `VrGamepadRouterTest` 用例，20/20 通过，
  避免再浪费一次约 6 分钟的 CI。

**验证**：run `42362976400`（`9d4ddd2`）两 job 全绿 —— `build-cardboard-sdk-aar`、
`Run VR JVM unit tests and build the debug APK`、`Assert VR JVM tests actually ran`、
`Collect the APK` 以及两处 `Verify the Cardboard JNI bridge ...` 全部 success。当前 `main` 为绿灯。

**遗留**：长按「触发」仍依赖手柄发出 OS key-repeat；不发 repeat 的手柄需引入
`Handler.postDelayed`（会破坏纯 JVM 可测性），属已知限制，真机验证项见 §14.6 第 2 条。
另：`VrHeadPose` 四参重载每帧 `FloatArray(4)` 临时分配（与 §9 零分配目标冲突）、
`centerOffsetQuaternion` 主线程写 / GL 线程读无同步，均为待优化项，本轮未改动。

---

### 14.8 2026-11-07 第五轮：修复「ipd 调整不正常」

**用户反馈**：`9d4ddd2` 绿灯版本的真机上，「ipd调整不正常」。

**根因（读 vendor Cardboard SDK 源码定案；不是设置没传到 renderer）**：
数值通路完整（VR 菜单 / 2D 滑块 → `VrMenuModel.adjust` → `VrSession.updateSettings` →
StateFlow → `VrModeHost` → `VrTheaterView.applySettings` → renderer），`ipdScale` 确实到达
`onDrawEye`。问题在旧实现只缩放 `viewMatrix[12]`：

- `third_party/cardboard/sdk/lens_distortion.cc:44-47`：
  `eye_from_head_matrix_[kLeft/kRight] = Translation(±inter_lens_distance/2, 0, 0)`。
- 同文件 `:71-75`：`GetEyeProjectionMatrix()` = `Matrix4x4::Perspective(fov_[eye], ...)`，
  而 `fov_` 由 `CalculateFov()`（`:172-194`）用**同一个 `inter_lens_distance`** 算出的离轴
  `outer_angle` / `inner_angle`（还经过非线性 `distortion.Distort`）。
- 同文件 `:229-244`：`CalculateViewportParameters()` 又用同一参数算畸变 mesh 的 `x_eye_offset`。

即 **eyeFromHead 平移、离轴投影、畸变 mesh 三者同源于 `inter_lens_distance`，是必须一起变的
一套**。只改 `viewMatrix[12]` 让相机位置与它的视锥 / 畸变失配 → 左右眼画面朝相反方向错位、
无法融合。`VrMatrix4.scaleIpdInto` 的 KDoc 当初假设「投影矩阵不含 IPD」，该前提是错的。

**修复（纯 Kotlin、不改 shader、不改 SDK 参数）**：
放弃在 renderer 里重算 SDK 瞳距（那要改 vendor `LensDistortion` 并重编译 AAR，还会连光学畸变
一起改掉，风险大）。改为**完全不碰 SDK 的 view + projection + distortion**，只在每眼 MVP 组装
完成后做等大反向的屏幕空间水平位移：

- `VrMatrix4.applyIpdShiftInto(out, ipdScale, eyeSign)`：实现 `clip.x += k * clip.w`，即把
  第 3 行（clip.w）乘 `k` 加到第 0 行（clip.x）；`k = (1 - ipdScale) * IPD_SHIFT_GAIN(0.08)
  * eyeSign`，左眼 +1 / 右眼 -1 / 单目 0。调小 → 左右眼靠拢更易重合；调大 → 分离立体感更强。
- `VrTheaterRenderer.onDrawEye()`：删掉 `scaleIpdInto(viewMatrix, ...)`，在
  `multiplyInto(mvpMatrix, projection, view)` **之后**调用 `applyIpdShiftInto`；`eyeSign` 由
  `eye.getEyeType()` 得到（`Eye.LEFT=0 / RIGHT=1 / MONOCULAR=2`）。幕布与 360° 球体共用
  `mvpMatrix`，一起生效。
- **默认 1.00x 时 k=0 且整段跳过，与未修改的 SDK 渲染 bit-identical**；MONOCULAR
  （SIMULATION）不生效。
- 对单块固定深度的平面幕布，屏幕空间视差与改变瞳距视觉等价，但绝不会破坏立体自洽性。

**测试**：`VrMatrix4Test` 删掉旧 `scaleIpdInto` 两例，新增 4 例 —— 默认 / 单目 no-op
（delta=0 全矩阵相等）、只改 x 行且增量 = k·w 行、左右眼等大反向且调小 / 调大方向相反、
错误尺寸抛异常。菜单 / 范围 / 持久化 / 滑块通路不动。

**验证**：run `42366035113`（`d3a0f32`）两 job 全绿 —— `build-cardboard-sdk-aar`、
`Run VR JVM unit tests and build the debug APK`（JVM 单测全通过、debug APK 产出）、
`Assert VR JVM tests actually ran`、`Collect the APK` 以及两处
`Verify the Cardboard JNI bridge ...` 全部 success。当前 `main` 恢复绿灯。
文档（`README-VR.md` / `docs/HANDOFF.md`）随后以纯文档 `[skip ci]` 提交单独推送。

**待真机复测**：调小 / 调大是否让左右眼画面靠拢 / 分离且能正常融合，1.00x 是否与原版一致。

---

## 15. 2026-11-08 第四轮：上下填满（双目）、二级菜单、A 门控编辑、入口重排（commit `6cac3e3`）

> ⚠️ **历史记录，实现已作废**：本节 15.3 描述的 `FILL` =「缓存左右眼 MVP、在
> `onFinishFrame()` 清掉畸变结果、左右半屏全高重画」这个方向，**已被真机反馈判定为错误并在
> §16 全部替换**。它去掉了桶形校正、破坏了双眼重叠、也没能真正填满。保留本节只是为了记录
> 决策过程和回滚点，**接手时请以 §16 为准，不要照本节改代码**。二级菜单 / A 门控 / 入口重排
> 等其余部分仍然有效。

### 15.1 先纠正一次理解偏差（重要，别再犯）

用户说「依然是不能够全屏铺满」，并贴截图说明「整个 VR 窗口要能上下填满，而不是有大的黑边」。
**一开始被误解成**：加 `FULLSCREEN` / `SQUARE` 模式、加 `fillScale` 数值项、加菜单主题，甚至考虑
`setStereoRenderMode(false)` 退化成单目无畸变 NDC quad。用户明确纠正：

> 「单目模式，不就是 VR 直接显示嘛」——即**不能用退化成单目当作 VR 全屏方案**；
> 「全屏填充」= 上下方向铺满物理屏幕、消掉黑边，**不是**把画面做成方形、也不是 2D 直显。

随后回退了半成品（tag / branch / stash 见 15.6），重新读 vendored SDK 源码定根因。

### 15.2 根因：黑边来自 distortion mesh，不是 Activity window flag

- `third_party/cardboard/sdk/lens_distortion.cc:189-194`：`CalculateFov()` 用
  `std::min(几何角, device_fov[...])` 把 FOV **截断到设备参数**。
- `distortion_mesh.cc:53-59`：网格顶点由 tan-angle 转 NDC（`2*u_screen-1`），FOV 偏小 →
  畸变网格只覆盖屏幕中间一块 → 上下被裁、边缘出现圆角/暗角/中缝。
- `CardboardView.onRenderStereoFrame()`：左右眼先分别画进共享 FBO 的
  `(0,0,w/2,h)` 与 `(w/2,0,w/2,h)` viewport（**内容本来就是全高**），
  再调 `renderEyeToDisplay()` 做畸变合成 —— **黑边是这一层加上的**。
- 结论：`FLAG_LAYOUT_NO_LIMITS` / 隐藏 systemGestures / cutout 只解决系统栏与刘海，
  **消不掉畸变 mesh 的黑边**。继续堆 window flags 是死路。

### 15.3 方案：保留双目，覆盖畸变合成（不碰 SDK / shader）

`VrScreenFillMode { DISTORTED, FILL }`，默认 `FILL`。

1. `onNewFrame()` 每帧把 `frameMvpCount = 0`。
2. `onDrawEye()` 组装完每眼最终 MVP（`eyeFromHead × head`，再 `perspective`，
   最后 `applyIpdShiftInto`）后 `System.arraycopy` 缓存进 `frameMvps[0/1]`；
   同时快照 `frameUseVideo` / `frameDrawPanel`。原 `onDrawEye` 的绘制体抽成
   `drawEyeScene(mvp, s, useVideo, drawPanel, allowBackgroundLoad)`。
3. `onFinishFrame(viewport)`（SDK 完成 FBO + 畸变合成之后、默认 framebuffer 上）：
   `drawVerticalFill()` → `glBindFramebuffer(0)`、`glClear` 掉畸变结果、
   `glDisable(DEPTH/SCISSOR/BLEND)`，然后设 `(0,0,w/2,h)` 画左眼缓存 MVP、
   `(w/2,0,w/2,h)` 画右眼缓存 MVP。单目（`frameMvpCount==1`）时整屏画一次。
4. 矩形 viewport 尺寸与 SDK 的 FBO viewport **完全一致**，MVP 一致 → 画面内容 / 比例 /
   双目视差都不变，只是不再经过畸变 mesh。上下 100% 顶满、无圆角暗角/中缝，
   横屏手机上每眼接近方形。固定中心辅助线在这之后画，物理屏幕层级不变。
5. **两眼用的是不同 MVP，仍是真双目立体**；不是 `setStereoRenderMode(false)`、
   不是单目 2D 直显。不改 GLSL、不改 vendor C++、不动零拷贝 OES 视频通路。
6. `DISTORTED` 完全不进 `drawVerticalFill()`，逐像素回到旧渲染 —— 运行时一键回退。

**代价（已写进 README）**：SDK 的预畸变本来用于抵消纸盒凸透镜的桶形畸变，关掉后戴纸盒看
边缘直线会略外凸 / 边角轻微拉伸；中心清晰、双眼视差与深度完整。裸眼看手机 / 简易支架
无影响且更满。这是做成开关而不是硬改的原因。

### 15.4 菜单：二级页面 + A 门控 + 左摇杆独占数值

用户要求「VR 内的设置界面改成有二级页面的那种」「调整具体数值要按 A 才能调整」
「方向键只用于选择条目」「左摇杆调整具体数值」「开关类按 A」「现代化美观一点」。

- `VrMenuModel`：`VrMenuPage` + `pages`（画面 / 观看 / 操作 / 环境 / 快捷 5 页）、
  扁平 `items` 22 行 + `pageRanges = 0..8, 9..13, 14..16, 17..18, 19..21`，
  `init{}` 里 `require` 校验连续且恰好覆盖，改坏立刻崩在类初始化。
  新增 `itemsFor / firstIndexOf / lastIndexOf / clampToPage / pageIndex`。
  新增纯函数 `confirm(settings, id, editing): VrConfirmResult` 与
  `adjustIfEditing(settings, id, steps, editing)` 作为**A 门控的唯一入口**，便于 JVM 单测锁契约。
  新增 `SCREEN_FILL_MODE` ENUM 行（`FILL` ↔ `DISTORTED`）。
- `VrSession`：新增 `menuPage` / `editMode` 两个 `StateFlow`；`setMenuOpen(false)`、
  `setMenuPage()` 都会清编辑态；`setSelectedIndex()` 用 `clampToPage()` 保证十字键不会跨页；
  `VrCommand.Adjust` 只在编辑态写 SharedPreferences（未编辑时直接跳过磁盘写，避免摇杆每帧刷盘）。
- `VrGamepadRouter`：新增 `ChangePage(delta)`；D-pad ←→ 只换页且**移出 key-repeat 列表**
  （否则按住会从第一页一路跳到最后一页）；左摇杆水平 1 步 / 垂直 5 步
  （`STICK_COARSE_STEPS = 5`，companion 改 public 供测试引用）；肩键 ×5 加速移除。
- `VrTheaterRenderer.drawMenu()` / `drawStatus()` 重写为现代化 UI：渐变背景、圆角卡片 +
  描边、分类 chip、选中高亮竖条、编辑中 banner、数值进度条、开关胶囊、
  页计数 `01 / 05`。`MENU_COLORS` 统一配色，paint 在 `createPanelResources()` 分配、
  `onRendererShutdown()` 释放。

### 15.5 入口：删掉误触的常驻按钮

用户：「去掉进入 VR 的全局按钮……放在右屏幕右下角始终，有点容易按到误触」，
要求外部主页面用单独原生按钮、游戏内串流菜单可点按进入 VR、手柄 `L1+R1` 保留。

- `VrModeHost.kt`：删除 `Alignment.BottomEnd` 常驻「进入 VR」按钮及全部相关 imports，
  VR 开启后画面上**零浮层控件**。
- `OpenNowScreens.kt`：`TopStatusBar` 新增 `onEnterVr: () -> Unit = {}`，
  顶栏加 `ElevatedButton("进入 VR")`；外部主页面调用处传 `VrSession.setEnabled(true)`。
- `OpenNowStreamControls.kt`：`StreamControlsPanel` 新增 `onEnterVr` 参数，
  Quick Menu 加 `ControlActionRow(label = "进入 VR")`；`OpenNowStreamSurface.kt` 传入的
  回调**先 `controlsOpen = false` 收起菜单**再进 VR，避免退出 VR 后菜单还开着。
- `VrSettingsSection.kt` 的 VR 模式开关、VR 菜单「退出 VR」行均保留。

### 15.6 可调项 / 回退 / 验证

- `screenFillMode` 全链路：`VrSettings`（含 `sanitized()` 保留 + `DEFAULT`）、
  `VrSettingsStore`（`K_FILL_MODE = "screenFillMode"`，`valueOf` 带 `runCatching` 兜底）、
  `VrSettingsSection` 2D 开关、`VrMenuModel` VR ENUM 行、恢复默认。
- 防改错回滚点：`snapshot-20261108-pre-refactor` tag、
  `backup/pre-fullscreen-refactor-20261108` branch、`git stash` 双副本。
- 测试更新：`VrMenuModelTest`（22 行 / 页结构 / clamp / 门控 / confirm / fill 切换）、
  `VrGamepadRouterTest`（ChangePage / 摇杆粗细调 / STICK_COARSE_STEPS）、
  `VrSettingsTest`（fill 默认与 sanitize 保留）、
  `OnScreenControlsSettingsInstrumentedTest`（补 `onEnterVr = { }`）。
- **CI run `42438601098`（`6cac3e3`）：两 job 全绿** —— AAR 构建 + JNI 守卫、
  JVM 单测 + debug APK、测试数量断言、APK 收集与两处 JNI 守卫全部 success。
- 本地无 Android 工具链，所有编译验证只能走 GitHub Actions；纯文档提交带 `[skip ci]`。

### 15.7 待真机复测（CI 抓不到）

`FILL` 是否真的上下顶满、黑边/圆角是否消失；纸盒镜片下边缘外凸是否可接受；
切 `DISTORTED` 是否回到旧渲染；二级菜单 5 页排版是否完整不裁切；
`A` 门控 + 左摇杆粗细调手感；十字键 ←→ 换页；三个新入口是否都能进 VR 且无误触；
以及 §14 遗留的朝向 / 辅助线 / 瞳距 / 长按等项目。

### 15.8 已知技术债

- `VrHeadPose.viewRotationInto` 四参重载每帧 `FloatArray(4)` 临时分配，与「渲染循环零堆分配」目标冲突。
- `centerOffsetQuaternion` 主线程写 / GL 线程读，无显式同步。
- 长按菜单依赖手柄对 `START/SELECT/MODE` 发 OS key-repeat；若某手柄不发则不会触发。
- `FILL` 绕开畸变，真实纸盒边缘可能变形；「保留畸变且铺满」需改 vendored
  `CalculateFov()` 并重编 AAR，风险高，暂不做。
  → **此条已被 §16 解决**：不改 SDK，改为对最终合成画面做一次等比放大。

---

## 16. 2026-11-08 第五轮：真机四问 +「单纯放大」+ LB/RB 换页

### 16.1 这一轮用户到底反馈了什么

真机实测后用户连着指出四件事，随后又追加两条交互 / 画面方向：

1. **VR 设置项没法操控**：「无法切换分页、也无法上下切换选择哪一个内容」。
2. **按 `B` 很容易退出应用**：「`B` 在 Android 被映射成返回键」。
3. **底层 2D 界面仍被操控**：「`Start` 键或点到后面画布可能突然弹出窗口」。
4. **「方形」方向整个错了**：「我要依然是跟之前那样的，只不过呢它的画面视角改成方形」「并且要
   双眼能够这样重叠」「并不需要左右填充嘛，只要上下填充完整就是了」。
5. **换页换到肩键**：「不要用全部用摇杆和十字键来去换页选择什么的，换页的话…换成 LB 和 RB」。
6. **「单纯放大」**：「能不能做成单纯放大…抓更大的大小来去弄到全屏差不多。当然了，不是说
   全部填满只是上下至少要填满」。

### 16.2 根因与修复（逐条）

**(1) 菜单按键全部被 2D 吃掉。** `VrSession.onKeyEvent()` 原来对
`isGamepadSource(event.source)` 做了门控。部分蓝牙手柄的 face button / D-pad 报
`SOURCE_KEYBOARD`，于是每个菜单键都被交给 2D Compose，选行和换页自然都不动。修复：VR 开启时
**不再做 source 门控**，所有按键先交给 `VrGamepadRouter`；router 只对一小份白名单发命令。
VR 关闭时才保留 source 门控，保证手机软键盘和原 2D UI 完全不受影响。
`onMotionEvent()` 同样去掉 source 门控（原来左摇杆会静默失效）。

**(2) `B` 退出应用。** 旧 `MainActivity.dispatchKeyEvent()` 在 `KEYCODE_BACK` 的 `ACTION_DOWN`
里直接 `VrSession.setEnabled(false)`；等到 `ACTION_UP` 时 VR 已经是 false，事件落到 2D back
stack，`finish()` 了 Activity。修复：整个 BACK 的 DOWN+UP 序列由 VR 层无条件消费，**只在 UP 上
动作** —— 菜单开着就关菜单，否则退出 VR（回到 2D，不结束应用）。同时 `MainActivity` 在 VR 开启
时把所有未消费按键先转发给 `NativeStreamInputRouter` 再 `return true`，绝不落到 Compose。
音量键是唯一例外（交回系统）。

**(3) 底层 2D 被操控。** 三处隔离：
- `dispatchKeyEvent()`：VR 开启时吞掉所有非 VR 键（普通键仍转发给串流游戏）；
- `dispatchGenericMotionEvent()`：VR 开启时只转发 motion 给串流游戏，跳过
  `dispatchGamepadHatNavigation()` 和 `super`，避免隐藏的 2D Compose 焦点被移动；
- `dispatchTouchEvent()`：VR 开启时在原有触摸路由**之前**吞掉整个手势，避免点到不可见的 2D
  画布弹窗。

> ⚠️ **编译硬伤（本轮实际踩到并修好）**：`MainActivity.kt` 原本已经有
> `override fun dispatchTouchEvent`（内含 `NativeStreamInputRouter` 的鼠标 / 触摸转发和
> `postDispatchTouch` 的 finally）。第一版隔离代码又新增了一个同名 override，会直接编译失败。
> 最终做法是把 VR 判断合并进**原有的**那个方法开头，不新增方法。改动前务必先 grep 是否已存在。

**(4) FILL 改成「单纯放大」。** 废弃 `6cac3e3` 的 `frameMvps` / `drawVerticalFill()`，改为纯后处理：

- 完全保留 SDK 原版双目 + 镜头畸变渲染（两只眼的矩阵、头部姿态、IPD 位移、360° 背景、
  OES 零拷贝视频通路一律不动）；
- `onFinishFrame()` 里用 `glCopyTexSubImage2D` 把已合成到默认 framebuffer 的最终画面拷进一张
  RGBA `GL_TEXTURE_2D`；
- 清黑，把 viewport 设成比屏幕大 `zoomScale` 倍并居中（GLES2 允许 viewport 超出
  framebuffer，超出部分被裁掉），用 identity MVP + panel program 把整张图贴回去；
- 上下黑边被顶出屏幕，左右允许保留小黑边；两只眼窗口一起长大，**重叠关系不变**；
- 最后恢复全屏 viewport，再画固定屏幕层级的中心辅助线（辅助线不受放大影响）。

不改 FOV、不改 projection、不改 geometry、不改 shader、不改 SDK C++、不退化成单目。

**驱动兜底（必须保留）**：少数 GLES2 驱动会拒绝 RGB565 → RGBA8 的
`glCopyTexSubImage2D`。如果盲目清屏 + 贴一张未定义纹理，用户会看到**全黑**，比黑边更糟。
实现里首帧先 drain 旧 GL error、拷贝后 `glGetError()` 校验一次；失败则
`zoomCopyUnsupported = true` 并**直接 return（不 clear）**，原画完好，之后永久降级到 SDK 原版。
`onRendererShutdown()` 重置这两个标志，GL context 重建后重新校验。

### 16.3 LB / RB 换页

| 输入 | 菜单内行为 |
|---|---|
| D-pad ↑ / ↓ | 只 `MoveSelection`，长按可连发 |
| D-pad ← / → | `Consumed`，**什么都不做**（用户明确不要十字键换页 / 调值） |
| LB (`BUTTON_L1`) | `ChangePage(-1)` |
| RB (`BUTTON_R1`) | `ChangePage(1)`，长按不连发，避免一次按住就翻到头 |
| A / D-pad CENTER | RANGE 行进入 / 退出编辑态；TOGGLE / ENUM / ACTION 直接确认 |
| B / SELECT / START / MODE | 关闭菜单 |
| 左摇杆 | 仅在 A 已武装编辑态时 `Adjust`（←→ 细调 1 步，↑↓ 粗调 5 步） |

`L1+R1` 仍是 VR 开关，但 `VrGamepadRouter.onKey()` 只在**组合从「不成立」变成「成立」的那一个
`ACTION_DOWN` 边沿**上触发：先记 `comboBefore`，再 `pressed.add(keyCode)` 得到 `comboNow`，
条件为 `!menuOpen && comboNow && !comboBefore && shoulderDown && !comboFired`。
`REPEATABLE_MENU_KEYS` 仍只含 `DPAD_UP/DOWN`。

> 为什么不能用朴素的 `comboPressed()`：如果用户在菜单里**同时按住 LB+RB** 换页后直接关掉面板，
> 这两个键仍留在 `pressed` 集合里；此后**任意一个无关键**（A、十字键…）、甚至**这对肩键自身的
> OS key-repeat**（`alreadyDown == true`）都会看到组合成立，于是 `ToggleVrMode` 把用户踢出 VR。
> 边沿判定让「陈旧组合」永远不可能再次触发，用户必须松开再按一次；`alreadyDown`/`comboFired`
> 分支把这些重复事件吞成 `Consumed`。回归用例：
> `shouldersHeldFromMenuPagingCannotToggleVrAfterPanelCloses`（含「松开后重新按仍能正常退出 VR」）。
> 本机以 Python 精确复刻该状态机跑过 79 条断言，全绿后再提交。

### 16.4 新增可调项（全链路）

`zoomScale`：`Float = 1.18f`，范围 `1.00..2.00`，步长 `0.02`，`1.00x` 严格 no-op。
已接入 `VrRanges.ZOOM_*` → `VrSettings`（含 `sanitized()` 与 `DEFAULT`）→
`VrSettingsStore.K_ZOOM`（SharedPreferences）→ `VrSettingSpecs.ALL`（2D 滑块
「整体放大（上下填满）」）→ `VrMenuModel.ZOOM_SCALE`（VR 菜单「画面」页 RANGE 行）→
「恢复默认」。`screenFillMode` 开关一键回到 `DISTORTED`。

菜单总行数 22 → 23，`画面` 页 9 → 10 行，`pageRanges` 改为
`listOf(0..9, 10..14, 15..17, 18..19, 20..22)`；`MENU_ROW_HEIGHT` 30f → 27f，
最后一行进度条落在 `154 + 9*27 + 27 = 424 < 432`，不会被裁。

`VrSettingsSection` 的旧文案「画面上下填满（方形 / 去遮罩）」及那段「左右眼各占满半个屏幕、
变成规整方形、去掉圆角暗角」的描述**全部是错的**，已改成「单纯放大」并补上 1.00x 说明。

### 16.5 测试更新

- `VrGamepadRouterTest`：肩键换页而非 `Consumed`、十字键 ←→ 变 `Consumed`、
  新增「按住肩键不连翻页」用例。
- `VrMenuModelTest`：`items.size` 22 → 23、画面页 9 → 10、新增 `ZOOM_SCALE` 边界 / A 门控用例。
- `VrSettingsTest`：默认 `1.18f` 在范围内、越界 sanitize 到 `ZOOM_MIN`。
- `VrSettingSpecTest`：新增 `zoomScale` 滑块范围与 1.00x no-op 用例。

### 16.6 待真机复测（CI 抓不到）

- LB/RB 换页、十字键选行是否真的顺了（这是本轮最主要的真机验证点）；
- `B` 是否只关菜单 / 退出 VR 而不再结束应用；
- VR 开启后底层 2D 是否彻底不再响应按键 / 点击 / 摇杆焦点；
- `zoomScale` 从 1.00 往上调，上下黑边是否按预期减少；双眼是否仍能重叠；
- 放大后 VR 设置菜单四边是否被裁（1.18x 理论上留有余量，但要用真机确认）；
- 若设备驱动不支持 `glCopyTexSubImage2D`，应自动降级为原版画面而非黑屏；
- 菜单关闭时 `L1+R1` 仍能正常退出 VR。（⚠️ 此条已随 §17 改为 **entry-only**，作废。）

---

## 17. 2026-11-08 真机第四轮反馈：摇杆选行 / 退出方式 / 全屏仍未生效 / 并行代码

### 17.1 并行工作区冲突与处置（重要）

用户误让另一个 AI 同时改了同一个本地目录（Shizuku 虚拟显示器：在 VR 里跑 Chrome / YouTube 等
2D App）。对方声称「原项目完全不动」，实际工作区里 `build.gradle.kts`、`VrMenuModel.kt`、
`VrModeHost.kt`、`VrSession.kt`、`VrTheaterRenderer.kt` 被改，另有 `vr/shizuku/` 6 个新文件。

审计结论：
- **我的工作没被破坏**：`b109061` 的 `onFinishFrame()` / `drawZoomFill()` 未被触碰；
  `VrMenuModel` 的并行改动是在 23 行版本上增量加第 6 页，没回退 `ZOOM_SCALE` / `SCREEN_FILL_MODE`。
  对方列出的 `VrGamepadRouter.kt` / `VrGamepadRouterTest.kt` / `README-VR.md` / `HANDOFF.md`
  实际只有我的改动。
- **该批代码合并前编译必红**：① `drawEyeScene()` 把嵌套 `Pair` 做 4 元解构、`handles.program` 失效；
  ② `rememberUpdated()` 返回 `State<T>` 却赋给 `VrVirtualDisplayHost?` 并调 `.start()`；
  ③ `VrSession.showVirtualDisplayStatus()` 读 `private host.args`；④ `VrMenuModelTest` 断言 23 行而实际 27；
  ⑤ 4 处 trailing whitespace。另有功能隐患（虚拟显示器复用视频 `stMatrix`、异步 `start()` 晚于 GL 创建）。
- 处置：先精确 `git add` 提交我自己的文件，再把整批并行改动提交到本地分支
  `wip/shizuku-virtual-display-20261108`（commit message 明确标注 NOT COMPILING / DO NOT PUSH），
  切回 `main` 得到干净工作区；用户随后确认「没有用到，可以删掉先」，于是 `git branch -D` 删除该分支。
  **仓库里已完全没有这批代码**；工作区上级的 `VR-Shizuku-VirtualDisplay-Extracted/` 提取包仍在，
  作为独立副本保留（不在 git 内）。
- 教训：不要用对方建议的 `git stash push` 来合并 —— 当时暂存区还有我本轮 4 个文件，会一起被卷走。
  正确做法是精确路径 `git add`，或开独立分支隔离。

### 17.2 「左摇杆上下滑动不能切换选项」根因

`VrGamepadRouter.onAxis()` 无论是否处于 A 门控编辑态都只发 `VrCommand.Adjust`；
`VrSession.handle()` 再用 `VrMenuModel.adjustIfEditing()` 把未编辑态的 Adjust **整条丢掉**。
于是没按 `A` 时摇杆上下推完全无响应 —— 真机表现就是「滑不动、设置依然有问题」。

修复：`VrGamepadRouter` 新增 `editing` 标志（由 `VrSession.setEditMode()` 单点同步），
`onAxis()` 双模式：
- **未编辑态**：垂直主导 → `MoveSelection(±1)`（与十字键同节奏连选）；水平主导 → `Consumed`。
- **A 编辑态**：垂直 → `Adjust(±5)` 粗调，水平 → `Adjust(±1)` 细调（原行为不变）。

`setEditMode()` 同时调 `router.resetAxisHold()` 重置 `lastAxisMs`（摇杆重复节奏），
让新模式立刻响应，不必等上一个模式的节流窗口过期。`reset()` 清 `editing` / `lastHatY`。
注意 `resetAxisHold()` **故意不清 `lastHatY`**：它记录的是十字键物理按下的边沿状态，
清零会让「按 A 时仍按着的十字键」在下一个 motion event 再发一次 `MoveSelection`（选中行跳一格）。
保留它才能做到「按住不连发、松开再按才走下一行」。

**HAT 轴兼容**：不少蓝牙手柄把十字键报成 `AXIS_HAT_Y`（MotionEvent）而非 `KEYCODE_DPAD_*`。
而 `b109061` 的输入隔离在 VR 开启时跳过了 `dispatchGamepadHatNavigation()`，这些事件会被直接吞掉。
现新增 `VrKeys.AXIS_HAT_X/Y`、`VrGamepadRouter.onHat(hatY)`（边沿触发，按一次走一行、按住不连发），
`VrSession.onMotionEvent()` 优先消费 HAT 的 `MoveSelection`，否则走 `onAxis()`。

### 17.3 退出 VR 方式（按用户偏好收紧）

用户问「只能使用返回吗？改成只能返回来退出也可以，但设置在设置里退出更好」。
现状其实有三条（`L1+R1` / 系统返回键 / 菜单「退出 VR」行），但 `L1+R1` 会误退出。
现改为 **`VrSession.handle()` 里 `ToggleVrMode` 仅在 `_enabled == false` 时执行**：
- `L1+R1` → **只进入**，在 VR 里按不动作（命令被吞，不会落到游戏）。
- 退出只剩两条显式路径：① 手机系统返回键（菜单开 → 关菜单；菜单关 → 退出 VR）；
  ② VR 设置菜单「快捷 → 退出 VR」按 `A`。
- `README-VR.md §3` 与 2D 设置页文案同步改写。`VrKeys.VR_TOGGLE_COMBOS` 注释改为 ENTRY-ONLY。

### 17.4 「依然不能全屏」根因与修复（本轮最关键）

上一版 `b109061` 的 FILL 在真机上表现为**完全没变化**，不是放大倍数不够，而是根本没跑：
`ensureZoomTexture()` 只分配 **RGBA8** 纹理，但多数手机给 Cardboard 的默认 framebuffer 是 **RGB565**；
GLES2 的 `glCopyTexSubImage2D` 对格式不兼容的窗口缓冲拷贝返回 `GL_INVALID_OPERATION`。
旧代码的「安全兜底」是校验失败就 `zoomCopyUnsupported = true` **永久降级回 SDK 原版**，
且首帧校验前的 drain 只做一次，任何 SDK 残留错误也会误判 → 放大永远不执行。

修复（`VrTheaterRenderer`）：
1. 首帧 drain 旧 GL error 后，用 `ZOOM_COPY_FORMATS`（**RGBA8 → RGB565 → RGB8**）依次重新
   `glTexImage2D` 分配并试拷，驱动接受哪个就固定哪个；之后每帧零 `glGetError()` 开销。
2. 三种全失败 → `glReadPixels`(RGBA8) + `glTexSubImage2D` 上传兜底，带 32 MB 上限保护；
   该路径有全屏读回开销，但只在 copy-tex 完全不可用时启用。
3. 只有连读回都失败才置 `zoomCopyUnsupported`。**所有失败路径都在 `glClear()` 之前 return**，
   原 SDK 画面完好，绝不黑屏。`onRendererShutdown()` 重置全部探测状态，GL context 重建时重来。
4. `menuOpen` 时直接跳过放大（`VrSession.menuOpen.value`），设置面板永远不被裁切。
5. `VrRanges.ZOOM_MAX` 2.00 → **2.50**，`VrSettings.DEFAULT.zoomScale` 1.18 → **1.35**。
6. 尺寸变化保护：`zoomSnapshotMode` 已确定（含 `ZOOM_READPIXELS`）的分支也先调
   `allocateZoomTexture(w, h, mode)`。之前只有首帧探测循环分配纹理，横竖屏切换 / surface 尺寸
   变化后仍往旧尺寸纹理里 `glCopyTexSubImage2D`，会导致画面被拉伸或空白。`allocateZoomTexture()`
   在 `w/h/mode` 未变时是 no-op，不进入每帧热路径。

### 17.5 可调项与文档

- 本轮没有新增设置项；`zoomScale` 默认值与上限调整会自动贯穿 `VrSettings` /
  `VrSettingsStore` / `VrSettingSpecs.ALL` / `VrMenuModel.ZOOM_SCALE` / 恢复默认。
- `README-VR.md §3` 整节重写为完整操作手册（进入 / 退出 / 开菜单 / 菜单内 / 菜单外 5 小节）。
- `VrSettingsSection` 两处文案更新（退出方式、放大范围与「菜单不放大」）。
- 菜单 banner 改为 `↑↓/摇杆上下 选择   LB/RB 换页   A 编辑 / 开关`。
- `README-VR.md §3` 重写为完整操作手册后，§4 的「进入 / 退出 VR」旧文案也一并订正为
  「进入」+ 两条显式退出路径，避免手册自相矛盾。

### 17.6 测试更新

`VrGamepadRouterTest`：原先所有 `onAxis()` 用例显式设 `editing = true`（继续覆盖调值行为）；
新增 `disarmedStickSelectsRowsAndArmedStickAdjustsValues`、
`hatAxisSelectsRowsOncePerPressAndNotWhileHeld`、`hatAxisIsIgnoredWhenMenuIsClosed`、
`resetAxisHoldRestartsStickButKeepsHeldHatEdge`（后者钉住「按 A 不会让仍按着的十字键多走一行」）。
`VrKeys` 新增 `AXIS_HAT_X/Y` 纯 Int 常量，不引入 Android 依赖。

**默认值改动连带修的两处必红断言**：`VrMenuModelTest.zoomScaleRowStaysWithinItsDeclaredBounds`
与 `VrSettingsTest.defaultsAreInsideEveryDeclaredRange` 仍写死 `assertEquals(1.18f, s.zoomScale)`，
本轮把默认值改成 1.35 后这两条会直接失败；已同步改为 `1.35f`，并各加一条
`assertEquals(2.50f, VrRanges.ZOOM_MAX)` 钉住新上限。

### 17.7 待真机复测

**CI**：GitHub Actions run `42448340812`（commit `d09f94f`）两 job 全绿 ——
`build-cardboard-sdk-aar` 与 `vr-unit-tests-and-debug-apk` 均 `success`。
提交前本机 Python 精确复刻状态机跑 37 条断言全绿，`git diff --check` 无空白错误。

- 左摇杆上下（未按 A）能否顺畅选行、按住连选节奏是否合适；十字键若为 HAT 轴能否选行；
- 按 `A` 后摇杆是否只改数值、`B` 返回后是否立刻恢复选行；
- 退出：`L1+R1` 在 VR 内不再退出、手机返回键与菜单「退出 VR」两条是否都可用；
- **全屏**：`zoomScale = 1.35` 时上下黑边是否真的被顶掉；若仍有，继续往上调到 2.00~2.50；
  设置菜单打开时画面是否不裁切；驱动不走 readPixels 兜底时是否流畅。
- 若仍完全无变化，请确认 2D 设置页「画面上下填满（单纯放大）」开关是开着的，
  且「整体放大」滑块不在 `1.00x`（`screenFillMode` 会被 SharedPreferences 记住，
  旧版本手动关过的话升级后仍是 `DISTORTED`）。

---

## 18. 静态复核 + 「依然不能全屏」可诊断化（本轮续接）

> 本轮起点是上下文压缩后的续接，不是新的真机 bug 报告。用户上一句「目前还行吧，然后把你目前
> VR 相关的代码再次导出出来一次」已完成（r2 包）。本轮做的是：把最后一条未闭环反馈
> 「依然好像是不能全屏的」做源码级静态复核，并把「静默跳过」变成可查，然后重新导出包。

### 18.1 SDK 源码复核结论（重要事实，别再走回头路）

查 `sdk-reference/CardboardView.java` 的 `onDrawFrame()`（1038–1088 行）与
`onRenderStereoFrame()`（1098–1137 行），确认两件事：

- **调用顺序**：`renderer.onNewFrame()` → `onRenderStereoFrame()`（其中先绑共享 FBO 画左右眼，
  再 `cardboardViewApi.renderEyeToDisplay(...)` 做畸变合成）→ `renderer.onFinishFrame(viewport)`。
  `onFinishFrame` 在 `onDrawFrame` 里是最后一步，之后只剩 `checkGlError("onDrawFrame")`，
  然后 GLSurfaceView 才 `eglSwapBuffers`。所以 `onFinishFrame()` 里对默认 framebuffer 的
  `clear + 放大重画` **就是最终上屏画面**，不会被 SDK 后续绘制覆盖。
- **viewport 是整屏**：`viewport.setViewport(0, 0, getScreenParamsWidth(), getScreenParamsHeight())`，
  不是左右半屏的 `w/2`。因此 `drawZoomFill()` 里的 `w/h` 是物理屏幕尺寸，
  `glCopyTexSubImage2D` 拷的是整张最终合成图，`glViewport` 放大也是整屏居中。

结论：`d09f94f` 的「单纯放大」在**时机与尺寸上都成立**。真机「看起来完全没变化」更可能是
放大链路被某个 early return 静默跳过，或用户装的是 `d09f94f` 之前的 APK（本人已说明
「上一个 CI 的编译中 / 最新的 CI 还没编译完」）。不要再改 FOV / 投影 / geometry / shader /
SDK C++，也不要退回「左右眼各重画半屏矩形」的 `6cac3e3` 方向。

### 18.2 本轮实际改动（只有 2 个文件，零渲染路径改动）

`VrTheaterRenderer.kt`：

1. **修一个确定的过时文案 bug**：`drawStatus()` 原本写着
   `"L1+R1 或系统返回键：退出 VR（串流不中断）"`，但 `d09f94f` 已把 `L1+R1` 收紧为
   **只进入、不退出**（`VrSession.handle(ToggleVrMode)` 里 `if (!_enabled.value) toggle()`）。
   现改为 `"系统返回键 / 设置「退出 VR」退出 · L1+R1 只进入"`，与 README §3.2 /
   `VrSettingsSection` / `VrKeys` KDoc 一致。
2. **状态面板显示倍率**：第一张卡片第二行由
   `"曲面 %.1f  偏移 %+.1f  填充 %s"` 改为 `"曲面 %.1f  偏移 %+.1f  %s %.2fx"`，
   直接显示 `VrMenuModel.display(SCREEN_FILL_MODE)` + `s.zoomScale`。不改卡片布局，
   所以 `panelBitmap` 尺寸 / dirty 重绘链路完全不动。
3. **放大链路可诊断化**：新增 `logLastZoomSkip: String?` / `logLastZoomActive: Boolean` 与
   `noteZoomSkip(reason)` / `noteZoomActive(s,w,h)` 两个 edge-triggered 诊断；
   `onFinishFrame()` 的 `DISTORTED` 分支、`drawZoomFill()` 的 7 个 early return
   （viewport 无效 / 快照永久降级 / `zoomScale<=1.00` / 菜单打开 / `panel` 未就绪 /
   快照失败 / …）各记一条一次性 `Log.w`，成功画完记一条 `Log.i` 并清掉 skip 状态。
   `onRendererShutdown()` 重置这两个字段，GL context 重建后重新探测、重新记一次。
   健康渲染循环保持日志静默，不会刷屏。

**没有动**：`zoomScale` 默认值 1.35、`ZOOM_MAX=2.50`、三格式快照探测、readPixels 兜底、
菜单不放大策略、输入路由、设置架构、`VrSettings.DEFAULT`、任何 GL 绘制调用的顺序。
所有 JVM 单测不受影响（本轮改动都在 Android Canvas / GL 侧，纯 JVM 测不到）。

### 18.3 文档

`README-VR.md` 新增 **§3.6「装了最新 APK 却『依然不能全屏』？按这个顺序查」**，
六步清单：确认 APK 版本 → 确认 `screenFillMode` 没被 SharedPreferences 记住旧值 →
确认倍率不在 1.00x → 确认菜单关闭 → `adb logcat -s VrTheaterRenderer:V` 看
`zoom FILL active` / `zoom FILL skipped: <原因>` / `zoom snapshot: ...` →
状态面板读模式+倍率。并写入 18.1 的 SDK 调用顺序与整屏 viewport 事实，防止后人再改错方向。

### 18.4 待真机复测

- 装最新 CI 的 debug APK（**不是**上一个 CI 的），2D 设置页确认「画面上下填满」开着、
  「整体放大」≥ 1.35x，关掉菜单看上下黑边是否被顶掉；
- `adb logcat -s VrTheaterRenderer:V`：期望看到 `zoom FILL active: ...`，
  若看到 `zoom FILL skipped: ...` 直接按原因处理；
- 若 `active` 但仍有黑边，仅调 `zoomScale`（上限 2.50x），**不要改渲染代码**；
- 状态面板第二行文字是否完整不裁切；退出说明文案是否与真机一致。

### 18.5 交付物

代码提交后重新导出 `OpenNOW-VR-modules-20261108-r3.zip`（r2 会随代码变更过期）。
包内 `00_先看这个.txt` / `MANIFEST.txt` / `01_模块与设置总表.md` 同步更新提交号与 CI 号。

## 19. 第二轮另一台设备真机反馈：Select/Home 键、菜单关闭后焦点、点击屏幕重置视角

### 19.1 三条诉求

1. 「在另一台设备中测试下 Select 和 Home 键……不能够正常被在游戏中使用」；
2. 「在那台设备中测试下发现到在关闭设置页时，无法正常地将输入焦点对焦至游戏」；
3. 「点击屏幕自动的，重新修正，中心点重置视角」。

前两条**是同一个根因**，不是两个独立 bug；第三条是独立的新交互。

### 19.2 根因：VR 把未消费按键交给了带「本地菜单」分支的 dispatchKey()

`MainActivity.dispatchKeyEvent()` 在 VR enabled 时原本是：

```kotlin
if (VrSession.onKeyEvent(event)) return true
if (VrSession.enabled.value) {
    ...
    NativeStreamInputRouter.dispatchKey(event)   // ← 问题在这里
    return true
}
```

`NativeStreamInputRouter.dispatchKey()` **不是纯转发**，内部有多条本地 UI 分支：

- `KEYCODE_BUTTON_START` → `shouldTrackControllerStartHold()` → `postDelayed` 长按后
  `openMenu()` + `setStreamUiActive(true)`；
- `isStreamSystemMenuKey()` → `systemMenuHandler?.invoke()` → `openControls("input-shortcut")`
  → `setStreamUiActive(true)`；
- `isStreamControlsShortcutKey()` → 同上；
- `if (streamUiActive) return false`：之后**每一个**按键都不再发给游戏；
- `dispatchMotion()` 在 `streamUiActive` 时同样 `return false`，摇杆也断。

`OpenNowStreamSurface.kt` 的 `LaunchedEffect(streamReady, streamOverlayOpen, ...)` 把
`setStreamUiActive(streamReady && streamOverlayOpen)` 与 2D `controlsOpen` 绑定。
于是某些设备（键码映射 / Android TV profile 不同）把 `Select` / `Home` 映射到上述
「打开菜单」键码时，一次普通的游戏内按键就会：

> 打开**不可见**的 2D Quick Menu → `controlsOpen=true` → `streamUiActive=true` →
> 游戏收不到任何后续 key / motion → 因为 2D 菜单被 `setZOrderOnTop(true)` 的 VR GL
> SurfaceView 完全遮住，用户在头显里根本无法关掉它 → 表现为「输入焦点再也回不到游戏」。

这也解释了更早那条反馈「后面的 2D 原本应用内容仍会被手柄操控，Start 键或点到后面画布
可能突然弹出窗口」：`b109061` 的输入隔离只挡住了 Compose，**没有挡住 `dispatchKey()`
内部的本地菜单分支**。

另外，不少蓝牙手柄把 `Select` / `Back` 直接映射成 `KEYCODE_BACK`(4)，而
`VrSession.onKeyEvent()` 对 `KEYCODE_BACK` 是**无条件**吞掉、并在 `ACTION_UP` 执行
`setEnabled(false)`。所以那台设备上按 Select 不只是「没反应」，还会退出 VR。

### 19.3 本轮实际改动（4 个源码 / 测试文件 + 2 份文档）

`StreamInputRouting.kt`（`object NativeStreamInputRouter`）新增 3 个 public 方法：

| 方法 | 作用 |
| --- | --- |
| `dispatchVrGameKey(event)` | 直接 `client?.dispatchKey(event)`，绕过全部本地菜单 / 快捷键 / `streamUiActive` 分支 |
| `dispatchVrGameMotion(event)` | 直接 `client?.dispatchMotion(event)`，摇杆 / 鼠标不受卡住的 `streamUiActive` 影响 |
| `restoreVrGameInput(reason)` | `cancelControllerMenuHold(false)` + 若 `streamUiActive` 则 `setStreamUiActive(false)`；不注入合成 UP |

`MainActivity.kt`：

1. `dispatchKeyEvent()` 前置一条**来源判定**：VR enabled、菜单关闭、`KEYCODE_BACK`
   且 `AndroidControllerInput.isControllerEvent(source, deviceId)` 为真 → 直接
   `dispatchVrGameKey()` 并返回 true。手机系统返回键（非手柄来源）不受影响，
   仍按用户偏好保留「返回键退出 VR」。
2. VR 分支 `dispatchKey(event)` → `dispatchVrGameKey(event)`；
   `dispatchGenericMotionEvent()` 的 `dispatchMotion(event)` → `dispatchVrGameMotion(event)`。
3. `onCreate` 绑定 `VrSession.onVrMenuClosed = { restoreVrGameInput("vr-panel-closed") }`，
   并在 `VrSession.enabled.collect { active -> ... }` 里 `active==true` 时也复位一次
   （`"vr-entered"`）。
4. `dispatchTouchEvent()` 的 VR 分支在吞掉手势**之前**调用 `handleVrTapRecenter(event)`：
   记录 `ACTION_DOWN` 坐标，`ACTION_MOVE` 超过 `VR_TAP_SLOP_PX=24f` 标记取消，
   `ACTION_UP` 且 `eventTime - downTime <= VR_TAP_TIMEOUT_MS=400L`、位移在阈值内 →
   `VrSession.recenter()`；`ACTION_CANCEL` 复位。滑动 / 长按 / 取消均不触发。

`VrSession.kt`：新增 `@Volatile var onVrMenuClosed: (() -> Unit)?`；
`setMenuOpen(false)` 只在 `wasOpen && _enabled.value` 的**开→关边沿**触发一次。
`setEnabled(false)` 会先置 `_enabled=false` 再调 `setMenuOpen(false)`，所以
**正常退出 VR 不会触发**，不会干扰 2D UI 恢复。

`VrGamepadRouterTest.kt`：新增 2 个回归用例，锁定
「VR on + 菜单关闭时 SELECT / MODE 短按（含 OS repeat）必须 `PassThrough`」与
「关菜单后立刻变回普通游戏键」；`VrGamepadRouter` 本身**没有改动**。

### 19.4 刻意没动的部分

- `VrGamepadRouter` 状态机、长按 500 ms 开菜单、LB/RB 换页、A 门控、HAT 兼容：全部保持；
- `VrSession.onKeyEvent()` 对**非手柄** `KEYCODE_BACK` 的退出逻辑：保持（用户明确偏好）；
- VR 菜单打开时输入独占、2D Compose 按键 / 触摸 / 摇杆隔离：保持；
- 音量键仍走 `super.dispatchKeyEvent(event)`；
- `screenFillMode` / `zoomScale` / 三格式快照探测 / 辅助线 / 360° 背景 / IPD：全部不动；
- 设置项仍是 20 个、菜单 5 页 23 行。点击屏幕重置中心点**没有**新增可调项
  （用户未要求；若后续要加开关，走 `VrSettings` → `VrSettingSpecs` → `VrMenuModel` 链路）。

### 19.5 验证

- 本机 Python 精确复刻 `VrGamepadRouter.onKey` / `VrSession.setMenuOpen` /
  `handleVrTapRecenter`：**36 条断言全绿**（含长按仍触发、LB/RB 换页、VR 退出不触发
  回调、拖动 / 长按 / CANCEL 不 recenter）。
- `git diff --check` 无空白错误。
- GitHub Actions run `42460189808` 两 job 全绿（`build-cardboard-sdk-aar` 与
  `vr-unit-tests-and-debug-apk` 均 `success`），记录见 `README-VR.md §5.1`。

### 19.6 待真机复测（那台设备）

1. 菜单关闭时，游戏中按 `Select` / `Home`（含把 Select 映射成 BACK 的手柄）应正常生效，
   且**不会退出 VR**；
2. 长按 `START+SELECT` / `HOME/MODE` 仍能开菜单；菜单里 `SELECT` / `MODE` 仍能关菜单；
3. 打开 → 关闭 VR 设置菜单后，立刻按任意游戏键 / 推摇杆，游戏应马上收到；
   `adb logcat` 若出现 `VR input restored to game: streamUiActive=false` 说明复位生效；
4. VR 期间短按屏幕 → 视角立刻摆正；拖动 / 长按不应改变视角；
5. 手机系统返回键仍能退出 VR（不被来源判定误伤）。

### 19.7 交付物状态

以本轮提交为基准重新导出 `OpenNOW-VR-modules-20261108-r4.zip`（第 4 次导出）：
覆盖本轮 6 个变动文件，并**首次补入 `src/integration/StreamInputRouting.kt`**
（本轮新增 VR 游戏直通通道的所在文件；r2/r3 打包时它还不是 VR 相关代码，故遗漏）。
包内源码 / 配置 / 文档共 42 个文件、设置项仍为 20 个、菜单 5 页 23 行。

---

## 20. 2026-11-08 摄像头透视 / Passthrough + 三种布局 + 双扳机快捷键

### 20.1 用户诉求演进（按原话顺序）

1. `添加一个功能透视` —— 语义有歧义，先用一次选项确认，用户选「摄像头透视（Passthrough / 看穿）」。
2. `说明一下由于不是360°camera，你可以改成固定窗口在中心，或者有什么更好的方式你可以试试，要自己想哦。`
   —— 明确否定「把单目画面铺满整个 VR 背景」，要求自己给出更好的方案。
3. `能不能使用4：3的比例` —— 窗口默认比例从 16:9 改为 4:3，并做成 `4:3 / 16:9` 可调项。
4. `我想问一下你窗口是怎么打开，然后呢，是怎样的一个窗口吗？具体的画面告诉我一下。`
5. `可以弄个便捷的快捷键开启嘛，使用双扳机按键长按开启，把游戏画面放到后面后台，然后呢摄像头换成摄像头画面。当然啦，你可以改成摄像头，是一个窗口，然后旁边放着呃游戏窗口双窗口模式，这样子也可以。你目前这个模式也可以，做一下可切换。这三种模式做一下。`
6. `快捷键只负责开启和关闭，具体的样式调整放在设置里面调就好。` —— **重要收敛**：
   原设计误把 `L2+R2` 做成「循环三种样式」，改为只翻转 `passthroughEnabled` 一个总开关，
   三种布局纯设置项。

### 20.2 设计取舍（为什么这样做）

- **不伪造 360°**：手机摄像头只有一个约 60–80° 的单目视角。把单帧贴到球面 / 铺满整个背景
  等于假装存在不存在的方向信息，转头时也无法给出新内容，且画面被拉伸变形。排除。
- **head-locked 中心窗口**：手机与摄像头在纸盒里刚性连接，用户转头 = 摄像头一起转，
  所以画面**必须始终在正前方**。MVP 用 `eye.getPerspective() * eye.getEyeFromHead()`
  但**去掉头部旋转**（`headLockedMvpMatrix`），左右眼因此仍有正确视差，
  读起来是「正前方开了一扇窗」而不是贴在眼球上的 2D 贴纸。`ipdScale != 1.0` 时同样对
  `headLockedMvpMatrix` 应用 `VrMatrix4.applyIpdShiftInto()`，保持与幕布一致。
- **窗口在幕布之下**：`drawEyeScene()` 先画 `drawPassthroughWindow()`，再画串流 quad；
  游戏画面永远不会透视挡住。窗口外保持纯黑 clear。
- **4:3 原生比例**：多数手机传感器原生 4:3，16:9 是中心裁切。`SurfaceTexture.setDefaultBufferSize()`
  （1280×960 / 1280×720）与窗口 quad 的 aspect **同源**，切换比例会重启 Camera2 会话而不是拉伸。
- **三种布局**都对「摄像头 quad」和「串流 quad 的 model 变换」分别处理，两种画面各自保持
  比例、绝不拉伸；串流 quad 用 `gameModelMatrix`（纯 scale + translate）经 MVP 变换，
  不重建几何，因此曲率 / 顶点数 / 面板菜单 quad 全都不受影响。
- **透明度用 `uBrightness`**：`VIDEO_FRAGMENT_SHADER` 输出 `c.rgb * uBrightness`、
  `alpha = 1.0`，在 SDK 的纯黑 clear 上等价于 `mix(black, camera, opacity)`。
  不改 shader、不开 `GL_BLEND`、不改 SDK GL 状态。
- **透视与 360° 背景互斥**：`drawPassthroughWindow()` 返回 true 时 `drawBackground()` 整段跳过，
  两个背景层不会双重合成。
- **失败永远不致命**：第二个 `GL_TEXTURE_EXTERNAL_OES` 创建包在 `try/catch (RuntimeException)`
  里；无权限 / 无镜头 / 摄像头忙 / `updateTexImage()` 抛错 / 还没有预览帧，统统不画窗口，
  回退原来的纯黑 / 360° 背景。VR 不会黑屏或崩溃。

### 20.3 本轮实际改动（15 个源码 / 测试文件 + 2 份文档）

| 文件 | 改动 |
| --- | --- |
| `VrSettings.kt` | 新增 `VrPassthroughFacing` / `VrPassthroughAspect` / `VrPassthroughMode` 三个枚举、6 个透视字段、`VrRanges` 的 `PASSTHROUGH_OPACITY_*` / `PASSTHROUGH_WINDOW_*`，`sanitized()` 全量钳制 |
| `VrSettingsStore.kt` | 6 个新 key 的 SharedPreferences 读写（枚举用 `runCatching { valueOf(...) }` 容错，旧数据自动回默认） |
| `VrMenuModel.kt` | 环境页 `18..25`、快捷页 `26..28`，菜单 23 → **29 行**；新增 6 个 `PASSTHROUGH_*` 行（TOGGLE / ENUM / RANGE），`rawValue` / `display` / `activate` / `toggle` 全覆盖；三模式由 A 循环 |
| `VrSettingSpec.kt` | 2D 原生数字滑块 11 → **13 个**：`passthroughOpacity`、`passthroughWindowScale` |
| `VrSettingsSection.kt` | CAMERA 权限说明、透视总开关、前 / 后置镜头、4:3 / 16:9、三布局切换按钮与三段说明文案 |
| `VrPassthroughCamera.kt`（新增，303 行） | Camera2 + 私有 `HandlerThread`；`start(surface, facing, previewW, previewH)` 幂等、`stop()` / `release()`、`pickCameraId()` 前 / 后置优先再回退任意镜头、`previewRotationDeg()` 按 sensor + display rotation 计算、`onPreviewOrientation(rotationDeg, mirror)` 回调；权限检查用 `ContextCompat.checkSelfPermission` |
| `VrTheaterRenderer.kt` | 第二个 OES 纹理 + 独立 `SurfaceTexture` / `Surface`；`ptStMatrix` / `ptFrameAvailable` / `ptHasFrame` / `ptUnavailable`；`headLockedMvpMatrix`；`syncPassthrough()` / `configurePassthrough()` / `rebuildPassthroughQuad()` / `drawPassthroughWindow()`；三布局 `gameModelMatrix`；`onRendererShutdown()` 先停相机再释放 GL 对象；常量补齐 |
| `VrKeys.kt` | `AXIS_LTRIGGER/RTRIGGER/GAS/BRAKE`、`TRIGGER_PRESS_THRESHOLD=0.5f`、`PASSTHROUGH_TOGGLE_DEBOUNCE_MS=800L`、`PASSTHROUGH_TOGGLE_COMBO`、`PASSTHROUGH_LONG_PRESS_MS=500L` |
| `VrGamepadRouter.kt` | `VrCommand.TogglePassthrough`；keycode 版 `L2+R2` 长按 0→1 边沿 + latch（松开任一 trigger 才清），与 `L1+R1` 完全独立；新增纯函数 `passthroughTriggersHeld(l2, r2)` 供 JVM 单测锁定模拟轴阈值 |
| `VrSession.kt` | `requestCameraPermission` 回调；`updateSettings()` 在 OFF→ON 边沿统一申请权限；`onTriggerEvent(l2, r2)` 用 lazy `Handler` 的 500 ms 延时时钟兑现模拟扳机长按，`triggerHoldConsumed` latch 保证一次长按只翻转一次，`handle(TogglePassthrough)` 带 800 ms 去抖 |
| `MainActivity.kt` | `vrCameraPermissionLauncher` 统一 CAMERA 权限；`onCreate` 注册 `requestCameraPermission`；`dispatchGenericMotionEvent()` 读 4 个扳机轴取 max 后调 `onTriggerEvent()`（**不消费**事件，游戏照常收到扳机）；`onDestroy` 清空两个 Activity-bound 回调 |
| 4 个测试文件 | 菜单 29 行 / 环境页 8 行、三模式循环、透视默认值与边界、2 个新滑块、`L2+R2` 长按一次 / 短按不触发 / 菜单内外 inert / 不与 L1+R1 冲突、模拟轴阈值与 AOSP 常量 |

刻意保持纯 JVM 的 `VrKeys` / `VrGamepadRouter` / `VrSettings` / `VrMenuModel` / `VrSettingSpec`
不引入任何 `android.*` 依赖；`VrSession` 新增的 `Handler` 用 `by lazy`，否则
`VrMenuModelTest` 经 `VrSession.recenter()` 触发类加载时会在 JVM 单测里遇到
`Looper.getMainLooper() not mocked`。

### 20.4 相机 / 渲染链路（GL 线程所有权）

```
VrModeHost.update → VrTheaterView.applySettings → VrTheaterRenderer.applySettings
    → 只置 ptConfigDirty = true（相机启停、setDefaultBufferSize、quad 重建都必须在 GL 线程）

onSurfaceCreated: setupVideoTexture() → setupPassthroughTexture() → rebuildPassthroughQuad()
onNewFrame:      syncPassthrough()
                   ├─ ptConfigDirty → configurePassthrough()（设置预览尺寸 + cam.start / stop）
                   │                  rebuildPassthroughQuad()（camera quad + gameModelMatrix + UV 旋转）
                   └─ ptFrameAvailable → updateTexImage() + getTransformMatrix(ptStMatrix)
onDrawEye:       计算 headLockedMvpMatrix = perspective * eyeFromHead（无 head rotation）
drawEyeScene:    drawPassthroughWindow(headLockedMvp) ?: drawBackground(mvp)
                   → 串流 quad 用 (layoutActive ? headLockedMvp * gameModelMatrix : mvp)
onRendererShutdown: ptCamera.stop() → release() → releasePassthroughTexture()
```

`onPreviewOrientation()` 由相机线程回调，只写 `@Volatile` 的 `ptRotationDeg` / `ptMirror`
并置 `ptConfigDirty`；下一帧 GL 线程把 sensor / display 旋转与前置镜像**烘焙进窗口 UV**
（纯 CPU buffer 操作），不改 shader。

### 20.5 输入链路（两条路径 + 去抖）

- **keycode 路径**：发 `KEYCODE_BUTTON_L2/R2` 的手柄 → `VrGamepadRouter.onKey()`，
  沿用已验证可用的 OS key-repeat 作为时钟，组合 0→1 边沿起表、`≥500 ms` 发一次
  `TogglePassthrough`，`passthroughHoldFired` latch 直到松开任一 trigger 才清。
- **模拟轴路径（真机主路径）**：主流蓝牙 / Xbox / PlayStation 手柄把 LT/RT 报成
  `AXIS_LTRIGGER/RTRIGGER`（或旧式 `AXIS_BRAKE/GAS`）而**根本不发 keycode**。
  `MainActivity.dispatchGenericMotionEvent()` 取两轴 max → `VrSession.onTriggerEvent()`，
  轴 ≥ `0.5` 视为按下；因为扳机到底后可能停止发 MotionEvent，用 main-looper
  `Handler.postDelayed(500ms)` 作为时钟，而非等下一个输入事件。
- **去抖**：同一手柄若同时发 keycode 和轴，两条路径会各翻转一次互相抵消。
  `handle(TogglePassthrough)` 用 `SystemClock.uptimeMillis()` 做 800 ms 去抖，
  保证一个物理长按只产生一次可见翻转。菜单打开 / 离开 VR / 松开扳机都会取消 pending 时钟。

### 20.6 权限集中化

`VrSession.updateSettings()` 在 `passthroughEnabled` 的 **OFF→ON 边沿**调用
`requestCameraPermission?.invoke()`。回调由 `MainActivity.onCreate` 注册，用
`registerForActivityResult(ActivityResultContracts.RequestPermission())` 统一请求
`Manifest.permission.CAMERA`；拒绝时 `updateSettings(copy(passthroughEnabled = false))`
把开关还原，renderer 回退黑底，且不会重新进入 OFF→ON 边沿形成重试循环。
`AndroidManifest.xml` 原本已有 `<uses-permission android:name="android.permission.CAMERA" />`
且 `android.hardware.camera` / `.autofocus` 都是 `required="false"`，minSdk 24，Camera2 可用。

### 20.7 验证

- 本机 Python 精确复刻 `VrGamepadRouter.onKey` / `VrMenuModel` 三模式循环 /
  `passthroughTriggersHeld` / 设置钳制：**42 条断言全绿**。
- `git diff --check` 无空白错误。
- 符号级复核：`VrMatrix4.identityInto` / `VrScreenGeometry.halfHeight` / `ProgramHandles.stMatrix` /
  `VrPassthroughCamera.start()` 签名 / `Listener` 两方法 / `VrTheaterView.applySettings()` /
  `VrModeHost.update` / CAMERA 权限与 uses-feature 全部匹配，无必红编译点。
- **GitHub Actions run `42560700441`（commit `2008d48`）两 job 全绿**：
  `build-cardboard-sdk-aar` success、`vr-unit-tests-and-debug-apk` success
  （Kotlin 编译 + JVM 单测 + debug APK 出包 + JNI `.so` 守卫）。
  记录见 `README-VR.md §5.1` 本轮新增行。

### 20.8 待真机复测（CI 抓不到）

1. 三个入口开启透视都能弹出且只弹一次 CAMERA 权限；拒绝后开关自动回到关、画面是黑底不是黑屏；
2. 真机 `L2+R2` 长按能否开 / 关（**重点**：先确认手柄发的是 keycode 还是模拟轴，
   `adb logcat` 看 `VrPassthroughCamera` 是否 `preview started`）；短按不应触发；
3. 窗口是否 head-locked 居中、左右眼视差是否自然；4:3 / 16:9 切换是否不拉伸；
4. 三种布局的窗口大小 / 位置是否合适（`OVERLAY` 游戏是否确实盖在上面、
   `CAMERA_MAIN` 游戏小窗是否在右下角、`SIDE_BY_SIDE` 两个窗口是否不重叠）；
5. 传感器旋转方向：`previewRotationDeg()` + UV 烘焙在某些机型上可能需要 90/180/270 微调；
   另需确认 `SurfaceTexture.getTransformMatrix()` 会不会与 UV 烘焙造成双重旋转；
6. 摄像头被串流录制占用时的降级、退出 VR 后相机是否及时释放、反复进出 VR 是否泄漏。

### 20.9 已知技术债

- `previewRotationDeg()` 用的是标准 Camera2 公式 `(sensor - displayDeg + 360) % 360`，
  但不同 OEM 对 preview buffer 是否自动旋转处理不一致，且 `getTransformMatrix()` 可能已含旋转；
  真机若方向 / 镜像不对，需要按设备微调（目前没有做成可调项）。
- `triggerHoldConsumed` 依赖收到「扳机松开」的 MotionEvent 来清除；若某手柄松开时只发
  keycode UP 而完全不发 axis 事件，需要额外在 `dispatchKeyEvent()` 的 L2/R2 ACTION_UP 上
  补一次复位（当前主流模拟轴手柄都会发松开事件，风险较低）。
- 三种布局下串流 quad 用的是 head-locked MVP（与摄像头窗口一致），所以游戏画面在这些模式下
  也固定在正前方；`OVERLAY` 模式仍用随头转的原 MVP。这是当前的设计选择，不是 bug。

> **以上 §20.8 / §20.9 的相机方向、拉伸、相对滑动、帧率相关条目已由 §21 修复。**
> 本节保留作为「当时为什么那样写」的历史记录，不要再按它去改代码。

---

## 21. 2026-11-09 真机反馈：摄像头方向错 / 直接拉伸 / 不协调 / 分辨率与帧率

> **⚠️ 后验证伪（务必先读 §22）**：本章 §21.3 里「AUTO 基准取传感器安装角」以及
> 「90°/270° 时窗口比例取倒数」两条结论，在 v0.6.0 装到真机后被用户判定**仍然是错的**
> —— 正是这两条把窗口变成了竖屏 9:16。§21 保留作为历史记录，**技术实现以 §22 为准**。
> 分辨率 / 帧率档位、窗口深度与「跟随幕布」默认这两部分结论仍然有效。

### 21.1 用户诉求（原话）

> 「你的问题有点大，摄像头方向是错的，然后这个你也是直接拉伸，然后而且呢透视你这边各种的做的
> 都还是有问题，其次就是呢你能不能加下分辨率以及帧率选项。虽然说我这个手机帧率只有 30 帧，
> 但你是为了其他机型，呃也可以做一下，分辨率你可以做 360P、480P 以及 720P 三种。当然也可以
> 加一下 1080P，我目前主要用肯定是 480P，然后呢你目前摄像头方向是错的，以及呢有很大的
> 协呃不协调感，你自己调一下，然后我发你几张图片，一张是我现实的图片来去告诉你正确的呃方向。」

附 4 张真机截图 / 现实照片。要求「自己调一下」，不反问。

### 21.2 图片对比结论

- 现实照片：右侧窗格反光 / 灯带是**竖直矩形**，笔记本屏幕在上方偏左，键盘在下方偏右，相机俯视桌面。
- VR 透视小窗口 / 全屏背景：窗格反光变成**水平长条**，地砖缝隙方向跟着整体转了约 **90°**。
- 全屏透视背景里窗格被压扁 / 拉长，地砖透视不协调 → 除旋转外还存在**宽高比错配**。

### 21.3 四个根因（源码级）

1. **方向错 — 用错了公式。** `VrPassthroughCamera.previewRotationDeg()` 旧实现是
   `(SENSOR_ORIENTATION - displayRotation + 360) % 360`，这是 **JPEG_ORIENTATION 拍照**公式。
   Camera2 的**预览缓冲不随屏幕旋转**，`SurfaceTexture.getTransformMatrix()` 也只含垂直翻转 /
   裁剪、不含旋转。VR 强制横屏（`displayDeg = 90`）时，常见传感器角 90° 算出 **0°**，
   于是传感器安装角完全没补偿，画面横躺 90°。
2. **直接拉伸 — 假定尺寸。** 旧 `configurePassthrough()` 写死
   `setDefaultBufferSize(1280×960 / 1280×720)` 并按这个 4:3 / 16:9 比例建 quad。Camera2 只会
   输出 `StreamConfigurationMap` 里实际存在的尺寸，真实比例经常不同；另外 90° / 270° 旋转后
   有效宽高应取倒数，旧代码没取，等于按 `aspect² ≈ 1.78` 倍挤压画面。
3. **不协调感 — 两层 MVP 不一致。** `drawEyeScene()` 里摄像头窗口固定用
   `headLockedMvpMatrix`（eye 矩阵、**无头部旋转**），而默认 `OVERLAY` 的串流幕布用 `mvpMatrix`
   （**含头部旋转**），转头时两层相对滑动。再叠加 `PASSTHROUGH_WINDOW_DEPTH_M = 0.8f`（窗口挂在
   幕布后 0.8 m）与 `PASSTHROUGH_WINDOW_VS_SCREEN = 2.2f`（窗口是幕布两倍多高），
   形成一大圈晃动的实时环境噪声。
4. **缺分辨率 / 帧率。** r5 的相机参数是硬编码的，无任何档位。

### 21.4 本轮实际改动

| 文件 | 改动 |
| --- | --- |
| `VrSettings.kt` | 新增 `VrPassthroughResolution{P360,P480,P720,P1080}`、`VrPassthroughFrameRate{FPS_15,FPS_30,FPS_60}`、`VrPassthroughRotation{AUTO,R0,R90,R180,R270}` 三个枚举；新增 `passthroughResolution / passthroughFrameRate / passthroughRotation / passthroughMirror / passthroughHeadLocked` 五个字段并纳入 `sanitized()`。设置项 26 → **31**（帧率在用户追加要求后由 2 档扩为 3 档，见 §21.10） |
| `VrSettingsStore.kt` | 5 个新 key 的读写 + 默认值回退，全部走 `runCatching { valueOf(...) }` |
| `VrMenuModel.kt` | 5 个新行；菜单 29 → **34 行**，环境页 `18..30`（13 行）、快捷页 `31..33`；`rawValue / display / activate / toggle` 全链路覆盖，`display()` 仍是穷尽 when |
| `VrSettingSpec.kt` | 未改（新字段是枚举 / 开关，不是数字滑块） |
| `VrSettingsSection.kt` | 2D 设置页新增 3 个循环按钮（分辨率 / 帧率 / 旋转）+ 2 个开关（额外镜像 / 钉在眼前），含真机排障文案；更新透视总说明 |
| `VrPassthroughCamera.kt` | `Listener.onPreviewOrientation` → `onPreviewConfig(width,height,fps,autoRotationDeg,autoMirror)`；新增 `requestedWidth / choosePreviewSize / chooseFpsRange`；`start()` 参数改为 `(surfaceTexture, surface, facing, resolution, aspect, frameRate)`；在 `onOpened` 里选真实尺寸、`setDefaultBufferSize`、回报配置；`CONTROL_AE_TARGET_FPS_RANGE` 写入 repeating request；`previewRotationDeg()` 改为直接返回传感器安装角 |
| `VrTheaterRenderer.kt` | 新增 `ptAutoRotationDeg / ptAutoMirror / ptPreviewW / ptPreviewH / ptPreviewFps`；`effectivePassthroughRotation()`；quad 按**真实预览宽高**构建、90°/270° 取倒数；`drawEyeScene()` 让窗口与幕布共用 `streamBaseMvp`（可 `passthroughHeadLocked` 覆盖）；深度 `0.8 → 0.12`、窗口比例 `2.2 → 1.45`；状态面板显示实际宽高 / 帧率 / 旋转 / 镜像 / 窗口模式 |
| 测试 | `VrMenuModelTest` 34 行 / 环境 13 行 + 2 个新用例；`VrSettingsTest` 5 个新默认值 + 枚举档位 / sanitize 用例 |

### 21.5 相机 / 渲染链路

```
VrSettings (档位)
  -> configurePassthrough()  cam.start(surfaceTexture, surface, facing, resolution, aspect, fps)
  -> VrPassthroughCamera.onOpened (camera thread)
       getCameraCharacteristics -> SCALER_STREAM_CONFIGURATION_MAP.getOutputSizes(SurfaceTexture)
       choosePreviewSize(): 优先比例误差 <= 0.03 且短边最接近档位；否则取短边最接近的真实尺寸
       chooseFpsRange():    upper <= target 中取 upper 最大、再取 lower 最大（优先 [30,30] 而非 [15,30]）
       surfaceTexture.setDefaultBufferSize(realW, realH)   <- 会话创建之前，避免 getTransformMatrix 带裁剪
       Listener.onPreviewConfig(realW, realH, effectiveFps, sensorAngle, frontMirror)
       repeating request 写 CONTROL_AE_TARGET_FPS_RANGE
  -> renderer (GL thread, volatile 字段)
       syncPassthrough() 消费 ptConfigDirty -> rebuildPassthroughQuad()
       effectiveRotation = AUTO ? sensorAngle : manual
       cameraAspect = effectiveRotation in {90,270} ? 1/(realW/realH) : realW/realH
       UV 烘焙 effectiveRotation + (autoMirror XOR userMirror)
  -> drawPassthroughWindow(cameraMvp)  cameraMvp = headLocked ? headLockedMvp : streamBaseMvp
```

分辨率档位按**短边**命名，是**目标值不是硬编码缓冲**：设备没有精确档时选最接近的真实尺寸，
状态面板显示真实值，因此任何档位都不会拉伸。`chooseFpsRange()` 对「同类 upper 取 lower 更大」
是本轮验证脚本发现并修掉的隐患：否则 30 帧档会选中 `[15,30]`，低光下掉到 15 fps。

### 21.6 「不协调感」的收敛（设计取舍）

推翻了 §20.2 里「head-locked 才物理正确」的判断：**真机证明**它让窗口与幕布相对滑动。
现在默认 `passthroughHeadLocked = false`，窗口与幕布共用同一基础 MVP，转头零相对运动，
观感是「游戏幕布后面挂了一块实时画面板」。旧行为保留为开关，没有删除。
`CAMERA_MAIN / SIDE_BY_SIDE` 两种窗口布局本来就用 eye-only 基础 MVP，改后两层仍然一致。
相机失败降级时 `layoutActive` 仍受 `passthroughDrawn` 门控，游戏画面回到原尺寸不缩成小窗。

### 21.7 验证

- 本机 Python 复刻菜单分页 / 枚举循环 / 设置默认值 / Camera2 尺寸与帧率选择 / 传感器旋转基准 /
  quad 比例倒数 / MVP 选择 / UV 旋转双射 / 镜像 XOR：**78 条断言全绿**。
- 静态检查：34 个菜单 id 全部出现在 `items`，`display()` 无 `else` 且覆盖 34/34；
  31 个设置字段全部纳入 `sanitized()` 的 copy（3 个历史非数值字段依赖 data class copy 保留）；
  `git diff --check` 无空白错误。
- 本机无 Android SDK / Gradle / JDK 17，Kotlin 编译与 JVM 单测只能靠 GitHub Actions。

### 21.8 待真机复测（CI 抓不到）

1. 装本轮 APK，开透视后看状态面板：**实际宽高 / 帧率 / 生效旋转角 / 镜像 / 窗口模式**；
2. 对照现实照片，AUTO 是否正立；不正立就在「环境」页依次拨 `0° / 90° / 180° / 270°`，
   选正立那档（这正是把旋转做成可调项的目的）；
3. 竖直线条（窗格 / 灯带）是否仍是竖直且不被压扁；窗格长宽比与现实是否一致；
4. 转头时窗口与游戏幕布是否一起移动、无相对滑动；`passthroughHeadLocked` 开 / 关对比；
5. 依次切 `360P / 480P / 720P / 1080P`，确认状态面板实际尺寸变化、无拉伸、无黑屏；
6. `15 / 30 / 60` 三帧档切换：30 帧手机选 60 应显示实际 30 而不是黑屏或掉帧；选 15 应显示
   实际 15（或设备支持的最近稳定区间），确认低帧档确实更省电、画面仍能看清周围；
7. 前后置摄像头、额外镜像、三种布局、相机被占用降级、反复进出 VR 是否泄漏。

### 21.9 §20.9 技术债处置结论

- 「`previewRotationDeg()` 公式 + 未做成可调项」→ **已修**：改为传感器安装角基准并新增
  `VrPassthroughRotation` 手动覆盖。
- 「`getTransformMatrix()` 可能与 UV 双重旋转」→ **已定位**：`setDefaultBufferSize()` 现在用
  真实预览尺寸且在会话创建前设置，Camera2 生产者与 GL 纹理尺寸一致，stMatrix 不含裁剪缩放，
  只含垂直翻转，与 UV 旋转可组合、不冲突。
- 「三种布局用 head-locked、`OVERLAY` 用随头转」→ **不再是设计选择**：默认统一为幕布的基础
  MVP，只有显式 `passthroughHeadLocked = true` 才回退旧行为。
- `triggerHoldConsumed` 的松开事件依赖仍是遗留项，本轮未动。

### 21.10 追加要求：15 帧档（同一轮内）

用户在实现过程中追加「顺便加15帧的模式」。因 CI 刚被 `38378f6` 触发，先尝试取消该 run
（API 返回 409，说明已进入不可取消状态；它跑完只作为改动前基线，不作为交付依据），
随后把帧率从 2 档扩为 3 档：

- `VrPassthroughFrameRate` → `{ FPS_15(15), FPS_30(30), FPS_60(60) }`，按低→高排序，
  **默认仍是 `FPS_30`**，持久化 key `passthroughFrameRate` 不变、旧值 `FPS_30` / `FPS_60`
  仍能被 `valueOf` 正确读回。
- `VrMenuModel`：`rawValue` 改为按 `values().size - 1` 归一化；`display` 加「15 帧（省电）」；
  `toggle` 从二值 if/else 改为通用 `values()` 循环（30 → 60 → 15 → 30）。
- `VrSettingsSection`：2D 按钮同样改通用循环，说明文案补 15 帧用途与「状态面板显示的是实际
  生效帧率，不是所选档位」。
- `VrPassthroughCamera.chooseFpsRange()` 无需改动：`target = 15` 时会在
  `CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES` 中选 `upper <= 15` 且 `lower` 最大的区间，
  自然支持低帧档；达不到时回落到最慢稳定区间并在状态面板回报实际值。
- 测试：`VrMenuModelTest` 改为 3 档循环断言（含最低档不是死路）、`cycleFps` helper；
  `VrSettingsTest` 加 `FPS_15.fps == 15`、枚举 3 项、15 帧 survives sanitize。
- 文档：README §3.8 帧率行 / §4 设置行 / §3.8 真机修复第 4 条，HANDOFF §21.4 / §21.8。

帧率档位数量变化不影响设置项总数（仍是 31 个）与菜单行数（仍是 34 行）。

### 21.11 CI 三次运行与最终绿灯（如实记录）

r6 代码（`38378f6` + `9401ef0`）**连续两次 CI 都不是被编译器验证的**，这一点必须写清楚，
否则「CI 红过 → 又绿了」容易被误读成「修了某个编译错误」。

| run | commit | 结论 | 实际发生了什么 |
| --- | --- | --- | --- |
| `42570810255` | `38378f6` | 失败 | AAR job 下载 `gradle-9.8.0-bin.zip` 触发 `java.io.IOException: ... failed: timeout (10000ms)` → `SocketTimeoutException: Read timed out`；app job 被 `needs: cardboard-aar` 跳过 |
| `42571202478` | `9401ef0` | 失败 | 这次 AAR job 全绿；app job 下载 `gradle-9.7.1-bin.zip` 同样 10 秒超时；无测试报告产出 |
| `42572527294` | `e00287a` | **成功** | r6 业务代码首次完整编译 + 单测 + 出 APK |

根因是 `third_party/cardboard/gradle/wrapper/gradle-wrapper.properties` 里写死的
`networkTimeout=10000` / `retries=0`，而 `android/gradle/wrapper/gradle-wrapper.properties`
连这两项都没有（用 wrapper 默认 10 秒）。GitHub runner 到 `services.gradle.org` 偶发抖动时，
下载一超过 10 秒整个 job 就红，与 Kotlin / Gradle 构建脚本 / 依赖解析全部无关。

`e00287a` 的加固（**3 个文件，不含任何业务代码改动**）：

- 两个 wrapper 均设 `networkTimeout=120000`、`retries=5`、`retryBackOffMs=2000`。
- `.github/workflows/vr-build.yml` 两个 job 各新增
  `Warm the Gradle distribution (retry transient download timeouts)` 步骤：
  先带 5 次重试跑 `./gradlew --version`，把发行包缓存好，再执行真正的 assemble / test。
  这样即使 wrapper 版本较老不认新属性，也能靠步骤级重试兜住。

`42572527294` 最终结论（GitHub API + job 日志双查）：

- `build-cardboard-sdk-aar` **success**、`vr-unit-tests-and-debug-apk` **success**；
  run `conclusion=success`，`head_sha=e00287a72411ca8ca1b2f39a2e75572a85f00b84`。
- `Warm the Gradle distribution` 两个 job 都 success，说明预热确实生效。
- `VR JVM test suites: 7 | tests: 125 | failures: 0 | errors: 0 | skipped: 0`，
  `BUILD SUCCESSFUL in 9m 11s`。
- artifacts：`opennow-vr-debug-arm64-v8a` 47603818 B、`cardboard-sdk-aar` 385622 B、
  `vr-unit-test-report` 27648 B；AAR / APK 两道 `libcardboard_sdk_jni.so` 守卫均 success。

**CI 仍证明不了的部分不变**：AUTO 旋转在目标机型是否正立、真实预览尺寸 / 帧率、
竖直线条是否不再压扁、转头是否零相对滑动、15 帧档观感、三种布局的窗口位置、
L2/R2 在该手柄上是 keycode 还是模拟轴、上下黑边是否真的顶满。清单见 §21.8 与
`README-VR.md §3.8`。

---

## 22. 2026-11-09 第三轮真机反馈：竖屏 9:16 / 拉伸 / 设置页溢出且不能下滚

### 22.1 用户诉求（原话）

> 「没蒙住啊，依然是有错误的，依然是**被强制拉伸成了竖屏的 9:16 比例**，然后还有就是你这边呢
> 竟然**设置项是没有做到考虑未来越来越多的设置项**，所以说你这边呢，**设置选项超出了那个整个
> 设置页面的内容**，然后你又**没有做可以向下滑动什么的**，导致不能够向下拉到下面的设置项，
> 导致**看不到下面的设置项**，你可以自己看一下。」

用户同时附了 2 张截图：`clipboard-2026-11-09T08-23-32-656Z-c41355a0.jpg`（环境页 04/05）
与 `clipboard-2026-11-09T08-16-10-700Z-6ab54de7.jpg`（主界面）。

### 22.2 截图判读（不要靠猜）

- **环境页 04/05** 从上到下能数出：`360° 背景图 / 背景亮度 / 摄像头透视 / 透视摄像头 /
  透视可见度 / 透视窗口大小 / 透视窗口比例 / 透视布局 / 透视分辨率 / 透视帧率 …`，
  第 11 行（`透视钉在眼前`）已经**被面板底边裁掉**，右下角没有任何滚动条或计数。
  这与用户说的「超出页面 / 不能向下拉」完全一致。
- 面板是一张固定 **768×432** 的 Canvas 位图，行从 `y=154` 起、`27 px` 一行：
  `154 + 13*27 = 505 > 432`，**13 行必然有 3 行画到位图外面**。旧代码 `pageItems.forEachIndexed`
 一视同仁全画，既没裁剪也没滚动，所以超出的行直接消失。
- 菜单后面露出的摄像头画面是横向条带（被菜单挡住中段），但用户明确报告在别的布局 /
  视角下看到的是「竖屏 9:16」。**以用户描述为准**，并用源码去解释，而不是靠这张被遮挡的图反推。

### 22.3 两个根因（源码级）

**A. 竖屏 9:16 = r6 的 AUTO=90 与「旋转取倒数」叠加**

`38378f6`（r6）把 `VrPassthroughCamera.previewRotationDeg()` 改成直接返回
`CameraCharacteristics.SENSOR_ORIENTATION`（多数机型 **90**），注释理由是「预览缓冲不随屏幕
旋转，传感器安装角就是基准」。这判断错在：`SENSOR_ORIENTATION` 描述的是传感器相对设备
**自然（竖屏）方向**的安装角，不是「还欠着的旋转」。VR 强制横屏时，Camera2 输出的预览缓冲
本来就是正立的横向画面，额外再转 90° 才是把竖直窗格放倒成水平长条的原因 —— v0.5.0 用
`JPEG_ORIENTATION` 公式、v0.6.0 用裸传感器角，两版殊途同归都多转了 90°。

更致命的是 r6 的 `rebuildPassthroughQuad()`：

```kotlin
val cameraAspect = (if (effectiveRotation == 90 || effectiveRotation == 270) {
    1f / previewAspect          // 16:9 -> 9:16
} else previewAspect).coerceIn(0.25f, 8f)
```

AUTO 默认算出 90°，于是 `16:9 → 9:16`，`camHalfW = camHalfH * 0.5625` 直接造出一扇
**竖屏窗口**；画面再被硬塞进这个错误比例的 quad，就是用户说的「强制拉伸成竖屏 9:16」。
对 4:3 预览同理会变成 `3:4` 竖屏。**这不是某个机型的偶发，是默认路径必现。**

**B. 设置页溢出 = 固定面板位图 + 无条件全量绘制**

`drawMenu()` 里 `pageItems.forEachIndexed { row, item -> ... top = 154f + row * 27f }`，
环境页从 r6 起涨到 **13 行**，但 `PANEL_H = 432` 从未变过，也没有滚动状态。
这不是「设置项太多」而是「渲染层假设了行数 ≤ 10」，用户指出的「没有考虑未来越来越多的
设置项」正是这个隐含假设。

### 22.4 本轮实际改动（9 个文件：6 改 + 2 新 + 1 测试改）

| 文件 | 改动 |
| --- | --- |
| **`VrPassthroughGeometry.kt`（新增，58 行）** | 纯 Kotlin 几何规则，**无 Android / OpenGL 依赖**，可 JVM 单测：`windowAspect()` / `previewAspect()` / `contentAspect()` / `centreCrop()` / `cropUv()` |
| **`VrPassthroughGeometryTest.kt`（新增）** | 6 个用例锁定「窗口恒横向」「预览尺寸归一化到横向」「AUTO 零裁切」「手动 90° 仍横向且裁切」「4:3→16:9 对称中心裁切」「cropUv 恒在 [0,1]」 |
| `VrTheaterRenderer.kt` | `ptAutoRotationDeg` 默认 `90 → 0`；`rebuildPassthroughQuad()` 窗口比例改为 `VrPassthroughGeometry.windowAspect()`（恒横向、与旋转解耦），真实预览比例只进 `centreCrop()`；UV 生成在旋转 / 镜像之后对 `su/sv` 施加 `cropUv()`；删掉 `PASSTHROUGH_ASPECT_4_3 / _16_9` 两个硬编码常量；**菜单改为只画 `visibleWindowFor()` 的可见窗口 + 右侧滚动条 + 行数计数**；`MENU_ROWS_PER_COLUMN` 删除（已无引用）；状态面板补「窗口 4:3 / 16:9」 |
| `VrPassthroughCamera.kt` | `previewRotationDeg(cameraId)` → `autoRotationDeg(): Int = 0`（KDoc 完整记录两版误加 90° 的历史）；`choosePreviewSize()` 先过滤 `width >= height` 的横向条目，结果统一 `normaliseLandscape()` 后才上报，杜绝竖屏缓冲尺寸 |
| `VrSettings.kt` | `VrPassthroughAspect` / `VrPassthroughRotation` 的 KDoc 订正为「窗口恒横向 / AUTO=0 / 旋转只改采样」；**无字段变化** |
| `VrSettingsSection.kt` | 2D 设置页 4 处文案订正（开关说明、比例说明、分辨率说明、旋转说明），明确「绝不拉伸、绝不竖屏」「AUTO = 0°·横屏已正立」 |
| `VrMenuModel.kt` | 新增 `const val VISIBLE_ROWS = 10` 与纯函数 `visibleWindowFor(page, selectedIndex, visibleRows)`：返回相对 `itemsFor(page)` 的 `IntRange`，**无状态、无持久偏移**，选中行永远在窗口内 |
| `VrMenuModelTest.kt` | 新增 4 个用例：所有页所有选中行都不越界且包含选中项；13 行环境页能滚到最后一行 `PASSTHROUGH_HEAD_LOCKED`；窗口只在选中行越过底边时逐行下移、上行回卷；无状态 / 越界钳制 / 自定义 `visibleRows` |
| `README-VR.md` / `docs/HANDOFF.md` | §3.4 新增滚动说明、§3.8 整段重写为 r7 修复说明、§4 表格 3 行订正；§21 顶部加「后验证伪」提示，新增本章 §22 |

**没有改动**：`VrSettingsStore`（无新字段）、`VrSettingSpec`（无新滑块）、`VrGamepadRouter` /
`VrSession` / `MainActivity`（输入链路不动）、shader / `GL_BLEND` / Cardboard SDK GL 状态、
2D 设置页的 `Column`（见 §22.7）。设置项总数仍为 **31**，VR 菜单仍 **5 页 34 行**。

### 22.5 几何规则：为什么不再拉伸

窗口形状与采样内容彻底分离，这是本轮的核心设计：

1. **窗口形状**：`windowAspect(fourThree)` 只返回 `4/3` 或 `16/9`，**恒 > 1**，
   与 `effectiveRotation`、`ptPreviewW/H` 全部无关。quad 的 `camHalfW = camHalfH * windowAspect`。
2. **预览尺寸归一化**：`previewAspect(w, h, fallback)` 用 `max/min` 取长边比，
   所以厂商广告的 `720×1280` 这类竖屏条目也得到 `16/9`，不会污染窗口。
3. **内容比例**：`contentAspect(previewAspect, rotationDeg)` 只在手动 `90 / 270` 时取倒数，
   表示「旋转后内容的有效宽高比」。AUTO(0°) 时等于预览比例。
4. **居中裁切**：`centreCrop(contentAspect, windowAspect)` 只在**一个轴**上给出 `< 1` 的
   `keep`，`cropUv()` 以中心为基准缩放 UV。旋转 90° 时窗口 u 轴对应 buffer 短边、
   v 轴对应长边，裁切量仍满足 `cropU/cropV = previewAspect × windowAspect`，
   **采样区域在 buffer 像素里的宽高比恰好等于窗口比例**，因此每个像素保持原始宽高比。
   本地 Python 复刻专门断言了这一点（`kept_u / kept_v == windowAspect`）。

一句话：**旋转只改变「采样哪部分 buffer」，永远不改变窗口形状；比例不一致时裁掉多余，
绝不挤压。** 纯几何独立成类还有个好处：GL context 丢失 / 相机重开后规则不会漂移，且能被
JVM 单测在没有模拟器、没有 GLSL 的情况下锁死。

### 22.6 菜单滚动窗口

```kotlin
fun visibleWindowFor(page, selectedIndex, visibleRows = VISIBLE_ROWS): IntRange {
    val count = itemsFor(page).size
    val rows = minOf(visibleRows, count)
    val selectedRow = (selectedIndex - firstIndexOf(page)).coerceIn(0, count - 1)
    val maxStart = (count - rows).coerceAtLeast(0)
    val start = (selectedRow - (rows - 1)).coerceIn(0, maxStart)
    return start until (start + rows)
}
```

- **无状态**：不存 scroll offset，换页 / 退出重进 / 进程重启都不会错位；选中行恒在窗口内。
- 渲染器只 `for (row in window)` 画可见行，`top = 154 + (row - window.first) * 27`，
  最多 10 行 → 最后一行底边 `154 + 10*27 = 424 < 432`，**永远不会画到位图外**。
- 溢出时（`total > rowsShown`）在 banner 右侧显示 `1-10/13 ↓到底自动下翻`，
  并在 `x=752` 画轨道 + 按比例定位的滑块，用户能直观看出下面还有内容。
- 行数上限不再是布局约束：把环境页临时改成 60 行做本地复刻，选中任意行都能正确滚动。

### 22.7 2D 手机设置页为什么不用改（重要，别加嵌套滚动）

用户说「设置页面」时有歧义，但截图是 VR 内那块固定位图菜单。核查宿主
`OpenNowSettingsScreens.kt:466`：`VrSettingsSection()` 外层已经包在

```kotlin
Column(Modifier.fillMaxSize().verticalScroll(scrollState) ...)
```

里，**2D 设置页本来就能上下滑**。`VrSettingsSection()` 内部 `Column(Modifier.fillMaxWidth())`
若再加 `verticalScroll` 会造成嵌套滚动冲突 / 无限高度异常。因此本轮**刻意不动 2D 页**，
只修 VR 内 GL 菜单。

### 22.8 验证

- `python .verify_r7.py`（本地纯逻辑复刻，无 Android SDK）：**155 条断言全绿**。
  覆盖 `VrPassthroughGeometry` 全部函数、Camera2 横向尺寸选择 / 归一化、
  AUTO=0、AUTO 零裁切、4:3→16:9 对称裁切、手动 90° 仍横向且采样区比例匹配窗口、
  34 行 / 环境页 13 行的滚动窗口、越界钳制、自定义 `visibleRows`、假设 60 行未来页。
- `git diff --check`：无空白错误。
- JVM 单测与 Kotlin 编译**只能靠 GitHub Actions**（本机无 Android SDK / Gradle / JDK 17），
  见 §5.1 的 CI 记录与 §22.9。

### 22.9 待真机复测（CI 抓不到）

1. **装新 APK 后窗口是不是横向 4:3**（默认），而不是竖屏 9:16；竖直窗格 / 地砖缝是否竖直、
   不被压扁或拉长。
2. AUTO(0°) 下画面是否正立；若仍横躺，按 `0° / 90° / 180° / 270°` 顺序对照现实场景拨，
   **每档窗口都应是横向的**。
3. 切 `16:9` 窗口 + 4:3 预览时，上下是否对称裁切（不是拉伸）；切 360P / 480P / 720P / 1080P
   各档是否都不变形。
4. 环境页能否用 `↑↓` 一路走到 `透视钉在眼前`（第 13 行），右侧滚动条与 `1-10/13` 计数是否
   随之下移；换页再回来是否回到顶部窗口（无状态）。
5. 状态面板是否显示 `窗口 4:3` / `窗口 16:9` 与实际预览宽高、帧率、生效旋转角。
6. 三种布局 `OVERLAY / CAMERA_MAIN / SIDE_BY_SIDE` 下摄像头窗口是否都保持横向。

### 22.10 已知技术债（如实保留）

- 手机摄像头是单目 2D 画面，套 `eyeFromHead + perspective` 产生的是**近似**双眼视差，
  不是真实深度；「不协调感」无法 100% 消除，只能靠「跟随幕布 + 缩小深度差」压到最低。
- 若某机型 `StreamConfigurationMap` 只提供竖屏预览条目，`normaliseLandscape()` 只保证
  **上报值**是横向；Camera2 producer 仍可能按竖屏尺寸输出，`getTransformMatrix()` 于是携带
  缩放。当前优先挑横向条目，绝大多数设备不会走到这条分支；真机如遇变形可优先切 16:9 档。
- 手动 `90 / 270` 覆盖会把横向 buffer 的有效内容变成竖屏比例，为填满横向窗口会裁掉较多
  上下内容（数学上正确但视野变窄）。这是「保持像素比例」的必然代价，不是 bug。
- 竖屏 9:16 与菜单溢出都属**视觉 / 交互**问题，JVM 单测只能锁几何与滚动规则，
  最终仍以真机截图为准。

