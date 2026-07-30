//
// Created by longjm on 2025/9/15.
//

#include "himusic.h"
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <stdarg.h>
#include <sys/syscall.h>
#include <signal.h>
#include "android/log.h"
#include "himusic/crossdevice.h"
#include <iostream>
#include <cstring>
#include <sys/stat.h>
#include <fcntl.h>
#include <cstdint>
#include <sys/time.h>
#include <sys/syscall.h>
#include "himusic/dmsdp_audio_handler.h"
#include "himusic/dmsdp_audio_data_type.h"
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <cstdio>
#include <vector>
#include <thread>
#include <mutex>
#include <atomic>
#include <fstream>
#include <queue>
#include <android/log.h>
//#include <openssl/evp.h>
//#include <openssl/bio.h>


#define TAG "himusic_huawei" // 这个是自定义的LOG的标识
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,TAG ,__VA_ARGS__) // 定义LOGD类型
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG ,__VA_ARGS__) // 定义LOGI类型
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,TAG ,__VA_ARGS__) // 定义LOGW类型
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG ,__VA_ARGS__) // 定义LOGE类型
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL,TAG ,__VA_ARGS__) // 定义LOGF类型


// 全局变量保存回调接口和方法ID
static jobject g_dataListener = nullptr;

static jmethodID g_onMediaChange = nullptr;
static jmethodID g_onPlayState = nullptr;
static jmethodID g_onPlayProgress = nullptr;


static JavaVM *g_javaVm = nullptr;

JNIEnv *mEvn;

// 工具函数：从JavaVM获取当前线程的JNIEnv
JNIEnv *getJNIEnv() {
    if (g_javaVm == nullptr) {
        return nullptr;
    }
    JNIEnv *env = nullptr;
    jint result = g_javaVm->GetEnv((void **) &env, JNI_VERSION_1_6);
    if (result == JNI_EDETACHED) {
        // 如果线程未附加，尝试附加
        if (g_javaVm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            return nullptr;
        }
    }
    return env;
}

// JNI初始化时获取JavaVM
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_javaVm = vm;
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}


// 释放全局引用
void releaseGlobalRefs(JNIEnv *env) {
    if (g_dataListener != nullptr) {
        env->DeleteGlobalRef(g_dataListener);
        g_dataListener = nullptr;
    }
}

void CrossDeviceLogDebug(const char *tag, const char *funName, const char *format, ...) {
    va_list stVaList;
    va_start(stVaList, format);
    __android_log_vprint(ANDROID_LOG_DEBUG, tag, format, stVaList);
    va_end(stVaList);
}

void CrossDeviceLogInfo(const char *tag, const char *funName, const char *format, ...) {
    va_list stVaList;
    va_start(stVaList, format);
//    __android_log_vprint(ANDROID_LOG_INFO, tag, format, stVaList);
    va_end(stVaList);
}

void CrossDeviceLogWarn(const char *tag, const char *funName, const char *format, ...) {
    va_list stVaList;
    va_start(stVaList, format);
    __android_log_vprint(ANDROID_LOG_WARN, tag, format, stVaList);
    va_end(stVaList);
}

void CrossDeviceLogError(const char *tag, const char *funName, const char *format, ...) {
    va_list stVaList;
    va_start(stVaList, format);
    __android_log_vprint(ANDROID_LOG_ERROR, tag, format, stVaList);
    va_end(stVaList);
}

bool Base64Decode(const std::string &encoded, std::vector<unsigned char> &decoded) {
    if (encoded.empty()) {
        return false;
    }
    std::string cleaned = encoded;
    size_t out_len = encoded.length() * 3 / 4;
    decoded.resize(out_len);
//    int decoded_len = EVP_DecodeBlock(
//            decoded.data(),
//            reinterpret_cast<const unsigned char *>(encoded.data()),
//            static_cast<int>(encoded.length())
//    );
//    if (decoded_len < 0) {
//        printf("Base64Encode error");
//        return false;
//    }
//    int padding = 0;
//    int len = cleaned.length();
//    if (len > 0 && cleaned[len - 1] == '=') padding++;
//    if (len > 1 && cleaned[len - 1] == '=') padding++;
//    decoded_len -= padding;
//    decoded.resize(decoded_len);
//    return true;
}

