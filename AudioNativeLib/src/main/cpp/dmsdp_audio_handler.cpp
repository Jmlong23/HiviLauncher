//
// Created by longjm on 2025/9/18.
//

#include "himusic/dmsdp_audio_handler.h"
#include "himusic/dmsdp_audio_data_type.h"
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdint.h>
#include <alsa/asoundlib.h>
#include <stdbool.h>
#include <sys/time.h>
#include <sys/syscall.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <cstdio>
#include <cstring>
#include <vector>
#include <thread>
#include <mutex>
#include <atomic>
#include <iostream>
#include <fstream>
#include <queue>
#include <android/log.h>


#define TAG "dmsdp_audio_handler" // 这个是自定义的LOG的标识
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,TAG ,__VA_ARGS__) // 定义LOGD类型
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG ,__VA_ARGS__) // 定义LOGI类型
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,TAG ,__VA_ARGS__) // 定义LOGW类型
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG ,__VA_ARGS__) // 定义LOGE类型
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL,TAG ,__VA_ARGS__) // 定义LOGF类型

int16_t bitPerSample = 24;
int smpRate = 48000;
int16_t channels = 2;

class PCMPlayer {
private:
    SLObjectItf engineObject_;
    SLEngineItf engineEngine_;
    SLObjectItf outputMixObject_;
    SLObjectItf playerObject_;
    SLPlayItf playerPlay_;
    SLAndroidSimpleBufferQueueItf playerBufferQueue_;
    std::queue<std::vector<uint8_t>> audioDataQueue_;
    std::mutex queueMutex_;
    std::condition_variable queueCV_;
    std::atomic<bool> isPlaying_{false};
    std::atomic<bool> shouldStop_{false};

    size_t bufferSize_;

    static void bufferQueueCallback(SLAndroidSimpleBufferQueueItf bq, void *context)
    {
        PCMPlayer *player = static_cast<PCMPlayer *>(context);
        player->feedBuffer();
    }

    void feedBuffer()
    {
        std::unique_lock<std::mutex> lock(queueMutex_);

        queueCV_.wait(lock, [this] { return !audioDataQueue_.empty() || shouldStop_.load(); });

        if (shouldStop_.load()) {
            return;
        }

        // 先填充一帧静音帧数据到audioDataQueue_
        if (audioDataQueue_.empty()) {
            std::vector<uint8_t> silence(bufferSize_, 0);
            SLresult result = (*playerBufferQueue_)->Enqueue(playerBufferQueue_, silence.data(), silence.size());
            if (result != SL_RESULT_SUCCESS) {
                printf("Failed to enqueue silence: ");
            }
            return;
        }
        std::vector<uint8_t> data = std::move(audioDataQueue_.front());
        audioDataQueue_.pop();
        lock.unlock();

        SLresult result = (*playerBufferQueue_)->Enqueue(playerBufferQueue_, data.data(), data.size());

        if (result != SL_RESULT_SUCCESS) {
            LOGI("Failed to enqueue audio data: ");
        }
    }

    bool createEngine()
    {
        SLresult result;

        result = slCreateEngine(&engineObject_, 0, nullptr, 0, nullptr, nullptr);
        if (result != SL_RESULT_SUCCESS) {
            LOGI("Failed to create engine: \n");
            return false;
        }

        result = (*engineObject_)->Realize(engineObject_, SL_BOOLEAN_FALSE);
        if (result != SL_RESULT_SUCCESS) {
            LOGI("Failed to realize engine: \n");
            return false;
        }

        result = (*engineObject_)->GetInterface(engineObject_, SL_IID_ENGINE, &engineEngine_);
        if (result != SL_RESULT_SUCCESS) {
            LOGI("Failed to get engine interface: ");
            return false;
        }

        return true;
    }

    bool createOutputMix()
    {
        SLresult result;

        result = (*engineEngine_)->CreateOutputMix(engineEngine_, &outputMixObject_, 0, nullptr, nullptr);
        if (result != SL_RESULT_SUCCESS) {
            LOGI("Failed to create output mix: ");
            return false;
        }

        result = (*outputMixObject_)->Realize(outputMixObject_, SL_BOOLEAN_FALSE);
        if (result != SL_RESULT_SUCCESS) {
            LOGI("Failed to realize output mix: ");
            return false;
        }

        return true;
    }

    bool createAudioPlayer()
    {
        SLresult result;


        SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 4};

        SLDataFormat_PCM format_pcm = {SL_DATAFORMAT_PCM,
                                       channels_,
                                       sampleRate_ * 1000,
                                       bitsPerSample_,
                                       bitsPerSample_,
                                       (channels_ == 2) ? (SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT) : SL_SPEAKER_FRONT_CENTER,
                                       SL_BYTEORDER_LITTLEENDIAN};

