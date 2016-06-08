#include <malloc.h>
#include "librtmp-jni.h"
#include "rtmp.h"
#include "librtmp/log.h"
#include <android/log.h>
#define LOG_TAG "RTMPLOG"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

//
// Created by faraklit on 01.01.2016.
//

RTMP *rtmp = NULL;
/*
 * Class:     net_butterflytv_rtmp_client_RtmpClient
 * Method:    open
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_net_butterflytv_rtmp_1client_RtmpClient_open
        (JNIEnv * env, jobject thiz, jstring url_, jboolean isPublishMode, jboolean isLive) {
    const char *url = (*env)->GetStringUTFChars(env, url_, 0);
    if(!(rtmp = RTMP_Alloc())){
        rtmp = NULL;
        return -1;
    }
    LOGI("INIT!");
	RTMP_Init(rtmp);
    LOGI("SETUPURL!");
    if(!RTMP_SetupURL(rtmp, url)) {
        RTMP_Free(rtmp);
        rtmp = NULL;
        return -2;
    }

    if(isLive) {
        rtmp->Link.lFlags |= RTMP_LF_LIVE;
    }

    if(isPublishMode) {
        LOGI("ENABLEWRITE!");
        RTMP_EnableWrite(rtmp);
    }
    LOGI("CONNECT!");
    if(!RTMP_Connect(rtmp, NULL)) {
        RTMP_Free(rtmp);
        rtmp = NULL;
        return -3;
    }
    LOGI("CONNECTSTREAM!");
    if(!RTMP_ConnectStream(rtmp, 0)) {
        RTMP_Free(rtmp);
        rtmp = NULL;
        return -4;
    }
    LOGI("DONE OPENING!");
    (*env)->ReleaseStringUTFChars(env, url_, url);
    return 1;
}



/*
 * Class:     net_butterflytv_rtmp_client_RtmpClient
 * Method:    read
 * Signature: ([CI)I
 */
JNIEXPORT jint JNICALL Java_net_butterflytv_rtmp_1client_RtmpClient_read
        (JNIEnv * env, jobject thiz, jbyteArray data_, jint offset, jint size) {
    char* data = malloc(size * sizeof(char));

    if(!rtmp){
        return -1;
    }

    int readCount = 0;
    LOGI("READ!");
    if ((readCount = RTMP_Read(rtmp, data, size)) > 0) {
        (*env)->SetByteArrayRegion(env, data_, offset, readCount, data);
    }

    free(data);

 	return readCount;
}

/*
 * Class:     net_butterflytv_rtmp_client_RtmpClient
 * Method:    write
 * Signature: ([CI)I
 */
JNIEXPORT jint JNICALL Java_net_butterflytv_rtmp_1client_RtmpClient_write
        (JNIEnv * env, jobject thiz, jcharArray data, jint size) {
    return RTMP_Write(rtmp, data, size);
}

/*
 * Class:     net_butterflytv_rtmp_client_RtmpClient
 * Method:    seek
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_butterflytv_rtmp_1client_RtmpClient_seek
        (JNIEnv * env, jobject thiz, jint seekTime) {
    return 0;
}

/*
 * Class:     net_butterflytv_rtmp_client_RtmpClient
 * Method:    pause
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_net_butterflytv_rtmp_1client_RtmpClient_pause
        (JNIEnv * env, jobject thiz, jint pauseTime) {
    return RTMP_Pause(rtmp, pauseTime);
}

/*
 * Class:     net_butterflytv_rtmp_client_RtmpClient
 * Method:    close
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_net_butterflytv_rtmp_1client_RtmpClient_close
        (JNIEnv * env, jobject thiz) {
    if(rtmp != NULL){
        LOGI("CLOSE!");
        RTMP_Close(rtmp);
        rtmp = NULL;
    }
    return 0;
}


JNIEXPORT jint JNICALL
Java_net_butterflytv_rtmp_1client_RtmpClient_isConnected(JNIEnv *env, jobject instance) {
     return rtmp != NULL && RTMP_IsConnected(rtmp);
}

