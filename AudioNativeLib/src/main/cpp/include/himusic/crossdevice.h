/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 * Description: Cross Device header file
 * Author: yangchao
 * Create: 2025-06-26
 */

#ifndef CROSSDEVICE_H
#define CROSSDEVICE_H

#include <cstdint>
#include <cstdbool>

#ifdef __cplusplus
extern "C" {
#endif

#define LIB_CROSSDEVICE_API __attribute__((visibility("default")))
#define CROSS_DEVICE_HANDLE void*

/* enumeration definition */
typedef enum {
    CROSS_DEVICE_HICAR = 0,
    CROSS_DEVICE_HIMUSIC = 1,
} DeviceType;

typedef enum {
    DEVICE_HICAR_DEFAULT = 0,
    DEVICE_HICAR_DONGLEA = 1,
    DEVICE_HICAR_DONGLEB = 2,
    DEVICE_HICAR_VISION = 3,
    DEVICE_HICAR_FRONT = 4,
    DEVICE_HIMUSIC_DEFAULT = 20,
    DEVICE_HIMUSIC_BOX = 21,
} DeviceSubType;

typedef enum {
    DEVICE_CONNECTED = 101,
    DEVICE_CONNECTING = 102,
    DEVICE_DISCONNECTED = 103,
    DEVICE_DISCONNECTING = 104,
    DEVICE_NO_DATA = 105,
    DEVICE_DETECT_DISCONNECT = 106,
    DEVICE_NET_UNSTABLE_MILD = 107,
    DEVICE_TCP_CONNECTED = 108,
    DEVICE_CONNECT_TIMEOUT_FOR_REPORT = 109,
    DEVICE_MANUAL_DISCONNECT = 110,
    DEVICE_ADV_TIMEOUT = 111,

    DEVICE_SERVICE_UPDATE = 201,
    DEVICE_SERVICE_PAUSE = 202,
    DEVICE_SERVICE_RESUME = 203,
    DEVICE_SERVICE_START = 205,
    DEVICE_SERVICE_STOP = 206,
    DEVICE_DISPLAY_SERVICE_PLAYING = 207,
    DEIVCE_AUDIO_SERVICE_PLAYING = 208,

    /* DEVICE_HICAR 3xx */
    DEVICE_HICAR_OPEN_CAMERA = 301,
    DEVICE_HICAR_CLOSE_CAMERA = 302,
    DEVICE_HICAR_OPEN_SPEAKER = 303,
    DEVICE_HICAR_CLOSE_SPEAKER = 304,
    DEVICE_HICAR_OPEN_MIC = 305,
    DEVICE_HICAR_CLOSE_MIC = 306,

    DEVICE_HICAR_VIRMODEM_CALLING = 307,
    DEVICE_HICAR_VIRMODEM_HANGUP = 308,
    DEVICE_HICAR_VOIP_CALLING = 309,
    DEVICE_HICAR_VOIP_HANG_UP = 310,

    /* DEVICE_HIMUSIC 4xx */
    DEVICE_HIMUSIC_PLAYBACK_INITIAL = 401,
    DEVICE_HIMUSIC_PLAYBACK_PREPARE = 402,
    DEVICE_HIMUSIC_PLAYBACK_PLAY = 403,
    DEVICE_HIMUSIC_PLAYBACK_PAUSE = 404,
    DEVICE_HIMUSIC_PLAYBACK_FAST_FORWARD = 405,
    DEVICE_HIMUSIC_PLAYBACK_REWIND = 406,
    DEVICE_HIMUSIC_PLAYBACK_STOP = 407,
    DEVICE_HIMUSIC_PLAYBACK_COMPLETED = 408,
    DEVICE_HIMUSIC_PLAYBACK_RELEASED = 409,
    DEVICE_HIMUSIC_PLAYBACK_ERROR = 410,
    DEVICE_HIMUSIC_PLAYBACK_IDLE = 411,
    DEVICE_HIMUSIC_PLAYBACK_BUFFERING = 412,
} DeviceEvent;

typedef enum {
    DEVICE_OBJECT_REMOTE_CTRL = 1,
} DeviceObjectType;

typedef enum {
    DEVICE_REQ_REMOTE_CTRL = 1,
    DEVICE_REQ_VOICE_WAKEUP,
    DEVICE_REQ_REQUEST_IDRFRAME,
    DEVICE_REQ_LOG_SECTION_CHANGED,
} DeviceRequestType;

typedef enum {
    DEVICE_DATA_UNKNOWN = 0,
    DEVICE_DATA_VEHICLE_CONTROL = 500,
    DEVICE_DATA_DAY_NIGHT = 501,
    DEVICE_DATA_BRAND_ICON_DATA = 502,
    DEVICE_DATA_NAV_FOCUS = 503,
    DEVICE_DATA_CALL_STATE_FOCUS = 504,
    DEVICE_DATA_VOICE_STATE = 505,
    DEVICE_DATA_DRIVING_MODE = 506,
    DEVICE_DATA_CAR_STATE = 507,
    DEVICE_DATA_SERVICE_CHANNEL = 508,
    DEVICE_DATA_KEYCODE = 509,
    DEVICE_DATA_SENSOR_DATA = 510,
    DEVICE_DATA_NET_SERVICE = 511,
    DEVICE_DATA_BLUETOOTH = 512,
    DEVICE_DATA_SCREEN_SERVICE = 513,
    DEVICE_DATA_AA = 514,
    DEVICE_DATA_AA_MEDIA = 515,
    DEVICE_DATA_AA_CALL = 516,
    DEVICE_DATA_AA_NAVIGATION = 517,
    DEVICE_DATA_MANUAL_DISCONNECT = 518,
    DEVICE_DATA_VOICE_PROMPT = 519,
} DeviceDataType;

typedef enum {
    DEVICE_CONNECT_UNKNOWN = 0,
    DEVICE_CONNECT_WIFI = 3,
    DEVICE_CONNECT_USB = 6,
} DeviceConnectType;

typedef enum {
    DEVICE_STATUS_DISCONNECTED = 0,
    DEVICE_STATUS_CONNECTED = 1,
} DeviceConnectStatus;

typedef enum {
    DEVICE_MEDIA_INFO = 0,
    DEVICE_MEDIA_POSITION = 1,
} DeviceMediaType;

/* structure definition */
typedef struct {
    char *data;
    uint32_t dataLen;
} DataUnit;

typedef struct {
    uint8_t supportWlan;
    uint8_t supportAdvWifi;
    uint8_t supportQrWifi;
    uint8_t supportUsb;
    uint8_t supportNfc;
} DeviceConnectCapability;

typedef struct {
    DataUnit deviceModel;
    DataUnit manufacturer;
    DataUnit model;
    DataUnit os;
    DataUnit cpu;
    DataUnit ram;
    DataUnit rom;
    DataUnit screenSize;
    DataUnit screenMetrics;
    DataUnit linuxKernel;
    DataUnit btChip;
    DataUnit wifiChip;
} FeatureInfo;

typedef struct {
    DeviceType type;
    DeviceSubType subType;
    DataUnit modelId;
    DataUnit brMac;
    DataUnit deviceId;
    DataUnit deviceBrand;
    DeviceConnectCapability connectCapability;
} DeviceBasicInfo;

typedef struct {
    char *key;
    uint32_t keyLen;
    char *value;
    uint32_t valueLen;
} DeviceExtraInfo;

typedef struct {
    uint32_t num;
    DeviceExtraInfo *extraInfo;
} DeviceExtraInfos;

typedef struct {
    int32_t codecs;
    int32_t fps;
    int32_t gop;
    int32_t bitrate;
    int32_t minBitrate;
    int32_t maxBitrate;
    int32_t dpi;
    int32_t profile;
    int32_t level;
    int32_t screenWidth;
    int32_t screenHeight;
    int32_t width;
    int32_t height;
} DisplayCapability;

typedef struct {
    DeviceBasicInfo basicInfo;
    FeatureInfo featureInfo;
    DisplayCapability displayCapability;
} DeviceConfig;

typedef void (*DeviceLogFunc)(const char *tag, const char *funcName, const char *format, ...);

typedef struct {
    DeviceLogFunc logD;
    DeviceLogFunc logI;
    DeviceLogFunc logW;
    DeviceLogFunc logE;
} DeviceLogGroup;

typedef struct {
    uint16_t length;
    uint8_t screenRotation;
    uint8_t hasNavigationKey;
    char pack[1];
} VideoFrameInfo;

typedef struct {
    DataUnit phoneId;
    DataUnit phoneName;
    DataUnit phoneBrMac;
    int64_t lastConnectTime;
    uint32_t connectStatus;
} TrustPhoneInfo;

typedef struct {
    int32_t position;
    int32_t bufferPosition;
    int32_t duration;
} MediaPosition;

typedef struct {
    DataUnit assetId;          // 媒体ID
    DataUnit title;            // 标题
    DataUnit artist;           // 艺术家
    DataUnit author;           // 专辑作者
    DataUnit avQueueName;      // 歌单(歌曲列表)名称
    DataUnit avQueueId;        // 歌单唯一标识id
    DataUnit avQueueImage;     // 歌单封面图
    DataUnit avQueueImageUri;  // 歌单封面图uri
    DataUnit album;            // 专辑名称
    DataUnit writer;           // 词作者
    DataUnit composer;         // 曲作者
    int32_t duration;          // 媒体时长
    DataUnit mediaImage;       // 媒体图片
    DataUnit imageType;        // 媒体图片类型
    DataUnit mediaImageUri;    // 媒体图片uri
    DataUnit publishDate;      // 发行日期
    DataUnit subTitle;         // 子标题
    DataUnit description;      // 媒体描述
    DataUnit lyric;            // 媒体歌词内容
    DataUnit previousAssetId;  // 上一首媒体ID
    DataUnit nextAssetId;      // 下一首媒体ID
    int32_t skipIntervals;    // 快进快退支持的时间间隔
    int32_t filter;           // 当前session支持的协议
    int32_t mediaLength;       // 媒体长度
    int32_t avQueueLength;     // 歌单长度
    int32_t displayTags;      // 媒体资源金标类型
    DataUnit drmSchemes;       // 当前session支持的DRM方案
    DataUnit bundleIcon;       // 应用图标是否存在
    DataUnit bundleIconUri;    // 应用图标uri
    DataUnit singleLyricText;  // 单条媒体歌词内容
} MediaInfos;

typedef struct {
    void (*GetDeviceHandle)(CROSS_DEVICE_HANDLE handle);
    void (*DeviceChanged)(const char *deviceId, uint32_t idLen, int state, int errorCode);
    void (*DeviceServiceChanged)(const char *serviceId, uint32_t idLen, int state);
    void (*DataReceive)(const char *deviceId, uint32_t idLen, int dataType, char *data, uint32_t dataLen);
    int32_t (*GetSecureFileSize)(const char *filename);
    int32_t (*ReadSecureFile)(const char *filename, uint32_t offset, uint8_t *buf, uint32_t len);
    int32_t (*WriteSecureFile)(const char *filename, uint32_t offset, uint8_t *buf, uint32_t len);
    int32_t (*RemoveSecureFile)(const char *filename);
    int32_t (*GetVideoData)(
            const uint8_t *pPacket, uint32_t nPktSize, int64_t timeStamp, int64_t duration, VideoFrameInfo *info);
    int32_t (*DeviceMediaChanged)(DeviceMediaType type, void *item);
} DeviceListener;

/* function definition */
LIB_CROSSDEVICE_API int32_t CrossDeviceInit(DeviceConfig *config, DeviceLogGroup *logGroup);
LIB_CROSSDEVICE_API int32_t CrossDeviceRelease();

LIB_CROSSDEVICE_API int32_t CrossDeviceSetExtraInfo(DeviceExtraInfos *extraInfo);
LIB_CROSSDEVICE_API int32_t CrossDeviceRegisterListener(DeviceListener *listener);

LIB_CROSSDEVICE_API int32_t CrossDeviceGetPinCode(uint8_t *pinCode, uint32_t maxLen);
LIB_CROSSDEVICE_API int32_t CrossDeviceGetQRCode(uint8_t *url, uint32_t maxLen);
LIB_CROSSDEVICE_API int32_t CrossDeviceReconnect(const char *bluetoothMac, uint32_t realLen);

LIB_CROSSDEVICE_API int32_t CrossDeviceStartAdv();
LIB_CROSSDEVICE_API int32_t CrossDeviceStopAdv();

LIB_CROSSDEVICE_API int32_t CrossDeviceStartProjection(CROSS_DEVICE_HANDLE handle);
LIB_CROSSDEVICE_API int32_t CrossDeviceStopProjection(CROSS_DEVICE_HANDLE handle);
LIB_CROSSDEVICE_API int32_t CrossDevicePauseProjection(CROSS_DEVICE_HANDLE handle);

LIB_CROSSDEVICE_API void *CrossDeviceNewObject(CROSS_DEVICE_HANDLE handle, DeviceObjectType objType, uint32_t size);
LIB_CROSSDEVICE_API int32_t CrossDeviceDeleteObject(CROSS_DEVICE_HANDLE handle, DeviceObjectType objType, void *obj);
LIB_CROSSDEVICE_API int32_t CrossDevicePostEvent(
        CROSS_DEVICE_HANDLE handle, DeviceRequestType reqType, const char *content, uint32_t size);
LIB_CROSSDEVICE_API int32_t CrossDeviceSendData(
        CROSS_DEVICE_HANDLE handle, DeviceDataType dataType, uint8_t *data, uint32_t size);

LIB_CROSSDEVICE_API int32_t CrossDeviceGetTrustDeviceList(TrustPhoneInfo **list, uint32_t listLen);
LIB_CROSSDEVICE_API int32_t CrossDeviceDeleteTrustDevice(const uint8_t *phoneId, uint32_t idLen);
LIB_CROSSDEVICE_API int32_t CrossDeviceDisconnect(const uint8_t *phoneId, uint32_t idLen);

LIB_CROSSDEVICE_API char *CrossDeviceGetSdkVersion();
LIB_CROSSDEVICE_API int32_t CrossDeviceGetConnectType(CROSS_DEVICE_HANDLE handle);

#ifdef __cplusplus
}
#endif

#endif