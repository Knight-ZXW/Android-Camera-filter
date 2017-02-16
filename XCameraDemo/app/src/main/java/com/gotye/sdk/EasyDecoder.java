package com.gotye.sdk;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.gotye.bibo.util.LogUtil;

import java.nio.ByteBuffer;

public class EasyDecoder implements DecoderInterface {
	public static final String TAG = "EasyDecoder";
	
	public static final int CLIP_FPS				= 15;
	public static final int INVALID_HANDLE 		= -1;
	
	public static final int DEC_OUT_FMT_RGB565LE	= 0;
	public static final int DEC_OUT_FMT_YUV420P	= 1;
	
	private long mHandle = INVALID_HANDLE;
	private PPDecoder mDecoder;
	private String mEncOption;
	private int mWidth, mHeight;
	private int mOutFmt;
	private byte[] rgb565 = null;
	private byte[] mOutOpaque;
	private long mDecFrameCnt;
	private OnDataListener mOnDataListener;
	
	private SurfaceHolder mHolder;
	private Bitmap mBitmap;
	
	public EasyDecoder(PPDecoder dec) {
		mDecoder = dec;
	}
	
	@Override
	public boolean open(int width, int height, int framerate) {
		mWidth	= width;
		mHeight	= height;
		rgb565 = new byte[mWidth * mHeight * 2];
		mBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.RGB_565);
		mOutOpaque = new byte[16];
		mDecFrameCnt = 0L;
		
		return EasyDecoderOpen(mWidth, mHeight, DEC_OUT_FMT_RGB565LE/*hardcode*/);
	}

	@Override
	public void setView(SurfaceView surfaceview) {
		if (surfaceview != null)
			mHolder = surfaceview.getHolder();
	}
	
	@Override
	public void setOnDataListener(OnDataListener listener) {
		// TODO Auto-generated method stub
		mOnDataListener = listener;
	}

	@Override
	public void setOnNotifyListener(OnNotifyListener listener) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean addData(byte[] decdata, int start, int byteCount, byte[] opaque) {
		LogUtil.debug(TAG, String.format("Java: addData() in_len %d", decdata.length));

		int ret = EasyDecoderAdd(decdata, start, byteCount, opaque);
		if (ret < 0) {
			LogUtil.error(TAG, "Java: failed to EasyDecoderAdd()");
			return false;
		}
		
		while (true) {
			int got_pic = EasyDecoderGet(rgb565, mOutOpaque);
			if (got_pic < 0) {
				LogUtil.error(TAG, "failed to get picture");
				return false;
			}
			else if (got_pic == 0) {
				break;
			}
			else if (got_pic > 0) {
				// render
				Canvas canvas = mHolder.lockCanvas();//获得画布
				canvas.drawColor(Color.WHITE);//设置画布背景为白色
				ByteBuffer buffer = ByteBuffer.wrap(rgb565);
				mBitmap.copyPixelsFromBuffer(buffer);
				//if (bFullDisp)
				//	canvas.drawBitmap(mBitmap, null, disp_dst, null);
				//else
					canvas.drawBitmap(mBitmap, 0, 0, null);
					
				canvas.save();
				canvas.restore();
				mHolder.unlockCanvasAndPost(canvas);//完成绘图后解锁递交画布视图
				
				if (mOnDataListener != null) {
					mOnDataListener.onData(mDecoder, (int)mDecFrameCnt++);
				}
			}
		}
		
		return true;
	}
	
	@Override
	public void close() {
		EasyDecoderClose();
	}
	
	// byte to long
	private static long ByteToLong(byte[] b) {
		long s = 0;
		long s0 = b[0] & 0xff;// MSB
		long s1 = b[1] & 0xff;
		long s2 = b[2] & 0xff;
		long s3 = b[3] & 0xff;
		long s4 = b[4] & 0xff;// MSB
		long s5 = b[5] & 0xff;
		long s6 = b[6] & 0xff;
		long s7 = b[7] & 0xff;

		// s0 unchange
		s1 <<= 8;
		s2 <<= 16;
		s3 <<= 24;
		s4 <<= 8 * 4;
		s5 <<= 8 * 5;
		s6 <<= 8 * 6;
		s7 <<= 8 * 7;
		s = s0 | s1 | s2 | s3 | s4 | s5 | s6 | s7;
		return s;
	}
	
	// opaque(16 byte) to frame type 
	private static int OpaqueToType(byte[] b) { 
        int type = b[8];
        return type; 
    }
	
	// for encoder
	private native boolean EasyDecoderOpen(int w, int h, int out_fmt);
	
	private native int EasyDecoderAdd(byte[] decdata, int start, int byteCount, byte[] opaque);
	
	private native int EasyDecoderGet(byte[] picdata, byte[] opaque);
	
	private native void EasyDecoderClose();
	
	private native double EasyDecoderGetFPS();
	
    // Load the .so
    static {
    	System.loadLibrary("andcodec");
    }
    
}
