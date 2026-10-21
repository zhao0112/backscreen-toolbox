# 背屏工具箱 · Back Screen Toolbox

为 **STEPX Neo（StepOS / Android 16）** 背面那块 **17×23 点阵屏**做的 Android 工具箱。

把手机背面朝向自己，屏幕上看到的是一块 391 点的单色像素屏。这个 App 让你自己决定
上面显示什么：计时器、通知消息、音乐律动、麦克风频谱，或者一只会表达状态的 **NOMI 表情**。

<!-- 截图 -->

## 特点

- **背屏内容跟随当前页面** —— 切到计时器页，背屏立刻变成计时器。不用猜"现在背屏上是什么"。
- **计时器只有一个设置入口** —— 一根无极滑块。横向拖动调时长，纵向拖动微调到秒。没有 `+`/`−`，没有预设，没有档位，没有吸附。
- **NOMI 表情** —— 6 帧状态表情（待机 / 开心 / 思考 / 说话 / 睡觉 / 眨眼），纯点阵手绘，17×23 逐像素对齐。
- **手绘** —— 直接在背屏的 17×23 网格上画，手指每一笔都是真实像素点，画完即时投射。
- **自研 FFT 频谱** —— 不用 `Visualizer`（很多机型上引擎不可用，直接抛 `error: -3`），而是自己读 `AudioRecord` 的 PCM 做 1024 点 radix-2 FFT。
- **后台常驻** —— 用前台服务保活，退到后台继续推帧。
- **真背屏开关** —— 显示的是真实状态，不是假标志位。

## 支持的内容

| 内容 | 说明 |
| --- | --- |
| 动画 | 内置程序化旋转螺旋；也可以放自己的 `frames.bin` + `frames.idx` 素材 |
| 计时器 | 无极滑块设时长，多计时器并行，进度条实时反映 |
| 消息 | 监听系统通知，验证码单独分区，长文本滚动 |
| 音乐 | 均衡器律动 + 歌名滚动 |
| 频谱 | 麦克风实时频谱（自研 FFT），只在主动使用时采集，不保存、不上传 |
| NOMI 表情 | 6 帧状态表情循环 |
| 手绘 | 在 17×23 网格上手动画，实时投射到背屏 |

## 构建

依赖 **JDK 17**、**Android SDK**（platform 36 + build-tools 36.0.0）。**不需要 Gradle**。

```sh
git clone https://github.com/zhao0112/backscreen-toolbox.git
cd backscreen-toolbox
sh build.sh
# 产物：build/backscreen-player.apk
```

### 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ANDROID_SDK_ROOT` | `~/Android/Sdk` | Android SDK 路径 |
| `ANDROID_PLATFORM` | `android-36` | platform 目录名 |
| `BUILD_TOOLS_VERSION` | `36.0.0` | build-tools 版本 |
| `JAVA_HOME` | `~/Android/jdk17` | JDK 17 路径 |
| `KEYSTORE` / `KEY_ALIAS` | `~/.android/debug.keystore` / `androiddebugkey` | 签名配置 |
| `KS_PASS` | `android` | 签名口令（也可用 `KS_PASS_FILE` 指向口令文件） |
| `SKIP_SIGN=1` | - | 只出 unsigned APK（CI 用） |

CI：[`.github/workflows/build.yml`](.github/workflows/build.yml) 在 push / tag 时自动构建 unsigned APK 并上传 artifact。

## 安装 / 运行

手机上直接装 unsigned APK 需要先签名；用 CI artifact 或自己 `apksigner` 签好后：

```sh
adb install -r build/backscreen-player.apk
adb shell am start -n com.local.backscreengame/.ToolboxActivity
```

浏览器下载安装也可以（APK 必须 `android:debuggable="true"`，见下）。

**首次使用需要授权：**

- 通知使用权 —— 必须手动去「设置 → 通知使用权」打开，App 无法代开。
- 麦克风 —— 只在切到频谱时申请，只在主动使用时采集，**不保存、不上传**。
- 通知权限 —— 前台服务需要，否则后台进程可能被系统杀。

**注意：**

- 开启背屏前请先关掉 **Amoo**，它会抢占背屏通信通道。
- 异常退出导致背屏常亮 / 不旋转时：
  ```sh
  adb shell settings delete system stepos_back_screen_display_mode
  ```

## 技术说明

### 背屏接口

通过反射访问 StepOS 的 `display_ext` 服务：

```
android.hardware.display.StepOSDisplayManager
context.getSystemService("display_ext")
setBackScreenAgentFrame(int[391], Bundle)
```

- 物理点阵 **17 列 × 23 行 = 391 格**，每格亮度 `0–255`
- 帧索引 = `row * 17 + col`
- 服务端对背屏方法**没有权限校验**
- 但 `display_ext` 属于非 SDK 接口，Android 14+ 会拦截，**所以 APK 必须 `android:debuggable="true"`**
  - 这也是为什么 Studio / H5 / WebView 方案走不通：它们改不了自己的 manifest，也绕不开沙箱

### 手绘

手绘画布**直接就是背屏的 17×23 点阵网格**，不是先画在高清画布再降采样。

原因：背屏只有 391 个点，降采样会把所有细节吃掉。直接在点阵上画，所见即所得 ——
你在屏幕上点的那一格，就是背屏上会亮的那一格。

- 手指按下/移动 -> 涂点，用 Bresenham 连线，快速滑动不断线
- 橡皮 / 撤销 / 清空
- 画的时候实时同步到背屏，切页面也不丢
- 格心跨度严格保持 17:23，不形变（横向步距 == 纵向步距）

### 架构

```
AppState.backContent     ← 当前显示什么（全局单例）
RenderHub                ← 渲染中枢：推帧循环、频谱采集、视频素材
  ├── ToolboxActivity    背屏页：预览 / 电源 / 内容选择
  ├── TimerManageActivity 计时器页
  └── SettingsActivity   设置页
BackScreenService        前台服务，保证后台存活
```

推帧循环放在 `RenderHub` 而不是 Activity 里 —— 这样切页面时背屏不会停更、不会显示旧内容。

### 点阵限制

17×23 只有 391 点，能表达的东西很有限：

- 3×5 字体，步进 4 列，**一屏通常只放得下 4 个字符**
- 背屏字体是纯 ASCII，中文会变成 `?`（所以计时器名在背屏上显示为拼音缩写，如「番茄钟」→ `FQZ`）
- 保留不住真人脸部细节，只适合抽象轮廓、眼睛、动作和状态动画
- 歌名、消息正文这类长文本要滚动，不适合静态换行

## 版权与来源

本项目基于 [xzl01/back-screen-game](https://github.com/xzl01/back-screen-game)（MIT）的 `BackScreen.java` 实现，
该文件保留原作者版权声明，详见 [NOTICE](NOTICE) 与 [LICENSE](LICENSE)。

其余代码为 zhao0112 原创。

仓库**不包含任何第三方视频或音乐素材**，默认使用程序化生成的动画。

## License

[MIT](LICENSE) © 2026 Will (zhao0112) · BackScreen.java © 2026 HarryLoong (xzl01)
