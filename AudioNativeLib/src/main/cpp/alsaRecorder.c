//
// Created by longjm on 2025/9/5.
//

#include <jni.h>
#include <string.h>
#include <stdio.h>
#include "tinyalsa/asoundlib.h"
#include "alsaRecorder.h"

#include "android/log.h"
#include <stdlib.h>
#include <stdint.h>
#include <signal.h>
#include <time.h>


static const char *TAG = "alsaRecorder_JNI";

#define LOGD(fmt, args...)  __android_log_print(ANDROID_LOG_DEBUG, TAG, fmt, ##args);

#define ID_RIFF 0x46464952
#define ID_WAVE 0x45564157
#define ID_FMT  0x20746d66
#define ID_DATA 0x61746164

#define FORMAT_PCM 1

struct wav_header {
    uint32_t riff_id;
    uint32_t riff_sz;
    uint32_t riff_fmt;
    uint32_t fmt_id;
    uint32_t fmt_sz;
    uint16_t audio_format;
    uint16_t num_channels;
    uint32_t sample_rate;
    uint32_t byte_rate;
    uint16_t block_align;
    uint16_t bits_per_sample;
    uint32_t data_id;
    uint32_t data_sz;
};

int capturing = 1;

unsigned int capture_sample(FILE *file, unsigned int card, unsigned int device,
                            unsigned int channels, unsigned int rate,
                            enum pcm_format format, unsigned int period_size,
                            unsigned int period_count, unsigned int cap_time);



JNIEXPORT jboolean JNICALL
Java_com_hivi_audionativelib_manager_audioAlsa_AudioAlsaRecorder_startCapture(JNIEnv *env,
                                                                              jobject clazz,
                                                                              jstring filePath,
                                                                              jint card,
                                                                              jint device,
                                                                              jint channels,
                                                                              jint sampleRate,
                                                                              jint format) {

    struct wav_header header;
    unsigned int frames;
    unsigned int period_size = 1024;
    unsigned int period_count = 4;
    unsigned int cap_time = 0;
    capturing = 1;
    const char *path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) {
        return JNI_FALSE;
    }
    FILE *file = fopen(path, "wb");
    if (!file) {
        LOGD("Failed to create record file: %s", path);
        (*env)->ReleaseStringUTFChars(env, filePath, path);
        return JNI_FALSE;
    }

    struct pcm_config config;
    memset(&config, 0, sizeof(config));
    config.channels = (unsigned int) channels;
    config.rate = (unsigned int) sampleRate;
    config.period_size = 1024;
    config.period_count = 4;
    config.format = (enum pcm_format) format;
    config.start_threshold = 0;
    config.stop_threshold = 0;
    config.silence_threshold = 0;


    header.riff_id = ID_RIFF;
    header.riff_sz = 0;
    header.riff_fmt = ID_WAVE;
    header.fmt_id = ID_FMT;
    header.fmt_sz = 16;
    header.audio_format = FORMAT_PCM;
    header.num_channels = channels;
    header.sample_rate = sampleRate;

    header.bits_per_sample = pcm_format_to_bits(format);
    header.byte_rate = (header.bits_per_sample / 8) * channels * sampleRate;
    header.block_align = channels * (header.bits_per_sample / 8);
    header.data_id = ID_DATA;

    /* leave enough room for header */
    fseek(file, sizeof(struct wav_header), SEEK_SET);

    frames = capture_sample(file, card, device, header.num_channels,
                            header.sample_rate, format,
                            period_size, period_count, cap_time);
    printf("Captured %u frames\n", frames);

    /* write header now all information is known */
    header.data_sz = frames * header.block_align;
    header.riff_sz = header.data_sz + sizeof(header) - 8;
    fseek(file, 0, SEEK_SET);
    fwrite(&header, sizeof(struct wav_header), 1, file);

    fclose(file);
    LOGD("Recording stopped");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_hivi_audionativelib_manager_audioAlsa_AudioAlsaRecorder_stopCapture(JNIEnv *env,
                                                                             jobject clazz) {
    LOGD("Recording stopCapture");
    capturing = 0;
}