        SLDataSource audioSrc = {&loc_bufq, &format_pcm};


        SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject_};

        SLDataSink audioSnk = {&loc_outmix, nullptr};

        const SLInterfaceID ids[] = {SL_IID_BUFFERQUEUE};
        const SLboolean req[] = {SL_BOOLEAN_TRUE};

        result = (*engineEngine_)->CreateAudioPlayer(engineEngine_, &playerObject_, &audioSrc, &audioSnk, 1, ids, req);

        if (result != SL_RESULT_SUCCESS) {
            LOGI("Failed to create audio player: ");
            return false;
        }

        result = (*playerObject_)->Realize(playerObject_, SL_BOOLEAN_FALSE);
        if (result != SL_RESULT_SUCCESS) {
            LOGI("Failed to realize audio player: ");
            return false;
        }

        result = (*playerObject_)->GetInterface(playerObject_, SL_IID_PLAY, &playerPlay_);
        if (result != SL_RESULT_SUCCESS) {
            LOGI("Failed to get play interface: ");
            return false;
        }

        result = (*playerObject_)->GetInterface(playerObject_, SL_IID_BUFFERQUEUE, &playerBufferQueue_);
        if (result != SL_RESULT_SUCCESS) {
            LOGI("Failed to get buffer queue interface: ");
            return false;
        }

        result = (*playerBufferQueue_)->RegisterCallback(playerBufferQueue_, bufferQueueCallback, this);
        if (result != SL_RESULT_SUCCESS) {
            LOGI("Failed to register callback: ");
            return false;
        }

        return true;
    }

