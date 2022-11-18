package util;

import java.util.ArrayList;

import arc.Core;
import arc.util.Log;


public class AntiVpn {
	public static ArrayList<String> vpnServersList = new ArrayList<>();
	public static int timesLeft = 10, timesLimit = timesLeft;
	public static boolean isEnabled = false, vpnFileFound = true, fullLoaded = false;
	
	private AntiVpn() {
	}
	
	
	public static boolean checkIP(String ip) {
		return false;
	}

	public static void init() { init(false); }
	public static void init(boolean loadSettings) {
		if (loadSettings && Core.settings.has("AntiVpn")) {
			try {
				String[] temp = Core.settings.getString("AntiVpn").split(" \\| ");
				isEnabled = Boolean.parseBoolean(temp[0]);
				timesLimit = Integer.parseInt(temp[1]);
				
			} catch (Exception e) { saveSettings(); }
		}
		
		if (isEnabled) {
			arc.files.Fi file = Core.files.local("config/ip-vpn-list.txt");
			
	    	if (file.exists()) {
	    		try {
	    			Log.info("Loading anti VPN file...");
	    			
	    			for (Object line : file.readString().lines().toArray()) vpnServersList.add((String) line);
	    			if (vpnServersList.get(0).equals("### Vpn servers list ###")) {
	    				vpnServersList.remove(0);
	    				
	    				fullLoaded = true;
	    				Log.info("File loaded!");
	    				return;
	    			
	    			} else {
	    				vpnServersList.clear();
	    				Log.warn("You have an old version of the file, downloading the new file...");
	    			}
	    		} catch (Exception e) { Log.err("The anti VPN file could not be load! Try to download the file..."); }
	    	
	    	} else {
	    		Log.err("The anti VPN file was not found! Downloading the file from the web...");
	    		try { file.file().createNewFile(); } 
	    		catch (java.io.IOException e) {}
	    	}
	    	
		}
    }
	
	public static void saveSettings() {
		Core.settings.put("AntiVpn", isEnabled + " | " + timesLimit);
	}
}