unsigned int capture_sample(FILE *file, unsigned int card, unsigned int device,
                            unsigned int channels, unsigned int rate,
                            enum pcm_format format, unsigned int period_size,
                            unsigned int period_count, unsigned int cap_time) {
    struct pcm_config config;
    struct pcm *pcm;
    char *buffer;
    unsigned int size;
    unsigned int bytes_read = 0;
    unsigned int frames = 0;
    struct timespec end;
    struct timespec now;

    memset(&config, 0, sizeof(config));
    config.channels = channels;
    config.rate = rate;
    config.period_size = period_size;
    config.period_count = period_count;
    config.format = format;
    config.start_threshold = 0;
    config.stop_threshold = 0;
    config.silence_threshold = 0;

    pcm = pcm_open(card, device, PCM_IN, &config);
    if (!pcm || !pcm_is_ready(pcm)) {
        LOGD("Unable to open PCM device (%s)\n",
                pcm_get_error(pcm));
        return 0;
    }

    size = pcm_frames_to_bytes(pcm, pcm_get_buffer_size(pcm));
    buffer = malloc(size);
    if (!buffer) {
        LOGD("Unable to allocate %u bytes\n", size);
        free(buffer);
        pcm_close(pcm);
        return 0;
    }

    LOGD("Capturing sample: %u ch, %u hz, %u bit\n", channels, rate,
           pcm_format_to_bits(format));

    clock_gettime(CLOCK_MONOTONIC, &now);
    end.tv_sec = now.tv_sec + cap_time;
    end.tv_nsec = now.tv_nsec;

    while (capturing && !pcm_read(pcm, buffer, size)) {
        if (fwrite(buffer, 1, size, file) != size) {
            fprintf(stderr, "Error capturing sample\n");
            break;
        }
        bytes_read += size;
        if (cap_time) {
            clock_gettime(CLOCK_MONOTONIC, &now);
            if (now.tv_sec > end.tv_sec ||
                (now.tv_sec == end.tv_sec && now.tv_nsec >= end.tv_nsec))
                break;
        }
    }

    frames = pcm_bytes_to_frames(pcm, bytes_read);
    free(buffer);
    pcm_close(pcm);
    return frames;
}



// 全局变量保存回调接口和方法ID
static jobject g_dataListener = NULL;

static jmethodID g_onWakePcmRead = NULL;

/** 复用 Java short[]，避免采集循环内每帧 NewShortArray */
static jshortArray g_reusableWakePcm = NULL;
static jsize g_reusableWakePcmCapacity = 0;

int micCapturing = 1;

// 释放全局引用
void releaseGlobalRefs(JNIEnv *env) {
    if (g_dataListener != NULL) {
        (*env)->DeleteGlobalRef(env, g_dataListener);
        g_dataListener = NULL;
    }
    if (g_reusableWakePcm != NULL) {
        (*env)->DeleteGlobalRef(env, g_reusableWakePcm);
        g_reusableWakePcm = NULL;
        g_reusableWakePcmCapacity = 0;
    }
    g_onWakePcmRead = NULL;
}

