package org.peercast.core;
/**
* (c) 2013, T Yoshizawa
*
* Dual licensed under the MIT or GPL licenses.
*/
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.peercast.core.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;
import android.view.View.OnClickListener;

public class PeerCastMainActivity extends Activity {

	private PeerCastService mService;

	static final String LABLE_START = "Start Server";
	static final String LABLE_STOP = "Stop Server";
  
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.peercast_main_activity);

		Button btn_start_stop = (Button) findViewById(R.id.btn_start_stop);
		btn_start_stop.setOnClickListener(btnListener);

		Button btn_settings = (Button) findViewById(R.id.btn_settings);
		btn_settings.setOnClickListener(btnListener);

		TextView tv = (TextView) findViewById(R.id.textView1);
		try {
			//assetInstall(getDir("res", MODE_PRIVATE));
			assetInstall(getFilesDir());
		} catch (IOException e) {
			tv.setText("ERROR: html-dir install failed.");
			return;
		}

		startPeerCastService();
		
	}

	private void startPeerCastService() {
		Intent i = new Intent(this, PeerCastService.class);
		//startService(i);		 
		bindService(i, conn, BIND_AUTO_CREATE);
	}

	private void stopPeerCastService() {
		unbindService(conn);//();
		//stopService(new Intent(this, PeerCastService.class));
		//if (true)return;
		TextView tv = (TextView) findViewById(R.id.textView1);
		tv.setText("PeerCast-Server stopped.");
		Button btn_start_stop = (Button) findViewById(R.id.btn_start_stop);
		btn_start_stop.setText(LABLE_START);
		Button btn_settings = (Button) findViewById(R.id.btn_settings);
		btn_settings.setEnabled(false);
	}

	private OnClickListener btnListener = new OnClickListener() {
		public void onClick(View v) {
			Button btn = (Button) v;
			switch (v.getId()) {
			case R.id.btn_start_stop:

				if (LABLE_STOP.equals(btn.getText())) {
					btn.setText(LABLE_START);
					stopPeerCastService();
				} else if (LABLE_START.equals(btn.getText())) {
					btn.setText(LABLE_STOP);
					startPeerCastService();
				}
				break;

			case R.id.btn_settings:
				int port = mService.getServerPort();
				Uri uri = Uri.parse("http://localhost:" + port + "/");
				Intent i = new Intent(Intent.ACTION_VIEW, uri);
				Log.i("", i.toString());
				startActivity(i);
				break;
			}
		}
	};

	public void onBackPressed() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Are you sure you want to exit?")
				.setCancelable(false)
				.setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								stopPeerCastService();
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

	private void assetInstall(File dataDir) throws IOException {
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
				Log.d("PeCa", "Install resource -> " + file.getAbsolutePath());
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
	
	
	private ServiceConnection conn = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder binder) {
			mService = ((PeerCastService.ServiceBinder) binder).getService();


			TextView tv = (TextView) findViewById(R.id.textView1);
			Button btn_start_stop = (Button) findViewById(R.id.btn_start_stop);
			btn_start_stop.setText(LABLE_STOP);
			tv.setText("PeerCast-Server running on " + mService.getServerPort());
			
			Button btn_settings = (Button) findViewById(R.id.btn_settings);
			btn_settings.setEnabled(true);
		}
		@Override
		public void onServiceDisconnected(ComponentName name) {
			mService = null;
			TextView tv = (TextView) findViewById(R.id.textView1);
			tv.setText("PeerCast-Server stopped...");
			Button btn_settings = (Button) findViewById(R.id.btn_start_stop);
			btn_settings.setText(LABLE_START);			
		}
	};
}
