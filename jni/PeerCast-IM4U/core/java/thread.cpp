// ------------------------------------------------
// File : thread.cpp
// Date: 14-Mar-2013
// Author: T. Yoshizawa
// Desc:
//　作成したネイティブスレッドをJVMに紐付ける。
//
// ------------------------------------------------
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.

// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// ------------------------------------------------

#include "thread.h"

#ifdef _UNIX
#include <sys/types.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <pthread.h>

#ifndef ANDROID
//ANDROIDではgettid()が使える.
#ifdef __LINUX__
//FIXME: 試してない
static int gettid(){
	return syscall(SYS_gettid);
}
#else
static int gettid(){
	return pthread_self();
}
#endif //__LINUX__
#endif //!ANDROID


#ifdef NDEBUG
#define thLOG(...)
#else

//#define thLOG LOG
#include <android/log.h>
#define thLOG(...) __android_log_print(ANDROID_LOG_DEBUG, "PeCaTh", __VA_ARGS__)
#endif

static pthread_key_t sThreadKey;
static JavaVM *sVM;

static JNIEnv* _jni_attach_thread() {
	const char *errmsg = "";
	JNIEnv *env;
	jint ret = sVM->GetEnv((void**)&env, JNI_VERSION_1_6);
	if (ret!=JNI_EDETACHED){
		errmsg = "VM->GetEnv()!=JNI_EDETACHED";
		goto FAIL;
	}
	ret = sVM->AttachCurrentThread(&env, NULL);
	if (ret!=JNI_OK){
		errmsg = "VM->AttachCurrentThread()";
		goto FAIL;
	}
	thLOG("TID(%d) [OK] VM->AttachCurrentThread()", gettid());
	return env;
FAIL:
	thLOG("TID(%d) [FAIL] %s", gettid(), errmsg);
	return NULL;
}

void jni_logging_thread_func(const char* funcName){
	thLOG("%s: TID(%d) thread start...", funcName, gettid());
}

static void _jni_thread_destroyed(void* ptr) {
    JNIEnv *env = (JNIEnv*)ptr;
    if (env != NULL) {
        sVM->DetachCurrentThread();
        thLOG("TID(%d) thread finish. detached... ", gettid());
        ::pthread_setspecific(sThreadKey, NULL);
    } else {
    	thLOG("TID(%d) thread finish. bad ptr==NULL!!", gettid());
    }
}
jboolean jni_thread_setup(){
    JNIEnv *env = _jni_attach_thread();
    ::pthread_setspecific(sThreadKey, (void*)env);
    return JNI_TRUE;
}

jboolean jni_thread_register_shutdown_func(JavaVM *vm){
	sVM = vm;
    if (::pthread_key_create(&sThreadKey, _jni_thread_destroyed)) {
        thLOG("Error initializing pthread key");
    }
    return JNI_TRUE;
}

#else
#error "未実装"

#endif //_UNIX

