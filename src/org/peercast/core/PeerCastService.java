package org.peercast.core;
/**
* (c) 2013, T Yoshizawa
*
* Dual licensed under the MIT or GPL licenses.
*/
import java.io.File;
import java.io.FileDescriptor;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

public class PeerCastService extends Service {

	private ServiceThread peercast;
	static final String TAG = "PeCaSrv";

	public class ServiceBinder extends Binder {
		public PeerCastService getService() {
			return PeerCastService.this;
		}
	}
/*
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Context ctxt = getApplicationContext();
		File iniFile = ctxt.getFileStreamPath("peercast.ini");
		File resouceDir = ctxt.getFilesDir();//ctxt.getDir("res", ctxt.MODE_PRIVATE);
		if (peercast == null) {
			peercast = new ServiceThread(iniFile, resouceDir);
			peercast.start();
			try {
				// �J�n���A����|�[�g���擾���邽�ߑ҂��܂�.
				synchronized (peercast.lockStartWait) {
					peercast.lockStartWait.wait();
				}
			} catch (InterruptedException e) {
			}
		}
		Notification notifi = new Notification();
		//notifi.icon = R.drawable.ic_launcher;
		startForeground(1, notifi);
		return START_REDELIVER_INTENT;
	}
*/
	@Override
	public IBinder onBind(Intent arg0) {
		Context ctxt = getApplicationContext();
		File iniFile = ctxt.getFileStreamPath("peercast.ini");
		File resouceDir = ctxt.getFilesDir();//ctxt.getDir("res", ctxt.MODE_PRIVATE);
		if (peercast == null) {
			peercast = new ServiceThread(iniFile, resouceDir);
			peercast.start();
			try {
				// �J�n���A����|�[�g���擾���邽�ߑ҂��܂�.
				synchronized (peercast.lockStartWait) {
					peercast.lockStartWait.wait();
				}
			} catch (InterruptedException e) {
			}
		}
		Notification notifi = new Notification();
		//notifi.icon = R.drawable.ic_launcher;
		startForeground(1, notifi);
		
		return new ServiceBinder();
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		return false;
	}

	@Override
	public void onDestroy() {
		stopForeground(true);
		peercast.interrupt();
		peercast = null;
	}

	public int getServerPort() {
		if (peercast == null)
			return -1;
		return peercast.port;
	}

	private class ServiceThread extends Thread {
		int port;
		private String iniPath;
		private String resDirPath;
		final Object lockStartWait = new Object();

		public ServiceThread(File ini, File res) {
			iniPath = ini.getAbsolutePath();
			resDirPath = res.getAbsolutePath();
		}

		@Override
		public void run() {
			try {
				port = nativeStart(iniPath, resDirPath);
				synchronized (lockStartWait) {
					lockStartWait.notify();
				}

				Log.i(TAG, "start server.. " + Thread.currentThread());
				try {
					synchronized (this) {
						wait();
					}
				} catch (InterruptedException e) {
				}
			} finally {
				nativeQuit();
			}
			Log.i(TAG, "finish server.. " + Thread.currentThread());
		}
	}

	/**PeerCast���J�n���܂��B
	 * @param iniPath peercast.ini�̃p�X�B(�������݉\�ł��邱��)
	 * @param resourceDir 
	 * @return ����|�[�g
	 */
	private native static int nativeStart(String iniPath, String resourceDir);

	private native static void nativeQuit();

	private static native void nativeClassInit();

	static {
		System.loadLibrary("peercast");
		nativeClassInit();
	}

}
