# HiviLauncher

HiviLauncher 是面向智能安卓音响的专用 HOME 桌面，目标是替代通用 Launcher3。

工程结构参考 HiviAudio，支持直接用 Android Studio 打开、运行、调试，然后打包 APK 预置进系统。

## 已实现

- 横屏全屏 HOME Activity
- 首页卡片：WiFi、蓝牙、时间日期、天气占位、音量表盘、媒体播放入口
- 音量加减，使用 `AudioManager.STREAM_MUSIC`
- 启动第三方音乐软件，当前优先顺序：
  - QQ 音乐：`com.tencent.qqmusic`
  - 网易云音乐：`com.netease.cloudmusic`
  - 酷狗音乐：`com.kugou.android`
- 系统设置和屏保设置入口
- 应用页：安装前后使用统一品牌图标；未安装显示下载入口，下载中以圆环显示真实进度，点击可暂停/继续。队列等待、下载、安装和失败分别在卡片内显示，不使用全屏遮罩。
- 设置 → 网络：点击已连接 WiFi 可选择“断开连接”或“取消保存”，两项操作均需二次确认；断开保留密码且不立即自动重连，取消保存会删除该网络配置。已保存但未连接的网络可选择重新连接或取消保存，未保存网络仍按原流程连接。
- 设置 → 关于：显示当前服务器，点击可在正式服和测试服之间切换；选择会持久保存，默认沿用测试服。切换时同步更新 API 和 AI WebSocket 并清除账号授权；切换前已授权时弹出二维码要求重新授权，原本未授权时仅提示切换成功，不弹出二维码。
- 媒体会话标题读取，依赖通知监听/系统权限

## 图标资源接入

- 首页音乐入口复用应用页的 `drawable-nodpi/ic_app_*.png` 品牌图，避免同一图片因密度标记不同而先缩小再放大。
- `ic_settings.png`（38×32px）、`ic_app.png`（40×40px）、`ic_download.png`（40×40px）是原尺寸 PNG，放在 `drawable-nodpi`，由布局控制显示尺寸，不放入 `drawable-xhdpi`。
- 顶部 WiFi、音量和静音恢复使用原始 PNG；系统设置面板、网络列表、下拉菜单、设置及音量弹窗、屏保预览和壁纸使用的切图统一放在 `drawable-nodpi`，保留原始像素，不在其他密度目录保留同名副本。
- XML 状态选择器、形状和滑轨继续放在 `drawable`，其中引用的 PNG 迁入 `drawable-nodpi`，按下、选中及禁用状态保持不变。
- 下载入口保留 42dp 的图标控件，使用 `centerInside` 居中显示 40px 原图，避免在当前 160dpi 设备上将小图放大。
- 本项目上述界面保留 UI 原始切图，不另画替代矢量；如后续采用多密度 PNG，需从源稿按实际显示 dp 的倍数导出并使用匹配的密度目录，例如 42dp 的 2× 图片为 84×84px，放入 `drawable-xhdpi`，不要放大小 PNG。

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
