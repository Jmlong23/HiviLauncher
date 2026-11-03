# mc-200pro 功能结构调整说明

## 1. 依据与范围

本文档依据 `FireShot Capture 001 - mc-200pro - Notion - [app.notion.com].pdf` 中的
**AI 带屏智能音箱 PRD**整理。当前提交的目标是先让已经存在、可以运行的能力按产品域归位，
而不是为尚未具备设备协议或云端接口的功能创建不可用的空实现。

已纳入当前代码结构的 MVP/P0 相关能力：

- 主屏信息面板：时间、网络状态、蓝牙状态、音量、Now Playing。
- 音乐播放控制页面。
- AI 账号二维码授权与取消授权。
- 自定义 Wi-Fi 设置页与系统屏保设置入口。
- 系统应用切换页面。

尚未实现但已在 PRD 中定义的能力（首次配网、蓝牙配对、语音唤醒/ASR/LLM/TTS、
场景模式、屏保样式、OTA、诊断等）不会在本次提交中伪造接口或 UI；其目标模块位置见第 4 节。

## 2. 当前代码目录

```text
com.hivi.launcher
├── account                         # AI 账号授权
│   ├── model
│   │   └── AuthorizationUiState
│   ├── presenter
│   │   └── AuthorizationPresenter
│   └── ui
│       ├── AuthorizationDialog
│       └── AuthorizationView
├── base                             # 通用 MVP / ViewBinding 生命周期
├── main                             # PRD 中的 Home / 主屏信息面板
│   ├── data
│   │   └── MainStatusRepository
│   ├── model
│   │   ├── MainStatus
│   │   └── MusicInfo
│   ├── presenter
│   │   └── MainPresenter
│   └── ui
│       ├── MainActivity
│       └── MainView
├── music                            # PRD 中的 Music / NowPlaying
│   ├── model                        # 当前包含本地/UPnP 播放引擎与播放状态
│   ├── presenter
│   └── ui
├── settings
│   ├── data
│   │   └── SystemSettingsNavigator  # 当前仅用于系统屏保设置
│   ├── model
│   │   └── WifiNetwork
│   ├── presenter
│   │   └── SettingsPresenter
│   └── ui
│       ├── SettingsActivity
│       └── SettingsView
├── systemapps                       # 工程维护/应用切换入口
├── customview
└── utils
    └── network                      # 现有 Retrofit、令牌存储、网络执行工具
```

`main` 保留现有包名和 `MainActivity` 类名，以避免修改 HOME Activity 的 Manifest 入口、
系统预置配置或现有调用点；在产品语义中它对应 PRD 的 `Home`。

## 3. 本次边界调整

### 3.1 主屏

原先 `MainPresenter` 同时承担以下职责：

- 读取 Wi-Fi、蓝牙、音量和媒体会话。
- 调节系统音量。
- 管理 AI 授权弹窗。
- 打开应用内设置页面或系统屏保设置。
- 协调主屏 UI 刷新。

现在职责划分为：

| 位置 | 职责 |
| --- | --- |
| `main/data/MainStatusRepository` | 唯一读取 Android Wi-Fi、蓝牙、音量、媒体会话，并组装 `MainStatus`。 |
| `main/presenter/MainPresenter` | 定时刷新、将状态分发给 `MainView`、主屏导航协调。 |
| `main/ui/MainActivity` | ViewBinding、布局、广播注册、打开本模块拥有的授权弹窗和自定义设置页。 |
| `settings/ui/SettingsActivity` | Wi-Fi 设置页面的 ViewBinding、左侧菜单、返回与网络列表渲染。 |
| `settings/presenter/SettingsPresenter` | Wi-Fi 状态、当前连接网络、扫描结果与 Wi-Fi 开关操作。 |
| `settings/data/SystemSettingsNavigator` | 当前仅保留 Android 屏保设置 Intent 的容错启动。 |

因此，主屏 Presenter 不再持有 `Activity` 强引用，也不再直接依赖
`WifiManager`、`BluetoothAdapter`、`MediaSessionManager` 等系统服务。

### 3.2 AI 账号授权

原 `AuthorizationDialog` 同时处理 Dialog 生命周期、二维码渲染、网络请求、轮询、
授权令牌持久化和退出授权。现在拆分为：

