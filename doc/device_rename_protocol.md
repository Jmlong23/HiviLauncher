# 修改 WiFi 设备名字协议（setDeviceName）对接文档

> 目标读者：需要对接「修改音箱/WiFi 设备名称」能力的客户端（或 AI 代码生成助手）。
> 结论先行：本项目使用 **设备局域网 HTTP API（Linkplay 风格 `httpapi.asp`）**，`HTTP GET`
> 一条命令即可完成改名。**没有** UPnP SOAP 等价命令；MQTT 通道有一条镜像命令（见 §5）。

---

## 1. 协议定义

### 请求

```
GET http://<host>/httpapi.asp?command=setDeviceName:<urlEncoded(name)>
```

| 要素 | 规定 |
|------|------|
| 方法 | `HTTP GET` |
| `<host>` | 设备地址，解析优先级见 §2 |
| 命令前缀 | `setDeviceName:` **不编码**，只有名称部分做 URL 编码 |
| 名称编码 | `Uri.encodeComponent(name)`（Dart）/ `encodeURIComponent(name)`（JS）同义：保留中文原文编码为 `%E4%B8%AD...`，`/` → `%2F`，空格 → `%20` |
| 超时 | 20 秒（`AppConstants.lanDeviceHttpTimeout`） |
| 请求类别 | 控制类（不以 `get` 开头，App 内部归为 `httpControl`） |

### 响应

| 情形 | 判定 |
|------|------|
| HTTP 状态码 `200` | **成功**。响应体可能为空或 `OK`，客户端**不解析 body 内容** |
| 非 200 / 超时 / 连接异常 | 失败 |

响应体解码规则：优先按 UTF-8 解码，失败回退 latin1（部分旧固件响应头缺 charset）。改名命令本身不依赖 body。

### 示例

改名「客厅音箱」：

```
GET http://192.168.1.100/httpapi.asp?command=setDeviceName:%E5%AE%A2%E5%8E%85%E9%9F%B3%E7%AE%B1
```

英文名 `My Speaker`：

```
GET http://192.168.1.100/httpapi.asp?command=setDeviceName:My%20Speaker
```

---

## 2. `<host>` 解析优先级（必须实现，否则部分机型不通）

1. **实测命中端口缓存**：发现阶段会探测候选端口 `[80, 8080]`，命中的端口后续固定使用。
   - Linkplay 系列（SWAN MT1 / HiVi H8 等）：`80`
   - 部分自研固件（HiVi MG100 等）：`8080`
2. **`hardware=ANDROID_AUDIO` 设备**：从 `getStatusEx` 缓存取 `Apcli0` + `Communication_port`，
   拼 `http://<Apcli0>:<Communication_port>/httpapi.asp?...`（`Apcli0` 为空时回退设备 IP）。
3. **兜底**：纯设备 IP（隐含 80 端口）。

---

## 3. 客户端侧输入校验（发送前必须完成）

| 校验 | 规则 | 失败处理 |
|------|------|----------|
| 非空 | `name.trim()` 后不得为空串 | 拒绝发送，报 `emptyName` |
| 禁止 `+` | 名称含 `+` 直接拒绝 | 拒绝发送，报 `plusNotAllowed`（`+` 在 query 中会被解析为空格，导致固件收到错误名称） |
| 长度限制 | 含中文（Unicode `\u4E00-\u9FFF`）≤ **12** 字符；纯英文/数字/符号 ≤ **24** 字符；中英混合因含中文按 12 计 | 输入阶段即截断，不发送 |

长度规则与 Android 原生 `EditDeviceNameActivity` 的 `InputFilter` 一致，见
`lib/data/utils/device_name_input_utils.dart`。

---

## 4. 改名后必须知道的固件行为（对接方最容易踩的坑）

Linkplay 固件收到 `setDeviceName` 后：

1. **先回 HTTP ACK，再重启 SSDP**（先 `ssdp:byebye` 后 `ssdp:alive`），且重启期间
   HTTP/UPnP 发现载荷可能仍回放**旧名称**。
2. 因此客户端的时序要求：
   - **发送前**就打开「改名保护窗」（本项目为 **45 秒**）；
   - **收到 200 后立刻**把新名称写入本地真值（设备列表、播放条快照）；
   - 保护窗内忽略 discovery/轮询带回的旧名称覆盖，避免「改名后设备消失」或「名字被弹回旧值」。
3. 保护窗到期后恢复正常以设备上报为准。

不对接设备发现机制、只做单次改名的话，仅需保证：HTTP 200 即认为成功并展示新名称。

---

## 5. MQTT 通道等价命令（仅老 Android / MQTT 模式使用）

局域网不可达时，原生端有一条 MQTT 镜像命令，语义与 HTTP 版一致：

```json
{
  "action": "deviceinfo",
  "command": "setDeviceName",
  "dataSource": "app",
  "data": { "setDeviceName": "<name>" }
}
```

纯 Flutter 新链路**只走 HTTP**，MQTT 仅作为历史通道保留。

---

## 6. 参考实现位置（hivi_network_flutter_core）

| 内容 | 位置 |
|------|------|
| HTTP 命令实现 `setDeviceName` | `lib/data/services/device_http_service.dart:569` |
| URL 拼装 `_buildUrl` | `lib/data/services/device_http_service.dart:380` |
| 响应判定/超时 `_httpGet` | `lib/data/services/device_http_service.dart:170` |
| 上层端口（校验 + 保护窗时序）`updateDeviceName` | `lib/data/services/pure_flutter_device_setting_port.dart:247` |
| MethodChannel 处理器（Android 混合链路）`httpSetDeviceName` | `lib/channels/upnp_method_channel.dart:1359` |
| 输入长度规则 | `lib/data/utils/device_name_input_utils.dart` |
| 改名保护窗（45s） | `lib/data/services/device_manager_service.dart:1532`，常量 `:589` |
| Android 原生等价实现 | `hivi-network-android/.../io/flutter/adapter/SWDeviceUtils.java:747` |
| 完整协议总表 | `docs/wifi_speaker_protocol_reference.md` §7.1 / §8.4 |

---

## 7. 对接检查清单（给 AI 的硬性要求）

- [ ] 使用 `HTTP GET`，URL 严格为 `http://<host>/httpapi.asp?command=setDeviceName:<encoded>`，命令前缀不编码
- [ ] 仅对名称部分做 component 级 URL 编码（空格→`%20`，不是 `+`）
- [ ] 支持 host 解析三级回退：实测端口 → `Apcli0:Communication_port` → 纯 IP:80
- [ ] 超时 20s；HTTP 200 即成功，不解析 body
- [ ] 发送前执行 §3 三项校验（非空 / 禁 `+` / 长度 12 或 24）
- [ ] 成功后本地立即展示新名，45s 内忽略旧名回放（如对接设备发现）
- [ ] 不要用 UPnP SOAP 改名（固件无此能力）