public:
    PCMPlayer(SLuint32 sampleRate, SLuint32 channels, SLuint32 bitsPerSample)
            : sampleRate_(sampleRate), channels_(channels), bitsPerSample_(bitsPerSample)
    {

        bufferSize_ = calculateBufferSize();
//        audioDataQueue_.resize(14);
    }

    SLuint32 sampleRate_;
    SLuint32 channels_;
    SLuint32 bitsPerSample_;
    ~PCMPlayer()
    {
        cleanup();
    }
    void addAudioData(const uint8_t *data, size_t length)
    {
        if (!data || length == 0 || !isPlaying_.load()) {
            return;
        }

        size_t bytesPerSample = bitsPerSample_ / 8;
        size_t bytesPerFrame = channels_ * bytesPerSample;

        if (length % bytesPerFrame != 0) {
            std::cerr << "Warning: Data length " << length << " is not multiple of frame size " << bytesPerFrame
                      << std::endl;
            length = (length / bytesPerFrame) * bytesPerFrame;
            if (length == 0)
                return;
        }

        std::lock_guard<std::mutex> lock(queueMutex_);
        std::vector<uint8_t> audioData(data, data + length);
        audioDataQueue_.push(std::move(audioData));
        queueCV_.notify_one();
    }
    size_t calculateBufferSize()
    {
        return (sampleRate_ * channels_ * (bitsPerSample_ / 8)) / 50;
    }
    bool initialize()
    {
        if (!createEngine()) {
            return false;
        }

        if (!createOutputMix()) {
            cleanup();
            return false;
        }

        if (!createAudioPlayer()) {
            cleanup();
            return false;
        }

        return true;
    }
    bool start()
    {
        if (!playerPlay_) {
            LOGI("Player not initialized");
            return false;
        }

        shouldStop_.store(false);
        isPlaying_.store(true);

        SLresult result = (*playerPlay_)->SetPlayState(playerPlay_, SL_PLAYSTATE_PLAYING);
        if (result != SL_RESULT_SUCCESS) {
            LOGI("Failed to set play state: ");
            return false;
        }

        // 预先写入两帧数据
        for (int i = 0; i < 2; ++i) {
            std::vector<uint8_t> silence(bufferSize_, 0);
            result = (*playerBufferQueue_)->Enqueue(playerBufferQueue_, silence.data(), silence.size());
            if (result != SL_RESULT_SUCCESS) {
                LOGI("Failed to pre-fill buffer: ");
            }
        }

        LOGI("Real-time playback started");
        return true;
    }

    void stop()
    {
        shouldStop_.store(true);
        isPlaying_.store(false);
        queueCV_.notify_all();

        if (playerPlay_) {
            (*playerPlay_)->SetPlayState(playerPlay_, SL_PLAYSTATE_STOPPED);
        }

        if (playerBufferQueue_) {
            (*playerBufferQueue_)->Clear(playerBufferQueue_);
        }

        std::lock_guard<std::mutex> lock(queueMutex_);
        while (!audioDataQueue_.empty()) {
            audioDataQueue_.pop();
        }

        LOGI("Playback stopped");
    }
    bool isPlaying() const
    {
        return isPlaying_.load();
    }
    size_t getQueuedPackets() const
    {
        // std::lock_guard<std::mutex> lock(queueMutex_);
        return audioDataQueue_.size();
    }
    void cleanup()
    {
        stop();

        if (playerObject_ != nullptr) {
            (*playerObject_)->Destroy(playerObject_);
            playerObject_ = nullptr;
            playerPlay_ = nullptr;
            playerBufferQueue_ = nullptr;
        }

        if (outputMixObject_ != nullptr) {
            (*outputMixObject_)->Destroy(outputMixObject_);
            outputMixObject_ = nullptr;
        }

        if (engineObject_ != nullptr) {
            (*engineObject_)->Destroy(engineObject_);
            engineObject_ = nullptr;
            engineEngine_ = nullptr;
        }

        std::lock_guard<std::mutex> lock(queueMutex_);
        while (!audioDataQueue_.empty()) {
            audioDataQueue_.pop();
        }

        LOGI("Resources cleaned up");
    }
};
PCMPlayer *player = nullptr;
SLuint32 GetPcmFormat(int16_t bitDepth)
{
    switch (bitDepth) {
        case 1:
            return 16;  // AUDIO_FORMAT_PCM_16_BIT
        case 2:
            return 24;  // AUDIO_FORMAT_PCM_8_BIT
        case 3:
            return 32;  // AUDIO_FORMAT_PCM_32_BIT
        case 4:
            return 8;  // AUDIO_FORMAT_PCM_8_24_BIT
        case 6:
            return 24;  // AUDIO_FORMAT_PCM_24_BIT
        default:
            return 16;
    }
}
int32_t SetParams(int16_t bitSample, int smpRate, int16_t channels)
{
    LOGI("SetParams PCM start...");
    SLuint32 bitPerSamples = GetPcmFormat(bitSample);
    SLuint32 smpRates = static_cast<SLuint32>(smpRate);
    SLuint32 channelss = static_cast<SLuint32>(channels);

    if (player != nullptr) {
        delete player;
        player = nullptr;
    }
    player = new PCMPlayer(smpRates, channelss, bitPerSamples);

    if (!player->initialize()) {
        LOGI("Failed to initialize PCM player");
        return -1;
    }
    if (!player->start()) {
        LOGI("Failed to start playback");
        player->cleanup();
        return -1;
    }
    return DMSDP_OK;
}
int32_t SetAudioParameter(
        const char *id, uint32_t idLen, int32_t sessionId, uint32_t streamType, const DMSDPProfiles *profiles)
{
    LOGI("SetAudioParameter start...");
    uint32_t index = 1;
    // 只考虑一种speaker的设备
    if (streamType != DMSDP_STREAM_TYPE_AUDIO_MUSIC) {
        return -1;
    }
    if (profiles == nullptr) {
        return -1;
    }
    smpRate = profiles->profiles[index].value;  // profiles->profiles[0] 存放流类型  profiles->profiles[1] 为采样率
    index++;
    bitPerSample = profiles->profiles[index].value;  // profiles->profiles[2] 为format
    index++;
    channels = profiles->profiles[index].value;  // profiles->profiles[3] 为通道数 拿到的值是DMSDPAudioChannelMasks
    index++;                                     // profiles->profiles[4] 为解码格式
    index++;
    SetParams(bitPerSample, smpRate, channels);
    return DMSDP_OK;
}

int32_t ReadStreamBuffer(const char *id, uint32_t idLen, int32_t sessionId, DMSDPVirtualStreamData *data)
{
    LOGI("ReadStreamBuffer sdk audio,len=%d", data->header->length);
    return DMSDP_OK;
}

int32_t WriteStreamBuffer(const char *id, uint32_t idLen, int32_t sessionId, DMSDPVirtualStreamData *data)
{
//    LOGI("WriteStreamBuffer start");
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
    player->addAudioData(data->data->data(), data->header->length);
//    LOGI("WriteStreamBuffer start playing 20250906...");
    return DMSDP_OK;
}

int32_t OpenAudioRecord(
        const char *id, uint32_t idLen, int32_t sessionId, int32_t inputSource, const DMSDPProfiles *profiles)
{
    LOGI("OpenAudioRecord");
    return DMSDP_OK;
}

int32_t CloseAudioRecord(const char *id, uint32_t idLen, int32_t sessionId)
{
    LOGI("CloseAudioRecord");
    return DMSDP_OK;
}
int32_t RegisterListener(const DMSDPListener *listener)
{
    LOGI("RegisterListener");
    return DMSDP_OK;
}
void AudioFreeCapabilityParams(DMSDPAudioProfile *profile, DMSDPAudioCodec *codecs)
{
    LOGI("AudioFreeCapabilityParams");
    if (profile != nullptr) {
        free(profile);
    }

    if (codecs != nullptr) {
        free(codecs);
    }
    return;
}

