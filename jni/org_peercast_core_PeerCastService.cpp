// ------------------------------------------------
// File : org_peercast_core_PeerCastService.cpp
// Date: 10-Apr-2013
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
#include <jni.h>

#include "org_peercast_core_PeerCastService.h"

#define TAG "PeCaNt"

#define IF_NULL_RETURN(p, ret) \
	do { \
		if (!(p)) {\
			::__android_log_print(ANDROID_LOG_FATAL, TAG, "%s==NULL", #p);\
			return ret; \
		}\
	} while(0)

#define IF_NULL_RETURN_VOID(p) IF_NULL_RETURN(p, )


static JavaVM *sVM;

static JNIEnv *getEnv() {
	//必ずJAVAアタッチ済スレッドから呼ばれること。
	JNIEnv *env;
	if (sVM->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK) {
		::__android_log_write(ANDROID_LOG_FATAL, TAG, "GetEnv()!=JNI_OK");
		return NULL;
	}
	return env;
}

static jmethodID mid_nativecalled_notifyMessage;
static jmethodID mid_nativecalled_notifyChannel;
static jmethodID mid_HashMap_construct;
static jmethodID mid_HashMap_put;


#define NOTIFY_CHANNEL_START  0
#define NOTIFY_CHANNEL_UPDATE 1
#define NOTIFY_CHANNEL_STOP 2



class JHashMapHelper {
	JNIEnv *_env;
	jstring _empty;
	jobject _hashmap;
public:
	JHashMapHelper(JNIEnv *env) : _env(env), _hashmap(0){
		_empty = env->NewStringUTF("");
		jclass clzHashMap = env->FindClass("java/util/HashMap");
		IF_NULL_RETURN_VOID(clzHashMap);
		_hashmap = env->NewObject(clzHashMap, mid_HashMap_construct);
		IF_NULL_RETURN_VOID(_hashmap);
	}
	void putUTF8Value(const char *key, const char *value){
		jstring jsKey = _env->NewStringUTF(key);
		if (value && value[0]){
			jstring jsVal = _env->NewStringUTF(value);
			_env->CallObjectMethod(_hashmap, ::mid_HashMap_put, jsKey, jsVal);
		} else {
			_env->CallObjectMethod(_hashmap, ::mid_HashMap_put, jsKey, _empty);
		}
	}
	void putUTF8ValueF(const char *key, const char *fmt, ...){
		char buf[1024];
		va_list args;
		va_start(args, fmt);
		::vsnprintf(buf, sizeof(buf), fmt, args);
		va_end(args);
		putUTF8Value(key, buf);
	}
	const jobject jobj() const{
		return _hashmap;
	}
};


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

class AndroidPeercastInst: public PeercastInstance {
public:
	virtual Sys* APICALL createSys() {
		/**staticで持っておかないと、quit()のあと生きてるスレッドが
		 * sys->endThread()を呼んでクラッシュする。*/
		static ASys sys;
		return &sys;
	}
};

class AndroidPeercastApp: public PeercastApplication {
	jobject _jthis; //Instance of PeerCastService
	String _iniFilePath;
	String _resourceDir;
public:
	AndroidPeercastApp(jobject jthis, const char* ini, const char* res) :
			_jthis(jthis), _iniFilePath(ini), _resourceDir(res) {

		_resourceDir.append("/");

		JNIEnv *env = ::getEnv();
		env->NewGlobalRef(jthis);

		::__android_log_print(ANDROID_LOG_INFO, TAG, "IniFilePath=%s",
				_iniFilePath.cstr());
		::__android_log_print(ANDROID_LOG_INFO, TAG, "ResourceDir=%s",
				_resourceDir.cstr());
	}
	virtual ~AndroidPeercastApp() {
		JNIEnv *env = ::getEnv();
		env->DeleteGlobalRef(_jthis);
	}

	virtual const char * APICALL getIniFilename() {
		return _iniFilePath;
	}

	virtual const char * APICALL getPath() {
		return _resourceDir;
	}

	virtual const char *APICALL getClientTypeOS() {
		return PCX_OS_LINUX;
	}

	virtual void APICALL printLog(LogBuffer::TYPE t, const char *str) {
		int prio[] = {
			ANDROID_LOG_UNKNOWN, //	T_NONE
			ANDROID_LOG_DEBUG,   //	T_DEBUG
			ANDROID_LOG_ERROR,   //	T_ERROR,
			ANDROID_LOG_INFO,    //	T_NETWORK,
			ANDROID_LOG_INFO,    //	T_CHANNEL,
		};
		char tag[32];
		::snprintf(tag, sizeof(tag), "%s[%s]", TAG, LogBuffer::getTypeStr(t));
		::__android_log_write(prio[t], tag, str);
	}

	void APICALL notifyMessage(ServMgr::NOTIFY_TYPE tNotify,
			const char *message) {
		JNIEnv *env = ::getEnv();
		if (env->PushLocalFrame(8) < 0)
			return;
		jstring jMsg = env->NewStringUTF(message);
		//nativecalled_notifyMessage(int, String)
		env->CallVoidMethod(_jthis, mid_nativecalled_notifyMessage, tNotify,
				jMsg);
		env->PopLocalFrame(NULL);
	}

	void APICALL channelStart(ChanInfo *info) {
		notifyChannel(NOTIFY_CHANNEL_START, info);
	}

	void APICALL channelUpdate(ChanInfo *info) {
		notifyChannel(NOTIFY_CHANNEL_UPDATE, info);
	}

	void APICALL channelStop(ChanInfo *info) {
		notifyChannel(NOTIFY_CHANNEL_STOP, info);
	}

private:

	void notifyChannel(jint notifyType, ChanInfo *info) {
		JNIEnv *env = ::getEnv();
		if (env->PushLocalFrame(32) < 0)
			return;

		JHashMapHelper hmChInfo(env);
		if (!hmChInfo.jobj())
			goto FINISH;

		char strId[64];
		info->id.toStr(strId);
		hmChInfo.putUTF8Value("id", strId);

		hmChInfo.putUTF8Value("track.artist", info->track.artist);
		hmChInfo.putUTF8Value("track.title", info->track.title);
		hmChInfo.putUTF8Value("name", info->name);
		hmChInfo.putUTF8Value("desc", info->desc);
		hmChInfo.putUTF8Value("genre", info->genre);
		hmChInfo.putUTF8Value("comment", info->comment);
		hmChInfo.putUTF8Value("url", info->url);

		hmChInfo.putUTF8ValueF("bitrate", "%d", info->bitrate);
		//hmChInfo.putUTF8ValueF("uptime", "%u", info->getUptime());

		env->CallVoidMethod(_jthis, mid_nativecalled_notifyChannel,
				notifyType, hmChInfo.jobj());

		FINISH: {
			env->PopLocalFrame(NULL);
		}
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
Java_org_peercast_core_PeerCastService_nativeStart(JNIEnv *env, jobject _this,
		jstring oIniFilePath, jstring oResDir) {

	if (peercastApp) {
		jclass ex = env->FindClass("java/lang/IllegalStateException");
		IF_NULL_RETURN(ex, 0);
		env->ThrowNew(ex, "peercast already running!");
		return 0;
	}
	const char *ini = env->GetStringUTFChars(oIniFilePath, NULL);
	const char *res = env->GetStringUTFChars(oResDir, NULL);
	peercastApp = new AndroidPeercastApp(_this, ini, res);
	peercastInst = new AndroidPeercastInst();

	peercastInst->init();

	//peercastApp->getPathを上書きしない。
	servMgr->getModulePath = false;

	//ポートを指定して起動する場合
	//servMgr->serverHost.port = port;
	//servMgr->restartServer=true;

	env->ReleaseStringUTFChars(oIniFilePath, ini);
	env->ReleaseStringUTFChars(oResDir, res);

	return servMgr->serverHost.port;
}

#define DELETE_GLOBAL(sym) do {\
	delete sym;\
	sym = 0;\
	::__android_log_print(ANDROID_LOG_DEBUG, TAG, "delete global '%s' OK.", #sym);\
}while(0)

JNIEXPORT void JNICALL
Java_org_peercast_core_PeerCastService_nativeQuit(JNIEnv *env, jobject _this) {

	if (peercastInst) {
		peercastInst->saveSettings();
		peercastInst->quit();
		::__android_log_write(ANDROID_LOG_DEBUG, TAG, "peercastInst->quit() OK.");
		::sleep(1);
	}

	DELETE_GLOBAL(peercastInst);
	DELETE_GLOBAL(peercastApp);
	DELETE_GLOBAL(servMgr);
	DELETE_GLOBAL(chanMgr);
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *vm, void *reserved) {
//Androidで呼ばれることはないらしい。
}

JNIEXPORT void JNICALL Java_org_peercast_core_PeerCastService_nativeClassInit(
		JNIEnv *env, jclass clz) {
	mid_nativecalled_notifyMessage = env->GetMethodID(clz,
			"nativecalled_notifyMessage", "(ILjava/lang/String;)V");
	IF_NULL_RETURN_VOID(mid_nativecalled_notifyMessage);

	mid_nativecalled_notifyChannel = env->GetMethodID(clz,
			"nativecalled_notifyChannel",
			"(ILjava/util/Map;)V");
	IF_NULL_RETURN_VOID(mid_nativecalled_notifyChannel);

	jclass clzHashMap = env->FindClass("java/util/HashMap");
	mid_HashMap_construct = env->GetMethodID(clzHashMap, "<init>", "()V");
	IF_NULL_RETURN_VOID(mid_HashMap_construct);

	mid_HashMap_put = env->GetMethodID(clzHashMap, "put",
			"(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
	IF_NULL_RETURN_VOID(mid_HashMap_put);

}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
	JNIEnv* env;
	if (vm->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK) {
		return -1;
	}
	sVM = vm;
	::jni_thread_register_shutdown_func(vm);
	return JNI_VERSION_1_6;
}

