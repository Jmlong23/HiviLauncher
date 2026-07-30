/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2019-2020. All rights reserved.
 * Description: dmsdp audio type
 */

#ifndef DMSDP_AUDIO_DATA_TYPE_H
#define DMSDP_AUDIO_DATA_TYPE_H

#include <stdint.h>
#include <memory>
#include <cstddef>
#include <string>
#include <vector>


const size_t CHANNEL_MAX = 16; // same with CHANNEL_16
typedef enum {
    AUDIO_DEVICE_TYPE_SPEAKER = 0u,
    AUDIO_DEVICE_TYPE_MIC = 1u,
    AUDIO_DEVICE_TYPE_MODEM_SPEAKER = 2u,
    AUDIO_DEVICE_TYPE_MODEM_MIC = 3u
} DMSDPAudioDevice;

typedef enum {
    AUDIO_SAMPLE_RATE_8K = 8000u,
    AUDIO_SAMPLE_RATE_11K = 11025u,
    AUDIO_SAMPLE_RATE_12K = 12000u,
    AUDIO_SAMPLE_RATE_16K = 16000u,
    AUDIO_SAMPLE_RATE_22K = 22050u,
    AUDIO_SAMPLE_RATE_24K = 24000u,
    AUDIO_SAMPLE_RATE_32K = 32000u,
    AUDIO_SAMPLE_RATE_44_1K = 44100u,
    AUDIO_SAMPLE_RATE_48K = 48000u,
    AUDIO_SAMPLE_RATE_96000 = 96000u,
    AUDIO_SAMPLE_RATE_192000 = 192000u
} DMSDPAudioSampleRates;

typedef enum {
    AUDIO_CHANNEL_OUT_MONOS = 0x1u,
    AUDIO_CHANNEL_OUT_STEREOS = 0x3u,
    AUDIO_CHANNEL_IN_MONOS = 0x10u,
    AUDIO_CHANNEL_IN_STEREOS = 0xCu
} DMSDPAudioChannelMasks;

typedef enum {
    AUDIO_FORMAT_PCM_16_BITS = 0x1u,
    AUDIO_FORMAT_PCM_8_BITS = 0x2u,
    AUDIO_FORMAT_PCM_32_BITS = 0x3u,
    AUDIO_FORMAT_PCM_8_24_BITS = 0x4u,
    AUDIO_FORMAT_PCM_24_BITS = 0x6u
} DMSDPAudioFormats;

typedef enum {
    FORMAT_AAC = 0,
    FORMAT_PCM = 1,
    FORMAT_G711A = 2,
    FORMAT_L2HC = 3
} DMSDPAudioCodec;

typedef struct {
    uint32_t num;
    DMSDPAudioCodec *codecs;
} DMSDPAudioCodecs;

typedef enum {
    AUDIO_STREAM_VOICE_CALLS = 0,
    AUDIO_STREAM_SYSTEMS = 1,
    AUDIO_STREAM_RINGS = 2,
    AUDIO_STREAM_MUSICS = 3,
    AUDIO_STREAM_ALARMS = 4,
    AUDIO_STREAM_NOTIFICATIONS = 5,
    AUDIO_STREAM_BLUETOOTH_SCOS = 6,
    AUDIO_STREAM_SYSTEM_ENFORCEDS = 7,
    AUDIO_STREAM_DTMFS = 8,
    AUDIO_STREAM_TTSS = 9,
    AUDIO_STREAM_ACCESSIBILITYS = 10,
} DMSDPAudioStreamType;

