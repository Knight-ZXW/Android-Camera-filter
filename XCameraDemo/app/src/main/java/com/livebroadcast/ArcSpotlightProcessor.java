package com.livebroadcast;

import android.content.Context;

public class ArcSpotlightProcessor {
    public static final int ASL_MERR_BOUNDID_ERROR = 32768;
    public static final int ASL_MERR_PROCESSMODEL_UNSUPPORT = 32769;
    public static final int ASL_PROCESS_MODEL_FACEBEAUTY = 2;
    public static final int ASL_PROCESS_MODEL_FACEOUTLINE = 1;
    public static final int ASL_PROCESS_MODEL_NONE = 0;
    public static final int ASVL_PAF_NV12 = 2049;
    public static final int ASVL_PAF_NV21 = 2050;
    public static final int ASVL_PAF_RGB32_B8G8R8A8 = 770;
    public static final int ASVL_PAF_RGB32_R8G8B8A8 = 773;
    public static final int ASVL_PAF_YUYV = 1281;
    private Context mContext;
    private int nativeObjectRef = 0;

    public interface ProcessCallback {
        void onCallback(int result, ArcSpotlightResult trackData);
    }

    static {
        System.loadLibrary("ArcSoftSpotlight");
        nativeInitClassParameters();
    }

    public ArcSpotlightProcessor(Context paramContext) {
        this.mContext = paramContext;
        this.nativeObjectRef = nativeCreateEngine();
    }

    private final String getPackageName() {
        //return "com.jiuyan.infashion";/*this.mContext.getPackageName()*/
        return "com.smile.gifmaker";
    }

    private native int nativeCreateEngine();

    private native void nativeDestroyEngine(int paramInt);

    private native int nativeGetOutlinePointCount(int paramInt);

    private native Object nativeGetVersion(int paramInt);

    private static native void nativeInitClassParameters();

    private native int nativeInitial(String paramString, int paramInt1, int paramInt2);

    private native int nativeProcess(byte[] paramArrayOfByte, int paramInt1, Object paramObject, int paramInt2, boolean paramBoolean);

    private native void nativeSetFaceBrightLevel(int paramInt1, int paramInt2);

    private native void nativeSetFaceSkinSoftenLevel(int paramInt1, int paramInt2);

    private native void nativeSetInputDataFormat(int paramInt1, int paramInt2, int paramInt3, int paramInt4);

    private native void nativeSetProcessModel(int paramInt1, int paramInt2);

    private native int nativeUninitial(int paramInt);

    protected void finalize() {
        nativeDestroyEngine(this.nativeObjectRef);
    }

    public int getOutlinePointCount() {
        return nativeGetOutlinePointCount(this.nativeObjectRef);
    }

    public ArcSpotlightVersion getVersion() {
        return (ArcSpotlightVersion) nativeGetVersion(this.nativeObjectRef);
    }

    public int init(String trackDataPath, int faceCount) {
        return nativeInitial(trackDataPath, faceCount, this.nativeObjectRef);
    }

    public int process(byte[] data, int size, ProcessCallback paramProcessCallback, boolean paramBoolean) {
        return nativeProcess(data, size, paramProcessCallback, this.nativeObjectRef, paramBoolean);
    }

    public void setFaceBrightLevel(int value/*50*/) {
        nativeSetFaceBrightLevel(value, this.nativeObjectRef);
    }

    public void setFaceSkinSoftenLevel(int value/*50*/) {
        nativeSetFaceSkinSoftenLevel(value, this.nativeObjectRef);
    }

    public void setInputDataFormat(int width, int height, int format) {
        nativeSetInputDataFormat(width, height, format, this.nativeObjectRef);
    }

    public void setProcessModel(int model) {
        nativeSetProcessModel(model, this.nativeObjectRef);
    }

    public void uninit() {
        nativeUninitial(this.nativeObjectRef);
    }
}
