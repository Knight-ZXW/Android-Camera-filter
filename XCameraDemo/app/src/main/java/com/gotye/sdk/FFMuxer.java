package com.gotye.sdk;

import android.media.MediaCodec.BufferInfo;

import java.nio.ByteBuffer;

public class FFMuxer {
	public static final String TAG = "FFMuxer";
	
	private long mHandle;

	public long getHandle() {
		return mHandle;
	}
	
	public void writeSampleData(int trackIndex, ByteBuffer byteBuf, BufferInfo bufferInfo) {
		byte[] outData = new byte[bufferInfo.size];
		byteBuf.get(outData);

		nativeWriteFrame(true, outData, 0, bufferInfo.size, bufferInfo.presentationTimeUs);
	}

	public void SetSpsAndPps(ByteBuffer byteBuf, BufferInfo bufferInfo) {
		byte[] sps_pps = new byte[bufferInfo.size];
		byteBuf.position(bufferInfo.offset);
		byteBuf.limit(bufferInfo.offset + bufferInfo.size);
		byteBuf.get(sps_pps);

		nativeSetSpsAndPps(sps_pps);
	}
	
	public native boolean nativeOpen(String url);
	
	public native int nativeAddVideo(int width, int height, int framerate, int bitrate/* bps */);

	// @return
	// The track index for this newly added track,
	// and it should be used in the writeSampleData(int, ByteBuffer, MediaCodec.BufferInfo)
	public native int nativeAddAudio(int sample_rate, int channels, int bitrate/* bps */);

	public native boolean nativeSetMetaData(int stream_index, String key, String value);

	public native boolean nativeSetSpsAndPps(byte[] sps_pps);
	
	// video frame with start code
	public native boolean nativeWriteFrame(boolean isVideo, byte[] data, 
			int start, int byteCount, long timestamp/* usec */);
	
	public native void nativeClose();
	
	// unit: kbps
	public native int nativeGetBitrate();

    // unit: byte
	public native int nativeGetBufferingSize();
	
    // Load the .so
    static {
    	System.loadLibrary("andcodec");
    }
    
}