// 专辑图片下载
void DumpImage(MediaInfos *metaData, std::vector<unsigned char> &decode) {
    std::string mediaImage = metaData->mediaImage.data;
    if (mediaImage.empty()) {
        return;
    }
    LOGI("DumpImage compress size: %zu", mediaImage.size());
    std::unique_ptr<FILE, decltype(&std::fclose)> imageFp(std::fopen("/data/media_test.jpeg", "wb"),
                                                          &std::fclose);
    if (!imageFp) {
        LOGI("DumpImage Failed to open file");
        return;
    };
    if (!Base64Decode(mediaImage, decode)) {
        LOGI("Base64Decode is fail");
        return;
    }
}


// 将 C MediaInfos 转换为 Java MediaInfos
jobject convertToJavaMediaInfos(JNIEnv *env, MediaInfos *c_media) {
    LOGI("convertToJavaMediaInfos");
    jclass clazz = env->FindClass("com/hivi/audionativelib/manager/himusic/MediaInfo");
    jmethodID constructor = env->GetMethodID(clazz, "<init>", "()V");
    jobject j_media = env->NewObject(clazz, constructor);

    // 辅助函数：将 DataUnit 转换为 jstring
    auto setStringField = [env, j_media, clazz](const char *fieldName, DataUnit *du) {
        jfieldID fid = env->GetFieldID(clazz, fieldName, "Ljava/lang/String;");
        jstring jstr = NULL;

        if (du && du->data && du->dataLen > 0) {
            jstr = env->NewStringUTF(du->data);
        } else {
            jstr = env->NewStringUTF("");
        }

        env->SetObjectField(j_media, fid, jstr);
        env->DeleteLocalRef(jstr);
    };

    // 设置字符串字段
    setStringField("assetId", &c_media->assetId);
    setStringField("title", &c_media->title);
    setStringField("artist", &c_media->artist);
    setStringField("author", &c_media->author);
    setStringField("avQueueName", &c_media->avQueueName);
    setStringField("avQueueId", &c_media->avQueueId);
    setStringField("avQueueImage", &c_media->avQueueImage);
    setStringField("avQueueImageUri", &c_media->avQueueImageUri);
    setStringField("album", &c_media->album);
    setStringField("writer", &c_media->writer);
    setStringField("composer", &c_media->composer);
    setStringField("imageType", &c_media->imageType);
    setStringField("mediaImageUri", &c_media->mediaImageUri);
    setStringField("publishDate", &c_media->publishDate);
    setStringField("subTitle", &c_media->subTitle);
    setStringField("description", &c_media->description);
    setStringField("lyric", &c_media->lyric);
    setStringField("previousAssetId", &c_media->previousAssetId);
    setStringField("nextAssetId", &c_media->nextAssetId);
    setStringField("drmSchemes", &c_media->drmSchemes);
    setStringField("bundleIcon", &c_media->bundleIcon);
    setStringField("bundleIconUri", &c_media->bundleIconUri);
    setStringField("singleLyricText", &c_media->singleLyricText);

//        setStringField("mediaImage", &c_media->mediaImage);

    std::vector<unsigned char> decode;
//    DumpImage(c_media, decode); // 解析图片

    if (decode.empty()) {
        LOGE("DumpImage decode is invalid");
        jbyteArray jarray = (env)->NewByteArray(0);
        jfieldID fid = (env)->GetFieldID(clazz, "mediaImage", "[B");
        (env)->SetObjectField(j_media, fid, jarray);
    } else {
        int decoded_len = decode.size();
        jbyteArray jarray = (env)->NewByteArray(decoded_len);
        jbyte *jnum = new jbyte[decoded_len];
        std::memcpy(jnum, &decode[0], decoded_len);
        (env)->SetByteArrayRegion(jarray, 0, decoded_len, jnum);
        free(jnum);
        jfieldID fid = (env)->GetFieldID(clazz, "mediaImage", "[B");
        (env)->SetObjectField(j_media, fid, jarray);
        (env)->DeleteLocalRef(jarray);

    }

    // 设置整型字段
    auto setIntField = [env, j_media, clazz](const char *fieldName, int value) {
        jfieldID fid = (*env).GetFieldID(clazz, fieldName, "I");
        (*env).SetIntField(j_media, fid, value);
    };

    setIntField("duration", c_media->duration);
    setIntField("skipIntervals", c_media->skipIntervals);
    setIntField("filter", c_media->filter);
    setIntField("mediaLength", c_media->mediaLength);
    setIntField("avQueueLength", c_media->avQueueLength);
    setIntField("displayTags", c_media->displayTags);

    return j_media;
}