static jboolean ensureReusableWakePcm(JNIEnv *env, jsize shortLen) {
    if (g_reusableWakePcm != NULL && g_reusableWakePcmCapacity >= shortLen) {
        return JNI_TRUE;
    }
    if (g_reusableWakePcm != NULL) {
        (*env)->DeleteGlobalRef(env, g_reusableWakePcm);
        g_reusableWakePcm = NULL;
        g_reusableWakePcmCapacity = 0;
    }
    jshortArray local = (*env)->NewShortArray(env, shortLen);
    if (local == NULL) {
        return JNI_FALSE;
    }
    g_reusableWakePcm = (*env)->NewGlobalRef(env, local);
    (*env)->DeleteLocalRef(env, local);
    if (g_reusableWakePcm == NULL) {
        return JNI_FALSE;
    }
    g_reusableWakePcmCapacity = shortLen;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_hivi_audionativelib_manager_audioAlsa_AudioAlsaRecorder_startMicCapture(JNIEnv *env,
                                                                                jobject clazz,
                                                                                jint card,
                                                                                jint device,
                                                                                jint channels,
                                                                                jint sampleRate,
                                                                                jint format) {
    LOGD("startMicCapture request: card=%d, device=%d, channels=%d, sampleRate=%d, format=%d",
         card, device, channels, sampleRate, format);
    releaseGlobalRefs(env);
    // 创建接口的全局引用（因为要在其他地方使用）
    g_dataListener = (*env)->NewGlobalRef(env,clazz);
    if (g_dataListener == NULL) {
        LOGD("Failed to create global ref for listener");
        return -1;
    }

    // 获取接口类引用
    jclass listenerClass = (*env)->GetObjectClass(env, clazz);
    if (listenerClass == NULL) {
        LOGD("Failed to get listener class");
        releaseGlobalRefs(env);
        return -1;
    }

    g_onWakePcmRead = (*env)->GetMethodID(
            env,
            listenerClass,
            "onWakePcmRead",
            "([SI)V"
    );
    (*env)->DeleteLocalRef(env, listenerClass);
    if (g_onWakePcmRead == NULL) {
        LOGD("Failed to resolve Java callback methods");
        releaseGlobalRefs(env);
        return JNI_FALSE;
    }

    unsigned int period_count = 4;
    unsigned int min_period_frames = sampleRate / 50; // 20ms 默认
    if (min_period_frames == 0) {
        min_period_frames = 320;
    }
    // tinyalsa 通常要求 period_size 是32的倍数
    unsigned int period_size = (min_period_frames + 31) / 32 * 32;
    if (period_size < 160) {
        period_size = 160;
    }
    struct pcm_config config;
    memset(&config, 0, sizeof(config));
    config.channels = (unsigned int) channels;
    config.rate = (unsigned int) sampleRate;
    config.period_size = period_size;
    config.period_count = period_count;
    config.format = format;
    config.start_threshold = 0;
    config.stop_threshold = 0;
    config.silence_threshold = 0;

    micCapturing = 1;
    struct pcm *pcmReader;
    char *bufferRead;
    unsigned int bytes_reader = 0;
    unsigned int frames_read = 0;
    unsigned int size_read = 0;

    pcmReader = pcm_open(card, device, PCM_IN, &config);
    if (!pcmReader || !pcm_is_ready(pcmReader)) {
        fprintf(stderr, "Unable to open PCM device (%s)\n",
                pcm_get_error(pcmReader));
        return 0;
    }

    size_read = pcm_frames_to_bytes(pcmReader, config.period_size);
    bufferRead = malloc(size_read);
    if (!bufferRead) {
        fprintf(stderr, "Unable to allocate %u bytes\n", size_read);
        free(bufferRead);
        pcm_close(pcmReader);
        return 0;
    }

    LOGD("startMicCapture success: channels=%u, rate=%u, period_size=%u, period_count=%u, frameBytes=%u",
         config.channels, config.rate, config.period_size, config.period_count, size_read);

    jsize shortLen = (jsize) (size_read / 2);
    if (shortLen <= 0 || !ensureReusableWakePcm(env, shortLen)) {
        free(bufferRead);
        pcm_close(pcmReader);
        releaseGlobalRefs(env);
        return JNI_FALSE;
    }

    while (micCapturing && !pcm_read(pcmReader, bufferRead, size_read)) {
        /* S16_LE 与 Java short 在小端设备上布局一致；复用同一 short[]，回调内需同步拷贝 */
        (*env)->SetShortArrayRegion(env, g_reusableWakePcm, 0, shortLen,
                                    (const jshort *) bufferRead);

        (*env)->CallVoidMethod(
                env,
                g_dataListener,
                g_onWakePcmRead,
                g_reusableWakePcm,
                shortLen
        );

        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
            break;
        }

        bytes_reader += size_read;
    }

    frames_read = pcm_bytes_to_frames(pcmReader, bytes_reader);
    free(bufferRead);
    pcm_close(pcmReader);
    releaseGlobalRefs(env);
    LOGD("stop MicCapture");

    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_hivi_audionativelib_manager_audioAlsa_AudioAlsaRecorder_stopMicCapture(JNIEnv *env,
                                                                             jobject clazz) {
    LOGD("stopMicCapture ");
    micCapturing = 0;
}