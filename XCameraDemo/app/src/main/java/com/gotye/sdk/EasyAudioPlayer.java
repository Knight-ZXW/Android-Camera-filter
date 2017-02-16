package com.gotye.sdk;


import com.gotye.bibo.util.LogUtil;

/**
 * Created by Michael.Ma on 2016/7/15.
 */
public class EasyAudioPlayer {
    private static final String TAG = "EasyAudioPlayer";

    private OnPlayerCallback mCallback;

    public interface OnPlayerCallback {
        void onPlayerPCM(byte[] buf, int size, int timestamp/*msec*/);

        void onComplete();

        void onEndofStream();
    }

    public void setPCMCallback(OnPlayerCallback callback) {
        this.mCallback = callback;
    }

    public boolean open(
            String url, int out_channels, int out_format, int out_sample_rate) {
        return nativeOpen(url, out_channels, out_format, out_sample_rate);
    }

    public boolean play() {
        return nativePlay();
    }

    public void setLoop(boolean loop) {
        nativeSetLoop(loop);
    }

    public int getCurrentPosition() {
        return nativeGetCurrentPosition();
    }

    public int getDuration() {
        return nativeGetDuration();
    }

    public void setVolume(float vol) {
        if (vol <= 1.0f && vol >= 0.0f) {
            nativeSetVolume(vol);
        }
        else {
            LogUtil.warn(TAG, "invalid volumn value: " + vol);
        }
    }

    public float getVolume() {
        return nativeGetVolume();
    }

    public void release() {
        nativeClose();
    }

    private void onPCMData(byte[] buffer, int size, int timestamp/*msec*/) {
        if (mCallback != null) {
            mCallback.onPlayerPCM(buffer, size, timestamp);
        }
    }

    private void onEndofStream() {
        if (mCallback != null) {
            mCallback.onEndofStream();
        }
    }

    private void onComplete() {
        if (mCallback != null) {
            mCallback.onComplete();
        }
    }

    private native boolean nativeOpen(
            String url, int out_channels, int out_format, int out_sample_rate);

    private native boolean nativePlay();

    private native void nativeSetLoop(boolean loop);

    private native int nativeGetCurrentPosition();

    private native int nativeGetDuration();

    private native void nativeSetVolume(float vol);

    private native float nativeGetVolume();

    private native void nativeClose();

    static {
        System.loadLibrary("andcodec");
    }
}
