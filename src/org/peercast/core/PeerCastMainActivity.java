package org.peercast.core;

/**
 * (c) 2013, T Yoshizawa
 *
 * Dual licensed under the MIT or GPL licenses.
 */
import java.io.*;
import java.util.zip.*;

import org.peercast.core.R;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.util.Log;


public class PeerCastMainActivity extends PreferenceActivity implements
		ServiceConnection {

	final String TAG = getClass().getSimpleName();
	
	private IPeerCast mIPeerCast;

	private CharSequence pref_key_server_running;
	private CharSequence pref_key_settings;
	
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.prefs);
		
		pref_key_server_running = getText(R.string.pref_key_server_running);
		pref_key_settings = getText(R.string.pref_key_settings);
		
		CheckBoxPreference prefRun = (CheckBoxPreference)findPreference(pref_key_server_running); 
		prefRun.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference arg0, Object val) {
				if ((Boolean)val)
					bindPeerCastService();
				else
					unbindPeerCastService();
				
				return true;
			}
		});
		Preference prefSetting = findPreference(pref_key_settings);
		try {
			assetInstall();
		} catch (IOException e) {
			prefSetting.setTitle("ERROR: html-dir install failed.");
			prefSetting.setSummary(e.toString());
			prefRun.setChecked(false);
			prefRun.setEnabled(false);
			return;
		}

		
		if (prefRun.isChecked()){
			bindPeerCastService();
		} else {
			prefSetting.setEnabled(false);
		}
	}

	private void bindPeerCastService() {
		Intent i = new Intent(this, PeerCastService.class);
		bindService(i, this, BIND_AUTO_CREATE);
	}

	private void unbindPeerCastService() {
		if (mIPeerCast != null){
			onUnbinded();
			unbindService(this);
			mIPeerCast = null;
		}
	}
 
	private void onUnbinded() {
		CheckBoxPreference prefRun = (CheckBoxPreference)findPreference(pref_key_server_running);
		prefRun.setTitle(R.string.msg_peercast_server_stopped);
		Preference prefSetting = (Preference) findPreference(pref_key_settings);
		prefSetting.setEnabled(false);
	}
	
	

	public void onBackPressed() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.msg_are_you_sure_you_want_to_exit)
				.setCancelable(false)
				.setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								unbindPeerCastService();
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

	@Override
	public void onServiceConnected(ComponentName name, IBinder binder) {
		mIPeerCast = IPeerCast.Stub.asInterface(binder);

		CheckBoxPreference prefPeCaRunning = (CheckBoxPreference)findPreference(pref_key_server_running);
		int port = -1;
		try {
			port = mIPeerCast.getServerPort();
		} catch (RemoteException e) {
		}
		String s = getText(R.string.msg_peercast_server_running).toString();
		
		prefPeCaRunning.setTitle(s + " " + port);

		Preference prefSetting = (Preference) findPreference(pref_key_settings);
		Uri u = Uri.parse("http://localhost:"+port+"/");
		prefSetting.setSummary(u.toString());
		prefSetting.setEnabled(true);
		Intent i = new Intent(Intent.ACTION_VIEW, u);
		prefSetting.setIntent(i);
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		mIPeerCast = null;
		onUnbinded();
	}

}
