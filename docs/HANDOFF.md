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
   CI 全绿但用户点「进入 VR」直接闪退。现在 AAR/APK 两层二进制守卫已加，仍要真机复测。
4. **绝对不能做**：3D/SBS/左右眼不同内容、gaze/眼球/凝视/dwell/头部动作确认、
   把电视版 UI 放回主界面、把 2.3.8 只改个版本号冒充适配、在本机编译。
5. **接手后最优先的三件事**：① 查最新 CI run 是否全绿；② **按 8.5 节实现「串流中途切 VR」的
   `switchToSurface()` 双向切换**（这是当前最实在的功能缺口），并在真机验证关键帧恢复；
   ③ 请用户在 Android 10 真机复测画面 / 景深 / 软键盘，再按第 4 节补 P3 与处理 P2 阻塞项。

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
| **VR 黑底 + 2D 纹理正常渲染** | 代码完成，**真机未验证** | `vr/VrTheaterRenderer.kt`：黑底清屏色、OES 串流纹理、无流时 2D 面板 |
| **低端机性能优化** | 代码完成，**真机未验证** | 见第 9 节 |
| **VR 设置集成进原生设置页** | 代码完成，**真机未验证** | `设置 → Interface → 「VR 模式（半 VR 影院）」`：`vr/VrSettingsSection.kt` 复用上游原生 `SettingSwitch` / `NumberSlider`。**已删除**常驻浮层「VR 设置」按钮与 `VrPhoneSettings.kt`（用户明确要求）。含 VR 总开关、8 个滑块、3 个开关、恢复默认 |
| **景深 / 空间固定感修复** | 代码完成，**真机未验证** | 已核实 SDK `HeadTransform.getHeadView()` 是**逆旋转**（camera-to-head）。旧代码把四元数的**正向**旋转当 view 矩阵用，幕布会跟着头转、贴脸、无空间深度。现改 `VrHeadPose.viewRotationInto()`（转置 3x3 求逆）+ 2 个 JVM 测试 |
| **幕布宽高比接真实帧** | 代码完成，**真机未验证** | `Streaming.kt.onFrameResolutionChanged` → `VrSession.setVideoAspect(w,h)`；渲染器检测到 aspect 变化自动重建幕布网格，不再固定 16:9 |
| **无缝切回手机 / 软键盘** | 代码完成，**真机未验证** | VR 内按系统 **BACK** 或手柄快捷键退出；`VrVideoSurfaceRegistry.clear()` 让解码器回退 2D surface，不重连会话；退出后 2D UI 与软键盘恢复。VR 开启时画面上**没有任何浮层控件** |
| Android 10 / API 29 静态兼容性核验 | 完成（静态） | 计划书 12.2；`minSdk 24 / targetSdk 37` |

### 3.2 已核实但未实现

| 项目 | 说明 |
| --- | --- |
| **P3：VR 内部、戴头显时用手柄操作的设置面板** | `VrMenuModel` / `VrGamepadRouter` / `VrSettingsStore` 已就绪，但**没有**把菜单画成 VR 内可见面板。注意区分：**手机 2D 端设置面板已完成**（见 3.1），这里指 VR 里那个用手柄调的面板。当前 VR 内 2D 面板只显示状态文字，不可交互 |
| **串流开始后才进入 VR（中途切换）** | **受解码器架构限制，未实现无缝**，详见 **8.5**。已核实：先进 VR 再开始串流可用；串流中才进 VR 时，解码器仍是纯 texture 路径，VR 的 `SurfaceTexture` 收不到画面 |
| 完整 2D UI 镜像进 VR | 见 2.2，用户「可以试一下」的可选项，未实现 |
| 上游 2.3.8 适配 | 阻塞：见 4.2 |
| 汉化 | 用户说「我其实只需要汉化就行」，语义未确认，**未执行**。不要擅自全量改文案 |
| 上游 167 个 JVM 测试 | 未启用（引用 `android.*` 且无 Robolectric）。CI 只跑 `--tests "com.opencloudgaming.opennow.vr.*"` |

### 3.3 必须由用户真机复测（不能靠 CI 证明）

1. 点击「进入 VR」不闪退（**已由用户实测确认**修复生效）。
2. VR 内能看到**纯黑背景 + 一块 2D 平面面板**（此前是「啥也没有」）。
3. 面板中文文字是否上下颠倒（若颠倒，把 `VrTheaterRenderer.PANEL_FLIP_Y` 从 `0.0f` 改为 `1.0f`）。
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

