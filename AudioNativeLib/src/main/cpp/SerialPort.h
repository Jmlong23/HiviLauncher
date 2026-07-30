#include <jni.h>
#ifndef _Included_qingwei_kong_serialportlibrary_SerialPort
#define _Included_qingwei_kong_serialportlibrary_SerialPort
#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jobject JNICALL
Java_com_hivi_audionativelib_manager_serialport_SerialPortManager_JNIopenNative(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jstring path,
                                                                           jint baud_rate,
                                                                           jint flags);
JNIEXPORT void JNICALL
Java_com_hivi_audionativelib_manager_serialport_SerialPortManager_JNIcloseNative(JNIEnv *env,
                                                                            jobject thiz);

#ifdef __cplusplus
}
#endif
#endif
