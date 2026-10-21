# HiVi 音响与手机 App UPnP 对接协议

更新日期：2026-10-16。本文对应当前仓库实现，包含角色编号、角色订阅及查询/切换接口。
播放状态的详细约定另见 [播放状态订阅](UPnP_TransportState_Subscription.md)。
壁纸与时钟切换的参数、报文及 App 接入流程见 [壁纸订阅对接文档](UPnP_Wallpaper_Subscription.md)。

## 1. 服务与发现

App 使用 SSDP 发现音响，读取设备描述及各服务的 SCPD。相对地址按设备描述的
URLBase（存在时）或描述文档 URL 解析，不硬编码 IP、端口或路径。

| 功能 | serviceType | serviceId | 地址来源 |
| --- | --- | --- | --- |
| 播放状态 | `urn:schemas-upnp-org:service:AVTransport:1` | `urn:upnp-org:serviceId:AVTransport` | 该服务的 `eventSubURL` / `controlURL` |
| 角色、壁纸、音量 | `urn:schemas-upnp-org:service:RenderingControl:1` | `urn:upnp-org:serviceId:RenderingControl` | 该服务的 `eventSubURL` / `controlURL` |

角色通知是 RenderingControl 的 `LastChange` 扩展字段 `RoleId`，不是单独的服务，
也不是 AVTransport 的 `TransportState`。两项服务需要分别订阅、分别保存 SID 和序号。
已经订阅 RenderingControl 的 App 只需扩展 LastChange 解析，不需要再建一条相同订阅。

`RoleId`、`GetRoleId`、`SetRoleId` 是本项目扩展，不是标准 UPnP 角色控制动作。
对旧固件，先检查 SCPD 是否声明这些动作；不存在时不要调用或把角色缺失解释为 1。
使用标准 RCS 严格 XSD 校验的客户端应加入 RoleId 扩展，或对该扩展采用兼容的 XML 解析。
仓库自带的扩展 XSD 为 `AudioToolLib/src/main/resources/com/ljm/audiotoollib/upnpserver/hivi-rcs.xsd`。

## 2. 角色编号

字段区分大小写：`RoleId`。类型为 `ui2`，业务范围为 1–5，逻辑实例固定为 0。
编号按音响角色选择页顺序定义，不是 Live2D 内部索引，也不是云端角色配置 ID。

| RoleId | 显示角色 | 模式/资源 | 内部 characterIndex | 云端配置 ID（仅供排查） |
| --- | --- | --- | --- | --- |
| 1 | 小威 / Xiaowei | 默认粒子，非 Live2D | 不适用 | 476 |
| 2 | 小比 / Xiaobi | Snoopy | 0 | 475 |
| 3 | 小莉 / Xiaoli | PP | 3 | 474 |
| 4 | 小默 / Xiaomo | Siren | 1 | 478 |
| 5 | 小夜 / Xiaoye | Evelyn | 2 | 479 |

App 收发使用左侧 1–5，不发送内部索引或云端 ID。音响启动时读取已保存的角色；
没有保存过 Live2D 选择时为 1。设备本地切换、云端配置切换、App 切换、退出账号或
恢复出厂导致角色偏好改变时，均同步到该字段。同一角色不重复发送通知。

## 3. 订阅生命周期

App 先启动音响可访问的 HTTP 回调监听，再发送订阅。下文示例中的尖括号占位符
由真实值替换；CALLBACK 最外侧的 `<...>` 是协议要求，不能省略。

```http
SUBSCRIBE <rendering-eventSubURL-path> HTTP/1.1
HOST: <speaker-ip>:<port>
CALLBACK: <http://<phone-ip>:<callback-port>/upnp/rendering>
NT: upnp:event
TIMEOUT: Second-1800
```

成功响应为 HTTP 200，保存响应 `SID` 和实际 `TIMEOUT`。
建议在有效期的 70%–80% 时续订；续订只带 SID/TIMEOUT，不再携带 CALLBACK/NT：

```http
SUBSCRIBE <rendering-eventSubURL-path> HTTP/1.1
HOST: <speaker-ip>:<port>
SID: uuid:<subscription-id>
TIMEOUT: Second-1800
```

