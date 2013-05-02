package org.peercast.core;

/**
 * (c) 2013, T Yoshizawa
 *
 * Dual licensed under the MIT or GPL licenses.
 */
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class PeerCastService extends Service {
	static final String TAG = "PeCaSrv";

	public static final int MSG_GET_APPLICATION_PROPERTIES = 0x00;
	public static final int MSG_GET_CHANNELS = 0x01;
	public static final int MSG_GET_STATS = 0x02;
	
	public static final int MSG_CMD_CHANNEL_BUMP = 0x10;
	public static final int MSG_CMD_CHANNEL_DISCONNECT = 0x11;
	public static final int MSG_CMD_CHANNEL_KEEP_YES = 0x12;
	public static final int MSG_CMD_CHANNEL_KEEP_NO = 0x13;

	
	private static final int NOTIFY_ID_FOREGROUND = Integer.MAX_VALUE; //0以外



	private Messenger serviceMessenger;

	private ServiceHandler serviceHandler;
	private Looper serviceLooper;

	private Timer tRefreshNotify;
	private static final int intervaltRefreshNotify = 3000;
	private boolean isShowNotification;
	
	private Map<String, ChannelInfo> activeChInfos = new LinkedHashMap<String, ChannelInfo>();
	private boolean isRunning;
	
	private static class ServiceHandler extends Handler {
		public ServiceHandler(Looper serviceLooper) {
			super(serviceLooper);
		}

		@Override
		public void handleMessage(Message msg) {
			Message reply = obtainMessage(msg.what);
			Bundle data;
			switch (msg.what) {
			case MSG_GET_APPLICATION_PROPERTIES:
				data = nativeGetApplicationProperties();
				break;
				
			case MSG_GET_CHANNELS:
				data = nativeGetChannels();				
				break;
				
			case MSG_GET_STATS:
				data = nativeGetStats();
				break;
				
			case MSG_CMD_CHANNEL_BUMP:
			case MSG_CMD_CHANNEL_DISCONNECT:
			case MSG_CMD_CHANNEL_KEEP_YES:
			case MSG_CMD_CHANNEL_KEEP_NO:
				nativeChannelCommand(msg.what, msg.arg1);
				return;
				
			default:
				Log.e(TAG, "Invalid msg.what=" + msg.what);
				return;
			}
			reply.setData(data);
			try {
				if (msg.replyTo == null){
					Log.e(TAG, "msg.replyTo==null");
					return;
				}
				msg.replyTo.send(reply);
			} catch (RemoteException e) {
				Log.e(TAG, "msg.replyTo.send(reply)", e);
			}
		}
	}

	private Notification createNotification() {
		if (activeChInfos.isEmpty()){
			//視聴をやめたあとは表示しない。
			isShowNotification = false; 
			return new Notification();
		}
		
		Stats stats = Stats.fromNativeResult(nativeGetStats());
		
		String title = "PeerCast";
		String text = "..";
		Intent intent = new Intent();
		//せまいので最初のチャネルのみ表示。
		for (ChannelInfo info : activeChInfos.values()){
			title = "Playing: " + info.getName();
			text = info.getDesc();
			text += "  ";
			text += info.getComment();
			if (info.getUrl() != null){
				intent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.getUrl()));
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			}
			break;
		}		
		
		String info = String.format(
				"D %.1f / U %.1f kbs ",
				stats.getInBytes() / 1024f * 8,
				stats.getOutBytes() / 1024f * 8);
		info += String.format("[%d / %d MB]",
				stats.getInTotalBytes() / 1024 / 1024,
				stats.getOutTotalBytes() / 1024 / 1024);	
		
		Bitmap icon = BitmapFactory.decodeResource(getResources(),
				R.drawable.ic_notify_icon);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				intent, 0);				
		
		return new NotificationCompat.Builder(this)
	     .setContentTitle(title)
	     .setContentText(text)
	     .setContentInfo(info)
	     .setContentIntent(contentIntent)
	     .setSmallIcon(R.drawable.ic_notify_icon)
	     .setLargeIcon(icon)
	     .build();	     
	}
	

	
	@Override
	public void onCreate() {
		super.onCreate();
		
		HandlerThread thread = new HandlerThread("PeerCastService",
				Thread.MIN_PRIORITY);
		thread.start();

		serviceLooper = thread.getLooper();
		serviceHandler = new ServiceHandler(serviceLooper);
		serviceMessenger = new Messenger(serviceHandler);
	}
	
	//htmlフォルダ以下を解凍して /data/data/org.peercast.core/ にインストール。
	private void assetInstall() throws IOException {
		File dataDir = getFilesDir();

		ZipInputStream zipIs = new ZipInputStream(getAssets().open("peca.zip"));
		try {
			ZipEntry ze;
			while ((ze = zipIs.getNextEntry()) != null) {
				File file = new File(dataDir, ze.getName());
				if (file.exists())
					continue;

				if (ze.isDirectory()) {
					file.mkdirs();
					continue;
				}
				file.getParentFile().mkdirs();
				Log.d(TAG, "Install resource -> " + file.getAbsolutePath());
				FileOutputStream fout = new FileOutputStream(file);
				byte[] buffer = new byte[1024];
				int length = 0;
				while ((length = zipIs.read(buffer)) > 0) {
					fout.write(buffer, 0, length);
				}
				zipIs.closeEntry();
				fout.close();
			}
		} finally {
			zipIs.close();
		}
	}
	
	
	@Override
	public IBinder onBind(Intent i) {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(this);

		String iniPath = getFileStreamPath("peercast.ini").getAbsolutePath();
		String resDirPath = getFilesDir().getAbsolutePath();

		boolean showNotify = pref.getBoolean(
				PeerCastMainActivity.pref_notification, false);

		synchronized (this) {
			if (!isRunning) {
				try {
					assetInstall();
				} catch (IOException e) {
					Log.e(TAG, "html-dir install failed.");
					return null;
				}
				
				nativeStart(iniPath, resDirPath);
				startForeground(NOTIFY_ID_FOREGROUND, new Notification());
				if (showNotify) {
					tRefreshNotify = new Timer(false);
					tRefreshNotify.schedule(new TimerTask() {
						@Override
						public void run() {
							if (!isShowNotification){
								return;
							}
							NotificationManager nm = (NotificationManager) 
									getSystemService(Context.NOTIFICATION_SERVICE);
							Notification n = createNotification();
							nm.notify(NOTIFY_ID_FOREGROUND, n);			
						}
					}, intervaltRefreshNotify, intervaltRefreshNotify);
				}
				isRunning = true;
			}
		}
		return serviceMessenger.getBinder();
	}

	@Override
	public boolean onUnbind(Intent intent) {
		return false;
	}

	@Override
	public void onDestroy() {
		stopForeground(true);
		serviceLooper.quit();
		if (tRefreshNotify != null) {
			tRefreshNotify.cancel();
		}
		nativeQuit();
	}

	/**<pre>
	 * native called:
	 * 
	 * AndroidPeercastApp::notifyMessage(
	 *       ServMgr::NOTIFY_TYPE tNotify, 
	 *       const char *message)
	 * 
	 * */
	private void notifyMessage(int notifyType, String message) {
		if (BuildConfig.DEBUG) {
			Log.i(TAG, "notifyMessage: " + notifyType + ", " + message);
		}
	}

	
	private static final int NOTIFY_CHANNEL_START = 0;
	private static final int NOTIFY_CHANNEL_UPDATE = 1;
	private static final int NOTIFY_CHANNEL_STOP = 2;
	
	/**
	 * <pre>
	 * native called:
	 * 
	 * AndroidPeercastApp:: 
	 *   channelStart(ChanInfo *info) 
	 *   channelUpdate(ChanInfo *info)
	 *   channelStop(ChanInfo *info)
	 * **/
	private void notifyChannel(int notifyType, Bundle bInfo) {
		ChannelInfo info = new ChannelInfo(bInfo);
		String chId = info.getId();
		
		switch (notifyType) {
		case NOTIFY_CHANNEL_START:
			//Notification表示用に使う。
			activeChInfos.put(chId, info);
			isShowNotification = true;
			break;
		case NOTIFY_CHANNEL_UPDATE:
			if (activeChInfos.containsKey(chId))
				activeChInfos.put(chId, info);
			break;
		case NOTIFY_CHANNEL_STOP:
			activeChInfos.remove(chId);
			break;
		}

		if (BuildConfig.DEBUG) {
			String s = bInfo.getString("id");
			for (String key : bInfo.keySet())
				s += ", " + key + "=" + bInfo.get(key);
			Log.i(TAG, "notifyType_" + notifyType + ": " + s);
		}
	}

	/**
	 * PeerCastを開始します。
	 * 
	 * @param iniPath
	 *            peercast.iniのパス(書き込み可能)
	 * @param resourceDir
	 *            htmlディレクトリのある場所
	 * @return 動作ポート
	 */
	private native int nativeStart(String iniPath, String resourceDir);

	private native void nativeQuit();

	/**
	 * <pre>
	 *  現在アクティブなChannel情報を取得します。
	 * Channel[] channels = Channel.fromNativeResult(nativeGetChannels())
	 *   
	 * 
	 * @return Bundle
	 */
	private static native Bundle nativeGetChannels();

	/**
	 * <pre>
	 * Stats stats = Stats.fromNativeResult(nativeGetStats())
	 * 
	 * 
	 * @return Bundle
	 */	
	private static native Bundle nativeGetStats();

	private static native Bundle nativeGetApplicationProperties();
	
	private static native void nativeChannelCommand(int cmdType, int channel_id);
	
	private static native void nativeClassInit();

	static {
		System.loadLibrary("peercast");
		nativeClassInit();
	}

}
