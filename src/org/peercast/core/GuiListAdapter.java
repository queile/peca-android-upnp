package org.peercast.core;

/**
 * (c) 2013, T Yoshizawa
 *
 * Dual licensed under the MIT or GPL licenses.
 */
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class GuiListAdapter extends BaseExpandableListAdapter {

	static final String TAG = "GuiListAdapter";

	private final Context context;
	private List<Channel> channels = Collections.emptyList();
	private final HostNameCacheThread hostNameCacheThread;

	GuiListAdapter(Context c) {
		context = c;
		hostNameCacheThread = new HostNameCacheThread();
	}

	public void destory() {
		hostNameCacheThread.finish();
	}

	public void setChannelData(List<Channel> channels) {
		if (channels == null)
			throw new NullPointerException();
		
		this.channels = channels;
		notifyDataSetChanged();
		
		if (BuildConfig.DEBUG) {
			for (Channel ch : channels) {
				Log.d(TAG, "Channel:" + ch);
				for (Servent svt : ch.getServents())
					Log.d(TAG, " Servent:" + svt);
			}
		}
	}

	public List<Channel> getChannelData(){
		return this.channels;
	}
	
	
	@Override
	public View getGroupView(int pos, boolean isExpanded, View convertView,
			ViewGroup parent) {
		View v = convertView;
		if (v == null) {
			LayoutInflater inflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			v = inflater.inflate(R.layout.ch_item_g, null);
		}
		Channel ch = channels.get(pos);
		ChannelInfo info = ch.getInfo();

		ImageView vChIcon = (ImageView) v.findViewById(R.id.vChIcon);
		vChIcon.setImageResource(channelStatusIcon(ch));

		TextView vChName = (TextView) v.findViewById(R.id.vChName);
		vChName.setText(info.getName());

		TextView vChRelays = (TextView) v.findViewById(R.id.vChRelays);
		String s = String.format("%d/%d  -  [%d/%d]", ch.getTotalListeners(),
				ch.getTotalRelays(), ch.getLocalListeners(),
				ch.getLocalRelays());
		vChRelays.setText(s);

		TextView vChBitrate = (TextView) v.findViewById(R.id.vChBitrate);
		s = String.format("% 5d kbps", info.getBitrate());
		vChBitrate.setText(s);

		ImageView vChServents = (ImageView) v.findViewById(R.id.vChServents);

		Bitmap bm = createServentMonitor(ch.getServents());
		vChServents.setImageBitmap(bm);

		return v;
	}

	private Bitmap createServentMonitor(List<Servent> servents) {
		// とりあえず10コ分。
		Bitmap bmp = Bitmap.createBitmap(12 * 10, 12, Bitmap.Config.ARGB_8888);

		Canvas cv = new Canvas(bmp);

		Paint pWhite = new Paint();
		pWhite.setColor(0x40ffffff);

		for (int i = 0; i < servents.size() && i < 10; i++) {
			Servent svt = servents.get(i);

			Paint pRect = new Paint();
			if (svt.isSetInfoFlg()) {
				if (svt.isFirewalled()) {
					pRect.setColor(Color.RED);
				} else if (svt.isRelay()) {
					pRect.setColor(Color.GREEN);
				} else {
					if (svt.getNumRelays() > 0) {
						pRect.setColor(Color.BLUE);
					} else {
						pRect.setColor(0xff800080); // Purple
					}
				}
			} else {
				pRect.setColor(0xff222222); // Blackだと黒テーマで見えない
			}
			cv.drawRect(0 + 12 * i, 0, 12 + 12 * i, 12, pWhite); // 白枠
			cv.drawRect(1 + 12 * i, 1, 11 + 12 * i, 11, pRect);
		}
		return bmp;
	}

	private int channelStatusIcon(Channel ch) {
		switch (ch.getStatus()) {
		case Channel.S_IDLE:
			return R.drawable.st_idle;

		case Channel.S_SEARCHING:
		case Channel.S_CONNECTING:
			return R.drawable.st_connect;

		case Channel.S_RECEIVING:
			int nowTimeSec = (int) (System.currentTimeMillis() / 1000);

			if (ch.getSkipCount() > 2
					&& ch.getLastSkipTime() + 120 > nowTimeSec) {
				return R.drawable.st_conn_ok_skip;
			}
			return R.drawable.st_conn_ok;

		case Channel.S_BROADCASTING:
			return R.drawable.st_broad_ok;

		case Channel.S_ERROR:
			// if (ch && ch->bumped)
			// img = img_connect;
			return R.drawable.st_error;
		}
		return R.drawable.st_idle;

	}

	private int serventStatusIcon(Channel ch, Servent svt) {
		if (ch.getStatus() != Channel.S_RECEIVING)
			return R.drawable.st_empty;

		int nowTimeSec = (int) (System.currentTimeMillis() / 1000);
		if (ch.getSkipCount() > 2 && ch.getLastSkipTime() + 120 > nowTimeSec) {
			if (svt.isRelay())
				return R.drawable.st_conn_ok_skip;
			if (svt.getNumRelays() > 0)
				return R.drawable.st_conn_full_skip;
			return R.drawable.st_conn_over_skip;
		} else {
			if (svt.isRelay())
				return R.drawable.st_conn_ok;
			if (svt.getNumRelays() > 0)
				return R.drawable.st_conn_full;
			return R.drawable.st_conn_over;
		}
	}

	@Override
	public Object getChild(int gPos, int cPos) {
		List<Servent> servents = channels.get(gPos).getServents();
		return servents.get(cPos);
	}

	@Override
	public long getChildId(int gPos, int cPos) {
		return 0;
	}

	@Override
	public View getChildView(int gPos, int cPos, boolean isLastChild, View v,
			ViewGroup parent) {
		if (v == null) {
			LayoutInflater inflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			v = inflater.inflate(R.layout.ch_item_c, null);
		}
		Channel ch = (Channel) getGroup(gPos);
		Servent svt = (Servent) getChild(gPos, cPos);

		ImageView vSvtIcon = (ImageView) v.findViewById(R.id.vSvtIcon);
		vSvtIcon.setImageResource(serventStatusIcon(ch, svt));

		String hostName = hostNameCacheThread.getHostName(svt.getHost());
		// (Version) 0/0 123.0.0.0(hostname)
		TextView vSvtVersion = (TextView) v.findViewById(R.id.vSvtVersion);
		vSvtVersion.setText("(" + svt.getVersion() + ")");

		TextView vSvtRelays = (TextView) v.findViewById(R.id.vSvtRelays);
		vSvtRelays.setText(String.format("%d/%d", svt.getTotalListeners(),
				svt.getTotalRelays()));

		TextView vSvtHost = (TextView) v.findViewById(R.id.vSvtHost);
		vSvtHost.setText(String.format("%s (%s)", svt.getHost(), hostName));

		// tv.setText(String.format("(%s) %d/%d %s(%s)",
		// svt.getTotalListeners(),
		// svt.getTotalRelays(), svt.getHost(), hostName));
		return v;
	}

	@Override
	public int getChildrenCount(int gPos) {
		return channels.get(gPos).getServents().size();
	}

	@Override
	public Object getGroup(int pos) {
		return channels.get(pos);
	}

	@Override
	public int getGroupCount() {
		return channels.size();
	}

	@Override
	public long getGroupId(int pos) {
		return pos;
	}

	@Override
	public boolean hasStableIds() {
		return false;
	}

	@Override
	public boolean isChildSelectable(int gPos, int cPos) {
		return true;
	}

	/**
	 * ホスト名を取得するキャッシュ&スレッド。
	 * 
	 * アプリ終了時にfinish()を呼ぶこと。
	 * */
	static private class HostNameCacheThread extends Thread {
		static final int MAX_CACHE_SIZE = 64;
		private boolean isRun = true;

		@SuppressWarnings("serial")
		private Map<String, String> hostNameCache = new LinkedHashMap<String, String>(
				MAX_CACHE_SIZE) {
			protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
				//
				return size() > MAX_CACHE_SIZE;
			};
		};
		private List<String> resolving = new ArrayList<String>();

		HostNameCacheThread() {
			setPriority(Thread.MIN_PRIORITY);
			setDaemon(true);
			start();
		}

		public String getHostName(String host) {
			synchronized (hostNameCache) {
				if (hostNameCache.containsKey(host))
					return hostNameCache.get(host);
			}
			synchronized (this) {
				resolving.add(host);
				notify();
				return "Resolving..";
			}
		}

		@Override
		public void run() {
			while (isRun) {
				List<String> hosts;
				synchronized (this) {
					hosts = new ArrayList<String>(resolving);
					resolving.clear();
				}

				for (String host : hosts) {
					try {
						String hostName = InetAddress.getByName(host)
								.getHostName();
						hostNameCache.put(host, hostName);
					} catch (UnknownHostException e) {
						hostNameCache.put(host, host);
					}
				}

				try {
					synchronized (this) {
						wait(5000);
					}
				} catch (InterruptedException e) {
					return;
				}
			}
		}

		public void finish() {
			isRun = false;
			interrupt();
		}

	}

}