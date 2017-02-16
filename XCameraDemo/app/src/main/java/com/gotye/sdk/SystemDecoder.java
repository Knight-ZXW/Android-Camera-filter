package com.gotye.sdk;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.view.SurfaceView;

import com.gotye.bibo.util.LogUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SystemDecoder implements DecoderInterface {
	private static final String TAG = "SystemDecoder";
	private static final String MIME_TYPE = "video/avc";
	private static final int TIMEOUT_USEC = 5000;
	
	private PPDecoder mDecoder;
	
	private MediaCodec mSysDecoder;
	
	private int mWidth, mHeight;
	private int mFrameRate;
	private long mDecFrameCnt;
	private SurfaceView mSurfaceView;
	
	private OnDataListener mOnDataListener;
	
	public SystemDecoder(PPDecoder dec) {
		mDecoder = dec;
	}
	
	@Override
	public boolean open(int width, int height, int framerate) {
		// TODO Auto-generated method stub
		if (mSurfaceView == null) {
			LogUtil.error(TAG, "Java: surfaceview is NOT set");
			return false;
		}
		
		mWidth 			= width;
		mHeight			= height;
		mFrameRate		= framerate;

		try {
			mSysDecoder = MediaCodec.createDecoderByType(MIME_TYPE);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
		mSysDecoder.configure(format,
	    		mSurfaceView.getHolder().getSurface(),
	            null,
	            0);
		mSysDecoder.start();
		
		mDecFrameCnt = 0L;
	    return true;
	}
	
	@Override
	public void setView(SurfaceView surfaceview) {
		mSurfaceView = surfaceview;
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
		// TODO Auto-generated method stub
		LogUtil.debug(TAG, String.format("Java: offerEncoder() in_len %d", decdata.length));
		
		boolean ret = false;
		ByteBuffer[]decodeInputBuffers = mSysDecoder.getInputBuffers();
		int inputBufferIndex = mSysDecoder.dequeueInputBuffer(-1);
        LogUtil.info(TAG, "Java: inputBufferIndex: " + inputBufferIndex);
        if (inputBufferIndex >= 0) {
			ByteBuffer buffer = decodeInputBuffers[inputBufferIndex];
	        buffer.clear();
	        buffer.put(decdata, start, byteCount);
	        mSysDecoder.queueInputBuffer(inputBufferIndex,
	        		0/* offset */,
	        		byteCount,
	                0,
	                0);
	        ret = true;
        }
        
        BufferInfo bufferInfo = new BufferInfo();
        int outputBufferIndex = mSysDecoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
        //LogUtil.info(TAG, "Java: outputBufferIndex: " + outputBufferIndex);
        do {
             if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                 //no output available yet
            	 break;
             }
             else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                 //encodeOutputBuffers = mDecodeMediaCodec.getOutputBuffers();
             }
             else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                 MediaFormat formats = mSysDecoder.getOutputFormat();
                 //mediaformat changed
             }
             else if (outputBufferIndex < 0) {
                 //unexpected result from encoder.dequeueOutputBuffer
             }
             else {
            	 mSysDecoder.releaseOutputBuffer(outputBufferIndex, true);
                 if (mOnDataListener != null)
                	 mOnDataListener.onData(mDecoder, (int)mDecFrameCnt++);

                 outputBufferIndex = mSysDecoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                 LogUtil.info(TAG, "Java: inner outputBufferIndex: " + outputBufferIndex);
             }
        } while (outputBufferIndex > 0);
        
	    return ret;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		try {
	        mSysDecoder.stop();
	        mSysDecoder.release();
	        mSysDecoder = null;
	    } catch (Exception e){
	        e.printStackTrace();
	    }
	}
    
}
