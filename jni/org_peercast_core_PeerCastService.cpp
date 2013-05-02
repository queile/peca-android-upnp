// ------------------------------------------------
// File : org_peercast_core_PeerCastService.cpp
// Date: 25-Apr-2013
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

#include <unistd.h>
#include "unix/usys.h"

#include "peercast.h"
#include "stats.h"

#include "org_peercast_core_PeerCastService.h"
#include <android/log.h>

#include <memory>

static JavaVM *gJVM;

#define TAG "PeCaNt"

#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, TAG,__VA_ARGS__)
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO, TAG,__VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, TAG,__VA_ARGS__)
#define LOGF(...)  __android_log_print(ANDROID_LOG_FATAL, TAG,__VA_ARGS__)

#define IF_NULL_RETURN(p, ret) \
	do { \
		if (!(p)) {\
			LOGF("%s==NULL", #p);\
			return ret; \
		}\
	} while(0)

#define IF_NULL_RETURN_VOID(p) IF_NULL_RETURN(p, )

#define IF_NULL_GOTO(p, label) \
	do { \
		if (!(p)) {\
			LOGF("%s==NULL", #p);\
			goto label; \
		}\
	} while(0)

static JNIEnv *getJNIEnv() {
	//必ずJAVAアタッチ済スレッドから呼ばれること。
	JNIEnv *env;
	if (gJVM->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK) {
		::__android_log_write(ANDROID_LOG_FATAL, TAG, "GetEnv()!=JNI_OK");
		return 0;
	}
	return env;
}

/**
 * private継承すれば、コピーコンストラクタがコンパイルエラーになる。
 * */
class NoCopyConstructor {
	NoCopyConstructor(const NoCopyConstructor&);
	void operator=(const NoCopyConstructor&);
public:
	NoCopyConstructor() {
	}
};

/**
 * クラス、メソッドIDをキャッシュするための基底クラス。
 * */
class JClassCache: private NoCopyConstructor {
protected:
	JClassCache() : clazz(0) {
	}

public:
	jclass clazz;

	virtual ~JClassCache() {
		//Androidでは.soがアンロードされないので呼ばない。
		//env->DeleteGlobalRef(clazz)
	}

	//JNI_OnLoad時に一度だけfindClassすればよい。
	bool initClass(JNIEnv *env, const char *className) {
		jclass cls = env->FindClass(className);
		if (cls)
			initClass(env, cls);
		return !!cls;
	}
	void initClass(JNIEnv *env, jclass clz) {
		if (clazz)
			return; //クラスが再ロードされたときなら何もしない。
		clazz = (jclass) env->NewGlobalRef(clz);
	}

	/*
	 * methodIDをキャッシュする。
	 * Androidではクラスが再ロードされ、MethodIDが変わることがある。
	 */
	virtual void initIDs(JNIEnv *env) = 0;

};

/**
 * android.os.Bundleのクラス、メソッドIDをキャッシュする。
 * */
static struct BundleClassCache: public JClassCache {
	//clazz = android.os.Bundle
	jmethodID init; //<init>()
	jmethodID putString; // (String key, String value)
	jmethodID putInt; // (String key, int value)
	jmethodID putDouble; // (String key, double value)
	jmethodID putLong; //  (String key, long value)
	jmethodID putBoolean; // (String key, boolean value)
	jmethodID putBundle; // (String key, Bundle value)
	//jmethodID putParcelableArray; // (String key, Parcelable[] value)

	void initIDs(JNIEnv *env) {
		init = env->GetMethodID(clazz, "<init>", "()V");
		IF_NULL_RETURN_VOID(init);

		putString = env->GetMethodID(clazz, "putString",
				"(Ljava/lang/String;Ljava/lang/String;)V");
		IF_NULL_RETURN_VOID(putString);

		putInt = env->GetMethodID(clazz, "putInt", "(Ljava/lang/String;I)V");
		IF_NULL_RETURN_VOID(putInt);

		putDouble = env->GetMethodID(clazz, "putDouble",
				"(Ljava/lang/String;D)V");
		IF_NULL_RETURN_VOID(putDouble);

		putLong = env->GetMethodID(clazz, "putLong", "(Ljava/lang/String;J)V");
		IF_NULL_RETURN_VOID(putLong);

		putBoolean = env->GetMethodID(clazz, "putBoolean",
				"(Ljava/lang/String;Z)V");
		IF_NULL_RETURN_VOID(putBoolean);

		putBundle = env->GetMethodID(clazz, "putBundle",
				"(Ljava/lang/String;Landroid/os/Bundle;)V");
		IF_NULL_RETURN_VOID(putBundle);

		//putParcelableArray = env->GetMethodID(clazz, "putParcelableArray",
		//		"(Ljava/lang/String;[Landroid/os/Parcelable;)V");
		//IF_NULL_RETURN_VOID(putParcelableArray);
	}
} gBundleCache;

/**
 * android.os.Bundleのインスタンス作成とc++ラッパー
 *
 * ローカル関数内のみで有効
 **/
class JBundle: private NoCopyConstructor {
	jobject jbundle;
protected:
	JNIEnv *env;


public:
	JBundle(JNIEnv *e) : env(e)  {
		jbundle = env->NewObject(gBundleCache.clazz, gBundleCache.init);
	}
	virtual ~JBundle(){
		env->DeleteLocalRef(jbundle);
	}

	void putString(const char *key, const char *value) {
		jstring jsKey = env->NewStringUTF(key);
		jstring jsVal = env->NewStringUTF(value);

		env->CallVoidMethod(jbundle, gBundleCache.putString, jsKey, jsVal);

		env->DeleteLocalRef(jsKey);
		env->DeleteLocalRef(jsVal);
	}
	void putStringF(const char *key, const char *fmt, ...) {
		char buf[1024];
		va_list list;
		va_start(list, fmt);
		::vsnprintf(buf, sizeof(buf), fmt, list);
		va_end(list);
		putString(key, buf);
	}

#define DEFINE_PUT_METHOD(Method, TVal)		\
	void Method(const char *key, TVal value){	\
		jstring jsKey = env->NewStringUTF(key);	\
		env->CallVoidMethod(jbundle, gBundleCache.Method, jsKey, value);	\
		env->DeleteLocalRef(jsKey);	\
	}

	DEFINE_PUT_METHOD(putInt, jint)
	DEFINE_PUT_METHOD(putDouble, jdouble)
	DEFINE_PUT_METHOD(putLong, jlong)
	DEFINE_PUT_METHOD(putBoolean, jboolean)
	DEFINE_PUT_METHOD(putBundle, jobject)
	//DEFINE_PUT_METHOD(putParcelableArray, jobjectArray)

#undef DEFINE_PUT_METHOD

	/**
	 * Bundleオブジェクトを返す。
	 * NULLの場合は、JAVA例外(OutOfMemoryError)の可能性。
	 * */
	jobject jobj() const {
		return jbundle;
	}

	/**
	 * 新しいLocal参照のBundleオブジェクトを返す。
	 * ネイティブ関数の戻り値用。
	 */
	jobject newRef(){
		return env->NewLocalRef(jbundle);
	}
};

/**
 * org.peercast.core.PeerCastServiceのクラス、メソッドIDをキャッシュする。
 * */
static struct PeerCastServiceClassCache: public JClassCache {
	// clazz = org.peercast.core.PeerCastService
	jmethodID notifyMessage; //void notifyMessage(int, String)
	jmethodID notifyChannel; //void notifyChannel(int, Bundle)

	void initIDs(JNIEnv *env) {
		notifyMessage = env->GetMethodID(clazz, "notifyMessage",
				"(ILjava/lang/String;)V");
		IF_NULL_RETURN_VOID(notifyMessage);

		notifyChannel = env->GetMethodID(clazz, "notifyChannel",
				"(ILandroid/os/Bundle;)V");
		IF_NULL_RETURN_VOID(notifyChannel);
	}
} gPeerCastServiceCache;


class ASys: public USys {
public:
	void exit() {
		LOGF("%s is Not Implemented", __FUNCTION__);
	}
	void executeFile(const char *f) {
		LOGF("%s is Not Implemented", __FUNCTION__);
	}
};

class AndroidPeercastInst: public PeercastInstance {
public:
	virtual Sys* APICALL createSys() {
		/**
		 * staticで持っておかないと、quit()のあと生きてるスレッドが
		 * sys->endThread()を呼んでクラッシュする。
		 **/
		static std::auto_ptr<Sys> apSys(new ASys());
		return apSys.get();
	}
};

/**
 * ChanInfoの情報をBundleにput。
 *
 * ラッパー: ChannelInfo.java
 * */
class JBundleChannelInfoData: public JBundle {
public:
	JBundleChannelInfoData(JNIEnv *env) : JBundle(env) {
	}

	void setData(ChanInfo* info) {
		char strId[64];
		info->id.toStr(strId);
		putString("id", strId);

		putString("track.artist", info->track.artist);
		putString("track.title", info->track.title);
		putString("name", info->name);
		putString("desc", info->desc);
		putString("genre", info->genre);
		putString("comment", info->comment);
		putString("url", info->url);
		putInt("bitrate", info->bitrate);
	}
};

class AndroidPeercastApp: public PeercastApplication {
	jobject _jthis; //Instance of PeerCastService
	String _iniFilePath;
	String _resourceDir;
public:
	AndroidPeercastApp(jobject jthis, const char* ini, const char* res) :
			_jthis(0), _iniFilePath(ini), _resourceDir(res) {

		_resourceDir.append("/");

		JNIEnv *env = ::getJNIEnv();
		_jthis = env->NewGlobalRef(jthis);

		LOGI("IniFilePath=%s", _iniFilePath.cstr());
		LOGI("ResourceDir=%s", _resourceDir.cstr());
	}
	virtual ~AndroidPeercastApp() {
		JNIEnv *env = ::getJNIEnv();
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
				ANDROID_LOG_DEBUG, //	T_DEBUG
				ANDROID_LOG_ERROR, //	T_ERROR,
				ANDROID_LOG_INFO, //	T_NETWORK,
				ANDROID_LOG_INFO, //	T_CHANNEL,
				};
		char tag[32];
		::snprintf(tag, sizeof(tag), "%s[%s]", TAG, LogBuffer::getTypeStr(t));
		::__android_log_write(prio[t], tag, str);
	}

	/**
	 * notifyMessage(int, String)
	 *
	 * Nativeからjavaメソッドを呼ぶ。
	 *
	 * ただ、finding channel.. と、PeCaソフト更新情報のみなら用途なし？
	 * */
	void APICALL notifyMessage(ServMgr::NOTIFY_TYPE tNotify,
			const char *message) {
		JNIEnv *env = ::getJNIEnv();

		jstring jMsg = env->NewStringUTF(message);
		IF_NULL_RETURN_VOID(jMsg);

		env->CallVoidMethod(_jthis, gPeerCastServiceCache.notifyMessage,
				tNotify, jMsg);
		env->DeleteLocalRef(jMsg);
	}
    /*
    *  channelStart(ChanInfo *)
    *  channelUpdate(ChanInfo *)
    *  channelStop(ChanInfo *)
    *
    *    -> (Java) notifyChannel(int, Bundle)
    */
	void APICALL channelStart(ChanInfo *info) {
		notifyChannel(org_peercast_core_PeerCastService_NOTIFY_CHANNEL_START, info);
	}

	void APICALL channelUpdate(ChanInfo *info) {
		notifyChannel(org_peercast_core_PeerCastService_NOTIFY_CHANNEL_UPDATE, info);
	}

	void APICALL channelStop(ChanInfo *info) {
		notifyChannel(org_peercast_core_PeerCastService_NOTIFY_CHANNEL_STOP, info);
	}

private:

	void notifyChannel(jint notifyType, ChanInfo *info) {
		JNIEnv *env = ::getJNIEnv();

		JBundleChannelInfoData bChInfo(env);
		IF_NULL_RETURN_VOID(bChInfo.jobj());

		bChInfo.setData(info);
		env->CallVoidMethod(_jthis, gPeerCastServiceCache.notifyChannel,
					notifyType, bChInfo.jobj());
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
Java_org_peercast_core_PeerCastService_nativeStart(JNIEnv *env, jobject jthis,
		jstring jsIniFilePath, jstring jsResDir) {

	if (peercastApp) {
		jclass ex = env->FindClass("java/lang/IllegalStateException");
		IF_NULL_RETURN(ex, 0);
		env->ThrowNew(ex, "PeerCast already running!");
		return 0;
	}
	const char *ini = env->GetStringUTFChars(jsIniFilePath, NULL);
	const char *res = env->GetStringUTFChars(jsResDir, NULL);
	peercastApp = new AndroidPeercastApp(jthis, ini, res);
	peercastInst = new AndroidPeercastInst();

	peercastInst->init();

	//peercastApp->getPathを上書きしない。
	servMgr->getModulePath = false;

	//ポートを指定して起動する場合
	//servMgr->serverHost.port = port;
	//servMgr->restartServer=true;

	env->ReleaseStringUTFChars(jsIniFilePath, ini);
	env->ReleaseStringUTFChars(jsResDir, res);

	return servMgr->serverHost.port;
}

#define DELETE_GLOBAL(sym) do {\
	delete sym;\
	sym = 0;\
	LOGD("delete global '%s' OK.", #sym);\
}while(0)

JNIEXPORT void JNICALL
Java_org_peercast_core_PeerCastService_nativeQuit(JNIEnv *env, jobject _this) {

	if (peercastInst) {
		peercastInst->saveSettings();
		peercastInst->quit();
		LOGD("peercastInst->quit() OK.");
		::sleep(1);
	}

	DELETE_GLOBAL(peercastInst);
	DELETE_GLOBAL(peercastApp);
	DELETE_GLOBAL(servMgr);
	DELETE_GLOBAL(chanMgr);
}

class JBundleServentData: public JBundle {
	void putVersionData(ChanHit *chHit) {
		if (chHit->version_ex_number) {
			// 拡張バージョン
			putStringF("version", "%c%c%04d", chHit->version_ex_prefix[0],
					chHit->version_ex_prefix[1], chHit->version_ex_number);
		} else if (chHit->version_vp) {
			putStringF("version", "VP%04d", chHit->version_vp);
		} else {
			putStringF("version", "%04d", chHit->version);
		}
	}

public:
	JBundleServentData(JNIEnv *env) :
			JBundle(env) {
	}

	void setChanHitData(ChanHit *chHit){
		putBoolean("relay", chHit->relay);
		putBoolean("firewalled", chHit->firewalled);
		putInt("numRelays", chHit->numRelays);
		putVersionData(chHit);
		char ip[32];
		chHit->host.IPtoStr(ip);
		putString("host", ip);
		putInt("port", chHit->host.port);
		putBoolean("infoFlg", 1);
	}

	void setData(Servent *servent) {
		jint totalRelays = 0;
		jint totalListeners = 0;
		jboolean infoFlg = 0;

		chanMgr->hitlistlock.on();
		ChanHitList *chHitList = chanMgr->findHitListByID(servent->chanID);
		// チャンネルのホスト情報があるか
		if (chHitList) {
			// チャンネルのホスト情報がある場合
			ChanHit *chHit = chHitList->hit;
			//　チャンネルのホスト情報を全走査して
			while (chHit) {
				// IDが同じものであれば
				if (servent->servent_id == chHit->servent_id) {
					// トータルリレーとトータルリスナーを加算
					totalRelays += chHit->numRelays;
					totalListeners += chHit->numListeners;
					// 直下であれば
					if (chHit->numHops == 1) {
						setChanHitData(chHit);
					}
				}
				chHit = chHit->next;
			}
		}
		chanMgr->hitlistlock.off();

		putInt("totalListeners", totalListeners);
		putInt("totalRelays", totalRelays);
	}
};



class JBundleChannelData: public JBundle {
	void putServentDatas(Channel *ch) {

		std::auto_ptr<JBundleServentData> apServentData(0);
		putBundle("servent", 0); // null

		servMgr->lock.on();

		int i = 0;
		for (Servent *svt = servMgr->servents; svt && i < 16; svt = svt->next) {
			if (svt->isConnected() && ch->channel_id == svt->channel_id
						&& svt->type == Servent::T_RELAY) {

				JBundleServentData *bServent = new JBundleServentData(env);
				if (!bServent->jobj()){
					delete bServent; //OutOfMemoryError
					break;
				}
				bServent->setData(svt);

				if (i == 0){
					//リニアリストの先頭
					putBundle("servent", bServent->jobj());
				} else {
					//前の要素のnextにプット。
					apServentData->putBundle("next", bServent->jobj());
				}
				//auto_ptrに入れる。前の要素があればdeleteされる。
				apServentData.reset(bServent);
				i++;
			}
		}
		servMgr->lock.off();
	}

public:
	JBundleChannelData(JNIEnv *env) :
			JBundle(env) {
	}
	void setData(Channel *ch) {
		char id[64];
		ch->getID().toStr(id);
		putString("id", id);

		putInt("channel_id", ch->channel_id);
		putInt("totalListeners", ch->totalListeners());
		putInt("totalRelays", ch->totalRelays());
		putInt("status", ch->status);
		putInt("localListeners", ch->localListeners());
		putInt("localRelays", ch->localRelays());
		putBoolean("stayConnected", ch->stayConnected);
		putBoolean("tracker", ch->sourceHost.tracker);
		putInt("lastSkipTime", ch->lastSkipTime);
		putInt("skipCount", ch->skipCount);

		JBundleChannelInfoData bChInfo(env);
		if (bChInfo.jobj())
			bChInfo.setData(&ch->info);
		putBundle("info", bChInfo.jobj());

		putServentDatas(ch);

		//JBundleServentData bChDisp(env);
		//bChDisp.setChanHitData(&ch->chDisp);
		//putBundle("chDisp", bChDisp.jobj());
	}

};




/**
 *  nativeGetChannels()
 *
 * 現在アクティブなチャンネルの情報をBundleで返す。
 *   ラッパー: Channel.java ::fromNativeResult()
 *
 * [JType, Key, NativeCode]
 *
 * {
 *   String, "id", getID().toStr()
 *   int, "channel_id", ch->channel_id
 * 	 int, "totalListeners", ch->totalListeners()
 * 	 int, "totalRelays", ch->totalRelays()
 * 	 int, "status", ch->status
 * 	 int, "localListeners", ch->localListeners()
 * 	 int, "localRelays", ch->localRelays()
 * 	 boolean, "stayConnected", ch->stayConnected
 * 	 boolean, "tracker", ch->sourceHost.tracker
 * 	 int, "lastSkipTime", ch->lastSkipTime
 * 	 int, "skipCount", ch->skipCount
 * 	 Bundle, "info", {
 * 	    //チャンネル情報
 *	    String, "id", info->id.toStr()
 *      String, "track.artist", info->track.artist
 *      String, "track.title", info->track.title
 *      String, "name", info->name
 *      String, "desc", info->desc
 *      String, "genre", info->genre
 *      String, "comment", info->comment
 *      String, "url", info->url
 *      int, "bitrate", info->bitrate
 * 	 };
 *   Bundle, "servent", {
 *      // 直下のServent情報のリスト
 *      boolean, "relay", chHit->relay
 *      boolean, "firewalled", chHit->firewalled
 *      int, "numRelays", chHit->numRelays
 *      String, "version",
 *      String, "host", chHit->host.IPtoStr(ip);
 *      int, "port", chHit->host.port
 *      int, "totalListeners", このServent以下の合計リスナ/リレー数
 *		int, "totalRelays",
 *
 *      Bundle, "next", 次のServentへ。なければnull
 *   };
 *   Bundle, "next", 次のチャンネルへ。なければnull
 * };
 *
 */
JNIEXPORT jobject JNICALL Java_org_peercast_core_PeerCastService_nativeGetChannels(
		JNIEnv *env, jclass jclz) {
	if (!peercastApp) {
		jclass ex = env->FindClass("java/lang/IllegalStateException");
		IF_NULL_RETURN(ex, 0);
		env->ThrowNew(ex, "PeerCast app not running!");
		return 0;
	}

	std::auto_ptr<JBundleChannelData> apChData(0);
	jobject firstChData = 0;//戻り値 Bundle

	chanMgr->lock.on();

	Channel *ch = chanMgr->channel;
	int i = 0;
	for (Channel *ch = chanMgr->channel; ch && i < 16; ch = ch->next) {
		if (!ch->isActive())
			continue;

		JBundleChannelData *chData = new JBundleChannelData(env);
		if (!chData->jobj()) {
			delete chData; //OutOfMemoryError
			break;
		}
		chData->setData(ch);

		if (i == 0) {
			//戻り値
			firstChData = chData->newRef();
		} else {
			//前の要素のnextにプット。
			apChData->putBundle("next", chData->jobj());
		}
		//auto_ptrに入れる。前の要素はdeleteされる。
		apChData.reset(chData);
		i++;
	}
	chanMgr->lock.off();

	return firstChData; //Bundle
}

/**
 * nativeGetStats()
 *
 * stats <stats.h>の情報をBundleで返す。
 *
 *  int, "in_bytes", ダウンロード bytes / sec
 *  int, "out_bytes", アップロード bytes / sec
 *  long, "in_total_bytes", 起動時からの合計ダウンロード
 *  long, "out_total_bytes", 起動時からの合計アップロード
 *
 */
JNIEXPORT jobject JNICALL Java_org_peercast_core_PeerCastService_nativeGetStats(
		JNIEnv *env, jclass) {

	JBundle bStats(env);

	jint down_per_sec = stats.getPerSecond(Stats::BYTESIN)
			- stats.getPerSecond(Stats::LOCALBYTESIN);
	jint up_per_sec = stats.getPerSecond(Stats::BYTESOUT)
			- stats.getPerSecond(Stats::LOCALBYTESOUT);
	jlong totalDown = stats.getCurrent(Stats::BYTESIN)
			- stats.getCurrent(Stats::LOCALBYTESIN);
	jlong totalUp = stats.getCurrent(Stats::BYTESOUT)
			- stats.getCurrent(Stats::LOCALBYTESOUT);

	bStats.putInt("in_bytes", down_per_sec);
	bStats.putInt("out_bytes", up_per_sec);
	bStats.putLong("in_total_bytes", totalDown);
	bStats.putLong("out_total_bytes", totalUp);

	return bStats.newRef();
}


/**
 * nativeGetApplicationProperties()
 *
 * PeerCastの動作プロパティーをBundleで返す。
 *
 *  [JType, Key, NativeCode]
 *    int, "port", servMgr->serverHost.port
 *
 * */
JNIEXPORT jobject JNICALL Java_org_peercast_core_PeerCastService_nativeGetApplicationProperties
  (JNIEnv *env, jclass jclz){

	JBundle bProp(env);

	if (peercastApp) {
		bProp.putInt("port", servMgr->serverHost.port);
	} else {
		bProp.putInt("port", 0);
	}
	return bProp.newRef();
}

/**
 * nativeChannelCommand()
 *  チャンネルに関する操作を行う。
 *
 *    (BUMP|DISCONNECT|KEEP_YES|KEEP_NO)
 */
JNIEXPORT void JNICALL Java_org_peercast_core_PeerCastService_nativeChannelCommand
  (JNIEnv *env, jclass, jint cmdType, jint channel_id){

	Channel *ch = chanMgr->findChannelByChannelID(channel_id);
	if (!ch){
		LOGE("nativeChannelCommand: channel not found. (channel_id=%d)", channel_id);
		return;
	}
	switch (cmdType){
		case org_peercast_core_PeerCastService_MSG_CMD_CHANNEL_BUMP:
			// 再接続
			LOGI("Bump: channel_id=%d", channel_id);
			ch->bump = true;
			break;
		case org_peercast_core_PeerCastService_MSG_CMD_CHANNEL_DISCONNECT:
			// 切断
			// bump中は切断しない
			if (!ch->bumped) {
				LOGI("Disconnect: channel_id=%d", channel_id);
				ch->thread.active = false;
				ch->thread.finish = true;
			}
			break;
		case org_peercast_core_PeerCastService_MSG_CMD_CHANNEL_KEEP_YES:
			// キープ True
			LOGI("Keep yes: channel_id=%d", channel_id);
			ch->stayConnected  = true;
			break;
		case org_peercast_core_PeerCastService_MSG_CMD_CHANNEL_KEEP_NO:
			// キープ False
			LOGI("Keep no: channel_id=%d", channel_id);
			ch->stayConnected = false;
			break;
		default:
			LOGE("nativeChannelCommand: Invalid cmdType=0x%x", cmdType);
			break;
	}
}



JNIEXPORT void JNICALL Java_org_peercast_core_PeerCastService_nativeClassInit(
		JNIEnv *env, jclass jclz) {
	gPeerCastServiceCache.initClass(env, jclz);
	gPeerCastServiceCache.initIDs(env);
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
	JNIEnv* env;
	if (vm->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK) {
		return -1;
	}
	gJVM = vm;
	::jni_thread_register_shutdown_func(vm);

	gBundleCache.initClass(env, "android/os/Bundle");
	gBundleCache.initIDs(env);

	return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *vm, void *reserved) {
	// Androidで呼ばれないらしい。
}