// 媒体状态显示
int32_t DeviceMediaChangedCallback(DeviceMediaType type, void *item) {
    if (item == nullptr) {
        LOGD("item nullptr -- yangfan test");
        return -1;
    }
    if (type == DeviceMediaType::DEVICE_MEDIA_POSITION) {
        auto *mediaPosition = static_cast<MediaPosition *>(item);
//        LOGD("mediaPosition.position:%d\n", mediaPosition->position);
        // LOGD("mediaPosition.bufferPosition:%d\n", mediaPosition->bufferPosition);
//        LOGD("mediaPosition.duration:%d\n", mediaPosition->duration);
        // 检查是否已初始化
        if (g_dataListener == nullptr || g_onPlayProgress == nullptr) {
            LOGE("Listener not initialized");
            return 0;
        }
        // 获取当前线程的JNIEnv
        JNIEnv *env = getJNIEnv();
        if (env == nullptr) {
            LOGE("Failed to get JNIEnv");
            return 0;
        }
        env->CallVoidMethod(
                g_dataListener,
                g_onPlayProgress,
                mediaPosition->position,
                mediaPosition->duration
        );
    }
    if (type == DeviceMediaType::DEVICE_MEDIA_INFO) {
        auto *mediaInfos = static_cast<MediaInfos *>(item);
        LOGD("mediaInfo.assetId:%s\n", mediaInfos->assetId.data);
        LOGD("mediaInfo.title:%s\n", mediaInfos->title.data);
        LOGD("mediaInfo.artist:%s\n", mediaInfos->artist.data);
        LOGD("mediaInfo.author:%s\n", mediaInfos->author.data);
        LOGD("mediaInfo.avQueueName:%s\n", mediaInfos->avQueueName.data);
        LOGD("mediaInfo.avQueueId:%s\n", mediaInfos->avQueueId.data);
        LOGD("mediaInfo.avQueueImage:%s\n", mediaInfos->avQueueImage.data);
        LOGD("mediaInfo.avQueueImageUri:%s\n", mediaInfos->avQueueImageUri.data);
        LOGD("mediaInfo.album:%s\n", mediaInfos->album.data);
        LOGD("mediaInfo.writer:%s\n", mediaInfos->writer.data);
        LOGD("mediaInfo.composer:%s\n", mediaInfos->composer.data);
        LOGD("mediaInfo.duration:%d\n", mediaInfos->duration);
        LOGD("dump picture mediaImage.....\n");
        LOGD("mediaInfo.imageType:%s\n", mediaInfos->imageType.data);
        LOGD("mediaInfo.mediaImageUri:%s\n", mediaInfos->mediaImageUri.data);
        LOGD("mediaInfo.publishDate:%s\n", mediaInfos->publishDate.data);
        LOGD("mediaInfo.subTitle:%s\n", mediaInfos->subTitle.data);
        LOGD("mediaInfo.description:%s\n", mediaInfos->description.data);
        LOGD("mediaInfo.lyric:%s\n", mediaInfos->lyric.data);
        LOGD("mediaInfo.previousAssetId:%s\n", mediaInfos->previousAssetId.data);
        LOGD("mediaInfo.nextAssetId:%s\n", mediaInfos->nextAssetId.data);
        LOGD("mediaInfo.skipIntervals:%d\n", mediaInfos->skipIntervals);
        LOGD("mediaInfo.filter:%d\n", mediaInfos->filter);
        LOGD("mediaInfo.mediaLength:%d\n", mediaInfos->mediaLength);
        LOGD("mediaInfo.avQueueLength:%d\n", mediaInfos->avQueueLength);
        LOGD("mediaInfo.displayTags:%d\n", mediaInfos->displayTags);
        LOGD("mediaInfo.drmSchemes:%s\n", mediaInfos->drmSchemes.data);
        LOGD("mediaInfo.bundleIcon:%s\n", mediaInfos->bundleIcon.data);
        LOGD("mediaInfo.bundleIconUri:%s\n", mediaInfos->bundleIconUri.data);
        LOGD("mediaInfo.singleLyricText:%s\n", mediaInfos->singleLyricText.data);

        // 检查是否已初始化
        if (g_dataListener == nullptr || g_onMediaChange == nullptr) {
            LOGE("Listener not initialized");
            return 0;
        }

        // 获取当前线程的JNIEnv
        JNIEnv *env = getJNIEnv();
        if (env == nullptr) {
            LOGE("Failed to get JNIEnv");
            return 0;
        }


        // 转换为Java对象
        LOGI("j_media start");
//        jobject j_media = convertToJavaMediaInfos(env, mediaInfos);
        LOGI("j_media finish");

//
//        env->CallVoidMethod(
//                g_dataListener,
//                g_onMediaChange,
//                j_media
//        );
//
//        // 清理本地引用
//        env->DeleteLocalRef(j_media);
    }
    return 0;
}

