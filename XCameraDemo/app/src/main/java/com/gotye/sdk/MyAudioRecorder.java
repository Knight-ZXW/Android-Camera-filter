package com.gotye.sdk;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.gotye.bibo.util.LogUtil;

import java.util.Arrays;
import java.util.Locale;

// ENCODING_PCM_8BIT: 
// The audio sample is a 8 bit unsigned integer in the range [0, 255], with a 128 offset for zero. 
// This is typically stored as a Java byte in a byte array or ByteBuffer. Since the Java byte is signed, be careful with math operations and conversions as the most significant bit is inverted.

// ENCODING_PCM_16BIT: 
// The audio sample is a 16 bit signed integer typically stored as a Java short in a short array, 
// but when the short is stored in a ByteBuffer, 
// it is native endian (as compared to the default Java big endian). 
// The short has full range from [-32768, 32767], and is sometimes interpreted as fixed point Q.15 data.

// ENCODING_PCM_FLOAT: 
// Introduced in API LOLLIPOP, 
// this encoding specifies that the audio sample is a 32 bit IEEE single precision float. 
// The sample can be manipulated as a Java float in a float array, 
// though within a ByteBuffer it is stored in native endian byte order. 
// The nominal range of ENCODING_PCM_FLOAT audio data is [-1.0, 1.0]. 
// It is implementation dependent whether the positive maximum of 1.0 is included in the interval. 
// Values outside of the nominal range are clamped before sending to the endpoint device. 
// Beware that the handling of NaN is undefined; subnormals may be treated as zero; 
// and infinities are generally clamped just like other values for AudioTrack – try to avoid infinities 
// because they can easily generate a NaN. 
// To achieve higher audio bit depth than a signed 16 bit integer short, 
// it is recommended to use ENCODING_PCM_FLOAT for audio capture, processing, and playback. 
// Floats are efficiently manipulated by modern CPUs, have greater precision than 24 bit signed integers, 
// and have greater dynamic range than 32 bit signed integers. 
// AudioRecord as of API M and AudioTrack as of API LOLLIPOP support ENCODING_PCM_FLOAT.

/*channel count	channel position mask
1	CHANNEL_OUT_MONO
2	CHANNEL_OUT_STEREO
3	CHANNEL_OUT_STEREO | CHANNEL_OUT_FRONT_CENTER
4	CHANNEL_OUT_QUAD
5	CHANNEL_OUT_QUAD | CHANNEL_OUT_FRONT_CENTER
6	CHANNEL_OUT_5POINT1
7	CHANNEL_OUT_5POINT1 | CHANNEL_OUT_BACK_CENTER
8	CHANNEL_OUT_7POINT1_SURROUND*/

/**
 * 音频录制的帮助类内部支持 系统级的AudioRecoder
 */
public class MyAudioRecorder {
	private final static String TAG = "AudioRecord";

	public final static int REC_SOURCE_MIC                  = MediaRecorder.AudioSource.MIC;
    public final static int REC_SOURCE_VOICE_COMMUNICATION  = MediaRecorder.AudioSource.VOICE_COMMUNICATION;

	private AudioRecord mAudioRecord;
	private short[] mBuffer;
	private int mBufferSize;
	private boolean mIsRecording = false;
	private boolean mIsStopping = false;
	private Thread mThr;
	private OnAudioRecListener mOnData;
    private int mOneSecDataLen;

    private long mTotalDataLen;
	private long mStartMsec;

	public void setOnData(OnAudioRecListener onData) {
		mOnData = onData;
	}

