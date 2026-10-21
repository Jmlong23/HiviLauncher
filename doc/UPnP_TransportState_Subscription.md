# AVTransport 播放状态订阅联调

## 对齐范围

依据手机端提供的《Wi-Fi 音箱 UPnP GENA 订阅事件说明》截图，对齐
`urn:upnp-org:serviceId:AVTransport` 的播放状态通知。本仓库不包含截图提到的
`packages/hivi_upnp_subscription` 手机端源码，以下是音响端实际实现和联调约定。

角色切换扩展及 App 完整对接流程见 [App 对接协议](UPnP_App_Integration.md)。

`TransportState` 不单独开启 `sendEvents`，而是作为 `LastChange` 内的字段发送。
`LastChange` 的 XML 命名空间为 `urn:schemas-upnp-org:metadata-1-0/AVT/`，
逻辑实例为 `InstanceID val="0"`。

## 订阅与通知

手机从设备描述中的 AVTransport 服务读取 `eventSubURL`，不要硬编码路径或端口。
首次订阅请求示例：

```http
SUBSCRIBE <eventSubURL> HTTP/1.1
HOST: <speaker-ip>:<port>
CALLBACK: <http://<phone-ip>:<callback-port>/<callback-path>>
NT: upnp:event
TIMEOUT: Second-1800
```

成功后保存响应中的 `SID` 和实际 `TIMEOUT`。续订使用同一 `eventSubURL`、
`SID` 和 `TIMEOUT`，不携带 `CALLBACK`/`NT`；退订使用 `UNSUBSCRIBE` 和 `SID`。
订阅、续订、退订及序号管理继续由仓库内 Cling GENA 实现处理。

每条新订阅的初始通知携带当前状态快照，序号为 `SEQ: 0`；后续状态变化沿用该订阅的
SID 并递增序号。通知发往各手机独立的 CALLBACK，各订阅互不替代。

```http
NOTIFY <callback-path> HTTP/1.1
CONTENT-TYPE: text/xml; charset="utf-8"
NT: upnp:event
NTS: upnp:propchange
SID: uuid:<subscription-id>
SEQ: <sequence>
```

消息体包含 `e:propertyset / e:property / LastChange`。`LastChange` 是经过 XML
转义的文本，不是直接嵌套的 Event 节点。手机解析外层 XML，再解析 LastChange 文本：

```xml
<e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0">
  <e:property>
    <LastChange>&lt;Event xmlns="urn:schemas-upnp-org:metadata-1-0/AVT/"&gt;&lt;InstanceID val="0"&gt;&lt;TransportState val="PLAYING"/&gt;&lt;TransportStatus val="OK"/&gt;&lt;CurrentTransportActions val="Stop,Pause,Seek"/&gt;&lt;/InstanceID&gt;&lt;/Event&gt;</LastChange>
  </e:property>
</e:propertyset>
```

## 播放状态

| 音响实际状态 | TransportState | TransportStatus | 手机端业务标签 |
| --- | --- | --- | --- |
| 准备资源、准备完成但还未开始播放 | TRANSITIONING | OK | load |
| 播放器实际开始或恢复播放 | PLAYING | OK | play |
| 已暂停 | PAUSED_PLAYBACK | OK | pause |
| 已停止，仍有当前曲目 | STOPPED | OK | stop |
| 队列和当前媒体已清空 | NO_MEDIA_PRESENT | OK | no_media_present |
| 播放器出错 | STOPPED 或 NO_MEDIA_PRESENT | ERROR_OCCURRED | error |

设备不发送非标准 `LOADING`、`STOP` 或 `ERROR` 作为 TransportState。
错误通过 `TransportStatus=ERROR_OCCURRED` 表示，手机应优先识别该错误状态。

每次播放状态通知同时携带 `TransportState`、`TransportStatus`、
`CurrentTransportActions`。无媒体时动作列表为空；暂停时支持 `Play,Seek,Stop`。
相同状态和相同错误状态不会重复通知；即使 TransportState 未变，错误或错误恢复也会通知。