void DeviceServiceChangedCallback(const char *serviceId, uint32_t idLen, int state) {
    LOGD("Device TD: %s,idLen:%d, State:%d\n", serviceId, idLen, state);
    // 检查是否已初始化
    if (g_dataListener == nullptr || g_onMediaChange == nullptr) {
        LOGE("Listener not initialized");
        return;
    }

    // 获取当前线程的JNIEnv
    JNIEnv *env = getJNIEnv();
    if (env == nullptr) {
        LOGE("Failed to get JNIEnv");
        return;
    }

    // 转换为Java对象
    env->CallVoidMethod(
            g_dataListener,
            g_onPlayState,
            state
    );
}

void copyStringToBuffer(DataUnit &targetBuffer, const char *sourceString) {
    if (sourceString == nullptr) {
        throw std::invalid_argument("source string is null");
    }
    size_t strLen = strlen(sourceString) + 1;
    if (targetBuffer.data != nullptr) {
        free(targetBuffer.data);
        targetBuffer.data = nullptr;
    }
    targetBuffer.data = static_cast<char *>(malloc(strLen));
    if (targetBuffer.data == nullptr) {
        throw std::runtime_error("Memory allocation failed");
    }
    targetBuffer.dataLen = strLen - 1;
    memset(targetBuffer.data, 0, strLen);
    memcpy(targetBuffer.data, sourceString, strLen);
}

// 设备能力设置
void initDeviceConfig(DeviceConfig *deviceConfig) {
    deviceConfig->basicInfo.type = DeviceType::CROSS_DEVICE_HIMUSIC;
    deviceConfig->basicInfo.subType = DeviceSubType::DEVICE_HIMUSIC_DEFAULT;
    copyStringToBuffer(deviceConfig->basicInfo.modelId, "HIVI");
    copyStringToBuffer(deviceConfig->basicInfo.brMac, "01-00-5e-00-00-fa");
    copyStringToBuffer(deviceConfig->basicInfo.deviceId, "DD:DD:DD:DD:DD:Da");
    copyStringToBuffer(deviceConfig->basicInfo.deviceBrand, "MG100");
    // 初始化 connectCapability 成员
    deviceConfig->basicInfo.connectCapability.supportWlan = false;
    deviceConfig->basicInfo.connectCapability.supportAdvWifi = false;
    deviceConfig->basicInfo.connectCapability.supportQrWifi = false;
    deviceConfig->basicInfo.connectCapability.supportUsb = false;
    deviceConfig->basicInfo.connectCapability.supportNfc = false;
    // 初始化 HwFeatureInfo 成员
    copyStringToBuffer(deviceConfig->featureInfo.deviceModel, "");
    copyStringToBuffer(deviceConfig->featureInfo.manufacturer, "");
    copyStringToBuffer(deviceConfig->featureInfo.model, "");
    copyStringToBuffer(deviceConfig->featureInfo.os, "");
    copyStringToBuffer(deviceConfig->featureInfo.cpu, "1");
    copyStringToBuffer(deviceConfig->featureInfo.ram, "1");
    copyStringToBuffer(deviceConfig->featureInfo.rom, "1");
    copyStringToBuffer(deviceConfig->featureInfo.screenSize, "1");
    copyStringToBuffer(deviceConfig->featureInfo.screenMetrics, "1");
    copyStringToBuffer(deviceConfig->featureInfo.linuxKernel, "1");
    copyStringToBuffer(deviceConfig->featureInfo.btChip, "1");
    copyStringToBuffer(deviceConfig->featureInfo.wifiChip, "1");
    // 初始化 HwDisplayCapability 成员
    deviceConfig->displayCapability.codecs = 0;
    deviceConfig->displayCapability.fps = 0;
    deviceConfig->displayCapability.gop = 0;
    deviceConfig->displayCapability.bitrate = 0;
    deviceConfig->displayCapability.minBitrate = 0;
    deviceConfig->displayCapability.maxBitrate = 0;
    deviceConfig->displayCapability.dpi = 0;
    deviceConfig->displayCapability.profile = 0;
    deviceConfig->displayCapability.level = 0;
    deviceConfig->displayCapability.screenWidth = 0;
    deviceConfig->displayCapability.screenHeight = 0;
    deviceConfig->displayCapability.width = 0;
    deviceConfig->displayCapability.height = 0;
}


