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
5. **接手后最优先的三件事**：① 查最新 CI run 是否全绿；② 请用户在 Android 10 真机复测
   新 APK；③ 按第 4 节补齐 P3 / 处理 P2 阻塞项。

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
| Android 10 / API 29 静态兼容性核验 | 完成（静态） | 计划书 12.2；`minSdk 24 / targetSdk 37` |

### 3.2 已核实但未实现

| 项目 | 说明 |
| --- | --- |
| **P3：手柄驱动的 VR 设置面板可见渲染** | `VrMenuModel` / `VrGamepadRouter` / `VrSettingsStore` 已就绪，但**没有**把菜单画成 VR 内可见面板。当前 2D 面板只显示状态文字与提示，不可交互 |
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

> **GLSL shader 是运行时编译，CI 完全抓不到 shader 错误。** 两个 program 都用
> `runCatching` 包住，最坏情况是黑屏而不是崩溃；真机若黑屏先查 shader 编译日志。

---

## 4. 你要做什么（按优先级）

### P0 — 接手立刻做

1. `git status` / `git log --oneline -6` 确认本地与 `origin/main` 一致、工作区 clean。
2. 查 GitHub Actions 最新 `vr-build` run：两个 job 全绿，`Assert VR JVM tests actually ran`
   输出应为 `6 suites / 64 tests / 0 failures / 0 errors / 0 skipped`
   （原 58 个 + 本轮新增 6 个零分配路径测试）。
3. 请用户用**最新** APK 在 Android 10 真机按 3.3 清单复测，拿回结论再继续改渲染。

### P1 — 剩余功能（按序）

1. **P3 可见设置面板**：把 `VrMenuModel` 渲染进 2D 面板纹理（Canvas 绘制），
   接 `VrGamepadRouter` 的上下/确认/返回，保持纯手柄、无 gaze。当前面板只是静态状态显示。
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
  Streaming.kt                       ★decoderSurface → VrVideoSurfaceRegistry
  OpenNowScreens.kt                  ★主界面：Home 恒为原版 HomeScreen（电视版分支已删）
  ConsoleHomeScreen.kt               ✗已从编译路径移除 → docs/ui-reference/*.txt
android/app/src/main/java/com/opencloudgaming/opennow/vr/
  VrTheaterRenderer.kt               ★黑底 + OES 串流平面 + 2D 面板 + 零分配渲染循环
  VrTheaterView.kt                   CardboardView 子类；viewerMode → setStereoRenderMode
  VrModeHost.kt                      Compose overlay + 「进入 VR / 退出 VR」按钮
  VrSession.kt / VrSettings.kt       全局状态与可调项
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

push 到 `main` 会自动触发 `vr-build`。**每次代码改动都要跑一次并记录 run ID。**

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
| `42194578267` | `4c67196` | 成功：docs-only push 回归验证 |

> 本轮（电视版 UI 移除 + VR 渲染 + 性能优化 + 本文）提交后会产生**新的 run**，
> 请在 Actions 页面查最新一条并补记到 `README-VR.md` 5.1 与计划书 9.1。

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

- [ ] 最新 `vr-build` run 全绿，测试数 `64`、产物含两个 `.so`
- [ ] 用户在 Android 10 真机按 3.3 复测并反馈
- [ ] 把新 run ID 补进 `README-VR.md` 5.1 与计划书 9.1
- [ ] 工作区 `git status` clean，仓库内无 PAT 字面串残留
- [ ] 提醒用户撤销并重新生成 PAT
