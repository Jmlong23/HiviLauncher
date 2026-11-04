# 应用页与下载

应用页只显示固定九项，顺序为 QQ音乐、网易云音乐、酷狗音乐、汽水音乐、全民K歌、酷我音乐、喜马拉雅、蜻蜓FM、懒人听书。`SupportedApp` 中维护包名白名单，优先识别车机 / IoT / HD 版本。所有状态统一使用设计提供的 `ic_app_*.png`，不替换成 APK 内图标或重绘矢量图。未下载时将 Logo 置灰并在其正中央覆盖 `ic_download.png`，应用名称保持居中。

## 下载源配置

在 `app/src/main/res/values/system_app_downloads.xml` 填入每项应用经过确认的 APK 直链，建议使用自有可信 HTTPS 分发源。URL 中的 `&` 必须写成 `&amp;`。不能填写网页或应用商店详情页。

目前项目未提供这九项的分发地址，因此默认保留空值。点击尚未配置地址的应用只提示“暂未配置下载地址”，不会模拟进度、打开任意第三方下载站或安装不明 APK。

| 应用 | 配置项 |
| --- | --- |
| QQ音乐 | `system_app_qq_download_url` |
| 网易云音乐 | `system_app_netease_download_url` |
| 酷狗音乐 | `system_app_kugou_download_url` |
| 汽水音乐 | `system_app_soda_download_url` |
| 全民K歌 | `system_app_karaoke_download_url` |
| 酷我音乐 | `system_app_kuwo_download_url` |
| 喜马拉雅 | `system_app_ximalaya_download_url` |
| 蜻蜓FM | `system_app_qingting_download_url` |
| 懒人听书 | `system_app_lanren_download_url` |

## 交互

- 返回与音量共用 `HeaderButtonBackgroundDrawable` 绘制黑色内凹按压态：按 52dp 高的设计尺寸缩放，圆角 31dp，黑色填充不透明度 38%，黑色内描边 12dp，高斯模糊半径 9dp。参数位于 `system_apps_button_styles.xml`；模糊在软件位图上生成并缓存，兼容硬件加速页面。按压背景以正常 PNG 的实体轮廓为蒙版，保留原始透明留白、逐像素透明度和外阴影，使可见宽高与正常态一致，而不是画满控件边界。正常态保持原图，按压/聚焦只改变背景，不调暗图标和文字，也不改变按钮位置及大小。
- 未下载：Logo 置灰，云下载图标居中叠加在 Logo 上；点击开始下载。
- 等待中：单任务串行队列，显示转动圆环；点击暂停排队任务。
- 下载中：图标蒙层上显示真实字节进度圆环和停止方块；点击暂停。未知长度时只显示转动圆环，不伪造百分比。
- 已暂停：保留进度圆环并显示继续三角；点击重新排队继续。
- 安装中：转动圆环与安装文案，忽略重复点击。
- 卸载中：转动圆环与“正在卸载”文案，禁用点击和长按；离开后重进应用页仍保持实时状态。成功后恢复下载入口，失败则恢复已安装状态并提示重试。
- 失败：显示失败提示与云下载图标；点击重试。
- 已安装：移除下载状态，点击启动应用并返回进入应用页前的页面。
- 长按已安装应用：显示“卸载应用 / 应用信息”。QQ 音乐作为受保护预置应用只提供“应用信息”，其他状态不弹出菜单。

任务不会因离开应用页而终止；重进页面恢复实时状态。进程重启后发现部分文件则恢复为已暂停。下载源支持 `Range` 及强 `ETag` / `Last-Modified` 时可安全断点续传；不支持时从头下载。响应范围、长度与 1 GiB 上限均会校验，APK 包名必须匹配该项白名单。

安装复用设备现有系统 UID 的 `pm install` 能力；普通非系统签名部署不保证支持静默安装。失败保留重试入口，不标记为已安装。

卸载使用 `PackageInstaller` 与 `DELETE_PACKAGES` 权限，Android 8.1 以下回退 `pm uninstall --user 0`。结果通过显式指向未导出 `AppUninstallReceiver` 的一次性可变 `PendingIntent` 回调，不使用无权限保护的动态广播接收器，避免系统 UID 发送非保护广播的告警。卸载状态由共享任务管理，重复请求和过期结果不能覆盖新任务；普通非系统签名部署不保证支持静默卸载。

## 验证

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

单元测试覆盖固定九项顺序、包名隔离、QQ 音乐卸载保护、排队/暂停/继续/失败/安装/卸载状态、卸载失败恢复和成功后重新下载、过期回调隔离、进度及续传响应范围校验。实际第三方下载及安装验收需要先配置可信 APK 地址；不要用虚假定时器代替真实下载。

已完成首次引导的调试设备还可以运行不依赖额外测试库的页面状态检查：

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
adb -s <device> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <device> shell am instrument -w -r com.hivi.launcher.test/com.hivi.launcher.systemapps.SystemAppsUiInstrumentation
adb -s <device> pull /data/user_de/0/com.hivi.launcher/files/system-app-ui-tests
```

该检查仅在测试进程中注入展示状态，验证九项顺序、云下载标记、返回按压、所有下载/卸载状态布局、卸载菜单保护与私有回调接收器，输出默认页、返回按压和状态集合截图；不下载、安装或卸载第三方应用，也不改写真实任务状态。
