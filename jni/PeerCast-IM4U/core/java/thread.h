#ifndef _JAVA_THREAD_H_
#define _JAVA_THREAD_H_
// ------------------------------------------------
// File : thread.h
// Date: 14-Mar-2013
// Author: T. Yoshizawa
// Desc:
//�@�쐬���ꂽ�l�C�e�B�u�X���b�h��JVM�ɕR�Â����܂��B
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
 * �f�o�b�O�p�Ɋ֐����ƃX���b�hID�����O�Ɏc���܂��B
 * */
void jni_logging_thread_func(const char* funcName);

/**
 * �l�C�e�B�u�̃X���b�h��JVM�ɃA�^�b�`���܂��B
 * �X���b�h�֐��̐擪��BEGIN_THREAD_PROC�}�N�����g���܂��B
 * */
jboolean jni_thread_setup();

/**
 * �X���b�h�I����Ƀf�^�b�`�����s����f�X�g���N�^��o�^���܂��B
 * JVM_OnLoad�֐�����Ă�ł��������B
 * */
jboolean jni_thread_register_shutdown_func(JavaVM *vm);


#endif
