package org.peercast.core;

import java.lang.ref.WeakReference;
import java.util.Map;
import android.annotation.TargetApi;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

public class PeerCastService extends Service {

	public static final int MSG_SERVICE_QUERY_SERVER_PROPERTIES = 0x0000;


	public static final int NOTIFY_CHANNEL_START = 0x00;
	public static final int NOTIFY_CHANNEL_UPDATE = 0x01;
	public static final int NOTIFY_CHANNEL_STOP = 0x02;
	
	static final String TAG = "PeCaSrv";

	private Messenger serviceMessenger;

	private ServiceHandler serviceHandler;
	private Looper serviceLooper;
 
	private static class ServiceHandler extends Handler {
		private final WeakReference<PeerCastService> serv;

		public ServiceHandler(Looper serviceLooper, PeerCastService serv) {
			super(serviceLooper);
			this.serv = new WeakReference<PeerCastService>(serv);
		}

		@Override
		public void handleMessage(Message msg) {
			Message reply = obtainMessage(msg.what);
			Bundle repObj = new Bundle();
			switch (msg.what) {
			case MSG_SERVICE_QUERY_SERVER_PROPERTIES:
				repObj.putInt("port", serv.get().serverPort);
				break;
			}
			reply.obj = repObj;
			try {
				msg.replyTo.send(reply);
			} catch (RemoteException e) {
				Log.e(TAG, "", e);
			} 
		}
	}   

	@Override
	public void onCreate() {
		super.onCreate();
		HandlerThread thread = new HandlerThread("PeerCastService",
				Thread.MIN_PRIORITY);
		thread.start();

		serviceLooper = thread.getLooper();
		serviceHandler = new ServiceHandler(serviceLooper, this);
		serviceMessenger = new Messenger(serviceHandler);
	}

	private int serverPort = 0;
 
	@Override
	public IBinder onBind(Intent i) {
		Context c = getApplicationContext();
		String iniPath = c.getFileStreamPath("peercast.ini").getAbsolutePath();
		String resDirPath = c.getFilesDir().getAbsolutePath();
		synchronized (this) {
			if (serverPort == 0) {
				serverPort = nativeStart(iniPath, resDirPath);
			}
			Notification n = new Notification();
			startForeground(ID_NOTIFY, n);
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
		nativeQuit();
	}  

	private static final int ID_NOTIFY = 0xABCDEF;
	private Map<String, String> lastInfos;
	
	@SuppressWarnings("deprecation")
	@TargetApi(11)
	private void updateNotification(NotificationManager nm, Map<String, String> infos){
		if (infos.equals(lastInfos))
			return;
		lastInfos = infos;
		
		String chName = infos.get("name");
		String chDesc = infos.get("desc");
		String chComment = infos.get("comment");
		String chUrl = infos.get("url");
		if (chName==null || "".equals(chName))
			return;
		
		Intent intent = new Intent();
		if (chUrl!=null && chUrl.startsWith("http")){
			intent = new Intent(Intent.ACTION_VIEW, Uri.parse(chUrl));
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
			Log.d(TAG, intent+"");
		}
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);
		Notification n;
		Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_notify_icon);

		String title = String.format("Playing: %s", chName);
		String text = String.format("%s %s", chDesc, chComment);
		if (Build.VERSION.SDK_INT >= 11){
			n = new Notification.Builder(this)
			.setContentIntent(contentIntent)
			.setLargeIcon(icon)
			.setContentTitle(title)
			.setContentText(text)
			.setContentInfo(String.format("%sK", infos.get("bitrate")))
			.setSmallIcon(R.drawable.ic_notify_icon)
			.getNotification();
		} else {
			n = new Notification();
			n.setLatestEventInfo(this, title, text, contentIntent);
			n.icon = R.drawable.ic_notify_icon;
			n.largeIcon = icon;
		}
		//n.flags |= Notification.FLAG_AUTO_CANCEL;
		nm.notify(ID_NOTIFY, n);
		Log.d(TAG, "nm.notify()");		
	}
	
	private void nativecalled_notifyMessage(int notifyType, String message) {
		Log.i(TAG, "notifyMessage: " + notifyType + ", " + message);
	} 

	private void nativecalled_notifyChannel(int notifyType, Map<String, String> infos){
		String s = infos.get("id");
		for (Map.Entry<String, String> e: infos.entrySet())
			s += ", " + e; 
		Log.i(TAG, "notifyType_" + notifyType + ": " + s);

		NotificationManager nm = 
				   (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		if (notifyType==NOTIFY_CHANNEL_STOP){
			Log.d(TAG, "nm.cancel()");
			//foregroundなのでnm.cancelはきかない。
			lastInfos = null;
			nm.notify(ID_NOTIFY, new Notification());
			return;
		}

		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		if (pref.getBoolean(PeerCastMainActivity.pref_notification, false)){
			updateNotification(nm, infos);
		}
	}
	
	/**
	 * PeerCastを開始します。
	 * 
	 * @param iniPath
	 *            peercast.iniのパス。(書き込み可能であること)
	 * @param resourceDir
	 * @return 動作ポート
	 */
	private native int nativeStart(String iniPath, String resourceDir);

	private native void nativeQuit();

	private static native void nativeClassInit();

	static {
		System.loadLibrary("peercast");
		nativeClassInit();
	}

}
