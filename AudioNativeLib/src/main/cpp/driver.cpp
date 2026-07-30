#include <jni.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/select.h>
#include <sys/time.h>
#include <unistd.h>
#include  <iostream>

#include "android/log.h"
#include "driver.h"

static const char *TAG = "ec0902_JNI";

#define LOGI(fmt, args...)  __android_log_print(ANDROID_LOG_INFO, TAG, fmt, ##args);
#define LOGD(fmt, args...)  __android_log_print(ANDROID_LOG_DEBUG, TAG, fmt, ##args);
#define LOGE(fmt, args...)  __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, ##args);

extern "C"
JNIEXPORT jint JNICALL
Java_com_hivi_audionativelib_manager_audioDriver_AudioDriverManager_audioDriverOpen(JNIEnv *env,
                                                                                 jobject thiz,
                                                                                 jstring path) {

    jboolean iscopy;
    const char *path_utf = (*env).GetStringUTFChars(path, &iscopy);
    int i = 3;
    int fd = -1;
    while(i > 0 && fd < 0){
        fd = open(path_utf, O_RDWR);
        usleep(1000 * 1000);
        LOGD("open %d, fd: %d\n", i, fd);
        i--;
    }

    if (0 > fd) {
        LOGD("open error\n");
        return -1;
    }

    LOGD("打开设备 %s成功\n", path_utf);
    LOGD("fd %d\n", fd);
    return fd;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_hivi_audionativelib_manager_audioDriver_AudioDriverManager_audioDriverClose(JNIEnv *env, jobject thiz, jint fd) {
    if (fd > 0) {
        close(fd);
        fd = -1;
    } else {
        return -1;
    }
    LOGD("关闭设备成功");
    return 0;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_hivi_audionativelib_manager_audioDriver_AudioDriverManager_audioDriverRead(JNIEnv *env,
                                                                                 jobject thiz, jint fd) {

    char buffer[256];
    int len = 0;
    int ret;
    fd_set readfds;

    memset(buffer, 0, sizeof(buffer));
    while (fd > 0) {
        FD_ZERO(&readfds);
        FD_SET(fd, &readfds);
        ret = select(fd + 1, &readfds, NULL, NULL, NULL);
        if(FD_ISSET(fd, &readfds)) {
            len = read(fd, buffer, sizeof(buffer));
            LOGD("len: %d", len);
            len = strlen(buffer);
            jbyteArray c_result = (*env).NewByteArray(len);
            // 赋值
            (*env).SetByteArrayRegion(c_result, 0, len, reinterpret_cast<const jbyte *>(buffer));
            // 释放内存
            env->ReleaseByteArrayElements(c_result, env->GetByteArrayElements(c_result, JNI_FALSE), 0);
            return c_result;
        }
    }
    return nullptr;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_hivi_audionativelib_manager_audioDriver_AudioDriverManager_audioDriverWrite(JNIEnv *env,
                                                                                  jobject thiz,
                                                                                  jint fd,
                                                                                  jstring message) {
    if (message == NULL) {
        return -1;
    }

    // 将 jstring 转换为 C 字符串
    const char *buffer = env->GetStringUTFChars(message, NULL);
    if (buffer == NULL) {
        return -1;  // 内存不足
    }

    // 获取字符串长度
    size_t length = strlen(buffer);

    // 写入文件描述符
    ssize_t bytes_written = write(fd, buffer, length);
    LOGD("write bytes: %d, write buffer: %s", bytes_written, buffer);
    // 释放缓冲区
    env->ReleaseStringUTFChars(message, buffer);

    // 返回写入的字节数，如果出错返回-1
    return (jint)bytes_written;
}