typedef enum {
    AUDIO_SOURCE_MICS = 1,
    AUDIO_SOURCE_VOICE_UPLINK = 2,
    AUDIO_SOURCE_VOICE_DOWNLINK = 3,
    AUDIO_SOURCE_VOICE_CALL = 4,
    AUDIO_SOURCE_CAMCORDER = 5,
    AUDIO_SOURCE_VOICE_RECOGNITION = 6,
    AUDIO_SOURCE_VOICE_COMMUNICATION = 7,
    AUDIO_SOURCE_REMOTE_SUBMIX = 8,
    AUDIO_SOURCE_UNPROCESSED = 9,
    AUDIO_SOURCE_FM_TUNER = 1998
} DMSDPAudioSource;

typedef enum {
    RELEASE_TYPE_QUERY_ABILITY = 1
} DMSDPAudioReleaseType;

typedef enum {
    AUDIOFOCUS_NONE = 0,
    AUDIOFOCUS_GAIN = 1,
    AUDIOFOCUS_GAIN_TRANSIENT = 2,
    AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK = 3,
    AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE = 4,
    AUDIOFOCUS_RELEASE = 5,
    AUDIOFOCUS_LOSS = -1,
    AUDIOFOCUS_LOSS_TRANSIENT = -2,
    AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK = -3
} DMSDPAudioFocus;

typedef enum {
    AUDIO_USAGE_UNKNOWN = 0,
    AUDIO_USAGE_MEDIA = 1,
    AUDIO_USAGE_VOICE_COMMUNICATION = 2,
    AUDIO_USAGE_VOICE_COMMUNICATION_SIGNALLING = 3,
    AUDIO_USAGE_ALARM = 4,
    AUDIO_USAGE_NOTIFICATION = 5,
    AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE = 6,
    AUDIO_USAGE_NOTIFICATION_COMMUNICATION_REQUEST = 7,
    AUDIO_USAGE_NOTIFICATION_COMMUNICATION_INSTANT = 8,
    AUDIO_USAGE_NOTIFICATION_COMMUNICATION_DELAYED = 9,
    AUDIO_USAGE_NOTIFICATION_EVENT = 10,
    AUDIO_USAGE_ASSISTANCE_ACCESSIBILITY = 11,
    AUDIO_USAGE_ASSISTANCE_NAVIGATION_GUIDANCE = 12,
    AUDIO_USAGE_ASSISTANCE_SONIFICATION = 13,
    AUDIO_USAGE_GAME = 14,
    AUDIO_USAGE_VIRTUAL_SOURCE = 15,
    AUDIO_USAGE_ASSISTANT = 16,
    AUDIO_USAGE_TTS = 17
} DMSDPAudioType;

typedef struct {
    DMSDPAudioSampleRates sampleRates;
    DMSDPAudioChannelMasks channelMask;
    DMSDPAudioFormats format;
} DMSDPAudioProfile;

typedef struct {
    uint32_t num;
    DMSDPAudioProfile *profiles;
} DMSDPAudioProfiles;

typedef struct {
    char *key; /* key: "channel", "format", "sampleRate", "codec" */
    uint32_t keyLen;
    uint32_t value; /* value: AudioChannelMasks, AudioFormats, AudioSampleRates, AudioCodec */
} DMSDPProfile;

typedef struct {
    uint32_t num;
    DMSDPProfile *profiles;
} DMSDPProfiles;

typedef struct {
    std::string key;
    uint32_t keyLen;
    std::string value;
    uint32_t valueLen;
} DMSDPReserved;

typedef struct {
    uint32_t num;
    std::shared_ptr<DMSDPReserved> reserveds;
} DMSDPReserveds;

typedef struct {
    uint32_t format; // should be same with AudioStandard::AudioSampleFormat in audio_stream_info.h
    uint32_t channels; // should be same with AudioStandard::AudioChannel in audio_stream_info.h
} AudioRawFormat;

