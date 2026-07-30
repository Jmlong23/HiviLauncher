/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2019-2019. All rights reserved.
 * Description: Remote Control related data structure definitions.
 * Author: ***
 * Create: 2025-07-24
 */

#ifndef CROSSDEVICE_LOG_H
#define CROSSDEVICE_LOG_H

#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>

#include <time.h>
#include <sys/time.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include "crossdevice.h"

/* Log domain */
#ifndef LOG_DOMAIN
#define LOG_DOMAIN 0
#endif

/* Log tag */
#ifndef LOG_TAG
#define LOG_TAG NULL
#endif

#define DFTLOG_TAG "CrossDevice"
typedef enum {
    LOG_TYPE_MIN = 0,
    LOG_APP = 0,
    /* Log to kmsg, only used by init phase. */
    LOG_INIT = 1,
    /* Used by core service, framework. */
    LOG_CORE = 3,
    LOG_TYPE_MAX
} LogType;

typedef enum {
    HILOG_LEVEL_MIN = 0,
    HILOG_DEBUG = 3,
    HILOG_INFO = 4,
    HILOG_WARN = 5,
    HILOG_ERROR = 6,
    HILOG_FATAL = 7,
    HILOG_LEVEL_MAX,
} LogLevel;

typedef int (*HilogPrint)(LogType type, LogLevel level, unsigned int domain, const char* tag, const char* fmt, ...);

extern HilogPrint g_hiLogPrintFunc;

#define HILOGDFT_DEBUG(...) ((void)g_hiLogPrintFunc((LOG_CORE), HILOG_DEBUG, LOG_DOMAIN, DFTLOG_TAG, __VA_ARGS__))

#define HILOGDFT_INFO(...) ((void)g_hiLogPrintFunc((LOG_CORE), HILOG_INFO, LOG_DOMAIN, DFTLOG_TAG, __VA_ARGS__))

#define HILOGDFT_WARN(...) ((void)g_hiLogPrintFunc((LOG_CORE), HILOG_WARN, LOG_DOMAIN, DFTLOG_TAG, __VA_ARGS__))

#define HILOGDFT_ERROR(...) ((void)g_hiLogPrintFunc((LOG_CORE), HILOG_ERROR, LOG_DOMAIN, DFTLOG_TAG, __VA_ARGS__))

#define HILOGDFT_FATAL(...) ((void)g_hiLogPrintFunc((LOG_CORE), HILOG_FATAL, LOG_DOMAIN, DFTLOG_TAG, __VA_ARGS__))

extern DeviceLogGroup g_LogFunc;

void DBGDefault(const char *format, ...);
void ERRDefault(const char *format, ...);

#define LOGI(...) do {                                       \
        if (g_LogFunc.logI != nullptr) {                        \
            g_LogFunc.logI("CrossDevice-", __func__, __VA_ARGS__);  \
        } else {                                                  \
            DBGDefault(__VA_ARGS__); \
        }                                                         \
        if (g_hiLogPrintFunc != nullptr) {   \
            HILOGDFT_INFO(__VA_ARGS__); \
        } \
    } while (0);

#define DBG(...) do {                                                          \
        if (g_LogFunc.logD != nullptr) {                        \
            g_LogFunc.logD("CrossDevice-", __func__, __VA_ARGS__);  \
        } else {                                                  \
            DBGDefault(__VA_ARGS__); \
        }                                                         \
        if (g_hiLogPrintFunc != nullptr) {   \
            HILOGDFT_DEBUG(__VA_ARGS__); \
        } \
    } while (0);

#define LOGW(...) do {                                                          \
        if (g_LogFunc.logW != nullptr) {                        \
            g_LogFunc.logW("CrossDevice-", __func__, __VA_ARGS__);  \
        } else {                                                  \
            ERRDefault(__VA_ARGS__); \
        }                                                         \
        if (g_hiLogPrintFunc != nullptr) {   \
            HILOGDFT_WARN(__VA_ARGS__); \
        } \
    } while (0);

#define ERR(...) do {                                                          \
        if (g_LogFunc.logE != nullptr) {                        \
            g_LogFunc.logE("CrossDevice-", __func__, __VA_ARGS__);  \
        } else {                                                  \
            ERRDefault(__VA_ARGS__); \
        }                                                         \
        if (g_hiLogPrintFunc != nullptr) {   \
            HILOGDFT_ERROR(__VA_ARGS__); \
        } \
    } while (0);

void SetLogFunction(DeviceLogGroup *log);

#endif  // LOG_H