离开设备或结束监听时退订：

```http
UNSUBSCRIBE <rendering-eventSubURL-path> HTTP/1.1
HOST: <speaker-ip>:<port>
SID: uuid:<subscription-id>
```

App 切换 Wi-Fi、回调 IP/端口改变、续订返回 412、订阅过期或音响重启后重新发现并
建立新订阅，不把旧 SID 用到新服务。多手机各自订阅，一台手机退订不影响其他手机。
AVTransport 使用相同流程，但目标地址和 SID 是 AVTransport 自己的。

## 4. 角色通知与解析

每条新 RenderingControl 订阅的首个事件为 `SEQ: 0`，携带当前 RoleId 和原有音量等
初始快照。之后角色偏好改变时发送增量通知，例如切换至 3：

```http
NOTIFY /upnp/rendering HTTP/1.1
HOST: <phone-ip>:<callback-port>
CONTENT-TYPE: text/xml; charset="utf-8"
NT: upnp:event
NTS: upnp:propchange
SID: uuid:<rendering-subscription-id>
SEQ: 1
```

```xml
<e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0">
  <e:property>
    <LastChange>&lt;Event xmlns="urn:schemas-upnp-org:metadata-1-0/RCS/"&gt;&lt;InstanceID val="0"&gt;&lt;RoleId val="3"/&gt;&lt;/InstanceID&gt;&lt;/Event&gt;</LastChange>
  </e:property>
</e:propertyset>
```

解析外层 XML 后，LastChange 的文本内容是：

```xml
<Event xmlns="urn:schemas-upnp-org:metadata-1-0/RCS/">
  <InstanceID val="0">
    <RoleId val="3"/>
  </InstanceID>
</Event>
```

App 接收步骤：

1. 按 SID 找到 RenderingControl 订阅；校验 NT/NTS。初始通知可能很快到达，回调应能暂存尚未完成 SID 登记的初始消息。
2. 使用禁用外部实体/DTD 的 XML 解析器解析外层 propertyset，取 LastChange 的文本，再按 RCS 命名空间解析第二层 XML；不要用字符串替换反复反转义。
3. 读取 `InstanceID val="0"` 下的 `RoleId` 的 `val` 属性，将合法的 1–5 映射到 App 当前角色。
4. 按增量更新缓存。没有 RoleId 的音量通知不能把角色重置为 1；未知字段忽略，未知编号不覆盖现有合法值。
5. 对正常事件返回 HTTP 200。回调必须可从音响访问，不能使用 localhost、移动网络地址或其他不可达地址。
6. 每个 SID 独立跟踪 SEQ。0 表示初始快照，之后递增；4294967295 后回绕到 1。丢失、重复、乱序时不要让旧值覆盖新值，必要时调用 GetRoleId 校准。

通知反映已写入本地角色偏好的选择，不表示 Live2D 渲染加载、动画切换或云端上报已全部完成。
角色不带 `channel` 属性。音量/Mute 仍按原有 `channel="Master"` 解析。

## 5. 查询当前角色

请求发往 RenderingControl 的 `controlURL`：

```http
POST <rendering-controlURL-path> HTTP/1.1
HOST: <speaker-ip>:<port>
CONTENT-TYPE: text/xml; charset="utf-8"
SOAPACTION: "urn:schemas-upnp-org:service:RenderingControl:1#GetRoleId"
```

```xml
<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
            s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:GetRoleId xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
      <InstanceID>0</InstanceID>
    </u:GetRoleId>
  </s:Body>
</s:Envelope>
```

HTTP 200 响应的 SOAP Body：

```xml
<u:GetRoleIdResponse xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
  <CurrentRoleId>3</CurrentRoleId>
</u:GetRoleIdResponse>
```

首订阅快照延迟、App 回到前台或通知序号有缺口时可查询校准。不要用一个较早发起、
较晚返回的查询响应覆盖查询期间已经收到的更新事件。

## 6. App 请求切换角色

请求仍发往 RenderingControl 的 `controlURL`：