	/**
	 *
	 * @param source 表示音频源  详细的 参考 See {@link MediaRecorder.AudioSource}
	 * @param sampleRate 取样 码率
	 * @param channels See {@link AudioFormat#CHANNEL_IN_MONO} and
	 *   {@link AudioFormat#CHANNEL_IN_STEREO}.  {@link AudioFormat#CHANNEL_IN_MONO} is guaranteed
	 *   to work on all devices.
	 * @param fmt 好像没有用到
     * @return 是否启动成功
     */
    public boolean open(int source, int sampleRate, int channels, int fmt) {
        LogUtil.info(TAG, String.format(Locale.US,
                "open audio recorder: source %s, sample_rate %d, channels %d, fmt %d",
                source == REC_SOURCE_MIC ? "mic" : "voice",
                sampleRate, channels, fmt));

        int channelConfiguration;
        if (channels == 1)
        	channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
        else
        	channelConfiguration = AudioFormat.CHANNEL_IN_STEREO;
        int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
        
        try {
            mOneSecDataLen = sampleRate * channels * 16/*S16*/ / 8;
        	// Create a new AudioRecord object to record the audio.
			mBufferSize = AudioRecord.getMinBufferSize(sampleRate,
					channelConfiguration, audioEncoding);
			LogUtil.info(TAG, String.format(Locale.US,
					"Java: mBufferSize %d(%d msec)",
					mBufferSize, mBufferSize * 1000 / mOneSecDataLen));
			mAudioRecord = new AudioRecord(source,
                    sampleRate, channelConfiguration, audioEncoding, mBufferSize * 2);

			mBuffer = new short[mBufferSize / 2];
			return true;
		} catch (IllegalStateException e) {
			e.printStackTrace();
			LogUtil.error(TAG, "IllegalStateException");
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			LogUtil.error(TAG, "IllegalArgumentException");
		}
        
		return false;
	}

    /**
	 * 开始录制音频
	 * @return
     */
    public boolean start() {
		if (!mIsRecording) {
			mAudioRecord.startRecording();

			mIsRecording = true;
			mIsStopping = false;
			mTotalDataLen = 0L;
			mStartMsec = System.currentTimeMillis();

			mThr = new Thread(new WorkThread());
			mThr.start();
		}
		
		return true;
    }

    /**
	 * 停止录制
	 */
    public void stop() {
    	if (!mIsRecording || mThr == null || mIsStopping)
    		return;
    	
    	try {
    		mIsStopping = true;
    		LogUtil.info(TAG, "Java: before join");
			mThr.join(1000);
			mThr = null;
			mAudioRecord.stop();
			mAudioRecord.release();
			mAudioRecord = null;
			mIsStopping = false;
            mIsRecording = false;
			LogUtil.info(TAG, "Java: after join");
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

	/**
	 *
	 * @return audio session id
     */
	public int getAudioSessionId() {
		if (mAudioRecord != null)
			return mAudioRecord.getAudioSessionId();

		return 0;
	}

	/**
	 * 音频数据的转换 工作线程
	 * 不断的从 Audio 硬件中不断的读取short类型的数据 并转换成data
	 */
    private class WorkThread implements Runnable {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			LogUtil.info(TAG, "Java: work thread started");

			try {
				android.os.Process.setThreadPriority(
						android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
			} catch (Exception e) {
				LogUtil.warn(TAG, "Set rec thread priority failed: " + e.getMessage());
			}
			
			while (!mIsStopping) {
				int bufferReadResult = mAudioRecord.read(mBuffer, 0, mBufferSize / 2);
				if (bufferReadResult > 0) {
					if (mOnData != null) {
						byte[] data = short2byte(mBuffer, 0, bufferReadResult);
                        long pts = mTotalDataLen * 1000000L / mOneSecDataLen;
						mOnData.OnPCMData(data, 0, data.length, pts);
                        mTotalDataLen += data.length;

                        long curr = System.currentTimeMillis() - mStartMsec;
                        long gap_msec = curr - pts / 1000;
                        if (gap_msec > 200) {
                            Arrays.fill(data, (byte) 0);
                            pts = mTotalDataLen * 1000000L / mOneSecDataLen;
                            mOnData.OnPCMData(data, 0, data.length, pts);
                            mTotalDataLen += data.length;
                            LogUtil.warn(TAG, "fill mute audio data, gap " + gap_msec + " msec");
                        }
					}
				}
			}
			
			LogUtil.info(TAG, "Java: work thread exited");
		}
    	
    };
    
    public interface OnAudioRecListener {
    	void OnPCMData(byte[] data, int start, int byteCount, long timestamp);
	}
    
    // convert short to byte
    private byte[] short2byte(short[] sData, int start, int count) {
        int shortArrsize = count;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = start; i < start + shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        
        return bytes;
    }
}