- [x] **当前代码 run `42208037616` 全绿**，测试数 `75`、产物含两个 `.so`（已人工解包核验，见 8.4）
- [x] run ID 已补进 `README-VR.md` 5.1、计划书 9.1、本文 8.4
- [x] 提交纪律已写入 8.1（纯文档提交用 `[skip ci]`，不重复触发构建）
- [ ] 用户在 Android 10 真机按 3.3 复测并反馈（**唯一剩下的关键验证**）
- [ ] 工作区 `git status` clean，仓库内无 PAT 字面串残留
- [ ] 提醒用户撤销并重新生成 PAT

---

## 12. 2026-11-06 追加：「进入 VR 显示原本非 VR 界面」诊断与日志方案

### 12.1 现象与核实到的事实

- 用户测的是 **`3cfcade`**（当前 `main`，CI run `42219713375` 全绿）。进入 VR 后看到的是
  **「原本的 no-VR 界面」**——不是纯黑、不是「全屏 2D 串流」，而是底层 2D 手机 UI 直接透出，
  VR 层没有出现。
- 诊断导出 `opennow-android-logs-20261106-211655.txt`（4 MB）里那条
  `UnsatisfiedLinkError: couldn't find "libcardboard_sdk_jni.so"` 的调用栈虽经过
  `VrTheaterView.<init> → VrModeHost.kt:45`，但它是 **`[Retained Java crash — previous process]`
  timestamp 08:56**，当前测试是 13:00–13:14；且该缺库闪退早在 `11a4d69` 已修复、用户实测确认
  「能进 VR」。**不是本次根因**。
- 之前「零 CardboardView 生命周期日志 = renderer 没画帧」是**误判**：`vr/` 目录本来就没有
  任何 `Log.*` 语句（见 §7.16）。
- `docs/VR_IMPLEMENTATION_PLAN.md` §12.5 早已写明剩余问题是「**渲染内容为空 / 啥也没有**」。

### 12.2 本次改动（只加日志 + 回退置顶，不改渲染逻辑）

| 文件 | 改动 |
| --- | --- |
| `VrTheaterView.kt` | **移除 `setZOrderOnTop(true)` / `promoteGlSurface()`**（回到 `fb64a9a` 的层级）；保留 `setKeepScreenOn` / overlay 隐藏；所有生命周期加 `Log.d`（TAG `VrTheaterView`） |
| `VrTheaterRenderer.kt` | shader 链接失败改 `getOrElse` 记录 `Log.e`；`onSurfaceCreated`/`onNewFrame`/`onRendererShutdown` 加一次性日志；`onDrawEye` 加 **useVideo 跳变 + 三种 SKIP 原因 + 每 120 帧一条节流心跳**；**渲染逻辑与 early-return 顺序完全不变** |
| `VrSession.kt` | `setEnabled()` 加一行日志 |
| `VrModeHost.kt` | `AndroidView.factory` / `onViewAttachedToWindow` 加日志 |
| `docs/HANDOFF.md` / `docs/VR_IMPLEMENTATION_PLAN.md` | 记录结论与日志过滤命令 |

回退 `setZOrderOnTop` 的依据：它把 Cardboard GL SurfaceView 提成不透明置顶层且要求在 surface
创建前设置；从 `init`/`onAttachedToWindow` 遍历子 view 调用时机不可靠，疑似让 GL 层透明/失效，
于是底层 2D UI 透出 = 用户看到的「原本的非 VR 界面」。**这只靠真机最终判定**，CI 无法验证画面。

### 12.3 下一步（真机验证，需用户配合）

1. 推本次改动 → CI 出新 APK（工件 `opennow-vr-debug-arm64-v8a`）→ 用户覆盖安装。
2. 进 VR 前后用 `adb logcat -s VrSession,VrModeHost,VrTheaterView,VrTheaterRenderer` 抓日志并回传。
   判定链：
   - **没有 `VrSession: setEnabled(true)`** → 开关/按键没通。
   - **有 setEnabled 但无 `VrModeHost: AndroidView.factory`** → Compose 分支没渲染 VR host。
   - **有 factory 但无 `VrTheaterRenderer: onSurfaceCreated`** → Cardboard GL surface 没起来（回到 JNI/SDK 层）。
   - **有 onSurfaceCreated 但 shader `link FAILED`** → shader 编译问题。
   - **有 onSurfaceCreated、有 draw 心跳，但 `SKIP — ...`** → 定位到具体「啥也没画」原因。
   - **一切都正常但用户仍看不到** → 纯层级/compositing 问题，再评估 `setZOrderMediaOverlay` 或
     Compose `zIndex`，而不是 `setZOrderOnTop`。
3. 依据日志结果决定最终层级修复；**在拿到日志前不要再猜、不要再改渲染**。

> 本次只改诊断与回退，不含 shader/层级的新修复。所有判断以真机日志为准。