// channel
enum AudioChannel : uint8_t {
    CHANNEL_UNKNOW = 0,
    MONO = 1,
    STEREO = 2,
    CHANNEL_3 = 3,
    CHANNEL_4 = 4,
    CHANNEL_5 = 5,
    CHANNEL_6 = 6,
    CHANNEL_7 = 7,
    CHANNEL_8 = 8,
    CHANNEL_9 = 9,
    CHANNEL_10 = 10,
    CHANNEL_11 = 11,
    CHANNEL_12 = 12,
    CHANNEL_13 = 13,
    CHANNEL_14 = 14,
    CHANNEL_15 = 15,
    CHANNEL_16 = 16
};

typedef struct {
    AudioChannel channel = STEREO;
    int32_t volStart[CHANNEL_MAX];
    int32_t volEnd[CHANNEL_MAX];
} ChannelVolumes;

typedef struct {
    long tvSec;
    long tvNsec;
} CurrentTime;

typedef struct {
    char *id;
    uint32_t idLen;
    DMSDPAudioDevice type;
    DMSDPAudioProfiles profiles;
    DMSDPAudioCodecs codecs;
    DMSDPReserveds reserveds;
} DMSDPAudioCapabilities;

typedef struct {
    uint32_t streamType;
    uint32_t length;
    DMSDPReserveds reserveds;
} DMSDPVirtualStreamDataHeader;

enum AudioSamplingRate {
    SAMPLE_RATE_8000 = 8000,
    SAMPLE_RATE_11025 = 11025,
    SAMPLE_RATE_12000 = 12000,
    SAMPLE_RATE_16000 = 16000,
    SAMPLE_RATE_22050 = 22050,
    SAMPLE_RATE_24000 = 24000,
    SAMPLE_RATE_32000 = 32000,
    SAMPLE_RATE_44100 = 44100,
    SAMPLE_RATE_48000 = 48000,
    SAMPLE_RATE_64000 = 64000,
    SAMPLE_RATE_88200 = 88200,
    SAMPLE_RATE_96000 = 96000,
    SAMPLE_RATE_176400 = 176400,
    SAMPLE_RATE_192000 = 192000
};

enum AudioEncodingType {
    ENCODING_INVALID = -1,
    ENCODING_PCM = 0,
    ENCODING_AAC = 1,
    ENCODING_L2HC = 3,
    ENCODING_AUDIOVIVID = 4
};

// format
enum AudioSampleFormat : int8_t {
    SAMPLE_U8 = 0,
    SAMPLE_S16LE = 1,
    SAMPLE_S24LE = 2,
    SAMPLE_S32LE = 3,
    SAMPLE_F32LE = 4,
    INVALID_WIDTH = -1
};

struct FrameInfo {
    uint16_t length;
    uint8_t screen_rotation;
    uint8_t has_navigation_key;
    char pack[1];
    AudioSamplingRate samplingRate;
    AudioEncodingType encoding = AudioEncodingType::ENCODING_PCM;
    AudioSampleFormat format = AudioSampleFormat::INVALID_WIDTH;
    AudioChannel channels;
};

typedef struct {
    std::shared_ptr<std::vector<uint8_t>> data;
    uint64_t timeStampUs;
    uint32_t usage;
    FrameInfo frameInfo;
    std::shared_ptr<DMSDPVirtualStreamDataHeader> header;
} DMSDPVirtualStreamData;

typedef struct {
    DMSDPAudioDevice type;
    const char *id;
    uint32_t idLen;
    uint32_t sessionId;
    char *data;
    uint32_t dataLen;
} DMSDPAudioBussinessCtrlData;

/*-----------------------------------------------------------------------------------------*/

typedef enum {
    OPERATION_INVALID = -1,
    OPERATION_STARTED,
    OPERATION_PAUSED,
    OPERATION_STOPPED,
    OPERATION_FLUSHED,
    OPERATION_DRAINED,
    OPERATION_RELEASED,
    OPERATION_UNDERRUN,
    OPERATION_UNDERFLOW,
    OPERATION_SET_OFFLOAD_ENABLE,
    OPERATION_UNSET_OFFLOAD_ENABLE,
    OPERATION_DATA_LINK_CONNECTING,
    OPERATION_DATA_LINK_CONNECTED,
} IOperation;

