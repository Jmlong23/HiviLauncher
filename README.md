# HiviLauncher

HiviLauncher 是面向智能安卓音响的专用 HOME 桌面，目标是替代通用 Launcher3。

工程结构参考 HiviAudio，支持直接用 Android Studio 打开、运行、调试，然后打包 APK 预置进系统。

## 已实现

- 横屏全屏 HOME Activity
- 系统底部导航栏默认显示，主页面和普通弹窗保持一致；状态栏仍隐藏，屏保保持全屏隐藏系统栏。
- 应用底部仅保留居中的“系统设置”和“应用”两个图标按钮，使用独立背景和按下/聚焦高亮。
- 进入 AI 对话页时，应用底部导航及分割线与首页卡片同步过渡，页面完全覆盖后隐藏；返回首页时同步恢复，不影响系统底部导航栏。AI 对话页不显示左上角返回按钮，通过系统返回键退出。
- 进入 AI 对话页前检查账号授权；未授权时保留当前页面，弹出二维码授权弹窗并提示“请先授权 AI 服务”。
- 首页进入设置和应用页沿用 AI 的 240ms 转场：首页主体、底部导航及分割线同步向左偏移 28dp 并淡至 65%，目标页从右侧 28dp 偏移淡入；返回首页时反向恢复，顶部状态栏保持原位。
- 首页卡片：WiFi、蓝牙、时间日期、天气占位、音量表盘、媒体播放入口
- 音量加减，使用 `AudioManager.STREAM_MUSIC`
- 启动第三方音乐软件，当前优先顺序：
  - QQ 音乐：`com.tencent.qqmusic`
  - 网易云音乐：`com.netease.cloudmusic`
  - 酷狗音乐：`com.kugou.android`
- 系统设置和屏保设置入口
- 设置 → 网络：点击已连接 WiFi 可选择“断开连接”或“取消保存”，两项操作均需二次确认；断开保留密码且不立即自动重连，取消保存会删除该网络配置。已保存但未连接的网络可选择重新连接或取消保存，未保存网络仍按原流程连接。
- 设置 → 关于：显示当前服务器，点击可在正式服和测试服之间切换；选择会持久保存，默认沿用测试服。切换时同步更新 API 和 AI WebSocket 并清除账号授权；切换前已授权时弹出二维码要求重新授权，原本未授权时仅提示切换成功，不弹出二维码。
- 媒体会话标题读取，依赖通知监听/系统权限

## 应用下载

“应用”页固定展示 9 个音乐应用。已安装的应用点击直接打开；未安装时按应用类型请求当前服务器的 `version/details?type=...`，解析 `data.updateUrl` 下载 APK，校验候选包名后静默安装，页面仍打开时自动启动安装完成的应用。依赖目标系统匹配的 platform 签名和 system UID。

下载与安装共用单线程队列，支持等待、暂停、断点续传和失败后点按重试；安装阶段不可暂停，下载失败与安装失败分别显示。进程重启后可从本地断点手动继续；服务器不接受续传时重新下载，APK 上限为 512 MiB。后台地址或安装包地址变化后不会复用旧包的断点。

下载默认跟随“设置 → 关于”的正式服/测试服选择。`app/src/main/res/values/system_app_downloads.xml` 中的非空地址可覆盖对应应用的默认版本接口，支持 APK 直链或版本接口；留空即可使用后台发布的安装包，无需每次换包修改 Launcher。

应用页使用独立全屏层，首屏测量布局就绪后滑入并淡入，返回时滑出并淡出；不改变首页的 padding 或卡片尺寸。应用网格在测量阶段适配屏幕，下载状态刷新复用已有条目，避免布局回调中再次布局和重建。

## Android Studio 调试

直接用 Android Studio 打开本目录：

```text
/home/long/Desktop/hivi/HiviLauncher
```

运行 `app` 即可安装到设备。因为它声明了 HOME intent，设备上可能会弹出默认桌面选择；调试时选择 HiviLauncher 即可。

本工程使用：

```text
compileSdk 30
minSdk 24
targetSdk 30
```

命令行构建前需要本机安装 Android SDK 30，并由 Android Studio 自动生成 `local.properties`，或手动参考 `local.properties.example` 设置 `sdk.dir`。

命令行构建：

```bash
./gradlew assembleDebug
```

Debug APK 输出：

```text
app/build/outputs/apk/debug/hivi-launcher-v1.0.0-debug.apk
```

Release APK：

```bash
./gradlew assembleRelease
```

Release APK 输出：

```text
app/build/outputs/apk/release/hivi-launcher-v1.0.0.apk
```

Debug 和 Release APK 均使用项目根目录的 `platform.keystore` 签名，签名配置与 HiviAudio 保持一致。量产前需确认该证书与目标系统的 platform 证书一致；通过系统源码预置时也可由 `LOCAL_CERTIFICATE := platform` 重新签名。

## 预置到 RK356X Android 11 系统

先构建 APK：

```bash
./gradlew assembleRelease
```

创建预置目录：

```bash
mkdir -p /home/long/Desktop/orangecm4/RK356X_Android11/packages/apps/HiviLauncher
cp system-prebuilt/Android.mk \
  /home/long/Desktop/orangecm4/RK356X_Android11/packages/apps/HiviLauncher/Android.mk
cp app/build/outputs/apk/release/hivi-launcher-v1.0.0.apk \
  /home/long/Desktop/orangecm4/RK356X_Android11/packages/apps/HiviLauncher/HiviLauncher.apk
```

然后在对应产品 mk 中加入：

```make
PRODUCT_PACKAGES += HiviLauncher
```

再编系统包。`system-prebuilt/Android.mk` 会把 APK 作为 privileged 预置应用，并覆盖 Launcher3/QuickStep。

## 如果要直接源码集成

当前推荐走 APK 预置路线。如果后面要改回 Android 源码直接编译 Java，可以再单独加一份 `Android.bp` 或源码型 `Android.mk`。

## 包名

```text
com.hivi.launcher
```

## 默认桌面

Manifest 中已声明：

```xml
<category android:name="android.intent.category.HOME" />
<category android:name="android.intent.category.DEFAULT" />
```

如果系统里仍有多个 HOME，首次启动会弹默认桌面选择。量产时建议系统里只保留一个 HOME，或在首次启动时设置默认 Launcher。

## 旧源码树编译方式

旧版根目录 `Android.mk + src + res` 结构已经改为 Android Studio 工程结构。现在根目录不再直接作为 AOSP 源码 app 编译。

## 常用命令

```bash
./gradlew clean assembleDebug
./gradlew clean assembleRelease
```

## 后续建议

- 天气时钟屏保已下线，屏保样式现支持简约时钟、翻页时钟和夜间息屏三种横向滑动选择。
- 第三方音乐应用入口现在是“启动已安装的第一个音乐 App”，如果需要三个独立按钮，可以在底部区域继续扩展。
- 媒体标题读取在部分系统上需要通知监听授权；作为 platform privileged 系统应用时更容易拿到权限。
- 如需开机固定进入该桌面，确认系统只存在一个默认 HOME，或在首次启动时设置默认 HOME。
