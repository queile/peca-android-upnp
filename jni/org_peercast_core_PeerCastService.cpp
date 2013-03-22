// ------------------------------------------------
// File : org_peercast_core_PeerCast.cpp
// Date: 14-Mar-2013
// Author: (c) 2013 T Yoshizawa
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
#include <android/log.h>
#include <unistd.h>
#include "unix/usys.h"

#include "peercast.h"
#include "java/thread.h"

#include "org_peercast_core_PeerCastService.h"
#define TAG "PeCaNt"

class ASys: public USys {
public:
	void exit() {
		::__android_log_print(ANDROID_LOG_FATAL, TAG, "%s is Not Implemented",
				__FUNCTION__);
	}
	void executeFile(const char *f) {
		::__android_log_print(ANDROID_LOG_FATAL, TAG, "%s is Not Implemented",
				__FUNCTION__);
	}
};

class aPeercastInst: public PeercastInstance {
public:
	virtual Sys* APICALL createSys() {
		/**staticで持っておかないと、quit()のあと生きてるスレッドが
		 * sys->endThread()を呼んでクラッシュする。*/
		static ASys sys;
		return &sys;
	}
};

class aPeercastApp: public PeercastApplication {

	String iniFilePath;
	String resourceDir;
public:
	aPeercastApp(const char* ini, const char* res) :
			iniFilePath(ini), resourceDir(res) {
		resourceDir.append("/");
		::__android_log_print(ANDROID_LOG_INFO, TAG, "IniFilePath=%s",
				iniFilePath.cstr());
		::__android_log_print(ANDROID_LOG_INFO, TAG, "ResourceDir=%s",
				resourceDir.cstr());
	}
	virtual const char * APICALL getIniFilename() {
		return iniFilePath;
	}

	virtual const char * APICALL getPath() {
		return resourceDir;
	}

	virtual const char *APICALL getClientTypeOS() {
		return PCX_OS_LINUX;
	}

	virtual void APICALL printLog(LogBuffer::TYPE t, const char *str) {
		int prio = ::ANDROID_LOG_UNKNOWN;
		switch (t) {
		case LogBuffer::T_NONE:
			prio = ::ANDROID_LOG_UNKNOWN;
			break;
		case LogBuffer::T_DEBUG:
			prio = ::ANDROID_LOG_DEBUG;
			break;
		case LogBuffer::T_ERROR:
			prio = ::ANDROID_LOG_ERROR;
			break;
		case LogBuffer::T_NETWORK:
			prio = ::ANDROID_LOG_INFO;
			break;
		case LogBuffer::T_CHANNEL:
			prio = ::ANDROID_LOG_INFO;
			break;
		}
		char tag[64];
		::snprintf(tag, sizeof(tag), "%s[%s]", TAG, LogBuffer::getTypeStr(t));
		::__android_log_write(prio, tag, str); //thread-safe
	}

};

// ----------------------------------
void setSettingsUI() {
}
// ----------------------------------
void showConnections() {
}
// ----------------------------------
void PRINTLOG(LogBuffer::TYPE type, const char *fmt, va_list ap) {
}

JNIEXPORT jint JNICALL
Java_org_peercast_core_PeerCastService_nativeStart
(JNIEnv *env, jclass cls, jstring oIniFilePath, jstring oResDir) {
	env->MonitorEnter(cls);

	jint port = 0;
	const char *ini, *res;

	if (peercastApp) {
		jclass ex = env->FindClass("java/lang/IllegalStateException");
		env->ThrowNew(ex, "peercast already running!");
		env->DeleteLocalRef(ex);
		goto _exit_;
	}
	ini = env->GetStringUTFChars(oIniFilePath, NULL);
	res = env->GetStringUTFChars(oResDir, NULL);
	peercastApp = new aPeercastApp(ini, res);
	peercastInst = new aPeercastInst();

	peercastInst->init();

	//peercastApp->getPathを上書きしない。
	servMgr->getModulePath = false;

	//servMgr->serverHost.port = port;
	//servMgr->restartServer=true;

	env->ReleaseStringUTFChars(oIniFilePath, ini);
	env->ReleaseStringUTFChars(oResDir, res);

	port = servMgr->serverHost.port;

	_exit_:
	env->MonitorExit(cls);
	return port;
}

JNIEXPORT void JNICALL
Java_org_peercast_core_PeerCastService_nativeQuit
(JNIEnv *env, jclass cls) {
	env->MonitorEnter(cls);

	if (peercastInst) {
		peercastInst->saveSettings();
		peercastInst->quit();
		__android_log_write(ANDROID_LOG_DEBUG, TAG, "peercastInst->quit() OK.");
		::sleep(1);
	}

#define DELETE_GLOBAL(sym) do {\
	delete sym;\
	sym = 0;\
	::__android_log_print(ANDROID_LOG_DEBUG, TAG, "delete global '%s' OK.", #sym);\
}while(0)

	 DELETE_GLOBAL(peercastInst);
	 DELETE_GLOBAL(peercastApp);
	 DELETE_GLOBAL(servMgr);
	 DELETE_GLOBAL(chanMgr);

	 env->MonitorExit(cls);
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *vm, void *reserved) {
//Androidで呼ばれることはないらしい。
}

JNIEXPORT void JNICALL Java_org_peercast_core_PeerCastService_nativeClassInit
(JNIEnv *, jclass)
{

}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
JNIEnv* env;
if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
	return -1;
}
::jni_thread_register_shutdown_func(vm);
return JNI_VERSION_1_6;
}