typedef enum {
    PLUGIN = 1,
    UNPLUG,
    AVAILABLE,
    UNAVAILABLE
} DMSDPServiceStatus;

typedef enum {
    /* audio focus change */
    AUDIO_FOCUS_CHANGE = 1,
    /* add camera service info, using DMSDPCameraCapabilities struct */
    CAMERA_ADD_SERVICE = 2,
    /* add camera service info, using DMSDPCameraCapabilities struct json string */
    CAMERA_ADD_SERVICE_STRING = 3,
    /* add audio service info, using DMSDPAudioCapabilities struct */
    AUDIO_ADD_SERVICE = 4,
    /* add audio service info, using DMSDPAudioCapabilities struct json string */
    AUDIO_ADD_SERVICE_STRING = 5,
    /* device send common data to application */
    DEVICE_SEND_COMMON_DATA = 6,
    /* notify the hardware state change notify, such as the Speaker Driver broken */
    DEVICE_HARDWARE_STATE_NOTIFY = 7
} DMSDPServcieActionType;

typedef enum {
    NO_FADEOUT,
    DO_FADEOUT,
    DONE_FADEOUT
} FadeOutState;

typedef enum {
    SPEED_FORWARD_0_75_X = 0,
    SPEED_FORWARD_1_00_X = 1,
    SPEED_FORWARD_1_25_X = 2,
    SPEED_FORWARD_1_75_X = 3,
    SPEED_FORWARD_2_00_X = 4,
    SPEED_FORWARD_0_50_X = 5,
    SPEED_FORWARD_1_50_X = 6,
    SPEED_FORWARD_3_00_X = 7,
} PlaybackSpeed;

typedef struct {
    uint32_t samplingRate = 0;
    uint8_t encoding = 0;
    uint8_t format = 0;
    uint8_t channels = 0;
    uint64_t channelLayout = 0ULL;
} AudioStreamParams;

typedef struct {
    DMSDPServcieActionType type;
    void *value;
    uint32_t valLen;
} DMSDPServiceAction;

typedef struct {
    int32_t (*UpdateServiceStatus)(const char *id, uint32_t idLen, const DMSDPServiceStatus status);
    int32_t (*UpdateServiceAction)(const char *id, uint32_t idLen, DMSDPServiceAction *action);
} DMSDPListener;

typedef struct {
    char *id; /* device id */
    uint32_t idLen;
    int32_t type; /* mic camrea gps speaker */
} DVServiceIndex;

enum {
    DV_SERVICE_TYPE_UNKNOWN = 0,
    DV_SERVICE_TYPE_CAMERA = 1,
    DV_SERVICE_TYPE_MIC = 2,
    DV_SERVICE_TYPE_SPEAKER = 3,
    DV_SERVICE_TYPE_DISPLAY = 4,
    DV_SERVICE_TYPE_GPS = 5,
    DV_SERVICE_TYPE_BUTTON = 6,
    DV_SERVICE_TYPE_VIRMODEM_MIC = 9,
    DV_SERVICE_TYPE_VIRMODEM_SPEAKER = 10,
    DV_SERVICE_TYPE_MAX
};

enum {
    DMSDP_STREAM_TYPE_AUDIO_COMMUNICATION = 0,
    DMSDP_STREAM_TYPE_AUDIO_RESV = 1,
    DMSDP_STREAM_TYPE_AUDIO_RINGTONE = 2,
    DMSDP_STREAM_TYPE_AUDIO_MUSIC = 3,
    DMSDP_STREAM_TYPE_AUDIO_ALARM = 4,
    DMSDP_STREAM_TYPE_AUDIO_UNKNOWN = 100,
    DMSDP_STREAM_TYPE_AUDIO_ALL,
    DMSDP_STREAM_TYPE_VIDEO
};
#endif