```http
POST <rendering-controlURL-path> HTTP/1.1
HOST: <speaker-ip>:<port>
CONTENT-TYPE: text/xml; charset="utf-8"
SOAPACTION: "urn:schemas-upnp-org:service:RenderingControl:1#SetRoleId"
```

```xml
<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
            s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:SetRoleId xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
      <InstanceID>0</InstanceID>
      <DesiredRoleId>3</DesiredRoleId>
    </u:SetRoleId>
  </s:Body>
</s:Envelope>
```

成功响应为 HTTP 200，SOAP Body 为无输出参数的 `SetRoleIdResponse`。
该响应表示合法请求已经交给音响 UI 线程，不是角色切换完成确认。App 可显示待确认状态，
以后续 RoleId NOTIFY 或 GetRoleId 为准；若请求的就是当前角色，则不会额外通知。
连续点击时建议串行请求，旧响应不能覆盖新通知。确认等待超时后查询实际值，
不要直接假定成功或失败。主界面在请求接受后销毁、资源就绪状态在执行前变化等情况，
也应通过查询恢复真实选择。

| SOAP errorCode | 含义 | App 处理 |
| --- | --- | --- |
| 402 | 参数缺失、格式不正确或 DesiredRoleId 不在 1–5 | 检查参数，不更新当前角色 |
| 702 | InstanceID 不是 0 | 使用 InstanceID=0 |
| 501 | 角色控制尚未准备好，或目标 Live2D 资源未就绪 | 保持当前角色，提示稍后再试 |
| 401 | 旧固件没有该动作等 | 检查 SCPD，隐藏不支持的远程控制入口 |

错误响应通常为 HTTP 500 的 SOAP Fault；按 Fault/detail/UPnPError/errorCode 识别，
不将 HTTP 200 与收到目标 RoleId 视为同一件事。HTTP 客户端应自动生成正确 Content-Length。

## 7. 播放状态与角色同步

播放订阅仍使用 AVT 命名空间 `urn:schemas-upnp-org:metadata-1-0/AVT/`，角色订阅使用
RCS 命名空间 `urn:schemas-upnp-org:metadata-1-0/RCS/`。业务层分别更新播放状态和角色，
不要混用两项服务的 SID、SEQ 或命名空间。

播放字段为 `TransportState`、`TransportStatus`、`CurrentTransportActions`。
错误优先根据 `TransportStatus=ERROR_OCCURRED` 判断；准备态是 `TRANSITIONING`，
不发送非标准 `LOADING` 或 `ERROR` 作为 TransportState。进度已增加差异通知，既有查询接口仍作为兜底。

## 8. 联调验收

1. 分别在角色 1–5 下首次订阅 RenderingControl，确认 SEQ=0 快照、GetRoleId 与设备角色一致。
2. 音响旋钮切换全部角色；App 无需轮询即可收到对应 RoleId，重复选择同一角色不重复通知。
3. App 调用 SetRoleId 切换全部角色，确认设备选择、通知、查询及 App 显示一致。
4. 默认粒子与 Live2D 互切时不出现临时角色编号；快速 2→3→4→5→1 后最终值为 1。
5. 云端角色配置改变、退出账号、恢复出厂后核对角色变化通知；重启后核对保存值的初始快照。
6. 两部手机同时订阅；双方收到切换，一方续订或退订不影响另一方。
7. DesiredRoleId=0/6、InstanceID=1、Live2D 资源缺失分别得到约定错误，不把目标角色当作已切换。
8. 音量通知不重置角色；角色切换不覆盖播放状态；Wi-Fi 切换及音响重启后能重新订阅。

自动测试覆盖角色映射、SCPD、XML 校验、首次/增量 NOTIFY、快速切换、多手机序号、
重复值、查询/切换动作、错误、退订及服务关闭。测试构造 GENA 通知，不代替真实网络和手机联调。

```powershell
$env:JAVA_HOME = 'C:/Users/longjm/.jdks/ms-17.0.15'
.\gradlew.bat :AudioToolLib:testDebugUnitTest :app:compileCommonDebugJavaWithJavac --offline --console=plain
```

