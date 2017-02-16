package com.gotye.bibo.encode;

/**
 * Created by relex on 15/6/2.
 */

/**
 * Encoder configuration.
 * <p>
 * Object is immutable, which means we can safely pass it between threads without
 * explicit synchronization (and don't need to worry about it getting tweaked out from
 * under us).
 * <p>
 * TODO: make frame rate and iframe interval configurable?  Maybe use builder pattern
 * with reasonable defaults for those and bit rate.
 */
public class EncoderConfig {
    public final String mUrl;
    public final int mWidth;
    public final int mHeight;
    public final int mBitRate;
    public final int mFrameRate;
    public final boolean mEnableAudio;
    public final boolean mX264Encode;
    public final boolean mFdkAACEncode;
    public final boolean mbRotate;

    public EncoderConfig(String url, int width, int height,
                         int frameRate, int bitRate,
                         boolean enable_audio) {
        this(url, width, height, frameRate, bitRate, enable_audio, false, false, false);
    }

    public EncoderConfig(String url, int width, int height,
                         int frameRate, int bitRate,
                         boolean enable_audio,
                         boolean x264_encode, boolean enable_rotate,
                         boolean fdkaac_encode) {
        mUrl            = url;
        mWidth          = width;
        mHeight         = height;
        mBitRate        = bitRate;
        mFrameRate      = frameRate;
        mEnableAudio    = enable_audio;
        mX264Encode     = x264_encode;
        mbRotate        = enable_rotate;
        mFdkAACEncode   = fdkaac_encode;
    }

    public int getmFrameRate() {
        return mFrameRate;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("EncoderConfig: resolution: ");
        sb.append(mWidth);
        sb.append(" x ");
        sb.append(mHeight);
        sb.append(" , framerate ");
        sb.append(mFrameRate);
        sb.append(" , bitrate ");
        sb.append(mBitRate);
        sb.append(" , url ");
        sb.append(mUrl);
        sb.append(" , enable audio ");
        sb.append(mEnableAudio ? "Yes" : "No");
        sb.append(" , x264 encode ");
        sb.append(mX264Encode ? "Yes" : "No");
        sb.append(" , fdk-aac encode ");
        sb.append(mFdkAACEncode ? "Yes" : "No");
        sb.append(" , enable rotate ");
        sb.append(mbRotate ? "Yes" : "No");

        return sb.toString();
    }
}