/**
 * hi music SDK 初始化
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_hivi_audionativelib_manager_himusic_HiMisicManager_initDevices(JNIEnv *env, jobject thiz) {
    // TODO: implement crossDeviceInit()
    auto *deviceConfig = new DeviceConfig();
    // 初始化设备
    initDeviceConfig(deviceConfig);

    auto *g_logGroup = new DeviceLogGroup();
    g_logGroup->logD = CrossDeviceLogDebug;
    g_logGroup->logE = CrossDeviceLogError;
    g_logGroup->logI = CrossDeviceLogInfo;
    g_logGroup->logW = CrossDeviceLogWarn;
    int32_t ret = CrossDeviceInit(deviceConfig, g_logGroup);
    LOGD("CrossDeviceInit %d", ret);


    // 两个media回调
    auto *deviceListener = new DeviceListener();
    deviceListener->DeviceMediaChanged = DeviceMediaChangedCallback;
    deviceListener->DeviceServiceChanged = DeviceServiceChangedCallback;
    LOGD("deviceListener->DeviceMediaChanged,0x%p\n", deviceListener->DeviceMediaChanged);
    CrossDeviceRegisterListener(deviceListener);

    return ret;
}


/**
 * hi music SDK 释放
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_hivi_audionativelib_manager_himusic_HiMisicManager_crossDeviceRelease(JNIEnv *env, jobject thiz) {
    // TODO: implement crossDeviceRelease()
    int32_t ret = CrossDeviceRelease();
    if (ret != 0) {
        LOGE("CrossDeviceRelease failed\n");
    } else {
        LOGE("CrossDeviceRelease");
    }
    return ret;
}

/**
 * 音频接口
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_hivi_audionativelib_manager_himusic_HiMisicManager_dmsdpGetAudioHandler(JNIEnv *env,jobject listener) {
    //JavaVM是虚拟机在JNI中的表示，等下再其他线程回调java层需要用到

    // 释放之前的引用（避免内存泄漏）
    releaseGlobalRefs(env);
    // 创建接口的全局引用（因为要在其他地方使用）
    g_dataListener = env->NewGlobalRef(listener);
    if (g_dataListener == nullptr) {
        LOGE("Failed to create global ref for listener");
        return -1;
    }

    // 获取接口类引用
    jclass listenerClass = env->GetObjectClass(listener);
    if (listenerClass == nullptr) {
        LOGE("Failed to get listener class");
        releaseGlobalRefs(env);
        return -1;
    }

    // 获取方法ID：注意方法签名要与接口完全一致
    g_onMediaChange = env->GetMethodID(
            listenerClass,
            "onMediaChange",
            "(Lcom/hivi/audionativelib/manager/himusic/MediaInfo;)V"  // 方法签名：(MediaInfo)void
    );
    g_onPlayState = env->GetMethodID(
            listenerClass,
            "onPlayState",
            "(I)V"  // 方法签名：(int)void
    );
    g_onPlayProgress = env->GetMethodID(
            listenerClass,
            "onPlayProgress",
            "(II)V"  // 方法签名：(int int)void
    );

    LOGD("接口注册成功");
    // 释放局部引用
    env->DeleteLocalRef(listenerClass);


    return 0;
}

// 反向控制
extern "C"
JNIEXPORT jint JNICALL
Java_com_hivi_audionativelib_manager_himusic_HiMisicManager_crossDevicePostEvent(JNIEnv *env, jobject thiz,
                                                                    jint commend) {
    // TODO: implement crossDevicePostEvent()

    char *handle = "himusic";
    /**
       1 - play------播放
       2 - next-----下一首
       3 - previous-上一首
       4 - pause----暂停
    */
    char reg[20];
    if (commend == 1) {
        strncpy(reg, "play", sizeof(reg) - 1);                // -----播放
    } else if (commend == 2) {
        strncpy(reg, "next", sizeof(reg) - 1);                // -----下一首
    } else if (commend == 3) {
        strncpy(reg, "previous", sizeof(reg) - 1);            // -----上一首
    } else if (commend == 4) {
        strncpy(reg, "pause", sizeof(reg) - 1);               // -----暂停
    }
    reg[sizeof(reg) - 1] = '\0';
    return CrossDevicePostEvent(handle, DeviceRequestType::DEVICE_REQ_REMOTE_CTRL, reg, sizeof(reg));
}