日志关注 `AudioRenderServiceImpl`（RoleId 发布）、`DLNARendererService`（事件调度异常）
及 Cling GENA 的发送日志。手机 App 源码不在本仓库，App 解析接入和实机验证由双方按本文联调。

## 9. 手机订阅字段补齐（2026-10-14）

三个服务分别订阅，SID/SEQ 独立。外层 GENA propertyset 的 `LastChange` 文本解码后，
按对应服务解析下面的 XML；增量事件只合并出现的字段，不能将缺失字段清空。

| 服务 | LastChange 命名空间 / 子节点 | 首次快照及持续通知字段 |
| --- | --- | --- |
| RenderingControl | `urn:schemas-upnp-org:metadata-1-0/RCS/` / `InstanceID val="0"` | `Volume`（channel=Master）、`Mute`、`RoleId`、`DeviceName`、`GroupName`；`Route` 仅真实业务上报后提供 |
| AVTransport | `urn:schemas-upnp-org:metadata-1-0/AVT/` / `InstanceID val="0"` | 原有播放状态/错误/可执行动作，加 `CurrentTrack`、`CurrentTrackDuration`、`CurrentMediaDuration`、`RelativeTimePosition`、`CurrentTrackURI`、`AVTransportURI`、两项 `*MetaData`、`PlaybackStorageMedium`、`TrackSource`；`A_ARG_TYPE_SeekTarget` 仅接受 Seek 后通知 |
| PlayQueue | `urn:schemas-wiimu-com:metadata-1-0/PlayQueue/` / `QueueID val="0"` | `CurrentPlayListName`、`LoopMode`、`CurrentIndex` |

PlayQueue 原先误用 RCS 命名空间、InstanceID 和空初始快照，现已修正。
设备输出正确拼写 `CurrentPlayListName` / `LoopMode`，不发送兼容旧固件的错误拼写。
CurrentIndex 为零起点；空队列为 0。LoopMode 沿用播放器值：0 列表循环、1 单曲循环、2 随机。

播放状态仍由真实业务回调立即上报；本地 RoleId 切换在角色上传接口确认业务成功后通知；队列 SOAP 动作提交后立即调度通知。
本地旋钮、HTTP 音量、自然切歌、AI 队列变化及媒体时长/进度由主线程每秒读取真实状态，
串行进入各自服务管理器后只发送变化字段。发送执行器尚有采样任务时跳过下一轮，避免积压。
暂停后相同进度不重复通知。首次采样可能额外发送一次完整字段集合。

媒体时长来自播放器，未就绪为 `00:00:00`；清空队列后清除旧 URI/元数据/时长，
PlaybackStorageMedium 为 NONE，有网络媒体时为 NETWORK。元数据按曲目原值发送，
可能是 DIDL-Lite 或现有业务元数据，XML 属性和外层 LastChange 分层转义。
TrackSource 优先使用曲目 Source；无 Source 时 AI 队列为 AI，现有 UPnP 队列为 DLNA，
无媒体为 NONE。视频继续沿用标准 AVTransport 查询身份，GetInfoEx 仍不返回视频元数据。

Seek 仅接受 REL_TIME / ABS_TIME，目标格式 H+:MM:SS（允许小数秒）；动作被控制器接受后
通知 `A_ARG_TYPE_SeekTarget`。它表示接受的目标，不是已经完成定位的进度；实际位置以
RelativeTimePosition 或 GetPositionInfo 为准。首次订阅和周期采样不重放旧 SeekTarget，
避免把上一首歌曲的定位目标误当当前进度。非法模式/目标返回 710/711，不发送目标通知。

图片示例 Route=`0` 没有提供含义或数值映射，现有代码也无对应真实状态。
`AudioRenderServiceImpl.reportRouteChange(route)` 支持原值事件及后续订阅快照；未上报前省略该字段，
不能将独立的 AudioMode（WIFI / BT）擅自映射成 Route。
当前单机固件 GroupName 与 DeviceName 一致，没有真实组名/从机成员状态。
`AudioRenderServiceImpl.reportSlaveChange(action, uuid, volume, timestamp)` 已支持图片中的
Slave XML 属性，逐个 UUID 独立通知，避免同一批事件覆盖多个从机。
**实际多房间管理尚未实现，当前不会自动发送 Slave 事件，也不会伪造首次成员列表。**
以后接入真实分组业务时需同时补齐组名来源和首次成员快照。

