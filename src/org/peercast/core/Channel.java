package org.peercast.core;
/**
 * (c) 2013, T Yoshizawa
 *
 * Dual licensed under the MIT or GPL licenses.
 */
import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.util.Log;

public class Channel {

	public static final int S_NONE = 0;
	public static final int S_WAIT = 1;
	public static final int S_CONNECTING = 2;
	public static final int S_REQUESTING = 3;
	public static final int S_CLOSING = 4;
	public static final int S_RECEIVING = 5;
	public static final int S_BROADCASTING = 6;
	public static final int S_ABORT = 7;
	public static final int S_SEARCHING = 8;
	public static final int S_NOHOSTS = 9;
	public static final int S_IDLE = 10;
	public static final int S_ERROR = 11;
	public static final int S_NOTFOUND = 12;

	public static final int T_NONE = 0;
	public static final int T_ALLOCATED = 1;
	public static final int T_BROADCAST = 2;
	public static final int T_RELAY = 3;

	private final Bundle bChannel;

	private Channel(Bundle b) {
		if (b == null)
			throw new NullPointerException();
		bChannel = b;
	}

	public String getID() {
		return bChannel.getString("id");
	}

	public int getChannel_ID(){
		return bChannel.getInt("channel_id");
	}
	
	public int getTotalListeners() {
		return bChannel.getInt("totalListeners");
	}

	public int getTotalRelays() {
		return bChannel.getInt("totalRelays");
	}

	public int getLocalListeners() {
		return bChannel.getInt("localListeners");
	}

	public int getLocalRelays() {
		return bChannel.getInt("localRelays");
	}

	public int getStatus() {
		return bChannel.getInt("status");
	}

	public boolean isStayConnected() {
		return bChannel.getBoolean("stayConnected");
	}

	public boolean isTracker() {
		return bChannel.getBoolean("tracker");
	}

	public int getLastSkipTime() {
		return bChannel.getInt("lastSkipTime");
	}

	public int getSkipCount() {
		return bChannel.getInt("skipCount");
	}
	
	public ChannelInfo getInfo(){
		return new ChannelInfo(bChannel.getBundle("info"));
	}

	private List<Servent> servents;
	
	public List<Servent> getServents(){
		if (servents == null){
			servents = new ArrayList<Servent>();
			Bundle bServent = bChannel.getBundle("servent");
			while (bServent != null && !bServent.isEmpty()){
				servents.add(new Servent(bServent));
				bServent = bServent.getBundle("next");
			}
		}
		return servents;
	}
	
 
	public static List<Channel> fromNativeResult(Bundle bChannel) {
		List<Channel> channels = new ArrayList<Channel>();

		while (bChannel != null && !bChannel.isEmpty()){
			//Log.d("Channel.java", ""+bChannel);
			channels.add(new Channel(bChannel));
			bChannel = bChannel.getBundle("next");
		}
		return channels;
	}
	
	@Override
	public String toString() {
		String s = getClass().getSimpleName() + ": [";
		for (String k: bChannel.keySet()){
			s += k + "=" + bChannel.get(k) + ", ";
		}
		return s + "]";
	}
	

	
}
