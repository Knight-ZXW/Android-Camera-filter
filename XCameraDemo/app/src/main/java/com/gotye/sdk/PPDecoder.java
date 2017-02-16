package com.gotye.sdk;

import android.content.Context;
import android.util.Log;
import android.view.SurfaceView;

public class PPDecoder implements DecoderInterface {
	private final static String TAG = "PPDecoder";
	
	private Context mContext = null;
	private DecoderInterface mDecoder = null;
	private DecodeMode mDecodeMode = DecodeMode.SYSTEM;
	
	public enum DecodeMode {
		SYSTEM,
		FFMPEG {
			@Override
            public DecoderInterface newInstance(PPDecoder dec) {
				Log.i(TAG, "decoder_select ffmpeg");
                return new EasyDecoder(dec);
            }
		};
		
		public DecoderInterface newInstance(PPDecoder dec) {
        	Log.i(TAG, "decoder_select system");
            return new SystemDecoder(dec);
        }
	}
	
	public PPDecoder(Context ctx) {
		this(ctx, DecodeMode.SYSTEM);
	}
	
	public PPDecoder(Context ctx, DecodeMode mode) {
		mContext = ctx;
		mDecodeMode = mode;
		mDecoder = mDecodeMode.newInstance(this);
	}
	
	public DecodeMode getDecodeMode() {
		return mDecodeMode;
	}
	
	@Override
	public boolean open(int width, int height, int framerate) {
		// TODO Auto-generated method stub
		if (mDecoder != null)
			return mDecoder.open(width, height, framerate);
		
		return false;
	}

	@Override
	public void setView(SurfaceView surfaceview) {
		// TODO Auto-generated method stub
		if (mDecoder != null)
			mDecoder.setView(surfaceview);
	}
	
	@Override
	public void setOnDataListener(OnDataListener listener) {
		// TODO Auto-generated method stub
		if (mDecoder != null)
			mDecoder.setOnDataListener(listener);
	}

	@Override
	public void setOnNotifyListener(OnNotifyListener listener) {
		// TODO Auto-generated method stub
		if (mDecoder != null)
			mDecoder.setOnNotifyListener(listener);
	}

	@Override
	public boolean addData(byte[] decdata, int start, int byteCount, byte[] opaque) {
		// TODO Auto-generated method stub
		if (mDecoder != null)
			return mDecoder.addData(decdata, start, byteCount, opaque);
		
		return false;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		Log.i(TAG, "close()");
		
		if (mDecoder != null)
			mDecoder.close();
	}

}
