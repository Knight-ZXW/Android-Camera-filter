package com.gotye.sdk;

import android.content.Context;
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

    private Context mContext;
    private PPAudioEncoder mEnc;

	private MediaCodec mEncoder;
	private byte[] mInfo;
	private int mSampleRate;
	private int mChannels;
	private int mBitrate;
    private long lastAudioPresentationTime = -1;
	
	private boolean mAddAdtsHeader = false;
	private OnAudioDataListener mOnDataListener;

    public SysAudioEncoder(Context context, PPAudioEncoder enc) {
        mContext = context;
        mEnc = enc;
    }

    @Override
	public boolean open(int sample_rate, int channels, int bitrate, boolean bAddAdtsHeader) {
		// TODO Auto-generated method stub
		mSampleRate = sample_rate;
		mChannels = channels;
		mBitrate = bitrate;
		mAddAdtsHeader = bAddAdtsHeader;

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

                    if (mAddAdtsHeader) {
                        int outBitsSize   = bufferInfo.size;
                        int outPacketSize = outBitsSize + 7;    // 7 is ADTS size

                        outData = new byte[outPacketSize];
                        addADTStoPacket(outData, outPacketSize);

                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + outBitsSize);
                        outputBuffer.get(outData, 7, outBitsSize);
                    }
                    else {
                        outData = new byte[bufferInfo.size];
                        // adjust the ByteBuffer values to match BufferInfo (not needed?)
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        outputBuffer.get(outData);
                    }

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
    public void setMuxer(long muxer) {

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
	
	/**
	  * Add ADTS header at the beginning of each and every AAC packet.
	  * This is needed as MediaCodec encoder generates a packet of raw
	  * AAC data.
	  *
	  * Note the packetLen must count in the ADTS header itself.
	  **/
	private void addADTStoPacket(byte[] packet, int packetLen) {
		int profile = 2; // AAC LC
		// 39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
		
		/*		
		0x0 96000
		0x1 88200
		0x2 64000
		0x3 48000
		0x4 44100
		0x5 32000
		0x6 24000
		0x7 22050
		0x8 16000
		0x9 2000
		0xa 11025
		0xb 8000
		0xc reserved*/
		int freqIdx; // 44.1KHz mSampleRate
		if (mSampleRate == 48000)
			freqIdx = 0x3;
		else if (mSampleRate == 44100)
			freqIdx = 0x4;
		else if (mSampleRate == 16000)
			freqIdx = 0x8;
		else if (mSampleRate == 8000)
			freqIdx = 0xb;
		else {
			LogUtil.error(TAG, "invalid freqIdx, mSampleRate " + mSampleRate);
			freqIdx = 0x4;
		}
		
		int chanCfg = mChannels; // CPE
		
		/*adts_fixed_header() {
			syncword; 12 bslbf 
			ID; 1 bslbf 0-mpeg2, 1-mpeg4
			layer; 2 uimsbf  set 00
			protection_absent; 1 bslbf set 0
			profile; 2 uimsbf  
			sampling_frequency_index; 4 uimsbf 
			private_bit; 1 bslbf  
			channel_configuration; 3 uimsbf 
			original/copy; 1 bslbf 
			home; 1 bslbf 
		}
		adts_variable_header() { 
			 copyright_identification_bit; 1
			 bslbf copyright_identification_start; 1 bslbf 
			 frame_length; 13 bslbf 
			 adts_buffer_fullness; 11 bslbf  
			 number_of_raw_data_blocks_in_frame; 2 uimsfb
		}*/
		
		// fill in ADTS data
		packet[0] = (byte) 0xFF;
		packet[1] = (byte) 0xF9;
		packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
		packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
		packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
		packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
		packet[6] = (byte) 0xFC;
	}

	private byte[] getWaveFileHeader(long totalAudioLen,
									 long totalDataLen, long longSampleRate, int channels, long byteRate) {
		byte[] header = new byte[44];
		header[0] = 'R'; // RIFF/WAVE header
		header[1] = 'I';
		header[2] = 'F';
		header[3] = 'F';
		header[4] = (byte) (totalDataLen & 0xff);
		header[5] = (byte) ((totalDataLen >> 8) & 0xff);
		header[6] = (byte) ((totalDataLen >> 16) & 0xff);
		header[7] = (byte) ((totalDataLen >> 24) & 0xff);
		header[8] = 'W';
		header[9] = 'A';
		header[10] = 'V';
		header[11] = 'E';
		header[12] = 'f'; // 'fmt ' chunk
		header[13] = 'm';
		header[14] = 't';
		header[15] = ' ';
		header[16] = 16; // 4 bytes: size of 'fmt ' chunk
		header[17] = 0;
		header[18] = 0;
		header[19] = 0;
		header[20] = 1; // format = 1
		header[21] = 0;
		header[22] = (byte) channels;
		header[23] = 0;
		header[24] = (byte) (longSampleRate & 0xff);
		header[25] = (byte) ((longSampleRate >> 8) & 0xff);
		header[26] = (byte) ((longSampleRate >> 16) & 0xff);
		header[27] = (byte) ((longSampleRate >> 24) & 0xff);
		header[28] = (byte) (byteRate & 0xff);
		header[29] = (byte) ((byteRate >> 8) & 0xff);
		header[30] = (byte) ((byteRate >> 16) & 0xff);
		header[31] = (byte) ((byteRate >> 24) & 0xff);
		header[32] = (byte) (2 * 16 / 8); // block align
		header[33] = 0;
		header[34] = 16; // bits per sample
		header[35] = 0;
		header[36] = 'd';
		header[37] = 'a';
		header[38] = 't';
		header[39] = 'a';
		header[40] = (byte) (totalAudioLen & 0xff);
		header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
		header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
		header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

		return header;
	}
}