改名保留原 LocalDevice 和服务管理器，刷新动态 friendlyName、描述符缓存和 SSDP alive，
现有订阅继续接收 DeviceName / GroupName；服务重启仍需手机重新订阅。
设备主线程读取不会等待服务管理器锁，销毁时移除采样任务并丢弃关闭服务的排队通知。

补充自动测试：`EventFieldSubscriptionTest`，覆盖三服务首次/增量 GENA、PlayQueue 双订阅、
快速切换、差异抑制、特殊字符、Slave 属性、Seek 成功/失败、队列清空和关闭清理。
真机仍需核对：三服务分别订阅、本地与手机操作双向同步、改名后 SID 保持有效、
进度与播放器一致，以及 Route 业务来源和手机协议值的约定。

### 9.1 RoleId 无通知反馈的实机排查（2026-10-14）

已对当前连接的音箱 `192.168.3.191`（1.0.33，设备名 QQ10023）执行真实 HTTP 订阅，
并通过电脑局域网 CALLBACK 接收 NOTIFY，未依赖手机端解析器：

- SUBSCRIBE 返回 200；首次 NOTIFY 为 `SEQ=0`，RCS LastChange 内含 `RoleId=1`。
- 角色实际切换到 4 后，同一 SID 收到 `SEQ=1`，LastChange 内含 `RoleId=4`，GetRoleId 同时返回 4。
- 另一个独立测试订阅也收到相同变化；续订返回 200 并保留 SID。
- 原始局域网报文保存于本次工作区的 `build/diagnostics/role-gena-device-20261014.json`。

因此本次实测没有复现“音箱完全不发通知”，尚不能确定手机反馈的具体根因。
新增设备日志记录订阅 accepted/active/renewed/ended，以及角色 NOTIFY 的 SID、SEQ、
CALLBACK 和 HTTP 结果。`delivered HTTP=200` 仅说明回调 HTTP 服务确认接收，不能证明手机业务已解析或刷新。
日志改动需部署新构建后才能在设备生效；上述实机结果来自修改前已安装的固件。

手机端排查顺序：

1. 提供原始 SUBSCRIBE 请求与响应，确认订阅设备描述符中的 RenderingControl eventSubURL，而非 action/desc 或 AVTransport。
2. CALLBACK 必须是音箱可访问的手机局域网 IP、监听端口和路径，监听器先启动再订阅；手机的 localhost/127.0.0.1 不是手机的局域网地址。
3. 订阅 200 后应检查 `SEQ=0` 的原始 NOTIFY；若一个都没有，先核对 CALLBACK、监听器生命周期、网络隔离及音箱发送结果，不先改角色解析。
4. NOTIFY 到达后先解析外层 propertyset，取 LastChange 文本，再按 RCS 命名空间解析 `InstanceID val="0"` 内的 `RoleId val="..."`；不是外层独立的 RoleId property，也不是 AVT 字段。
5. 手机若使用固定字段白名单或不支持自定义 RCS 变量的解析器，需保留原始 LastChange 并补充 RoleId 解析；检查 XML 名称大小写、SID 路由及 SEQ 初始值处理。
6. 三服务 SID 必须独立管理并按返回 TIMEOUT 续订；切网、设备重启或续订 412 后重订阅，不继续等待旧 SID。

`RoleId` 在 SCPD 中 sendEvents=no 是因为它聚合在 sendEvents=yes 的 LastChange 中，
不能据此过滤 LastChange 内字段。协议 RoleId 为 1~5；云端 aiRoleId/selectId 为
476/475/474/478/479，对应协议 1/2/3/4/5，不能直接比较这两套数字。
云端轮询可保留为兜底，但不证明手机 GENA 回调路径正常。

