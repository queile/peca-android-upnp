package org.peercast.core;
/**
 * (c) 2013, T Yoshizawa
 *
 * Dual licensed under the MIT or GPL licenses.
 */
import java.util.Timer;
import java.util.TimerTask;
import org.peercast.core.PeerCastServiceController.OnServiceResultListener;
import org.peercast.core.R;
import org.peercast.core.PeerCastServiceController.OnPeerCastEventListener;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.TextView;
import android.widget.Toast;

public class PeerCastMainActivity extends Activity implements
		OnPeerCastEventListener {

	final static String TAG = "PeCaMainActivity";

	static final String pref_notification = "pref_notification";

	@TargetApi(11)
	private void initTheme() {
		//Honeycomb
		if (Build.VERSION.SDK_INT >= 11)
			setTheme(android.R.style.Theme_Holo_Light);
	}

	private Timer tListRefresh;
	private GuiListAdapter guiListAdapter;
	private PeerCastServiceController pecaController;

	private int serverPort = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		initTheme();

		super.onCreate(savedInstanceState);

		setContentView(R.layout.gui);

		ExpandableListView lv = (ExpandableListView) findViewById(R.id.vListChannel);

		guiListAdapter = new GuiListAdapter(this);
		lv.setAdapter(guiListAdapter);

		pecaController = new PeerCastServiceController(this);
		pecaController.setOnPeerCastEventListener(this);

		registerForContextMenu(lv);

		pecaController.bindService();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		SharedPreferences defPref = PreferenceManager
				.getDefaultSharedPreferences(this);
		MenuItem mSettings = menu.findItem(R.id.menu_settings);

		if (serverPort > 0) {
			String settingUrl = "http://localhost:" + serverPort + "/";
			Intent intent = new Intent(Intent.ACTION_VIEW,
					Uri.parse(settingUrl));
			Log.d(TAG, "" + intent);
			mSettings.setIntent(intent);
			mSettings.setEnabled(true);
		} else {
			mSettings.setEnabled(false);
		}

		// mSettings.setIcon(android.R.drawable.ic_menu_preferences);

		MenuItem mRemoveAllCh = menu.findItem(R.id.menu_remove_all_channel);
		mRemoveAllCh.setEnabled(!guiListAdapter.getChannelData().isEmpty());
		
		
		MenuItem mNotification = menu.findItem(R.id.menu_notification);
		mNotification.setChecked(defPref.getBoolean(pref_notification, true));
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		SharedPreferences defPref = PreferenceManager
				.getDefaultSharedPreferences(this);

		switch (item.getItemId()) {
		case R.id.menu_notification:
			boolean checked = !item.isChecked();
			defPref.edit().putBoolean(pref_notification, checked).commit();
			item.setChecked(checked);
			return true;

			case R.id.menu_remove_all_channel:
				for (Channel ch : guiListAdapter.getChannelData()) {
					if (ch.getLocalRelays()+ch.getLocalListeners() == 0)
						pecaController.sendChannelCommand(
							PeerCastServiceController.MSG_CMD_CHANNEL_DISCONNECT,
							ch.getChannel_ID());
				}
				return true;
			
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {

		ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) menuInfo;
		//ExpandableListView lv = (ExpandableListView) v;

		int type = ExpandableListView
				.getPackedPositionType(info.packedPosition);
		int gPos = ExpandableListView
				.getPackedPositionGroup(info.packedPosition);
		int cPos = ExpandableListView
				.getPackedPositionChild(info.packedPosition);

		if (type == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
			// チャンネル
			getMenuInflater().inflate(R.menu.cmenu_channel, menu);
			Channel ch = (Channel) guiListAdapter.getGroup(gPos);
			menu.setHeaderTitle(ch.getInfo().getName());
			MenuItem mKeep = menu.findItem(R.id.cmenu_ch_keep);
			mKeep.setChecked(ch.isStayConnected());
		} else if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
			// サーバント  TODO: 直下切断は未実装
			getMenuInflater().inflate(R.menu.cmenu_servent, menu);
			Servent svt = (Servent) guiListAdapter.getChild(gPos, cPos);
			menu.setHeaderTitle(svt.getHost());
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) item
				.getMenuInfo();
		int type = ExpandableListView
				.getPackedPositionType(info.packedPosition);
		int gPos = ExpandableListView
				.getPackedPositionGroup(info.packedPosition);
		int cPos = ExpandableListView
				.getPackedPositionChild(info.packedPosition);

		Channel ch = (Channel) guiListAdapter.getGroup(gPos);

		switch (item.getItemId()) {

		case R.id.cmenu_ch_disconnect:
			Log.i(TAG, "Disconnect channel: " + ch);
			pecaController.sendChannelCommand(
					PeerCastServiceController.MSG_CMD_CHANNEL_DISCONNECT,
					ch.getChannel_ID());
			return true;

		case R.id.cmenu_ch_keep:
			Log.i(TAG, "Keep channel: " + ch);
			if (item.isChecked())
				pecaController.sendChannelCommand(
						PeerCastServiceController.MSG_CMD_CHANNEL_KEEP_NO,
						ch.getChannel_ID());
			else
				pecaController.sendChannelCommand(
						PeerCastServiceController.MSG_CMD_CHANNEL_KEEP_YES,
						ch.getChannel_ID());
			return true;

		case R.id.cmenu_ch_play:
			Uri u = Uri
					.parse(String.format("mmsh://localhost:%d/stream/%s.wmv",
							serverPort, ch.getID()));
			Intent intent = new Intent(Intent.ACTION_VIEW, u);
			// intent.setFlags();0
			try {
				Toast.makeText(this, u.toString(), Toast.LENGTH_LONG).show();
				startActivity(intent);
			} catch (ActivityNotFoundException e) {
				errorDialog(e.getLocalizedMessage());
			}
			return true;

		case R.id.cmenu_ch_reconnect:
			Log.i(TAG, "Reconnect channel: " + ch);
			pecaController.sendChannelCommand(
					PeerCastServiceController.MSG_CMD_CHANNEL_BUMP,
					ch.getChannel_ID());
			return true;

		case R.id.cmenu_svt_disconnect:
			Servent svt = (Servent) guiListAdapter.getChild(gPos, cPos);
			Log.i(TAG, "Disconnect servent: " + svt); // TODO
			return true;

		default:
			return super.onContextItemSelected(item);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		tListRefresh = new Timer(true);
		tListRefresh.schedule(new TimerTask() {
			@Override
			public void run() {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						onListRefresh();
					}
				});
			}
		}, 0, 5000);
	}

	@Override
	protected void onPause() {
		super.onPause();
		tListRefresh.cancel();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		guiListAdapter.destory();
		pecaController.unbindService();
	}

	@Override
	public void onBackPressed() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.msg_are_you_sure_you_want_to_exit)
				.setCancelable(false)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								finish();
							}
						})
				.setNegativeButton(android.R.string.no,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
							}
						});
		AlertDialog alert = builder.create();
		alert.show();

	}

	// タイマーによってUIスレッドから呼ばれる。
	private void onListRefresh() {
		if (!pecaController.isConnected()) {
			return;
		}
		pecaController.sendCommand(PeerCastService.MSG_GET_CHANNELS,
				new OnServiceResultListener() {
					@Override
					public void onServiceResult(Bundle data) {
						// Log.d(TAG, "MSG_GET_CHANNELS: "+data);
						guiListAdapter.setChannelData(Channel
								.fromNativeResult(data));
						// guiListAdapter.notifyDataSetChanged();
					}
				});
		pecaController.sendCommand(PeerCastService.MSG_GET_STATS,
				new OnServiceResultListener() {
					@Override
					public void onServiceResult(Bundle stats) {
						TextView vBandwidth = (TextView) findViewById(R.id.vBandwidth);
						String s = String.format(
								"R: %.1fkbps S: %.1fkbs   Port: %d ",
								stats.getInt("in_bytes") / 1000f * 8,
								stats.getInt("out_bytes") / 1000f * 8,
								serverPort);
						vBandwidth.setText(s);
					}
				});

	}

	@Override
	public void onConnectPeerCastService() {
		pecaController.sendCommand(
				PeerCastService.MSG_GET_APPLICATION_PROPERTIES,
				new OnServiceResultListener() {
					@Override
					public void onServiceResult(Bundle data) {
						serverPort = data.getInt("port");
					}
				});
	}

	@Override
	public void onDisconnectPeerCastService() {
		serverPort = 0;
	}

	private void errorDialog(String msg) {
		new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert).setMessage(msg)
				.setTitle("Error").show();
	}

}
