package org.peercast.core;


import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

/**
 * PeerCast for Androidをコントロールする。<br>
 * <br>
 * Dual licensed under the MIT or GPL licenses.
 * 
 * @author (c) 2013, T Yoshizawa
 *
 */
public class PeerCastServiceController {

	public static final int MSG_GET_APPLICATION_PROPERTIES = 0x00;
	public static final int MSG_GET_CHANNELS = 0x01;
	public static final int MSG_GET_STATS = 0x02;	
	public static final int MSG_CMD_CHANNEL_BUMP = 0x10;
	public static final int MSG_CMD_CHANNEL_DISCONNECT = 0x11;
	public static final int MSG_CMD_CHANNEL_KEEP_YES = 0x12;
	public static final int MSG_CMD_CHANNEL_KEEP_NO = 0x13;
	
	private final static String TAG = "PeCaCtrl";
	private final static String PKG_PEERCAST = "org.peercast.core";
	private final static String CLASS_NAME_PEERCAST_SERVICE = PKG_PEERCAST + ".PeerCastService";

	private final Context context;

	private Messenger serverMessenger;

	public PeerCastServiceController(Context c) {
		context = c;
	}

	public interface OnServiceResultListener {
		void onServiceResult(Bundle data);
	}

	public interface OnPeerCastEventListener {
		/**
		 * bindService後にコネクションが確立されると呼ばれます。
		 */
		void onConnectPeerCastService();
		/**
		 * unbindServiceを呼んだ後、もしくはOSによってサービスがKillされたときに呼ばれます。
		 * */
		void onDisconnectPeerCastService();
	}
	
	private OnPeerCastEventListener peerCastEventListener;
	
	/**
	 * PeerCastServiceにコマンドを送り、戻り値を得る。
	 * 
	 *<ul>
	 * <li>
	 * 	MSG_GET_APPLICATION_PROPERTIES = 0x00;<br>
	 *   ビアキャスの動作ポートを取得する。<br>
	 *   戻り値:  getInt("port") ピアキャス動作ポート。停止時=0。<br><br>
	 * <li>    
	 *  MSG_GET_CHANNELS = 0x01;<br>
	 *  アクティブなチャンネルの情報を取得する<br>
	 *   戻り値:  nativeGetChannel()参照。<br>
	 *   ラッパー: Channel.java <br><br>
	 * <li>    
	 *  MSG_GET_STATS = 0x02;<br>
	 *  通信量の状況を取得する。<br>
     *   戻り値:   nativeGetStats()参照。<br>
     *   ラッパー: Stats.java <br><br>
	 *
	 * @param what MSG_ で始まる整数。
	 * @param listener サービスからの戻り値をBundleで受け取るリスナー。
	 */
	public void sendCommand(int what, final OnServiceResultListener listener) {
		if (serverMessenger == null)
			new IllegalStateException("service not connected.");
		Message msg = Message.obtain(null, what);
		msg.replyTo = new Messenger(new Handler(new Handler.Callback() {
			//Handlerの直接継承はメモリリーク警告が出る
			@Override
			public boolean handleMessage(Message msg) {
				listener.onServiceResult(msg.getData());
				return true;
			}
		}));
		try {
			serverMessenger.send(msg);
		} catch (RemoteException e) {
			Log.e(TAG, "what=" + what, e);
		}
	}
	
	/** チャンネルに関する操作をします。
	 * 
	 * <ul>
	 *  <li>
	 * MSG_CMD_CHANNEL_BUMP = 0x10;<br>
     * bumpして再接続する。  <br><br>
	 *  <li>
	 *  MSG_CMD_CHANNEL_DISCONNECT = 0x11;<br>
	 * チャンネルを切断する。 <br><br>
	 *  <li>
     * MSG_CMD_CHANNEL_KEEP_YES = 0x12;<br>
     * チャンネルをキープする。<br><br>
     *  <li>
     * MSG_CMD_CHANNEL_KEEP_NO = 0x13;<br>
     * チャンネルのキープを解除する。<br><br>
     * 
	 * @param cmdType MSG_CMD_CHANNEL_ で始まる整数。
	 * @param channel_id 対象のchannel_id
	 */
	public void sendChannelCommand(int cmdType, int channel_id){
		if (serverMessenger == null)
			new IllegalStateException("service not connected.");
		Message msg = Message.obtain(null, cmdType, channel_id, 0);
		try {
			serverMessenger.send(msg);
		} catch (RemoteException e) {
			Log.e(TAG, "what=" + cmdType, e);
		}		
	} 
	
	/**
	 * 
	 * context.bindServiceを呼びます。
	 * @return
	 */
	public boolean bindService() {
		if (!isInstalled()) {
			Log.e(TAG, "PeerCast not installed.");
			return false;
		}
		Intent intent = new Intent(CLASS_NAME_PEERCAST_SERVICE);
		return context.bindService(intent, serviceConn, Context.BIND_AUTO_CREATE);
	}

	public boolean isConnected(){
		return serverMessenger != null;
	}
	
	/**
	 * context.unbindServiceを呼びます。
	 * @return
	 */
	public void unbindService() {
		context.unbindService(serviceConn);
		serverMessenger = null;
		if (peerCastEventListener!=null)
			peerCastEventListener.onDisconnectPeerCastService();		
	}

	public void setOnPeerCastEventListener(OnPeerCastEventListener listener){
		peerCastEventListener = listener;
	}
	
	private ServiceConnection serviceConn = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName arg0, IBinder binder) {
			Log.d(TAG, "onServiceConnected!");
			serverMessenger = new Messenger(binder);
			if (peerCastEventListener!=null)
				peerCastEventListener.onConnectPeerCastService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			// OSにKillされたとき。
			Log.d(TAG, "onServiceDisconnected!");
			serverMessenger = null;
			if (peerCastEventListener!=null)
				peerCastEventListener.onDisconnectPeerCastService();
		}
	};

	/**
	 * 現在、PeerCastのサービスがOS上で起動していればtrue。
	 * 
	 * @return
	 */
	public boolean isServiceRunning() {
		ActivityManager activityManager = (ActivityManager) context
				.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningServiceInfo> services = activityManager
				.getRunningServices(256);
		for (RunningServiceInfo info : services) {
			if (CLASS_NAME_PEERCAST_SERVICE.equals(info.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 「PeerCast for Android」がインストールされているか。
	 * 
	 * @return "org.peercast.core" がインストールされていればtrue。
	 */
	public boolean isInstalled() {
		PackageManager pm = context.getPackageManager();
		try {
			pm.getApplicationInfo(PKG_PEERCAST, 0);
			return true;
		} catch (NameNotFoundException e) {
			return false;
		}
	}
}
