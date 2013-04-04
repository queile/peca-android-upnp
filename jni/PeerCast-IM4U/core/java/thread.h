#ifndef _JAVA_THREAD_H_
#define _JAVA_THREAD_H_
// ------------------------------------------------
// File : thread.h
// Date: 14-Mar-2013
// Author: T. Yoshizawa
// Desc:
//　作成されたネイティブスレッドをJVMに紐づけします。
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

#include <jni.h>
#include "sys.h"




#define BEGIN_THREAD_PROC do { \
	jni_logging_thread_func(__FUNCTION__); \
	jni_thread_setup(); \
} while (0)

/**
 * デバッグ用に関数名とスレッドIDをログに残します。
 * */
void jni_logging_thread_func(const char* funcName);

/**
 * ネイティブのスレッドをJVMにアタッチします。
 * スレッド関数の先頭でBEGIN_THREAD_PROCマクロを使います。
 * */
jboolean jni_thread_setup();

/**
 * スレッド終了後にデタッチを実行するデストラクタを登録します。
 * JVM_OnLoad関数から呼んでください。
 * */
jboolean jni_thread_register_shutdown_func(JavaVM *vm);


#endif