int32_t GetAudioCapability(DMSDPAudioCapabilities **capabilities, uint32_t *num)
{
    LOGI("GetAudioCapability");
    uint32_t audioNum = 1;
    uint32_t audioIndex = 0;
    *capabilities = (DMSDPAudioCapabilities *)malloc((sizeof(DMSDPAudioCapabilities)) * audioNum);
    if (*capabilities == nullptr) {
        LOGI("Failed to allocate memory for capabilities");
        return -1;
    }
    DMSDPAudioCapabilities *item = &(*capabilities)[0];
    item->id = "speaker1";
    item->idLen = sizeof("speaker1");
    item->type = DMSDPAudioDevice::AUDIO_DEVICE_TYPE_SPEAKER;
    item[audioIndex].profiles.num = 3;
    item->profiles.profiles = (DMSDPAudioProfile *)malloc(sizeof(DMSDPAudioProfile) * item[audioIndex].profiles.num);
    if (item->profiles.profiles == nullptr) {
        LOGI("Failed to allocate memory for profiles");
        free(*capabilities);
        *capabilities = nullptr;
        return -1;
    }
    item[audioIndex].profiles.profiles[0].sampleRates = DMSDPAudioSampleRates::AUDIO_SAMPLE_RATE_192000;
    item[audioIndex].profiles.profiles[0].format = DMSDPAudioFormats::AUDIO_FORMAT_PCM_24_BITS;
    item[audioIndex].profiles.profiles[0].channelMask = DMSDPAudioChannelMasks::AUDIO_CHANNEL_OUT_STEREOS;

    item[audioIndex].profiles.profiles[1].sampleRates = DMSDPAudioSampleRates::AUDIO_SAMPLE_RATE_96000;
    item[audioIndex].profiles.profiles[1].format = DMSDPAudioFormats::AUDIO_FORMAT_PCM_24_BITS;
    item[audioIndex].profiles.profiles[1].channelMask = DMSDPAudioChannelMasks::AUDIO_CHANNEL_OUT_STEREOS;

    item[audioIndex].profiles.profiles[2].sampleRates = DMSDPAudioSampleRates::AUDIO_SAMPLE_RATE_48K;
    item[audioIndex].profiles.profiles[2].format = DMSDPAudioFormats::AUDIO_FORMAT_PCM_16_BITS;
    item[audioIndex].profiles.profiles[2].channelMask = DMSDPAudioChannelMasks::AUDIO_CHANNEL_OUT_STEREOS;
    // 获取通道数
    item[audioIndex].codecs.num = 3;
    item[audioIndex].codecs.codecs = (DMSDPAudioCodec *)malloc(sizeof(DMSDPAudioCodec) * item[audioIndex].codecs.num);
    item[audioIndex].codecs.codecs[0] = DMSDPAudioCodec::FORMAT_L2HC;
    item[audioIndex].codecs.codecs[1] = DMSDPAudioCodec::FORMAT_L2HC;
    item[audioIndex].codecs.codecs[2] = DMSDPAudioCodec::FORMAT_L2HC;
    // 设置返回的音频设备数量
    *num = audioNum;
    return DMSDP_OK;
}
void Release(int32_t type, void *ptr, uint32_t num)
{
    LOGI("AudioRelease type=%d", type);
    player->cleanup();
    return;
}
int32_t BusinessControl(uint32_t cmd, void *inputPara, uint32_t inLen, void *outputPara, uint32_t outLen)
{
    LOGI("BusinessControl");
    return DMSDP_OK;
}
int32_t CloseAudioTrack(const char *id, uint32_t idLen, int32_t sessionId)
{
    LOGI("CloseAudioTrack");
    return DMSDP_OK;
}
const char *GetAudioCapabilityString(uint32_t *len)
{
    LOGI("GetAudioCapabilityString");
    /* reserved for ide automatic */
    return NULL;
}

int32_t DMSDPGetAudioHandler(DMSDPAudioHandler *audioHandler)
{
    LOGI("DMSDPGetAudioHandler");
    audioHandler->SetAudioParameter = SetAudioParameter;
    audioHandler->WriteStreamBuffer = WriteStreamBuffer;
    audioHandler->ReadStreamBuffer = ReadStreamBuffer;
    audioHandler->OpenAudioRecord = OpenAudioRecord;
    audioHandler->CloseAudioRecord = CloseAudioRecord;
    audioHandler->RegisterListener = RegisterListener;
    audioHandler->GetAudioCapability = GetAudioCapability;
    audioHandler->Release = Release;
    audioHandler->BusinessControl = BusinessControl;
    audioHandler->CloseAudioTrack = CloseAudioTrack;
    audioHandler->GetAudioCapabilityString = GetAudioCapabilityString;
    return DMSDP_OK;
}