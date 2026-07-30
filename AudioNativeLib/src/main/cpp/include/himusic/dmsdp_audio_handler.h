#ifndef DMSDP_AUDIO_HANDLER_H
#define DMSDP_AUDIO_HANDLER_H

#include "dmsdp_audio_data_type.h"
// #include "dmsdp_business.h"

#ifdef __cplusplus
extern "C" {
#endif


#define NN 128
#define TAIL 1024
#define DMSDP_OK 0

#define VENDOR_AUDIO_ID_MAX_LEN 64
#define VENDOR_AUDIO_MAX_NUM 16

#define PCM_DEVICE "plughw:0,1"           // ALSA 设备名称
#define SAMPLE_RATE 192000            // 采样率 (Hz)
#define CHANNELS 2                    // 声道数 (单声道)
#define FORMAT SND_PCM_FORMAT_S16_LE  // 16-bit 小端格式
#define BUFFER_SIZE 1024              // 每次写入的帧数


typedef struct {

    // 设置初始化音频能力
    int32_t (*SetAudioParameter)(
        const char *id, uint32_t idLen, int32_t sessionId, uint32_t streamType, const DMSDPProfiles *profiles);

    int32_t (*WriteStreamBuffer)(const char *id, uint32_t idLen, int32_t sessionId, DMSDPVirtualStreamData *data);
    // 读音频流，播放音频
    int32_t (*ReadStreamBuffer)(const char *id, uint32_t idLen, int32_t sessionId, DMSDPVirtualStreamData *data);

    // 写音频流

    int32_t (*CloseAudioRecord)(const char *id, uint32_t idLen, int32_t sessionId);
    int32_t (*OpenAudioRecord)(
        const char *id, uint32_t idLen, int32_t sessionId, int32_t inputSource, const DMSDPProfiles *profiles);
    int32_t (*RegisterListener)(const DMSDPListener *listener);
    int32_t (*GetAudioCapability)(DMSDPAudioCapabilities **capabilities, uint32_t *num);
    void (*Release)(int32_t type, void *ptr, uint32_t num);
    int32_t (*BusinessControl)(uint32_t cmd, void *inputPara, uint32_t inLen, void *outputPara, uint32_t outLen);
    int32_t (*CloseAudioTrack)(const char *id, uint32_t idLen, int32_t sessionId);
    const char *(*GetAudioCapabilityString)(uint32_t *len);
} DMSDPAudioHandler;

/* get audio handler interface */
int32_t DMSDPGetAudioHandler(DMSDPAudioHandler *audioHandler);

#ifdef __cplusplus
}
#endif

#endif