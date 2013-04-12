package org.peercast.core;

/**
 * (c) 2013, T Yoshizawa
 *
 * Dual licensed under the MIT or GPL licenses.
 */
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.zip.*;

import org.peercast.core.R;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.util.Log;

public class PeerCastMainActivity extends PreferenceActivity implements
		ServiceConnection {

	final String TAG = getClass().getSimpleName();

	private Messenger serverMessenger;

	static final String pref_server_running = "pref_server_running";
	static final String pref_settings = "pref_settings";
	static final String pref_notification = "pref_notification";

	@TargetApi(11)
	private void initTheme() {
		if (Build.VERSION.SDK_INT >= 11)
			setTheme(android.R.style.Theme_Holo_Light);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		initTheme();
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.prefs);

		CheckBoxPreference prefRun = (CheckBoxPreference) findPreference(pref_server_running);
		prefRun.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference arg0, Object val) {
				if ((Boolean) val)
					bindPeerCastService();
				else
					unbindPeerCastService();

				return true;
			}
		});
		Preference prefSetting = findPreference(pref_settings);
		try {
			assetInstall();
		} catch (IOException e) {
			prefSetting.setTitle("ERROR: html-dir install failed.");
			prefSetting.setSummary(e.toString());
			prefRun.setChecked(false);
			prefRun.setEnabled(false);
			return;
		}

		// 他のアプリによってすでに起動している。
		if (isPeerCastServiceRunning()) {
			Log.d(TAG, "isPeerCastServiceRunning(): true");
			bindPeerCastService();
			return;
		}
		if (prefRun.isChecked()) {
			bindPeerCastService();
		} else {
			prefSetting.setEnabled(false);
		}
	}

	private boolean isPeerCastServiceRunning() {
		ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		List<RunningServiceInfo> services = activityManager
				.getRunningServices(64);
		for (RunningServiceInfo info : services) {
			if (info.service.getClassName().equals(
					PeerCastService.class.getName())) {
				return true;
			}
		}
		return false;
	}

	private void bindPeerCastService() {
		Intent i = new Intent(this, PeerCastService.class);
		bindService(i, this, BIND_AUTO_CREATE);
	}

	private void unbindPeerCastService() {
		if (serverMessenger != null) {
			onUnbinded();
			unbindService(this);
			serverMessenger = null;
		}
	}

	private void onUnbinded() {
		CheckBoxPreference prefRun = (CheckBoxPreference) findPreference(pref_server_running);
		prefRun.setTitle(R.string.msg_peercast_server_stopped);
		Preference prefSetting = (Preference) findPreference(pref_settings);
		prefSetting.setEnabled(false);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindPeerCastService();
	}

	@Override
	public void onBackPressed() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.msg_are_you_sure_you_want_to_exit)
				.setCancelable(false)
				.setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								finish();
							}
						})
				.setNegativeButton("No", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		AlertDialog alert = builder.create();
		alert.show();

	}

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

	private void onStartPeerCastService(Bundle serverPropeties) {
		int port = serverPropeties.getInt("port");
		CheckBoxPreference prefPeCaRunning = (CheckBoxPreference) findPreference(pref_server_running);
		String s = getText(R.string.msg_peercast_server_running).toString();

		prefPeCaRunning.setTitle(s + " " + port);

		Preference prefSetting = (Preference) findPreference(pref_settings);
		Uri u = Uri.parse("http://localhost:" + port + "/");
		prefSetting.setSummary(u.toString());
		prefSetting.setEnabled(true);
		Intent i = new Intent(Intent.ACTION_VIEW, u);
		prefSetting.setIntent(i);

	}
	
	private static class ClientHandler extends Handler {
		final WeakReference<PeerCastMainActivity> wThis;
		ClientHandler(PeerCastMainActivity _this){
		 wThis = new WeakReference<PeerCastMainActivity>(_this);
		}
		public void handleMessage(Message msg) {
			Bundle ret = (Bundle) msg.obj;
			wThis.get().onStartPeerCastService(ret);
		}
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder binder) {
		Messenger clientMessenger = new Messenger(new ClientHandler(this));

		serverMessenger = new Messenger(binder);
		try {
			Message msg = Message.obtain(null,
					PeerCastService.MSG_SERVICE_QUERY_SERVER_PROPERTIES);
			msg.replyTo = clientMessenger;
			serverMessenger.send(msg);
		} catch (RemoteException e) {
			Log.e(TAG, "onServiceConnected()", e);
		}
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		onUnbinded();
	}

}
