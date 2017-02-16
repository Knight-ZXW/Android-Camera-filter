package com.gotye.sdk;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.gotye.bibo.util.AVCNaluType;
import com.gotye.bibo.util.ColorFormat;
import com.gotye.bibo.util.LogUtil;
import com.gotye.bibo.util.Util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class SystemEncoder implements EncoderInterface {
	private static final String TAG = "SystemEncoder";
	private static final int TIMEOUT_USEC = 0;//100;

	private Context mContext;
	private PPEncoder mEncoder;

	private MediaCodec mSysEncoder;
	private ByteBuffer videoSPSandPPS;
	private boolean sawFirstVideoKeyFrame = false;

	private int mWidth, mHeight;
	private int mInFmt;
	private int mEncFmt;
	private String mEncName;
	private int mEncBitrate = 512000;
	private int mEncFrameRate = 15;
	private byte[] yuv420;
	private byte[] m_info;
	private long mFrames;
	private OnDataListener mOnDataListener;

	public SystemEncoder(Context ctx, PPEncoder enc) {
		mContext = ctx;
		mEncoder = enc;
	}

	@Override
	public boolean open(int width, int height, int in_fmt, int framerate, int bitrate) {
		// TODO Auto-generated method stub
		mWidth 			= width;
		mHeight			= height;
		mInFmt			= in_fmt;
		mEncFrameRate	= framerate;
		mEncBitrate		= bitrate;
		yuv420 = new byte[width * height * 3 / 2];

		// xiaomi3: CodecCapabilities length 4, [2141391876, 2130708361, 21, -1225580544]
		// public static final int COLOR_TI_FormatYUV420PackedSemiPlanar = 2130706688;
		// public static final int COLOR_FormatSurface = 2130708361;
		// public static final int COLOR_QCOM_FormatYUV420SemiPlanar = 2141391872;

		// COLOR_FormatYUV420Planar for S39H
		// COLOR_FormatYUV420SemiPlanar for xiaomi3
		mEncFmt = Util.readSettingsInt(mContext, "sys_enc_input_fmt");
		mEncName = Util.readSettingsString(mContext, "sys_enc_name");
		if (mEncFmt == 0) {
			dumpEncoderCaps();

			//int supported_fmt = getSupportedInputFormat();
			int supported_fmt = getMediaColorFormat();
			if (supported_fmt == -1) {
				LogUtil.error(TAG, "failed to find supported encoder pixel format");
				return false;
			}

			mEncFmt = supported_fmt;
			LogUtil.info(TAG, "Java: getSupportedInputFormat " + mEncFmt);
			Util.writeSettingsInt(mContext, "sys_enc_input_fmt", mEncFmt);
			Util.writeSettingsString(mContext, "sys_enc_name", mEncName);
		}

		String fmt_str = "Other";
		if (mEncFmt == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)
			fmt_str = "COLOR_FormatYUV420Planar";
		else if (mEncFmt == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
			fmt_str = "COLOR_FormatYUV420SemiPlanar";

		LogUtil.info(TAG, String.format("Java: encoder_name %s, encoder_fmt %d(%s)",
				mEncName, mEncFmt, fmt_str));

		try {
			if (mEncName != null)
				mSysEncoder = MediaCodec.createByCodecName(mEncName);
			else
				mSysEncoder = MediaCodec.createEncoderByType("video/avc");
		} catch (IOException e) {
			e.printStackTrace();
            LogUtil.error(TAG, "failed to open MediaCodec: " + e.toString());
			return false;
		}

		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mEncBitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mEncFrameRate);
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mEncFmt);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5); //关键帧间隔时间 单位sec

		mSysEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mSysEncoder.start();
        if (supportSetBitrate())
            adjustBitrate(mEncBitrate);

		return true;
	}

	@Override
	public void setEncoderOption(String option) {
		// TODO Auto-generated method stub
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
	public void setMuxer(long muxer) {

	}

	@Override
	public boolean addFrame(byte[] picdata, long timestamp) {
		// TODO Auto-generated method stub
		//LogUtil.debug(TAG, String.format("Java: addFrame() in_len %d", picdata.length));

        if (mWidth * mHeight * 3 / 2 != picdata.length) {
            LogUtil.error(TAG, String.format("Java: picdata length is wrong: %d(input %d x %d)",
                    picdata.length, mWidth, mHeight));
            return false;
        }

        if (mEncFmt == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar/*19*/) {
			if (mInFmt == Util.PREVIEW_PIX_FORMAT_YV12)
				ColorFormat.swapYV12toI420(picdata, yuv420, mWidth, mHeight);
			else
				ColorFormat.NV21toYUV420Planar(picdata, yuv420, mWidth, mHeight);
		}
		else if (mEncFmt == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar/*21*/) { // nv21
			if (mInFmt == Util.PREVIEW_PIX_FORMAT_YV12)
				ColorFormat.YV12toYUV420SemiPlanar(picdata, yuv420, mWidth, mHeight);
			else {
				boolean bConvert = true;
				// fix huawei hw encoder color problem
				if (Build.MODEL.equals("HUAWEI C8813Q"))
					bConvert = false;

				if (bConvert)
					ColorFormat.convertNV21toNV12(picdata);
			}
		}
		else {
			LogUtil.error(TAG, "Java: unsupported encode format " + mEncFmt);
			return false;
		}

		try {
			ByteBuffer[] inputBuffers = mSysEncoder.getInputBuffers();
			int inputBufferIndex = mSysEncoder.dequeueInputBuffer(-1);
//	        LogUtil.debug(TAG, "Java: inputBufferIndex " + inputBufferIndex);
			if (inputBufferIndex >= 0) {
				ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
				inputBuffer.clear();

				//long timestamp = mFrames * 1000000 / mEncFrameRate;
				//long timestamp = TimerUtil.nowUsec();

				// public final void queueInputBuffer (
				// int index, int offset, int size, long presentationTimeUs, int flags)
				if (mEncFmt == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar &&
						mInFmt == Util.PREVIEW_PIX_FORMAT_NV21) {
					inputBuffer.put(picdata);
					mSysEncoder.queueInputBuffer(inputBufferIndex, 0, picdata.length, timestamp, 0);
				}
				else {
					inputBuffer.put(yuv420);
					mSysEncoder.queueInputBuffer(inputBufferIndex, 0, yuv420.length, timestamp, 0);
				}

//	        	LogUtil.debug(TAG, "Java: offerEncoder() queueInputBuffer #" + mFrames);
				mFrames++;
			}
			else {
				LogUtil.warn(TAG, "Java: input buffer overflow");
				return false;
			}

			ByteBuffer[] encoderOutputBuffers = mSysEncoder.getOutputBuffers();
			MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

			int outputBufferIndex = mSysEncoder.dequeueOutputBuffer(bufferInfo, 0);
			int pos = 0;
			while (outputBufferIndex >= 0) {
				// write more than 1 nalu(s)
				ByteBuffer outputBuffer = encoderOutputBuffers[outputBufferIndex];

				if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
					LogUtil.info(TAG, "Java: MediaCodec.BUFFER_FLAG_CODEC_CONFIG");
					if (m_info == null) {
						m_info = new byte[bufferInfo.size];
						outputBuffer.get(m_info);
						LogUtil.info(TAG, "Java: save sps and pps, data len " + m_info.length);
						LogUtil.info(TAG, String.format("Java: sps data %02x %02x %02x %02x %02x %02x",
								m_info[0], m_info[1], m_info[2], m_info[3], m_info[4], m_info[5]));

						if (mOnDataListener != null)
							mOnDataListener.onSpsPps(mEncoder, m_info, 0, m_info.length);
						LogUtil.info(TAG, "Java: sps_pps saved");
					}
				}
				else {
					byte[] outData = new byte[bufferInfo.size];
					// adjust the ByteBuffer values to match BufferInfo (not needed?)
					outputBuffer.position(bufferInfo.offset);
					outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
					outputBuffer.get(outData);

					if ((outData[4] & 0x1f) != AVCNaluType.SPS && (outData[4] & 0x1f) != AVCNaluType.PPS) {
						// video frame nalu
						if (mOnDataListener != null)
							mOnDataListener.onData(mEncoder, outData, 0, bufferInfo.size,
									bufferInfo.presentationTimeUs);
					}
				}

				mSysEncoder.releaseOutputBuffer(outputBufferIndex, false);
				outputBufferIndex = mSysEncoder.dequeueOutputBuffer(bufferInfo, 0);
			} // end of while

	        /*int encoderStatus;
	        while (true) {
	            encoderStatus = mSysEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
	            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
	            	break;
	            }
	            else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
	            	LogUtil.info(TAG, "Java: INFO_OUTPUT_BUFFERS_CHANGED");
	                // not expected for an encoder
	                encoderOutputBuffers = mSysEncoder.getOutputBuffers();
	            }
	            else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
	            	LogUtil.info(TAG, "Java: INFO_OUTPUT_FORMAT_CHANGED");
	            }
	            else if (encoderStatus < 0) {
	                LogUtil.warn(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
	                        encoderStatus);
	                // let's ignore it
	            }
	            else {
	                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
	                if (encodedData == null) {
	                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
	                }

	                byte []outOpaque = new byte[16];

	                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
	                    // Previously we ignored this state due to having fed Android's
	                	// MediaFormat to MediaMuxer. See above condition for:
	                	// else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)

	                	// Perhaps we should concentrate on setting FFmpeg's
	                	// AVCodecContext from Android's MediaFormat

                		// Copy the CODEC_CONFIG Data
                		// For H264, this contains the Sequence Parameter Set and
                		// Picture Parameter Set. We include this data with each keyframe
	                	LogUtil.info(TAG, "Java: copyVideoSPSandPPS, offset " + bufferInfo.offset);
                		videoSPSandPPS = ByteBuffer.allocateDirect(bufferInfo.size);
                		byte[] videoConfig = new byte[bufferInfo.size];
                		encodedData.get(videoConfig, 0, bufferInfo.size);
                		encodedData.position(bufferInfo.offset);
                		encodedData.put(videoConfig, 0, bufferInfo.size);
                		videoSPSandPPS.put(videoConfig, 0, bufferInfo.size);

	                    LogUtil.info(TAG, String.format("Writing codec_config for %s, pts %d size: %d",
	                    		"video", bufferInfo.presentationTimeUs,  bufferInfo.size));
	                    LogUtil.info(TAG, "writeCodecConfig");
	                    //if (mOnDataListener != null) {
            	        //	mOnDataListener.onData(mEncoder, encodedData.array(),
            	        //			bufferInfo.offset, bufferInfo.size, outOpaque);
            	        //}

	                    bufferInfo.size = 0;	// prevent writing as normal packet
	                }

	                if (bufferInfo.size != 0) {
	                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
	                    encodedData.position(bufferInfo.offset);
	                    encodedData.limit(bufferInfo.offset + bufferInfo.size);

	                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_SYNC_FRAME) != 0){
	                    	// A hack! Preceed every keyframe with the Sequence Parameter Set and Picture Parameter Set generated
	                    	// by MediaCodec in the CODEC_CONFIG buffer.

	                    	if (sawFirstVideoKeyFrame){
	                    		// Write SPS + PPS
	                    		LogUtil.info(TAG, "Java: writeSPSandPPS");
	                    		if (mOnDataListener != null) {
	                	        	mOnDataListener.onData(mEncoder, videoSPSandPPS.array(),
	                	        			0, videoSPSandPPS.capacity(), outOpaque);
	                	        }
	                    	}else {
	                    		sawFirstVideoKeyFrame = true;
	                    	}

	                    	// Write Keyframe
	                    	LogUtil.info(TAG, "Java: writeFrame");
	                    	if (mOnDataListener != null) {
                	        	mOnDataListener.onData(mEncoder, encodedData.array(),
                	        			bufferInfo.offset, bufferInfo.size, outOpaque);
                	        }
	                    }else{
	                    	// Write Non Key Video Frame
	                    	LogUtil.info(TAG, "Java: Non-key writeFrame");
	                    	if (mOnDataListener != null) {
                	        	mOnDataListener.onData(mEncoder, encodedData.array(),
                	        			bufferInfo.offset, bufferInfo.size, outOpaque);
                	        }
	                    }
	                }

	                mSysEncoder.releaseOutputBuffer(encoderStatus, false);
	            }

	            if (encoderStatus < 0)
	            	break;
	        }*/

			return true;
		} catch (Throwable t) {
			t.printStackTrace();
		}

		return false;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		try {
			mSysEncoder.stop();
			mSysEncoder.release();
			mSysEncoder = null;
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	// from 766040931@qq.com
	private int getMediaColorFormat() {
		int numbers = MediaCodecList.getCodecCount();
		MediaCodecInfo codeinfo = null;
		MediaCodecInfo.CodecCapabilities capabilities = null;
		for (int i = 0; i < numbers && codeinfo == null; i++) {
			MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
			if (!info.isEncoder()) {
				continue;
			}
			String[] types = info.getSupportedTypes();
			for (int j = 0; j < types.length; j++) {
				if (types[j].equals("video/avc")) {
					try {
						LogUtil.info(TAG, "Java: to get " + info.getName() + " codec capabilites");
						capabilities = info.getCapabilitiesForType("video/avc");
						LogUtil.info(TAG, "Java: capabilities.colorFormats.length: " + capabilities.colorFormats.length
								+ " == " + Arrays.toString(capabilities.colorFormats));
					} catch (IllegalArgumentException e) {
						e.printStackTrace();
						LogUtil.error(TAG, "IllegalArgumentException: " + e.toString());
						continue;
					}

					mEncName = info.getName();
					codeinfo = info;
					LogUtil.info(TAG, "Java: encoder name " + mEncName);
					break;
				}
			}
		}

		if (codeinfo == null) {
			LogUtil.error(TAG, "failed to find capable encoder");
			return -1;
		}

		int colorFormat = -1;
		for (int i = 0; i < capabilities.colorFormats.length && colorFormat == -1; i++) {
			int format = capabilities.colorFormats[i];
			switch (format) {
				case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar: // 19
					colorFormat = format;
					break;
			/*case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
				colorFormat = format;
				break;*/
				case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar: // 21
					colorFormat = format;
					break;
			/*case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
				colorFormat = format;
				break;
			case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
				colorFormat = format;
				break;*/

				default:
					LogUtil.warn(TAG, "Java: skipping unsupport format :" + format);
					break;
			}
		}

		LogUtil.info(TAG, "Java: colorformat " + colorFormat);
		return colorFormat;

	}

	private static int getSupportedInputFormat() {
		//#7 codecName OMX.qcom.video.encoder.avc, type [video/avc], fmt [2141391876, 2130708361, 21, -1225580544]
		//#10 codecName OMX.google.h264.encoder, type [video/avc], fmt [19, 21, 2130708361]
		//#32 codecName OMX.qcom.video.decoder.avc, type [video/avc], fmt [2141391876, 19]
		//#43 codecName OMX.google.h264.decoder, type [video/avc], fmt [19]

		//#13 codecName OMX.MTK.VIDEO.DECODER.AVC, type [video/avc], fmt [2130706433]
		//#23 codecName OMX.MTK.VIDEO.ENCODER.AVC, type [video/avc], fmt [2130706944, 2130708361, 2130706944, 19]

		// encoder_name OMX.IMG.TOPAZ.VIDEO.Encoder
		// supportedTypes [video/3gpp, video/avc, video/mp4v-es]
		// supportedcolorFormats [#1] video/avc :
		// [2135033992, 20, 21, 2130706433, 19, 2130706437,
		// 39, 2130706434, 2130706438, 23, 24, 2130706449,
		// 22, 2130706439, 2130706440, 25, 26, 27, 28,
		// 2130706435, 2130706441, 2130706444, 2130706450,
		// 2130706442, 2130706443, 2130708361, 2135033992,
		// 2130706445, 2130706446, 2130706447, 2130706448, 6]

		int codec_count = MediaCodecList.getCodecCount();
		LogUtil.info(TAG, "Java: MediaCodecList count: " + codec_count);
		for (int i = 0; i < codec_count; i++) {
			MediaCodecInfo codec_info = MediaCodecList.getCodecInfoAt(i);
			if (codec_info.getName().toUpperCase().contains("ENCODER")) {
				int supported_type_count = codec_info.getSupportedTypes().length;
				for (int j=0;j<supported_type_count;j++) {
					String type = codec_info.getSupportedTypes()[j];
					if (type.toLowerCase().equals("video/avc")) {
						MediaCodecInfo.CodecCapabilities codec_cap = codec_info
								.getCapabilitiesForType(type);
						int colorFmtCount = codec_cap.colorFormats.length;
						for (int k=0;k<colorFmtCount;k++) {
							if (codec_cap.colorFormats[k] == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar ||
									codec_cap.colorFormats[k] == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
								return codec_cap.colorFormats[k];
						}
					}
				}
			}
		}

		return -1;
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	public void adjustBitrate(int targetBitrate){
		if (supportSetBitrate() && mSysEncoder != null){
			Bundle bitrate = new Bundle();
			bitrate.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, targetBitrate);
			mSysEncoder.setParameters(bitrate);
		} else if (!supportSetBitrate()) {
			Log.w(TAG, "Ignoring adjustVideoBitrate call. This functionality is only available on Android API 19+");
		}
	}

	private boolean supportSetBitrate() {
		return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT/*API level 19*/);
	}

	private static void dumpEncoderCaps() {
		int codec_count = MediaCodecList.getCodecCount();
		LogUtil.info(TAG, "Java: MediaCodecList count: " + codec_count);
		for (int i = 0; i < codec_count; i++) {
			MediaCodecInfo codec_info = MediaCodecList.getCodecInfoAt(i);
			if (codec_info.getName().toUpperCase().contains("ENCODER")) {
				LogUtil.info(TAG, "Java: =======================");
				String encoder_name = codec_info.getName();
				LogUtil.info(TAG, "Java: encoder_name " + encoder_name);

				String[]supportedTypes = codec_info.getSupportedTypes();
				LogUtil.info(TAG, "Java: supportedTypes " + Arrays.toString(supportedTypes));
				for (int j=0;j<supportedTypes.length;j++) {
					try {
						MediaCodecInfo.CodecCapabilities codec_cap = codec_info
								.getCapabilitiesForType(supportedTypes[j]);
						LogUtil.info(TAG, String.format("Java: supportedcolorFormats [#%d] %s, %s",
								j, supportedTypes[j], Arrays.toString(codec_cap.colorFormats)));
					} catch (IllegalArgumentException e) {
						e.printStackTrace();
						LogUtil.error(TAG, "IllegalArgumentException: " + e.toString());
						continue;
					}
				}
			}
		}
	}
}
