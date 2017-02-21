package com.gotye.sdk;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import com.gotye.bibo.util.Constants;
import com.gotye.bibo.util.LogUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Android回声消除
 *在Android中回声消除可以通过三种方式进行处理：1、通过VOICE_COMMUNICATION模式进行录音，自动实现回声消除；
 * 2、利用Android自身带的AcousticEchoCanceler进行回声消除处理；3、使用第三方库（Speex、Webrtc）进行回声消除处理。
 * 使用AudioRecord模式进行录音的时候，需要将AudioManager设置模式为MODE_IN_COMMUNICATION，还需要将麦克风打开。有一点需要特别注意，音频采样率必须设置8000或者16000，通道数必须设为1个。
 *
 *
 *
 * AudioManager audioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
 * audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
 * audioManager.setSpeakerphoneOn(true);
 */

public class SysAudioEncoder implements AudioEncoderInterface {
	private static final String TAG = "SysAudioEncoder";
	private static final String mediaType = "audio/mp4a-latm";
	private static final int kSampleRates[] = { 8000, 11025, 22050, 44100, 48000 };
	private static final int kBitRates[] = { 64000, 128000 };
	
	private static final int TIMEOUT_USEC = 5000;

	private MediaCodec mEncoder;
	private byte[] mInfo;
	private int mSampleRate;
	private int mChannels;
	private int mBitrate;
    private long lastAudioPresentationTime = -1;
	
	private OnAudioDataListener mOnDataListener;

    public SysAudioEncoder() {

    }

    @Override
	public boolean open(int sample_rate, int channels, int bitrate) {
		// TODO Auto-generated method stub
		mSampleRate = sample_rate;
		mChannels = channels;
		mBitrate = bitrate;
		try {
			mEncoder = MediaCodec.createEncoderByType(mediaType);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		MediaFormat mediaFormat = MediaFormat.createAudioFormat(mediaType, sample_rate, channels);
		mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
		mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK,
				mChannels == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO);
		mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, mChannels);
		mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
		mEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mEncoder.start();
		return true;
	}

    @Override
	public void setOnDataListener(OnAudioDataListener listener) {
		// TODO Auto-generated method stub
		mOnDataListener = listener;
	}

    @Override
    public void setOnNotifyListener(OnNotifyListener listener) {

    }

    @Override
	public synchronized boolean addAudioData(byte[] data, int start, int byteCount, long timestamp) {
		// TODO Auto-generated method stub
		if (Constants.VERBOSE) LogUtil.info(TAG, String.format("Java: addAudioData() start %d, size %d, timestamp %d",
				start, byteCount, timestamp));
		
		try {
			ByteBuffer[] inputBuffers = mEncoder.getInputBuffers();
	        int inputBufferIndex = mEncoder.dequeueInputBuffer(TIMEOUT_USEC);
	        if (inputBufferIndex >= 0) {
	            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
	            inputBuffer.clear();
	            inputBuffer.put(data, start, byteCount);
	            
	            mEncoder.queueInputBuffer(inputBufferIndex, 0, byteCount, timestamp, 0);
	        }
	        else {
	        	LogUtil.warn(TAG, "Java: input buffer overflow");
	        	return false;
	        }
	        
	        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
	        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
	        
	        int outputBufferIndex = mEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
	        while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = encoderOutputBuffers[outputBufferIndex];
	            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
	            	LogUtil.info(TAG, "Java: MediaCodec.BUFFER_FLAG_CODEC_CONFIG, len " + bufferInfo.size);
	            	if (mInfo == null) {
	            		mInfo = new byte[bufferInfo.size];
	            		outputBuffer.get(mInfo);

						StringBuffer sbAudioConfig = new StringBuffer();
						sbAudioConfig.append("Java: audio codec config: ");
						for (int i=0;i<mInfo.length;i++) {
							sbAudioConfig.append(String.format("0x%02x ", mInfo[i]));
						}
						LogUtil.info(TAG, sbAudioConfig.toString());
	            		
	            		if (mOnDataListener != null) {
	            			mOnDataListener.OnLATMheader(mInfo, 0, mInfo.length);
	            		}
	            	}
	        	}
	            else if ((bufferInfo.size != 0) && mOnDataListener != null) {
                    // BufferInfo
                    // offset	The start-offset of the data in the buffer.
                    // size	    The amount of data (in bytes) in the buffer.

					if (lastAudioPresentationTime == -1) {
						lastAudioPresentationTime = bufferInfo.presentationTimeUs;
					}
					else if (lastAudioPresentationTime < bufferInfo.presentationTimeUs) {
						lastAudioPresentationTime = bufferInfo.presentationTimeUs;
					}
					else {
						LogUtil.info(TAG, String.format("Java: fake increase presentationTimeUs(%d.%d)",
								bufferInfo.presentationTimeUs, lastAudioPresentationTime));
						bufferInfo.presentationTimeUs = lastAudioPresentationTime + 10000;
						lastAudioPresentationTime = bufferInfo.presentationTimeUs;
					}

                    byte[] outData = null;

                        outData = new byte[bufferInfo.size];
                        // adjust the ByteBuffer values to match BufferInfo (not needed?)
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        outputBuffer.get(outData);

                    if (Constants.VERBOSE) {
                        LogUtil.debug(TAG,
                                String.format("Java: onAudioData %02x %02x %02x %02x %02x %02x %02x %02x",
                                        outData[start + 0], outData[start + 1],
                                        outData[start + 2], outData[start + 3],
                                        outData[start + 4], outData[start + 5],
                                        outData[start + 6], outData[start + 7]));
                    }

                    mOnDataListener.OnAudioData(outData, 0, outData.length,
                            bufferInfo.presentationTimeUs);
	            }
	            
	            mEncoder.releaseOutputBuffer(outputBufferIndex, false);
	            outputBufferIndex = mEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
	        }
	        
	        return true;
		}
		catch (Throwable t) {
	        t.printStackTrace();
	    }
		
	    return false;
	}

    @Override
	public synchronized void close() {
		// TODO Auto-generated method stub
		try {
	        mEncoder.stop();
	        mEncoder.release();
	        mEncoder = null;
	    } catch (Exception e){
	        e.printStackTrace();
	    }
	}

}