### 9.2 本地角色通知与云端上传时序（2026-10-15）

音箱本地选择角色及 App 下发 SetRoleId 时，按以下顺序处理：

1. 更新本地角色偏好和 GetRoleId 读值，不立即发送角色变更 NOTIFY。
2. 默认粒子直接发起上传；Live2D 加载成功后发起 `uploadVisualInfo(type=2, selectId=...)`。
3. 上传响应必须同时满足 `code=200`、`success=true`，才发送本次角色变更 NOTIFY。

未登录、加载失败、网络失败、业务失败或响应无法解析时，不发送此次变更通知；本地角色不回滚。
连续切换使用版本号绑定上传回调，旧角色加载完成或旧上传回调不再触发当前角色的通知；
重复成功回调不重复通知。云端已保存的配置下发仍按原路径应用，不再次上传。
GetRoleId 与新订阅的 SEQ=0 初始快照仍反映本地当前角色，不作为云端上传成功凭证。

验收日志顺序应为 `uploadVisualInfoToServer ok type=2`、`role upload confirmed=true`，
随后 `publish RenderingControl role event` / `GENA NOTIFY`；事件线程异步调度，
`role upload confirmed` 与 `publish` 两条日志的相对先后不固定，但都必须晚于上传成功日志。

## 10. 壁纸与时钟切换订阅

RenderingControl 的 LastChange 新增 `WallpaperId`（ui4）、`WallpaperSource`（string）、
`WallpaperIds`（JSON 数组字符串）、`ClockId`（ui4）、`ClockStyle`（ui4）。
复用角色订阅 SID，InstanceID 固定为 0；每次桌面变更五个字段一起发送。
`WallpaperId` 直接对应服务端 type=6 资源 ID / selectId，与 RoleId 的 1–5 协议编号不同。
轮播的 WallpaperId 为列表首项，不是当前播放帧；有序列表、来源或时钟款式变化会发送增量通知。
时钟用 `WallpaperSource=art_clock` 区分，WallpaperId=0、WallpaperIds=[]；
三款时钟的 ClockId / ClockStyle 分别为：透视 589/1、十二边形 588/2、视频 591/3（第三款旧文档中的 590 已更正）。
ClockId 对应 type=7 资源 ID / selectId，不能混用为 type=6。非时钟模式两个时钟字段均为 0。

本机选择按“本地应用完成 → 壁纸上传 type=6 / 时钟上传 type=7 → 空 selectId 清空另一类互斥配置 → 通知 App”处理，
两次响应均须确认 code=200 且 success=true 后才更新确认快照并通知；旧请求回调不能确认新选择，任一步失败不通知。云端已保存配置实际加载应用后通知，不重复上传。
桌面初始快照及 `GetWallpaperId`、`GetClockId`、`GetClockStyle` 只返回最近确认状态，
未完成上传时不暴露本机新选择；启动暂无已应用快照时为 unknown / 0，时钟字段也为 0。

App 收到桌面事件后，应独立解析 LastChange 内五个字段并刷新页面，不能要求同条事件带 RoleId。
被动状态同步不得触发保存接口或 SetAudioBackground；较早发起的详情查询不能覆盖较新的设备事件。
HTTP 200 仅证明回调地址已响应，仍需核对字段解析、状态提交及 UI 刷新。

完整字段取值、预设资源映射、XML/SOAP 示例、手机解析及验收说明见
[壁纸与时钟切换订阅：手机 App 对接约定](UPnP_Wallpaper_Subscription.md)。

## 11. 参考

- UPnP Device Architecture 2.0，Eventing：https://openconnectivity.org/upnp-specs/UPnP-arch-DeviceArchitecture-v2.0-20200417.pdf
- UPnP RenderingControl 的 LastChange 机制：https://www.upnp.org/specs/av/UPnP-av-RenderingControl-v3-Service-20101231.pdf
- 项目动作定义：`AbstractRenderingControl.java`；角色来源与映射：`RoleController.java`。
- 本文角色字段和动作是 HiVi 项目约定，参考规范不定义这些扩展。