| 位置 | 职责 |
| --- | --- |
| `account/model/AuthorizationUiState` | 与 Android View 无关的授权状态。 |
| `account/presenter/AuthorizationPresenter` | 请求二维码、状态轮询、令牌存储/清除、请求取消和释放 Disposable。 |
| `account/ui/AuthorizationDialog` | 渲染二维码和提示文案，管理 Dialog 生命周期。 |

授权 Dialog 关闭时会 `detach()` Presenter，取消全部延迟轮询与 RxJava 请求，避免页面退出后继续轮询或持有 UI。

### 3.3 设置入口

当前主屏“系统设置”已改为打开应用内 `SettingsActivity`，首个落地页面是 PRD 中的
Wi-Fi 切换页。该页面展示 Wi-Fi 开关、当前连接网络和可读取到的扫描结果；因 Android 11
的普通应用限制，受保护网络的连接/密码输入仍需后续接入设备配网协议。

蓝牙、隐私、OTA、诊断和 AI 账号管理已在左侧菜单中预留入口；暂未接入的菜单会明确提示，
不会跳转到 Android 系统设置。屏保入口目前仍委托 Android 系统页，后续可替换为
`screensaver` 模块的样式与时长选择页。

## 4. 后续模块落位

以下目录为 PRD 后续功能的推荐落位；只有具备对应硬件协议、系统权限或云端 API 后才创建具体实现：

```text
com.hivi.launcher
├── ai
│   ├── model                        # 会话、授权、卡片、语音状态
│   ├── data                         # VoiceClient / AI Gateway / 本地缓存
│   ├── presenter
│   └── ui                           # AI 对话页、授权页
├── network
│   ├── model                        # Wi-Fi 列表、配网状态
│   ├── data                         # Wi-Fi 扫描、连接、配网协议
│   ├── presenter
│   └── ui                           # 首次配网、网络设置
├── bluetooth
│   ├── model
│   ├── data                         # 配对、连接、断开
│   ├── presenter
│   └── ui
├── volume
│   ├── model                        # 音量、输出模式、低音炮/音效状态
│   ├── data
│   ├── presenter
│   └── ui
├── screensaver
│   ├── model                        # Clock / PhotoWall / Visualizer 样式与时长
│   ├── data
│   ├── presenter
│   └── ui
├── scenes                           # Study / Party / Relax / Sleep（Phase 2）
├── diagnostics                      # 网络、蓝牙、麦克风、设备诊断
└── update                           # OTA 版本检查、下载、安装状态
```

依赖规则：

1. `ui` 只处理 ViewBinding、用户事件、渲染和生命周期强相关的广播/权限回调。
2. `presenter` 只依赖本功能的 `model`、`data` 和 View 接口，不直接操作 Binding。
3. `data` 负责 Android 系统服务、设备协议、云 API、缓存和持久化。
4. 跨功能跳转通过目标模块的 Navigator/Activity 边界完成；禁止页面直接读取其他功能的数据层。
5. `customview` 保持纯绘制，不放置设备、网络、账号或播放业务。

## 5. 与 PRD MVP 的对应关系

| PRD MVP 能力 | 当前状态 | 代码位置 |
| --- | --- | --- |
| 主屏信息面板 | 已有，已重组状态读取边界 | `main` |
| 音乐播放控制页面 | 已有 | `music` |
| 音量控制 | 已有；当前为主屏音量调节 | `main/data/MainStatusRepository`，后续迁入 `volume` |
| AI 账号二维码授权 | 已有，已拆为 MVP | `account` |
| Wi-Fi 自定义设置页 | 已有，已接入主页入口 | `settings` |
| 系统屏保入口 | 已有，当前委托 Android 系统页 | `settings/data/SystemSettingsNavigator` |
| 首次配网 | 未实现 | 规划为 `network` |
| 蓝牙连接/配对页面 | 未实现 | 规划为 `bluetooth` |
| AI 基础问答与语音唤醒 | 未实现 | 规划为 `ai` |
| 屏保样式/时长选择 | 未实现 | 规划为 `screensaver` |

## 6. 兼容性约束

- 保持包名 `com.hivi.launcher`、HOME/Launcher Intent、横屏全屏行为不变。
- 保持 `MainActivity`、`MusicActivity` 和 `SystemAppsActivity` 的现有 Manifest 入口不变，并新增内部 `SettingsActivity`。
- 保持现有二维码 API、令牌 SharedPreferences 名称、系统屏保设置动作和 UPNP 播放能力不变。
- 不增加第三方依赖；继续使用项目已有的 Retrofit、RxJava、ZXing 和 AudioToolLib。
