package org.peercast.core;
/**
 * (c) 2013, T Yoshizawa
 *
 * Dual licensed under the MIT or GPL licenses.
 */
import android.os.Bundle;

public class ChannelInfo {


//	b->putString("track.artist", info->track.artist);
//	b->putString("track.title", info->track.title);
//	b->putString("name", info->name);
//	b->putString("desc", info->desc);
//	b->putString("genre", info->genre);
//	b->putString("comment", info->comment);
//	b->putString("url", info->url);
//	b->putInt("bitrate", info->bitrate);

	
	private final Bundle bInfo;

	ChannelInfo(Bundle b) {
		if (b == null)
			throw new NullPointerException();
		bInfo = b;
	}
	
	public String getId() {
		return bInfo.getString("id");
	}

	public String getTrackArtist() {
		return bInfo.getString("track.artist");
	}
	
	public String getTrackTitle() {
		return bInfo.getString("track.title");
	}
	
	public String getName() {
		return bInfo.getString("name");
	}

	public String getDesc() {
		return bInfo.getString("desc");
	}
	
	public String getGenre() {
		return bInfo.getString("genre");
	}
	
	public String getComment() {
		return bInfo.getString("comment");
	}
	
	public String getUrl() {
		return bInfo.getString("url");
	}
	
	public int getBitrate() {
		return bInfo.getInt("bitrate");
	}

	@Override
	public String toString() {
		String s = getClass().getSimpleName() + ": [";
		for (String k: bInfo.keySet()){
			s += k + "=" + bInfo.get(k) + ", ";
		}
		return s + "]";
	}




}
