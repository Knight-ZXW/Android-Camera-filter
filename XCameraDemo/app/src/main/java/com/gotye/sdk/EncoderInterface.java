package com.gotye.sdk;

public interface EncoderInterface {
	public abstract boolean open(int width, int height, int in_fmt, int framerate, int bitrate);
	
	public abstract void setEncoderOption(String option);
	
	public abstract void setOnDataListener(OnDataListener listener);
	
	public abstract void setOnNotifyListener(OnNotifyListener listener);
	
	public abstract boolean addFrame(byte[] picdata, long timestamp);

	public abstract void setMuxer(long muxer);
	
	public abstract void close();
	
	public interface OnDataListener {
		void onData(PPEncoder enc, byte[] data, int start, int byteCount, long timestamp/*msec*/);
		
		void onSpsPps(PPEncoder enc, byte[] data, int start, int byteCount);
	}
	
	public interface OnNotifyListener {
		boolean onInfo(PPEncoder enc, int what, int extra);
		
		boolean onError(PPEncoder enc, int what, int extra);
		
		void onComplete(PPEncoder enc);
	}
}