## 音响实现

- `UpnpMusicFragment.markPlaybackState()` 汇总准备、播放、暂停、停止、完成、错误回调，主动上报控制器，无需等待手机查询。
- 清空并移除音乐播放器后，MainPresenter 主动上报 `NO_MEDIA_PRESENT`。
- 音频 Play/Pause/Stop 以播放器真实回调为准，不在收到控制命令时假报已完成；Stop 实际调用音乐播放器停止逻辑。
- AVTransportController 保留视频状态上报，并阻止音频回调覆盖正在投屏的视频状态。
- AVTransportServiceImpl 经串行执行器，在对应服务管理器锁内生成和触发 LastChange，避免播放器线程直接等待订阅通知锁。
- AVTransport 的 LastChange 不再使用原有 200ms 丢弃式限流，避免快速准备完成或暂停后恢复时丢掉最终状态。
- 新订阅继续使用 LastChangeAwareServiceManager 和 appendCurrentState 生成初始快照；服务销毁/替换时解除旧监听并丢弃旧服务待发事件。

2026-10-14 补齐手机订阅字段后，增加每秒差异采样的媒体/进度通知，并修正 PlayQueue
命名空间、QueueID 初始快照及队列变化通知；完整字段矩阵见 `UPnP_App_Integration.md` 第 9 节。
后续角色功能扩展 RenderingControl 的 `LastChange`，添加 `RoleId` 与 `GetRoleId` / `SetRoleId`，
使用独立的 RenderingControl 订阅；与本文 AVTransport 播放状态订阅互不混用。
初始快照和增量事件包含时长、进度、曲目 URI/元数据、存储介质及来源。
接受的 SeekTarget 仅作为命令增量事件通知，不保留到初始快照或周期采样中；实际进度仍以播放器状态为准。
手机端仍应保留 `GetInfoEx` / `GetPositionInfo` 查询兜底。

## 验证

自动回归测试：`AudioToolLib/src/test/java/com/ljm/audiotoollib/upnpserver/service/AVTransportSubscriptionTest.java`。
覆盖初始快照和 XML/NOTIFY 线包、快速状态变化、多订阅、重复状态抑制、错误恢复、可执行动作、退订、音视频隔离、停止回调、服务关闭。

```powershell
.\gradlew.bat :AudioToolLib:testDebugUnitTest :app:compileCommonDebugJavaWithJavac
```

设备与手机联调检查：

1. 空队列订阅：收到 `SEQ=0`、`NO_MEDIA_PRESENT` 和空动作列表；播放中订阅：初始快照为 `PLAYING`。
2. 手机发起播放、设备本地暂停/恢复、AI 打断、蓝牙提示音打断：观察实际状态改变后主动 NOTIFY，不依赖轮询。
3. 快速切换 `TRANSITIONING -> PLAYING`、连续暂停/恢复：最后一条通知与 `GetTransportInfo` 一致。
4. 自然播放完成/切歌、Stop、清空队列：分别确认 STOPPED、后续准备/播放以及无媒体状态。
5. 使用不可播放资源触发播放器错误：收到 ERROR_OCCURRED；下一次正常播放时恢复 OK。
6. 两部手机同时订阅：双方都收到变更；一方续订或退订不影响另一方。
7. 视频投屏期间暂停音频播放器：视频的 PLAYING/PAUSED_PLAYBACK 不被音频覆盖。
8. 服务重启后重新订阅：初始快照正确，旧服务不继续发送状态事件；设备改名保留既有订阅，收到新名字且能继续接收播放通知。

日志关注 `AVTransportController`、`AVTransportServiceImpl`、`DLNARendererService`，
分别核对实际状态变化、LastChange 发布及发送任务异常。手机端还需确认 CALLBACK 可从音响访问并返回成功响应。
