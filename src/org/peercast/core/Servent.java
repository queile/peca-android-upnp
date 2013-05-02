package org.peercast.core;
/**
 * (c) 2013, T Yoshizawa
 *
 * Dual licensed under the MIT or GPL licenses.
 */
import android.os.Bundle;

public class Servent {

	private final Bundle bServent;

	public Servent(Bundle b) {
		if (b == null)
			throw new NullPointerException();
		bServent = b;
	}

	public boolean isRelay(){
		return bServent.getBoolean("relay");
	}
	
	public boolean isFirewalled(){
		return bServent.getBoolean("firewalled");
	}

	public boolean isSetInfoFlg(){
		return bServent.getBoolean("infoFlg");
	}
	
	
	public int getNumRelays(){
		return bServent.getInt("numRelays");
	}
	
	public String getHost(){
		return bServent.getString("host");
	}
	
	public int getPort(){
		return bServent.getInt("port");
	}
	
	public int getTotalListeners(){
		return bServent.getInt("totalListeners");
	}
	
	public int getTotalRelays(){
		return bServent.getInt("totalRelays");
	}
	
	@Override
	public String toString() {
		String s = getClass().getSimpleName() + ": [";
		for (String k: bServent.keySet()){
			s += k + "=" + bServent.get(k) + ", ";
		}
		return s + "]";
	}

	public String getVersion() {
		return bServent.getString("version");
	}
	


	
}
