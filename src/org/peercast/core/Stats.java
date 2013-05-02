package org.peercast.core;
/**
 * (c) 2013, T Yoshizawa
 *
 * Dual licensed under the MIT or GPL licenses.
 */
import android.os.Bundle;

public class Stats {

	private final Bundle bStats;
	
	private Stats(Bundle b) {
		if (b == null)
			throw new NullPointerException();
		bStats = b;
	}

	public int getInBytes(){
		return bStats.getInt("in_bytes");
	}
	
	public int getOutBytes(){
		return bStats.getInt("out_bytes");
	}	
	
	public long getInTotalBytes(){
		return bStats.getLong("in_total_bytes");
	}
	
	public long getOutTotalBytes(){
		return bStats.getLong("out_total_bytes");
	}	
			
	public static Stats fromNativeResult(Bundle b){
		return new Stats(b);
	}
	
	@Override
	public String toString() {
		String s = getClass().getSimpleName() + ": [";
		for (String k: bStats.keySet()){
			s += k + "=" + bStats.get(k) + ", ";
		}
		return s + "]";
	}


	
}
