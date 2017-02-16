package com.gotye.sdk;

public class FFMediaInfo {
	public static MediaInfo getMediaInfo(String filePath) {
		if (filePath == null || filePath.isEmpty())
			return null;
		
		MediaInfo info = new MediaInfo(filePath);
		return nativeGetMediaInfo(filePath, info) ? info : null;
	}
	
	private native static boolean nativeGetMediaInfo(String filePath, MediaInfo info);
	
	// Load the .so
    static {
    	System.loadLibrary("andcodec");
    }
}
