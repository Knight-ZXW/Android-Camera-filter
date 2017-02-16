package com.gotye.sdk;

import android.view.SurfaceView;

public interface DecoderInterface {
	public abstract boolean open(int width, int height, int framerate);
	
	public abstract void setOnDataListener(OnDataListener listener);
	
	public abstract void setOnNotifyListener(OnNotifyListener listener);
	
	public abstract boolean addData(byte[] decdata, int start, int byteCount, byte[] opaque);
	
	public abstract void close();
	
	public abstract void setView(SurfaceView surfaceview);
	
	public interface OnDataListener {
		abstract void onData(PPDecoder dec, int frame_count);
	}
	
	public interface OnNotifyListener {
		abstract boolean onInfo(PPDecoder dec, int what, int extra);
		
		abstract boolean onError(PPDecoder dec, int what, int extra);
		
		abstract void onComplete(PPDecoder dec);
	}
}
