package com.gotye.bibo.ui.widget;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.media.MediaCodec;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import com.gotye.bibo.camera.CameraController;
import com.gotye.bibo.camera.CameraHelper;
import com.gotye.bibo.camera.CommonHandlerListener;
import com.gotye.bibo.encode.EncoderConfig;
import com.gotye.bibo.encode.TextureEncoder;
import com.gotye.bibo.filter.FilterManager;
import com.gotye.bibo.filter.FilterManager.FilterType;
import com.gotye.bibo.gles.EglCore;
import com.gotye.bibo.gles.FrameBufferObject;
import com.gotye.bibo.gles.FullFrameRect;
import com.gotye.bibo.gles.GlUtil;
import com.gotye.bibo.gles.WindowSurface;
import com.gotye.bibo.util.AVCNaluType;
import com.gotye.bibo.util.Constants;
import com.gotye.bibo.util.FaceUtil;
import com.gotye.bibo.util.ImageUtil;
import com.gotye.bibo.util.LogUtil;
import com.gotye.bibo.util.Util;
import com.gotye.sdk.AudioEncoderInterface;
import com.gotye.sdk.EasyAudioPlayer;
import com.gotye.sdk.EncoderInterface;
import com.gotye.sdk.FFMuxer;
import com.gotye.sdk.MyAudioRecorder;
import com.gotye.sdk.PPAudioEncoder;
import com.gotye.sdk.PPEncoder;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by zhuoxiuwu on 2017/2/15.
 * email nimdanoob@gmail.com
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class CameraRecorderView extends SurfaceView
        implements SurfaceHolder.Callback, CommonHandlerListener,
        EncoderInterface.OnDataListener,
        AudioEncoderInterface.OnAudioDataListener,
        MyAudioRecorder.OnAudioRecListener,
        SurfaceTexture.OnFrameAvailableListener,
        EasyAudioPlayer.OnPlayerCallback {

    private static final String TAG = "CameraRecorderView";

    public static final int RECORD_ERROR_INIT_MUXER = -200;
    public static final int RECORD_ERROR_INIT_AUDIO_REC = -201;
    public static final int RECORD_ERROR_INIT_AUDIO_ENC = -202;
    public static final int RECORD_ERROR_INIT_VIDEO_ENC = -203;
    public static final int RECORD_ERROR_WRITE_VIDEO_FRAME = -301;
    public static final int RECORD_ERROR_WRITE_AUDIO_FRAME = -302;
    public static final int RECORD_ERROR_SET_VIDEO_PARAM = -401;

    public static final int CAMERA_ERROR_FAIL_TO_OPEN = -501;

    // display mode
    public static final int SCREEN_FIT = 0; // 自适应
    public static final int SCREEN_STRETCH = 1; // 铺满屏幕
    public static final int SCREEN_FILL = 2; // 放大裁切
    public static final int SCREEN_CENTER = 3; // 原始大小
    public static final String[] DISPLAY_MODE_DESC = new String[]{
            "自适应", "铺满屏幕", "放大裁切", "原始大小"
    };

    private int mDisplayMode = SCREEN_FIT;

    private static final boolean ENCODER_SET_MUXER = false;

    private boolean mbTextureEncode = false;

    private final Context mContext;

    private final static int CAMERA_DEFAULT_FRAMERATE = 15; // 默认15的帧率

    private final static int AUDIO_WEBRTC_SAMPLE_RATE = 48000; // for webrtc
    private final static int AUDIO_SAMPLE_RATE = 44100;
    private final static int AUDIO_CHANNELS = 1;
    private final static int AUDIO_BITRATE = 64000;
    private final static int AUDIO_WEBRTC_BUF_SIZE = AUDIO_WEBRTC_SAMPLE_RATE * 2; // 1 sec
    private final static int AUDIO_PLAYER_BUF_SIZE = AUDIO_SAMPLE_RATE * 2 * 5; // 5 sec

    private final static int RECORD_LATENCY_MSEC = 500;
    private final static int MIX_THRESHOLD_MSEC = 100;

    private CameraHandler mCameraHandler;
    private HandlerThread mHandlerThread;

    private TextureEncoderHandler mTextureEncHandler;
    private MainHandler mMainHandler;

    // Let's keep track of the display rotation and orientation also:
    private int mDisplayRotation;
    // muxer set rotation value(considering camera facing)
    private int mDisplayOrientation;

    // camera
    private boolean mbUseFrontCam = false;
    private int mPreviewWidth, mPreviewHeight; // not consider rotation

    // We need the phone orientation to correctly draw the overlay:
    // phone direction ↑ ~ 0, → ~ 90, ← ~ 270
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private SimpleOrientationEventListener mOrientationListener;

    // encoder
    private EncoderConfig mEncCfg;

    // muxer
    private FFMuxer mMuxer = null;
    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;

    // audio
    private PPAudioEncoder mAudioEncoder;
    private MyAudioRecorder mAudioRec;
    private byte[] mAudioLATM = null;
    private Lock mAudioLock = null;

    // video
    private PPEncoder mVideoEncoder = null;
    private byte[] mVideoSPSandPPS = null;
    private byte[] mH264 = null;
    private FileOutputStream outSteamH264;
    private int mFileOffset = 0;
    private boolean mbWriteSize = false;
    private Lock mVideoLock = null;
    private int mEncWidth, mEncHeight;

    // get texture
    private int mBufferTexID = -1;

    // texture encoder
    private TextureEncoder mVideoTextureEncoder;
    private float mBeautifyValue = 0.3f;
    private long mSurfaceTextureStartTime = 0L;

    private int mSurfaceWidth, mSurfaceHeight; // surfaceview w x h
    private int mIncomingWidth, mIncomingHeight; // consider rotation w x h
    private float mMvpScaleX = 1f, mMvpScaleY = 1f;

    private EglCore mEglCore;
    private WindowSurface mDisplaySurface;
    private WindowSurface mEncoderSurface;
    private SurfaceTexture mCameraTexture;  // receives the output from the camera preview
    private FullFrameRect mFullFrameBlit;
    private final float[] mTmpMatrix = new float[16];
    private int mTextureId;

    private FilterManager.FilterType mCurrentFilterType;
    private FilterManager.FilterType mNewFilterType;
    private float[] mCurrentFilterParams;
    private float[] mNewFilterParams;

    private boolean mbUseFFmpegMux = true;

    private static final int CAMERA_BUFFER_LIST_SIZE = 5;

    // listener
    private EncoderListener mEncoderListener;
    private CameraListener mCameraListener;
    private boolean mbCameraFirstOpen = true;

    private int mApiVersion;

    // state
    private boolean mbRecording = false;
    private boolean mbStopping = false;
    private boolean mbSurfaceCreated = false;
    private boolean mbSetupWhenStart = false;

    // ui
    private ProgressDialog mProgDlg;

    // picture
    private String mSaveFolder;
    private Bitmap mTakePictureBitmap; // for texture take picture mode
    private TakePictureCallback mTakePictureCallback;

    //    private boolean mbPeerConnected = false; // pc connected, video and audio can transfer
    private byte[] mAudioBuf = null;
    private byte[] mAudioTrackBuf = null;
    private int mAudioTrackBufRead;
    private int mAudioTrackBufWrite;

    // watermark
    private Bitmap mWatermarkBitmap;
    private String mWatermarkText;
    private int mWatermarkTextSize;
    private int mWatermarkLeft;
    private int mWatermarkTop;
    private int mWatermarkWidth;
    private int mWatermarkHeight;
    private FullFrameRect mWatermarkFrame;
    private int mWatermarkTextureId;
    private final float[] IDENTITY_MATRIX = new float[16];

    // play song
    private EasyAudioPlayer mAudioPlayer;
    private boolean mbPlayingSong = false;
    private int mPlayerBufTimestamp = 0;
    private int mPlayerTimeOffset = -1;
    private float mMixVolume = 1.0f;

    // color picker
    private boolean mbEnableColorPicker = false;

    // stat
    private long mRecordDurationMsec;
    private int mAvgWriteFrameMsec;
    private int mAvgSwapBuffersMsec;
    private int mInstantAvgSwapBuffersMsec;
    private int mAvgDrawFrameMsec;
    private int mInstantAvgDrawFrameMsec;

    private long mTotalVideoBytes;
    private int mVideoKbps;
    private long mPrevFrameCount, mEncFrameCount;
    private double mPrevFps, mEncFps;
    private long mStartPreviewMsec, mStartRecordMsec;
    private long mAudioClockUsec;
    private int mAVDiffMsec;
    private int mLatencyMsec;

    public CameraRecorderView(Context context) {
        super(context);
        mContext = context;
        init();
    }

    public CameraRecorderView(Context context, AttributeSet attr) {
        super(context, attr);
        mContext = context;
        init();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private boolean init() {
        mApiVersion = Build.VERSION.SDK_INT;
        LogUtil.info(TAG, "android_platform version: " + mApiVersion);

        if (mApiVersion < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            LogUtil.warn(TAG, "mApiVersion < JELLY_BEAN_MR2, texture encode record NOT supported");
        }

        mHandlerThread = new HandlerThread("CameraHandlerThread");
        mHandlerThread.start();

        Looper looper = mHandlerThread.getLooper();
        mCameraHandler = new CameraHandler(looper, this);

        mTextureEncHandler = new TextureEncoderHandler(this);
        mMainHandler = new MainHandler(this);
        mMainHandler.sendMessageDelayed(mMainHandler.obtainMessage(MainHandler.MSG_CHECK_SETUP), 1000);

        mbUseFrontCam = Util.readSettingsBoolean(mContext, "use_front_camera", false);
        mbTextureEncode = Util.readSettingsBoolean(mContext, "texture_encode", false);

        getHolder().addCallback(this);

        mVideoLock = new ReentrantLock();
        mAudioLock = new ReentrantLock();

        mSurfaceWidth = mSurfaceHeight = 0;

        boolean autoRotateOn = (android.provider.Settings.System.getInt(
                mContext.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1);
        if (autoRotateOn) {
            mOrientationListener = new SimpleOrientationEventListener(mContext);
            mOrientationListener.enable();
            // wait for onOrientationChanged to launch SETUP_CAMERA
        } else {
            if (mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                mOrientation = 270;
            else if (mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
                mOrientation = 0;
            else
                mOrientation = 0;
            // camera preview can start ONLY when surface was created
            if (mbSurfaceCreated) {
                mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                        CameraHandler.SETUP_CAMERA));
                mbSetupWhenStart = false;
            } else {
                mbSetupWhenStart = true;
                LogUtil.info(TAG, "Java: camera will start preview when surface was created");
            }
        }

        mBeautifyValue = 60;
        mCurrentFilterType = mNewFilterType = FilterType.Normal;
        mCurrentFilterParams = mNewFilterParams = null;

        mProgDlg = new ProgressDialog(mContext);

        return true;
    }


    /**
     * We need to react on OrientationEvents to rotate the screen and
     * update the views.
     */
    private class SimpleOrientationEventListener extends OrientationEventListener {

        public SimpleOrientationEventListener(Context context) {
            super(context, SensorManager.SENSOR_DELAY_NORMAL);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            int new_orientation = FaceUtil.roundOrientation(orientation, mOrientation);
            if (new_orientation != mOrientation) {
                new_orientation = (new_orientation + 45) / 90 * 90;
                mOrientation = new_orientation;
                LogUtil.info(TAG, "Java: orientation: " + mOrientation);
                // fix huawei PLK_TL01H cannot start preview at first time
                // root cause surfaceCreated is later than onOrientationChanged
                if (mbSurfaceCreated) {
                    mCameraHandler.sendMessage(
                            mCameraHandler.obtainMessage(
                                    CameraHandler.SETUP_CAMERA));
                } else {
                    mbSetupWhenStart = true;
                    LogUtil.info(TAG, "Java: camera will start preview when surface was created");
                }
            }
        }

//        public void onOrientationChanged(int orientation) {
//            if (orientation == ORIENTATION_UNKNOWN) return;
//            android.hardware.Camera.CameraInfo info =
//                    new android.hardware.Camera.CameraInfo();
//            android.hardware.Camera.getCameraInfo(cameraId, info);
//            orientation = (orientation + 45) / 90 * 90;
//            int rotation = 0;
//            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
//                rotation = (info.orientation - orientation + 360) % 360;
//            } else {  // back-facing camera
//                rotation = (info.orientation + orientation) % 360;
//            }
//            mParameters.setRotation(rotation);
//        }
    }

    private static class MainHandler extends Handler {
        private WeakReference<CameraRecorderView> mWeakView;

        private static final int MSG_INIT_MUXER_DONE = 1004;
        private static final int MSG_CHECK_SETUP = 1005;
        private static final int MSG_UPDATE_LAYOUT = 1101;

        private static final int MSG_FAIL_TO_WRITE_VIDEO_FRAME = 2001;
        private static final int MSG_FAIL_TO_WRITE_AUDIO_FRAME = 2002;
        private static final int MSG_FAIL_TO_INIT_MUXER = 2003;
        private static final int MSG_FAIL_TO_SET_VIDEO_PARAM = 2007;

        public MainHandler(CameraRecorderView view) {
            mWeakView = new WeakReference<CameraRecorderView>(view);
        }

        @Override
        public void handleMessage(Message msg) {
            CameraRecorderView view = mWeakView.get();
            if (view == null) {
                LogUtil.debug(TAG, "Got message for dead view");
                return;
            }

            switch (msg.what) {
                case MSG_CHECK_SETUP:
                    if (view.mOrientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                        view.mOrientation = 0;
                        view.mCameraHandler.sendMessage(view.mCameraHandler.obtainMessage(
                                CameraHandler.SETUP_CAMERA));
                    }
                    break;
                case MSG_UPDATE_LAYOUT:
                    view.requestLayout();
                    break;
                case MSG_INIT_MUXER_DONE:

                    boolean success = false;
                    do {
                        if (view.mbTextureEncode && !view.initTextureVideoEncoder()) {
                            if (view.mEncoderListener != null) {
                                view.mEncoderListener.onError(view, RECORD_ERROR_INIT_VIDEO_ENC, 0);
                            }
                            break;
                        } else if (!view.initVideoEncoder()) {
                            if (view.mEncoderListener != null) {
                                view.mEncoderListener.onError(view, RECORD_ERROR_INIT_VIDEO_ENC, 0);
                            }
                            break;
                        }

                        if (view.mbUseFFmpegMux && view.mEncCfg.mEnableAudio &&
                                !view.initAudioEncoder()) {
                            if (view.mEncoderListener != null) {
                                view.mEncoderListener.onError(view, RECORD_ERROR_INIT_AUDIO_ENC, 0);
                            }
                            break;
                        }

                        if (view.mbUseFFmpegMux && view.mEncCfg.mEnableAudio &&
                                !view.initAudioRecorder()) {
                            if (view.mEncoderListener != null) {
                                view.mEncoderListener.onError(view, RECORD_ERROR_INIT_AUDIO_REC, 0);
                            }
                            break;
                        }

                        success = true;
                    } while (false);

                    if (success) {
                        view.mStartRecordMsec = System.currentTimeMillis();
                        view.mbRecording = true;
                        view.mbStopping = false;
                    }
                    break;
                case MSG_FAIL_TO_WRITE_VIDEO_FRAME:
                    if (view.mEncoderListener != null) {
                        view.mEncoderListener.onError(view, RECORD_ERROR_WRITE_VIDEO_FRAME, 0);
                    }
                    break;
                case MSG_FAIL_TO_WRITE_AUDIO_FRAME:
                    if (view.mEncoderListener != null) {
                        view.mEncoderListener.onError(view, RECORD_ERROR_WRITE_AUDIO_FRAME, 0);
                    }
                    break;
                case MSG_FAIL_TO_INIT_MUXER:
                    view.mProgDlg.dismiss();
                    if (view.mEncoderListener != null) {
                        view.mEncoderListener.onError(view, RECORD_ERROR_INIT_MUXER, 0);
                    }
                    break;
                case MSG_FAIL_TO_SET_VIDEO_PARAM:
                    if (view.mEncoderListener != null) {
                        view.mEncoderListener.onError(view, RECORD_ERROR_SET_VIDEO_PARAM, 0);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Custom message handler for main UI thread.
     * <p>
     * Used to handle camera preview "frame available" notifications, and implement the
     * blinking "recording" text.  Receives callback messages from the encoder thread.
     */
    private static class TextureEncoderHandler extends Handler
            implements TextureEncoder.Callback {
        public static final int MSG_FRAME_AVAILABLE = 1;
        public static final int MSG_BUFFER_STATUS = 2;

        private WeakReference<CameraRecorderView> mWeakView;

        public TextureEncoderHandler(CameraRecorderView view) {
            mWeakView = new WeakReference<CameraRecorderView>(view);
        }

        // CircularEncoder.Callback, called on encoder thread
        @Override
        public void bufferStatus(long totalTimeMsec) {
            sendMessage(obtainMessage(MSG_BUFFER_STATUS,
                    (int) (totalTimeMsec >> 32), (int) totalTimeMsec));
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public boolean onSampleData(ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
            if (Constants.VERBOSE) LogUtil.debug(TAG, "Java: onSampleData()");

            CameraRecorderView view = mWeakView.get();

            view.mTotalVideoBytes += bufferInfo.size;

            if (view.mbUseFFmpegMux && view.mMuxer != null) {
                long startMsec = System.currentTimeMillis();
                view.mMuxer.writeSampleData(view.mVideoTrackIndex, byteBuf, bufferInfo);
                long usedMsec = System.currentTimeMillis() - startMsec;
                view.mAvgWriteFrameMsec = (int) ((view.mAvgWriteFrameMsec * 4 + usedMsec) / 5);

                if (view.mEncFrameCount % 10 == 0) {
                    // camera texture timestamp is not exactly the same as the time clock
                    long duration = (view.mCameraTexture.getTimestamp() - view.mSurfaceTextureStartTime) / 1000;
                    view.mLatencyMsec = (int) (duration - bufferInfo.presentationTimeUs) / 1000;
                    if (view.mEncFrameCount > view.mEncCfg.mFrameRate)
                        view.mVideoKbps = (int) (view.mTotalVideoBytes * 8 * 1000 / duration);
                }

                if (Constants.VERBOSE)
                    LogUtil.info(TAG, "sent " + bufferInfo.size + " bytes to muxer");
            } else {
                byte[] data = new byte[bufferInfo.size];
                byteBuf.get(data);
                view.write_h264_data(data, 0, bufferInfo.size);
            }

            return true;
        }

        @Override
        public void onCodecConfig(ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
            LogUtil.info(TAG, "Java: onCodecConfig()");

            CameraRecorderView view = mWeakView.get();
            if (view.mVideoSPSandPPS == null) {
                view.mVideoSPSandPPS = new byte[bufferInfo.size];
                byteBuf.get(view.mVideoSPSandPPS);

                if (view.mMuxer != null) {
                    view.mMuxer.nativeSetSpsAndPps(view.mVideoSPSandPPS);
                }
            }
        }

        @Override
        public void handleMessage(Message msg) {
            CameraRecorderView view = mWeakView.get();
            if (view == null) {
                LogUtil.debug(TAG, "Got message for dead view");
                return;
            }

            switch (msg.what) {
                case MSG_FRAME_AVAILABLE: {
                    view.drawFrame();
                    break;
                }
                case MSG_BUFFER_STATUS: {
                    long duration = (((long) msg.arg1) << 32) |
                            (((long) msg.arg2) & 0xffffffffL);
                    //view.updateBufferStatus(duration);
                    break;
                }
                default:
                    throw new RuntimeException("Unknown message " + msg.what);
            }
        }
    }

    private static class CameraHandler extends Handler {
        public static final int SETUP_CAMERA = 1001;
        public static final int CONFIGURE_CAMERA = 1002;
        public static final int START_CAMERA_PREVIEW = 1003;
        public static final int STOP_CAMERA_PREVIEW = 1004;
        private WeakReference<CameraRecorderView> mWeakView;
        private CommonHandlerListener mListener;

        public CameraHandler(Looper looper, CommonHandlerListener listener) {
            super(looper);
            mListener = listener;
        }

        @Override
        public void handleMessage(Message msg) {
            mListener.handleMessage(msg);
        }
    }

    public interface EncoderListener {
        void onConnected(CameraRecorderView view);

        void onError(CameraRecorderView view, int what, int extra);

        void onInfo(CameraRecorderView view, int what, int extra);
    }

    public interface CameraListener {
        void onCameraOpen(CameraRecorderView view, boolean isFront, boolean isFirstTime);

        void onCameraPreviewSizeChanged(CameraRecorderView view, int width, int height);

        void onCameraOpenFailed(CameraRecorderView view, boolean isFront, int error);
    }

    public void setEncoderListener(EncoderListener encoderListener) {
        mEncoderListener = encoderListener;
    }

    public void setCameraListener(CameraListener cameraListener) {
        mCameraListener = cameraListener;
    }

    private void resetPreviewData() {
        mPrevFrameCount = 0L;
        mPrevFps = 0.0f;
        mAvgSwapBuffersMsec = 0;
        mInstantAvgSwapBuffersMsec = 0;
        mAvgDrawFrameMsec = 0;
        mInstantAvgDrawFrameMsec = 0;
    }

    private void resetEncodeData() {
        mTotalVideoBytes = 0L;
        mEncFrameCount = 0L;
        mAvgWriteFrameMsec = 0;
        mAvgSwapBuffersMsec = 0;
        mInstantAvgSwapBuffersMsec = 0;
        mAvgDrawFrameMsec = 0;
        mInstantAvgDrawFrameMsec = 0;
        mAVDiffMsec = 0;
        mLatencyMsec = 0;
        mAudioClockUsec = 0L;
        mEncFps = 0.0f;
        mEncWidth = mEncHeight = 0;
        mSurfaceTextureStartTime = 0L;
    }

    /**
     * 调整方向
     *
     * @return
     */
    private boolean swapWxH() {
        // phone 0,180 need swap     portait orientation 0,  backface cam_orientation 90
        // pad 90, 270 need swap     landscape orientation 0,  backface cam_orientation 0
        int cam_orientation = CameraController.getInstance().getCameraOrientation();
        boolean isPad = (cam_orientation == 0 || cam_orientation == 180);
        if (isPad)
            return (mOrientation == 90 || mOrientation == 270);
        else
            return (mOrientation == 0 || mOrientation == 180);
    }

    private void setCameraPreviewSize(int width, int height) {
        if (swapWxH()) {
            mIncomingWidth = height;
            mIncomingHeight = width;
        } else {
            mIncomingWidth = width;
            mIncomingHeight = height;
        }

        if (mFullFrameBlit != null) {
            // reset FIRST!
            mMvpScaleX = mMvpScaleY = 1f;

            float ratio = (float) (mSurfaceWidth * mIncomingHeight) / (float) (mSurfaceHeight * mIncomingWidth);
            /*if (ratio > 1f) {
                // screen much wider
                mMvpScaleX = 1f / ratio;
            }
            else if (ratio < 1f) {
                // preview much wider
                mMvpScaleY = ratio;
            }*/

            switch (mDisplayMode) {
                case SCREEN_CENTER:
                    mMvpScaleX = (float) mIncomingWidth / (float) mSurfaceWidth;
                    mMvpScaleY = (float) mIncomingHeight / (float) mSurfaceHeight;
                    break;
                case SCREEN_FIT:
                    if (ratio > 1f) {
                        // screen much wider
                        mMvpScaleX = 1f / ratio;
                    } else if (ratio < 1f) {
                        // preview much wider
                        mMvpScaleY = ratio;
                    }
                    break;
                case SCREEN_FILL:
                    if (ratio > 1f) {
                        // screen much wider
                        mMvpScaleY = ratio;
                    } else if (ratio < 1f) {
                        // preview much wider
                        mMvpScaleX = 1f / ratio;
                    }
                    break;
                case SCREEN_STRETCH:
                    /* Do nothing */
                    break;
                default:
                    break;
            }

            mFullFrameBlit.resetMVPMatrix();
            mFullFrameBlit.scaleMVPMatrix(mMvpScaleX, mMvpScaleY);
        }
    }

    public boolean supportFrontFacingCam() {
        return CameraHelper.checkSupportFrontFacingCamera();
    }

    public void enableTorch(boolean enable) {
        CameraController.getInstance().enableTorch(enable);
    }

    public boolean isTorchOn() {
        return CameraController.getInstance().isTorchOn();
    }

    public void setWatermark(Bitmap bitmap, int left, int top, int width, int height) {
        LogUtil.info(TAG, String.format(Locale.US,
                "Java: setWatermark left %d, top %d, %d x %d", left, top, width, height));

        if (!mbTextureEncode) {
            LogUtil.warn(TAG, "ONLY texture encode mode support watermark");
            return;
        }

        if (mWatermarkText != null) {
            mWatermarkText = null;
            LogUtil.warn(TAG, "watermark text is DISABLED because of bitmap is set");
        }

        mWatermarkBitmap = bitmap;
        mWatermarkLeft = left;
        mWatermarkTop = top;
        mWatermarkWidth = width;
        mWatermarkHeight = height;
    }

    public void setText(String text, int textsize, int left, int top, int width, int height) {
        LogUtil.info(TAG, String.format(Locale.US,
                "Java: setText %s, left %d, top %d, %d x %d", text, left, top, width, height));

        if (!mbTextureEncode) {
            LogUtil.warn(TAG, "ONLY texture encode mode support watermark text");
            return;
        }

        if (mWatermarkBitmap != null) {
            mWatermarkBitmap = null;
            LogUtil.warn(TAG, "watermark bitmap is DISABLED because of text");
        }

        mWatermarkText = text;
        mWatermarkTextSize = textsize;
        mWatermarkLeft = left;
        mWatermarkTop = top;
        mWatermarkWidth = width;
        mWatermarkHeight = height;
    }

    public void setZoomIn() {
        CameraController.getInstance().setZoomIn();
    }

    public void setZoomOut() {
        CameraController.getInstance().setZoomOut();
    }

    public void setColorPicker(boolean ON) {
        mbEnableColorPicker = ON;
    }

    public int getOrientation() {
        return mOrientation;
    }

    public int getDisplayOrientation() {
        return CameraController.getInstance().getDisplayOrientation();
    }

    /**
     * @return 当前显示模式
     * @brief 获取当前显示缩放模式
     */
    public int getDisplayMode() {
        return mDisplayMode;
    }

    /**
     * @brief 设置显示缩放模式
     * @param[in] mode 显示模式
     * @ int SCREEN_FIT = 0; // 自适应
     * @ int SCREEN_STRETCH = 1; // 铺满屏幕
     * @ int SCREEN_FILL = 2; // 放大裁切
     * @ int SCREEN_CENTER = 3; // 原始大小
     */
    public void setDisplayMode(int mode) {
        LogUtil.info(TAG, String.format("setDisplayerMode %d", mode));
        mDisplayMode = mode;
        requestLayout();
    }

    // stat
    public long getRecordDurationMsec() {
        if (mbRecording)
            return mRecordDurationMsec;

        return 0L;
    }

    public int getAVDiffMsec() {
        if (mbRecording)
            return mAVDiffMsec;

        return 0;
    }

    public double getPreviewFps() {
        return mPrevFps;
    }

    public double getEncodeFps() {
        if (mbRecording)
            return mEncFps;

        return 0.0f;
    }

    public long getPreviewFrameCount() {
        return mPrevFrameCount;
    }

    public long getEncodeFrameCount() {
        if (mbRecording)
            return mEncFrameCount;

        return 0;
    }

    public int getAvgWriteFrameMsec() {
        if (mbRecording)
            return mAvgWriteFrameMsec;

        return 0;
    }

    public int getAvgSwapBuffersMsec() {
        if (mbTextureEncode)
            return mAvgSwapBuffersMsec;

        return 0;
    }

    public int getAvgDrawFrameMsec() {
        if (mbTextureEncode)
            return mAvgDrawFrameMsec;

        return 0;
    }

    public int getLatencyMsec() {
        if (mbRecording)
            return mLatencyMsec;

        return 0;
    }

    public int getVideoKbps() {
        if (mbRecording)
            return mVideoKbps;

        return 0;
    }

    public int getTotalKbps() {
        if (mbRecording && mMuxer != null)
            return mMuxer.nativeGetBitrate();

        return 0;
    }

    public int getBufferingSize() {
        if (mbRecording && mMuxer != null)
            return mMuxer.nativeGetBufferingSize();

        return 0;
    }

    public int getPreviewWidth() {
        return mPreviewWidth;
    }

    public int getPreviewHeight() {
        return mPreviewHeight;
    }

    public void setExtureEncodeMode(boolean isTextureEncode) {
        if (isTextureEncode == mbTextureEncode)
            return;

        mbTextureEncode = isTextureEncode;
        LogUtil.info(TAG, "encode mode set to " + (mbTextureEncode ? "纹理编码" : "普通编码"));
        Toast.makeText(mContext, "设置编码模式为 " +
                (mbTextureEncode ? "纹理编码" : "普通编码"), Toast.LENGTH_SHORT).show();

        // refresh SurfaceView
        setVisibility(View.INVISIBLE);
        setVisibility(View.VISIBLE);
        mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                CameraHandler.SETUP_CAMERA ));
    }

    public boolean playSong(String url) {
        LogUtil.info(TAG, "playSong(): " + url);

        if (mAudioPlayer == null)
            mAudioPlayer = new EasyAudioPlayer();

        mAudioPlayer.setPCMCallback(this);
        boolean ret = mAudioPlayer.open(url, AUDIO_CHANNELS, 0, AUDIO_SAMPLE_RATE);
        if (!ret) {
            LogUtil.error(TAG, "failed to open music file: " + url);
            return false;
        }

        mMixVolume = 1.0f;
        mAudioPlayer.setLoop(true);

        mAudioLock.lock();
        try {
            if (mAudioTrackBuf == null)
                mAudioTrackBuf = new byte[AUDIO_PLAYER_BUF_SIZE];
            mAudioTrackBufRead = mAudioTrackBufWrite = 0;

            mPlayerTimeOffset = -1;
            mbPlayingSong = true;
        } finally {
            mAudioLock.unlock();
        }

        return mAudioPlayer.play();
    }

    public void stopSong() {
        LogUtil.info(TAG, "stopSong()");

        mAudioLock.lock();
        try {
            if (mAudioPlayer != null) {
                mAudioPlayer.release();
                mAudioPlayer = null;

                mbPlayingSong = false;
            }
        } finally {
            mAudioLock.unlock();
        }
    }

    public boolean isPlayingSong() {
        return mbPlayingSong;
    }

    public void setSongVolume(float vol/*0.0-1.0*/) {
        if (vol >= 0.0f && vol <= 1.0f) {
            mMixVolume = vol;

            if (mAudioPlayer != null) {
                mAudioPlayer.setVolume(vol);
            }
        }
    }

    public float getSongVolume() {
        return mMixVolume;
    }

    public int getSongPosition() {
        if (mAudioPlayer != null)
            return mAudioPlayer.getCurrentPosition();

        return 0;
    }

    public int getSongDuration() {
        if (mAudioPlayer != null)
            return mAudioPlayer.getDuration();

        return 0;
    }

    public boolean isTextureEncode() {
        return mbTextureEncode;
    }

    private void changeFilter(FilterType filterType, float... filterParams) {
        mNewFilterType = filterType;
        mNewFilterParams = filterParams;
        StringBuffer sb = new StringBuffer();
        sb.append("Java: change filter ");
        sb.append(filterType == FilterType.Normal ? "normal" : "bilateral blur");
        if (mNewFilterParams != null && mNewFilterParams.length > 0) {
            sb.append("params: ");
            for (int i = 0; i < filterParams.length; i++) {
                if (i > 0)
                    sb.append(", ");
                sb.append(filterParams[i]);
            }
        }

        LogUtil.info(TAG, sb.toString());
    }

    public void setFilter(FilterType type) {
        if (mbTextureEncode) {
            if (mCurrentFilterType != type) {
                changeFilter(type, null);
                LogUtil.info(TAG, "setFilter: " + type.canonicalForm());
            }
        }
    }

    public void switchFilter() {
        if (mbTextureEncode) {
            LogUtil.info(TAG, "Java: switchFilter()");

            if (mCurrentFilterType == FilterType.BeautyFaceWu) {
                changeFilter(FilterType.Normal, mCurrentFilterParams);
            } else {
                if (mCurrentFilterParams == null) {
                    mCurrentFilterParams = new float[2];
                    mCurrentFilterParams[0] = 1f;
                    mCurrentFilterParams[1] = 3f;
                }
                changeFilter(FilterType.BeautyFaceWu, mCurrentFilterParams);
            }
        }
    }

    public boolean isFilterEnabled() {
        if (!mbTextureEncode) {
            Toast.makeText(getContext(), "滤镜不可用", Toast.LENGTH_LONG).show();
            return false;
        }

        return (mNewFilterType != FilterType.Normal);
    }

    public void setFilterValue(float val/*0.0-1.0*/) {
        if (val == mBeautifyValue)
            return;

        float input = val;
        if (input < 0.01f)
            input = 0.01f;
        else if (input > 1.0f)
            input = 1.0f;
        mBeautifyValue = input;
        float[] params = null;
        if (FilterType.BeautyFaceWu == mNewFilterType) {
            params = new float[2];
            int level = 1 + (int) (input * 5f);
            if (level > 5)
                level = 5;
            params[0] = 1f;
            params[1] = level;
        } else if (FilterType.BilateralBlur == mNewFilterType ||
                FilterType.AllinOne == mNewFilterType) {
            params = new float[2];
            params[0] = 6f; // distanceNormalizationFactor hardcode
            params[1] = input * 10f; // blurRatio 0.1-10, default 3
        } else if (FilterType.Toon == mNewFilterType) {
            params = new float[2];
            params[0] = input * 3f;
            params[1] = input * 10f;
        } else if (FilterType.SmoothToon == mNewFilterType) {
            params = new float[3];
            params[0] = input * 3f;
            params[1] = input * 10f;
            params[2] = 0.5f;
        } else if (FilterType.White == mNewFilterType) {
            params = new float[2];
            params[0] = input;
            params[1] = 0.5f;
        } else if (FilterType.FaceColor == mNewFilterType) {

        } else if (FilterType.BulgeDistortion == mNewFilterType ||
                FilterType.PinchDistortion == mNewFilterType) {
            params = new float[4];
            params[0] = 0.5f; // radius
            params[1] = input; // scale
            params[2] = 0.5f; // center.x
            params[3] = 0.5f; // center.y
        } else if (FilterType.ChromaKey == mNewFilterType ||
                FilterType.ChromaKeyBlend == mNewFilterType) {
            params = new float[2];
            params[0] = 0.4f + input / 5f; // thresholdSensitivity
            params[1] = 0.1f; // smoothing
        } else {
            params = new float[1];
            params[0] = val;
        }

        changeFilter(mNewFilterType, params);
    }

    /**
     *  选择预览界面 大小
     */
    public void selectPreviewSize() {
        popOutputTypeDlg();
    }

    /**
     * 弹出预览大小选择的dialog
     */
    private void popOutputTypeDlg() {
        final List<Camera.Size> ResList = CameraHelper.getSupportedPreviewSize(
                CameraController.getInstance().getCameraParameters());
        if (ResList == null || ResList.size() <= 0) {
            Toast.makeText(mContext, "没有可以选择的摄像分辨率", Toast.LENGTH_LONG).show();
        }
        int index = -1;
        List<String> CamResList = new ArrayList<String>();
        for (int i = 0; i < ResList.size(); i++) {
            Camera.Size size = ResList.get(i);
            CamResList.add(String.format("%d x %d", size.width, size.height));
        }

        final String[] strRes = CamResList.toArray(new String[CamResList.size()]);
        Dialog choose_mux_fmt_dlg = new AlertDialog.Builder(mContext)
                .setTitle("选择摄像头分辨率")
                .setSingleChoiceItems(strRes, index, /*default selection item number*/
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {

                                //if (whichButton != mCamResIndex) {
                                Toast.makeText(mContext, "选择分辨率 " + strRes[whichButton],
                                        Toast.LENGTH_SHORT).show();

                                Camera.Size new_size = ResList.get(whichButton);
                                mCameraHandler.sendMessage(
                                        mCameraHandler.obtainMessage(
                                                CameraHandler.SETUP_CAMERA,
                                                new_size.width, new_size.height));
                                //}

                                dialog.dismiss();
                            }
                        })
                .setNegativeButton("取消", null)
                .create();
        choose_mux_fmt_dlg.show();
    }

    /**
     * 切换摄像头
     */
    public void switchCamera() {
        mbUseFrontCam = !mbUseFrontCam;
        mCameraHandler.sendMessage(
                mCameraHandler.obtainMessage(
                        CameraHandler.SETUP_CAMERA));
    }

    public boolean isFrontFacing() {
        return mbUseFrontCam;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void takePicture() {
        CameraController.getInstance().takePicture(
                mShutterCallback, null, mJpegCallback);
    }

    public interface onAutoFocusListener {
        void onFocus();
    }

    public void touchAutoFocus(MotionEvent e, onAutoFocusListener listener) {
        mOnAutoFocusListener = listener;
        CameraController.getInstance().startTouchAutoFocus(
                CameraRecorderView.this, e, mAutoFocusCallback);
    }

    private void setDisplayOrientation() {
        int camera_orientation = CameraController.getInstance().getCameraOrientation();
        if (mbUseFrontCam)
            mDisplayOrientation = (360 - mOrientation + camera_orientation) % 360;
        else
            mDisplayOrientation = (mOrientation + camera_orientation) % 360;
        LogUtil.info(TAG, "Java: mDisplayOrientation: " + mDisplayOrientation);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public boolean startRecording(EncoderConfig config) {
        if (mApiVersion < Build.VERSION_CODES.JELLY_BEAN_MR2 && mbTextureEncode) {
            LogUtil.error(TAG, "api level less than JELLY_BEAN_MR2, NOT support texture encode record");
            return false;
        }

        mEncCfg = config;

        if (config.mUrl.startsWith("rtmp://")) {
            mProgDlg.setTitle("视频录制");
            mProgDlg.setMessage("打开中...");
            mProgDlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgDlg.setCancelable(true);
            mProgDlg.show();
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                resetEncodeData();

                if (mEncCfg.mUrl.endsWith(".h264"))//如果是 h264则不适用FFmpegMux
                    mbUseFFmpegMux = false;
                else
                    mbUseFFmpegMux = true;

                setDisplayOrientation();

                // MUST need width and height info to init muxer
                mEncWidth = mEncCfg.mWidth;
                mEncHeight = mEncCfg.mHeight;

                if (mbTextureEncode || (mEncCfg.mX264Encode && mEncCfg.mbRotate)) {
                    if (swapWxH()) {
                        mEncWidth = mEncCfg.mHeight;
                        mEncHeight = mEncCfg.mWidth;
                    }
                }

                if (!mbUseFFmpegMux) {
                    try {
                        if (mH264 == null)
                            mH264 = new byte[1048576];
                        outSteamH264 = new FileOutputStream(mEncCfg.mUrl);
                    } catch (FileNotFoundException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                        mMainHandler.sendEmptyMessage(MainHandler.MSG_FAIL_TO_INIT_MUXER);
                        return;
                    }
                } else if (!initMuxer()) {
                    mMainHandler.sendEmptyMessage(MainHandler.MSG_FAIL_TO_INIT_MUXER);
                    return;
                }

                mMainHandler.sendEmptyMessage(MainHandler.MSG_INIT_MUXER_DONE);
            }
        }).start();

        return true;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void stopRecording() {
        LogUtil.info(TAG, "Java: stopRecording()");

        if (!mbRecording)
            return;

        mbStopping = true;

        // MUST sync mode
        // otherwise maybe NOT stopped when onDestroy() called
        closeAudioRecorder();
        closeVideoEncoder();
        closeAudioEncoder();
        closeMuxer();

        mbRecording = false;
        mbStopping = false;
        LogUtil.info(TAG, "Java: stopRecord() done!");
    }

    public boolean isRecording() {
        return mbRecording;
    }

    public interface TakePictureCallback {
        //传入的bmp可以由接收者recycle
        void takePictureOK(Bitmap bmp);

        void takePictureOK(String filename);

        void takePictureError(int error_msg);
    }

    public void setPictureParams(String saveFolder, TakePictureCallback callback) {
        mSaveFolder = saveFolder;
        mTakePictureCallback = callback;
    }

    private onAutoFocusListener mOnAutoFocusListener = null;
    private Camera.AutoFocusCallback mAutoFocusCallback = new Camera.AutoFocusCallback() {

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            LogUtil.info(TAG, "onAutoFocus " + (success ? "success" : "failed"));
            if (success) {
                if (mOnAutoFocusListener != null)
                    mOnAutoFocusListener.onFocus();

                CameraController.getInstance().takePicture(
                        mShutterCallback, null, mJpegCallback);
            }
        }
    };


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mbTextureEncode) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            int camera_width = mPreviewWidth;
            int camera_height = mPreviewHeight;
            if (swapWxH()) {
                camera_width = mPreviewHeight;
                camera_height = mPreviewWidth;
            }

            int width = getDefaultSize(camera_width, widthMeasureSpec);
            int height = getDefaultSize(camera_height, heightMeasureSpec);

            //LogUtil.info(TAG, String.format("Java: camera %d x %d, preview %d x %d",
            //        camera_width, camera_height, width, height));

            if (camera_width > 0 && camera_height > 0) {
                switch (mDisplayMode) {
                    case SCREEN_CENTER:
                        width = camera_width;
                        height = camera_height;
                        break;
                    case SCREEN_FIT:
                        if (camera_width * height > width * camera_height) {
                            height = width * camera_height / camera_width;
                        } else if (camera_width * height < width * camera_height) {
                            width = height * camera_width / camera_height;
                        }
                        break;
                    case SCREEN_FILL:
                        if (camera_width * height > width * camera_height) {
                            width = height * camera_width / camera_height;
                        } else if (camera_width * height < width * camera_height) {
                            height = width * camera_height / camera_width;
                        }
                        break;
                    case SCREEN_STRETCH:
                        /* Do nothing */
                        break;
                    default:
                        break;
                }
            }

            //LogUtil.info(TAG, String.format("Java: setMeasuredDimension %d x %d", width, height));
            super.setMeasuredDimension(width, height);
        }
    }

    public void onResume() {
        if (mOrientationListener != null)
            mOrientationListener.enable();

    }

    public void onPause() {
        Util.writeSettingsBoolean(mContext, "use_front_camera",
                CameraController.getInstance().isFrontCamera());

        // will stop recording in NORMAL mode
        if (mbRecording && !mbTextureEncode)
            stopRecording();

        if (mDisplaySurface != null) {
            mDisplaySurface.makeCurrent();
            mDisplaySurface.release();
            mDisplaySurface = null;
        }

        if (mOrientationListener != null)
            mOrientationListener.disable();
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void onDestroy() {
        LogUtil.info(TAG, "onDestroy()");

        stopSong();

        if (mbRecording)
            stopRecording();

        CameraController.getInstance().release();

        if (mDisplaySurface != null) {
            mDisplaySurface.makeCurrent();

            if (mBufferTexID != -1) {
                int[] tex = new int[1];
                tex[0] = mBufferTexID;
                GLES20.glDeleteTextures(1, tex, 0);
            }

            if (mCameraTexture != null) {
                mCameraTexture.release();
                mCameraTexture = null;
            }
            if (mFullFrameBlit != null) {
                mFullFrameBlit.release(true);
                mFullFrameBlit = null;
            }
            if (mWatermarkFrame != null) {
                mWatermarkFrame.release(true);
                mWatermarkFrame = null;
            }
            if (mEglCore != null) {
                mEglCore.release();
                mEglCore = null;
            }

            mDisplaySurface.release();
            mDisplaySurface = null;
        }

        if (mOrientationListener != null)
            mOrientationListener.disable();

        if (mCameraHandler != null)
            mCameraHandler.removeCallbacksAndMessages(null);

        mMainHandler.removeCallbacksAndMessages(null);
        mTextureEncHandler.removeCallbacksAndMessages(null);

        if (mHandlerThread != null) {
            Looper looper = mHandlerThread.getLooper();
            if (looper != null)
                looper.quit();
        }
    }

    private boolean initMuxer() {
        mMuxer = new FFMuxer();

        // would BLOCK
        if (!mMuxer.nativeOpen(mEncCfg.mUrl))
            return false;

        mVideoTrackIndex = mMuxer.nativeAddVideo(mEncWidth, mEncHeight,
                mEncCfg.mFrameRate, mEncCfg.mBitRate);
        if (mVideoTrackIndex < 0) {
            LogUtil.error(TAG, "failed to add video stream");
            return false;
        }

        if (!mbTextureEncode && !mEncCfg.mbRotate && mDisplayOrientation != 0) {
            if (!mMuxer.nativeSetMetaData(mVideoTrackIndex, "rotate",
                    String.valueOf(mDisplayOrientation))) {
                return false;
            }
        }

        if (mEncCfg.mEnableAudio) {
            int audio_samplerate = AUDIO_SAMPLE_RATE;
            mAudioTrackIndex = mMuxer.nativeAddAudio(audio_samplerate, AUDIO_CHANNELS, AUDIO_BITRATE);
            if (mAudioTrackIndex < 0)
                return false;
        }

        if (mbTextureEncode) {
            // waiting for EglCore ready
            int retry = 3;
            while (retry > 0) {
                if (mEglCore != null) {
                    break;
                }

                try {
                    Thread.sleep(500);
                    LogUtil.info(TAG, "mEglCore is null, waiting...");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                retry--;
            }

            if (mEglCore == null) {
                LogUtil.error(TAG, "mEglCore is null");
                return false;
            }
        }

        return true;
    }

    private boolean initVideoEncoder() {
        PPEncoder.EncodeMode mode;
        String option = null;
        if (mEncCfg.mX264Encode) {
            mode = PPEncoder.EncodeMode.X264;
            if (mEncCfg.mbRotate)
                option = String.format("rotate=%d", mDisplayOrientation);
        } else {
            mode = PPEncoder.EncodeMode.SYSTEM;
            option = "bitrate=" + mEncCfg.mBitRate;
        }

        mVideoEncoder = new PPEncoder(mContext, mode);
        mVideoEncoder.setEncoderOption(option);
        if (ENCODER_SET_MUXER) {
            long muxer = mMuxer.getHandle();
            mVideoEncoder.setMuxer(muxer);
            LogUtil.info(TAG, "set muxer: " + muxer);
        }
        mVideoEncoder.setOnDataListener(this);

        int in_fmt;
        int preview_fmt = CameraController.getInstance()
                .getCameraParameters().getPreviewFormat();
        if (preview_fmt == ImageFormat.NV21)
            in_fmt = Util.PREVIEW_PIX_FORMAT_NV21;
        else
            in_fmt = Util.PREVIEW_PIX_FORMAT_YV12;

        return mVideoEncoder.open(mEncWidth, mEncHeight,
                in_fmt, mEncCfg.mFrameRate, mEncCfg.mBitRate);
    }

    private boolean initTextureVideoEncoder() {
        try {
            mVideoTextureEncoder = new TextureEncoder(
                    mEncWidth, mEncHeight,
                    mEncCfg.mBitRate, mEncCfg.mFrameRate,
                    mTextureEncHandler);
        } catch (IOException e) {
            LogUtil.error(TAG, "初始化 TextEncoder 失败");
            e.printStackTrace();
        }

        mEncoderSurface = new WindowSurface(
                mEglCore, mVideoTextureEncoder.getInputSurface(), true);
        return true;
    }

    private boolean initAudioEncoder() {
        PPAudioEncoder.EncodeMode mode =
                (mEncCfg.mFdkAACEncode ? PPAudioEncoder.EncodeMode.FDK_AAC : PPAudioEncoder.EncodeMode.SYSTEM);
        mAudioEncoder = new PPAudioEncoder(mode);
        mAudioEncoder.setOnDataListener(this);
        int sample_rate = AUDIO_SAMPLE_RATE;
        return mAudioEncoder.open(sample_rate, AUDIO_CHANNELS, AUDIO_BITRATE);
    }

    private boolean initAudioRecorder() {
        if (mAudioBuf == null)
            mAudioBuf = new byte[AUDIO_WEBRTC_BUF_SIZE];


        if (mAudioRec == null) {
            mAudioRec = new MyAudioRecorder();
            mAudioRec.setOnData(this);
        }

        int sample_rate = AUDIO_SAMPLE_RATE;
        int source = MyAudioRecorder.REC_SOURCE_MIC;

        if (!mAudioRec.open(source, sample_rate, AUDIO_CHANNELS, 0/*format*/)) {
            LogUtil.error(TAG, "failed to open audio recorder");
            return false;
        }
        return mAudioRec.start();
    }

    private void closeMuxer() {
        if (mMuxer != null) {
            mMuxer.nativeClose();
            mMuxer = null;
        }

        if (outSteamH264 != null) {
            try {
                outSteamH264.close();
                outSteamH264 = null;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private void closeAudioRecorder() {
        LogUtil.info(TAG, "Java: closeAudioRecorder()");


        if (mAudioRec != null) {
            mAudioRec.stop();
            mAudioRec = null;
        }
    }

    private void closeAudioEncoder() {
        LogUtil.info(TAG, "Java: closeAudioEncoder()");

        mAudioLock.lock();
        try {
            if (mAudioEncoder != null) {
                mAudioEncoder.close();
                mAudioEncoder = null;
            }
        } finally {
            mAudioLock.unlock();
        }

        mAudioLATM = null;
    }

    private void closeVideoEncoder() {
        LogUtil.info(TAG, "Java: closeVideoEncoder()");

        mVideoLock.lock();
        try {
            if (mVideoEncoder != null) {
                if (ENCODER_SET_MUXER)
                    mVideoEncoder.setMuxer(0L);
                mVideoEncoder.close();
                mVideoEncoder = null;
            }
            if (mVideoTextureEncoder != null) {
                mVideoTextureEncoder.shutdown();
                mVideoTextureEncoder = null;
            }
        } finally {
            mVideoLock.unlock();
        }

        mVideoSPSandPPS = null;
        LogUtil.info(TAG, "Java: video encoder closed");
    }




    /**
     * Draws a frame onto the SurfaceView and the encoder surface.
     * <p>
     * This will be called whenever we get a new preview frame from the camera.  This runs
     * on the UI thread, which ordinarily isn't a great idea -- you really want heavy work
     * to be on a different thread -- but we're really just throwing a few things at the GPU.
     * The upside is that we don't have to worry about managing state changes between threads.
     * <p>
     * If there was a pending frame available notification when we shut down, we might get
     * here after onPause().
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void drawFrame() {
        if (Constants.VERBOSE) LogUtil.debug(TAG, "Java: drawFrame()");
        if (mEglCore == null) {
            LogUtil.debug(TAG, "Skipping drawFrame after shutdown");
            return;
        }

        long start_drawFrame = System.currentTimeMillis();
        int texturedId = mTextureId;

        if (mDisplaySurface != null) {
            // Latch the next frame from the camera.
            mDisplaySurface.makeCurrent();
            if (mNewFilterType != mCurrentFilterType ||
                    mNewFilterParams != mCurrentFilterParams) {

                mFullFrameBlit.changeProgram(
                        FilterManager.getCameraFilter(mNewFilterType, mContext, mNewFilterParams));
                mCurrentFilterType = mNewFilterType;
                mCurrentFilterParams = mNewFilterParams;
                mInstantAvgSwapBuffersMsec = mAvgSwapBuffersMsec = 0;
                mInstantAvgDrawFrameMsec = mAvgDrawFrameMsec = 0;
            }
            mCameraTexture.updateTexImage();
            mCameraTexture.getTransformMatrix(mTmpMatrix);

            if ((mWatermarkBitmap != null || mWatermarkText != null) &&
                    mWatermarkFrame == null) {
                mWatermarkFrame = new FullFrameRect(
                        FilterManager.getImageFilter(FilterType.Normal, mContext));
                if (mWatermarkBitmap != null)
                    mWatermarkTextureId = mWatermarkFrame.createTexture(mWatermarkBitmap);
                else
                    mWatermarkTextureId = mWatermarkFrame.createTexture(mWatermarkText, mWatermarkTextSize);
                Matrix.setIdentityM(IDENTITY_MATRIX, 0);
            }

            GLES20.glClearColor(0f, 0f, 0f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (mWatermarkFrame != null) {
                GLES20.glEnable(GLES20.GL_BLEND);
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            }

            // Fill the SurfaceView with it.
            setCameraPreviewSize(mPreviewWidth, mPreviewHeight);
            mFullFrameBlit.getFilter().setSurfaceSize(mSurfaceWidth, mSurfaceHeight);
            mFullFrameBlit.getFilter().setTextureSize(mPreviewWidth, mPreviewHeight);
            setViewport(mSurfaceWidth, mSurfaceHeight);
            mFullFrameBlit.drawFrame(texturedId, mTmpMatrix);

            if (mbEnableColorPicker) {
                IntBuffer buffer = IntBuffer.allocate(1);
                GLES20.glReadPixels(mPreviewWidth / 2, mPreviewHeight / 2, 1, 1,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
                buffer.rewind();
                int val = buffer.get();
                int r = 0xff & val;
                int g = (0xff00 & val) >> 8;
                int b = (0xff0000 & val) >> 16;
                //LogUtil.info(TAG, String.format(Locale.US,
                //        "color: r: %d, g: %d, b: %d", r, g, b));
            }


            if (mWatermarkFrame != null) {
                float scaleX = (float) mWatermarkWidth / (float) mIncomingWidth * mMvpScaleX;
                float scaleY = (float) mWatermarkHeight / (float) mIncomingHeight * mMvpScaleY;
                float translateX = -(mMvpScaleX - scaleX) +
                        (float) mWatermarkLeft * 2f * mMvpScaleX / (float) mIncomingWidth;
                float translateY = mMvpScaleY - scaleY -
                        (float) mWatermarkTop * 2f * mMvpScaleY / (float) mIncomingHeight;
                /*LogUtil.info(TAG, String.format("scaleX %.3f, scaleY %.3f, " +
                        "translateX %.3f, translateY %.3f || " +
                        "mMvpScaleX %.3f, mMvpScaleY %.3f",
                        scaleX, scaleY, translateX, translateY, mMvpScaleX, mMvpScaleY));*/
                mWatermarkFrame.resetMVPMatrix();
                mWatermarkFrame.translateMVPMatrix(translateX, translateY);
                mWatermarkFrame.scaleMVPMatrix(scaleX, -scaleY);
                mWatermarkFrame.drawFrame(mWatermarkTextureId, IDENTITY_MATRIX);
            }
            drawExtra(mPrevFrameCount, mSurfaceWidth, mSurfaceHeight);
            long startMsec = System.currentTimeMillis();
            mDisplaySurface.swapBuffers();
            int used = (int) (System.currentTimeMillis() - startMsec);
            if (!mbRecording) {
                mInstantAvgSwapBuffersMsec = (mInstantAvgSwapBuffersMsec * 4 + used) / 5;
                if (mPrevFrameCount % 10 == 0)
                    mAvgSwapBuffersMsec = mInstantAvgSwapBuffersMsec;
            }

            mPrevFrameCount++;
            if (mPrevFrameCount % 10 == 0) {
                mPrevFps = (double) (mPrevFrameCount * 1000) /
                        (double) (System.currentTimeMillis() - mStartPreviewMsec);
            }

            // get texture
            /*
            if (mTexBuffer == null) {
                mTexBuffer = IntBuffer.allocate(mIncomingWidth * mIncomingHeight);
            }
            mFrameBufferObject.bindTexture(mBufferTexID);
            mFullFrameBlit.getFilter().setSurfaceSize(mIncomingWidth, mIncomingHeight);
            mFullFrameBlit.getFilter().setTextureSize(mPreviewWidth, mPreviewHeight);
            mFullFrameBlit.getFilter().setFrameBuffer(mFrameBufferObject.getFrameBufferId());
            mFullFrameBlit.resetMVPMatrix();
            mFullFrameBlit.scaleMVPMatrix(1f, -1f); // fix bitmap upside down
            setViewport(mIncomingWidth, mIncomingHeight);
            mFullFrameBlit.drawFrame(mTextureId, mTmpMatrix);
            //mFullFrameBlit.getFilter().setFrameBuffer(0); // reset to normal
            GLES20.glReadPixels(0, 0, mIncomingWidth, mIncomingHeight,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mTexBuffer);
            // Switch back to the default framebuffer.
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            */

            // take picture
            texturedPictureBitmap();
        }

        // Send it to the video encoder.
        if (mbRecording && !mbStopping) {
            boolean bEncode = true;
            if (mEncFrameCount > mEncCfg.mFrameRate) {
                long elapsed = System.currentTimeMillis() - mStartRecordMsec;
                double fps = (double) (mEncFrameCount * 1000) / (double) elapsed;
                // ONLY when preview is ON, can drop frame(not call swap() but just return)
                // NOT call swap() return will cause next onFrameAvailable never come again
                // when in background mode
                if (fps > mEncCfg.mFrameRate) {
                    if (mDisplaySurface != null) {
                        // ONLY foreground recording can drop, NOT call swap()
                        bEncode = false;
                    } else {
                        int pause_time = (int) (mEncFrameCount * 1000 / mEncCfg.mFrameRate - elapsed);
                        if (pause_time > 5) {
                            try {
                                Thread.sleep(pause_time);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                    }
                }
            }

            if (bEncode) {
                mEncoderSurface.makeCurrent();
                if (mDisplaySurface == null) {
                    // background encode mode
                    mCameraTexture.updateTexImage();
                    mCameraTexture.getTransformMatrix(mTmpMatrix);
                }
                if (mSurfaceTextureStartTime == 0) {
                    mSurfaceTextureStartTime = mCameraTexture.getTimestamp();
                    LogUtil.info(TAG, "Java: mSurfaceTextureStartTime set to " + mSurfaceTextureStartTime);
                }

                GLES20.glClearColor(0f, 0f, 0f, 1f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                if (mWatermarkFrame != null) {
                    GLES20.glEnable(GLES20.GL_BLEND);
                    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                }

                // fix rotate camera problem when recording
                // mEncWidth and mEncHeight won't change when recording
                // but mIncomingWidth and mIncomingHeight would
                mFullFrameBlit.getFilter().setSurfaceSize(mEncWidth, mEncHeight);
                mFullFrameBlit.getFilter().setTextureSize(mPreviewWidth, mPreviewHeight);
                // encode video NOT left-right mirrored
                mFullFrameBlit.resetMVPMatrix();
                mFullFrameBlit.scaleMVPMatrix(mbUseFrontCam ? -1f : 1f, 1f);
                setViewport(mEncWidth, mEncHeight);
                mFullFrameBlit.drawFrame(texturedId/*mTextureId*/, mTmpMatrix);


                if (mWatermarkFrame != null) {
                    float scaleX = (float) mWatermarkWidth / (float) mIncomingWidth;
                    float scaleY = (float) mWatermarkHeight / (float) mIncomingHeight;
                    float translateX = -(1f - scaleX) +
                            (float) mWatermarkLeft * 2f / (float) mIncomingWidth;
                    float translateY = 1f - scaleY -
                            (float) mWatermarkTop * 2f / (float) mIncomingHeight;
                    /*LogUtil.info(TAG, String.format("scaleX %.3f, scaleY %.3f, " +
                        "translateX %.3f, translateY %.3f",
                        scaleX, scaleY, translateX, translateY));*/
                    mWatermarkFrame.resetMVPMatrix();
                    mWatermarkFrame.translateMVPMatrix(translateX, translateY);
                    mWatermarkFrame.scaleMVPMatrix(scaleX, -scaleY);
                    mWatermarkFrame.drawFrame(mWatermarkTextureId, IDENTITY_MATRIX);
                }
                drawExtra(mEncFrameCount, mEncWidth, mEncHeight);

                mVideoTextureEncoder.frameAvailableSoon();
                mEncoderSurface.setPresentationTime(mCameraTexture.getTimestamp() - mSurfaceTextureStartTime);
                long encStartMsec = System.currentTimeMillis();
                mEncoderSurface.swapBuffers(); // long time operation
                int encUsed = (int) (System.currentTimeMillis() - encStartMsec);
                mInstantAvgSwapBuffersMsec = (mInstantAvgSwapBuffersMsec * 4 + encUsed) / 5;

                mEncFrameCount++;
            }

            update_stat();
        }

        int encUsed = (int) (System.currentTimeMillis() - start_drawFrame);
        mInstantAvgDrawFrameMsec = (mInstantAvgDrawFrameMsec * 4 + encUsed) / 5;
        if (!mbRecording && mPrevFrameCount % 10 == 0)
            mAvgDrawFrameMsec = mInstantAvgDrawFrameMsec;
    }



    private void setViewport(int w, int h) {
        // single filter need set view port by user
        // filter group will set view port by self
        if (mCurrentFilterType.isSingleFilter()) {
            GLES20.glViewport(0, 0, w, h);
        }
    }

    /**
     * Adds a bit of extra stuff to the display just to give it flavor.
     */
    private static void drawExtra(long frameNum, int width, int height) {
        // We "draw" with the scissor rect and clear calls.  Note this uses window coordinates.
        int val = (int) frameNum % 3;
        switch (val) {
            case 0:
                GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
                break;
            case 1:
                GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
                break;
            case 2:
                GLES20.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
                break;
        }

        int xpos = (int) (width * ((frameNum % 100) / 100.0f));
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(xpos, 0, width / 32, height / 32);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    private void update_stat() {
        mRecordDurationMsec = System.currentTimeMillis() - mStartRecordMsec;

        if (mEncFrameCount % 10 == 0) {
            long timestamp = mRecordDurationMsec * 1000;

            if (mAudioClockUsec > 0)
                mAVDiffMsec = (int) ((timestamp - mAudioClockUsec) / 1000);

            mEncFps = (double) (mEncFrameCount * 1000) / (double) mRecordDurationMsec;
            mAvgSwapBuffersMsec = mInstantAvgSwapBuffersMsec;
            mAvgDrawFrameMsec = mInstantAvgDrawFrameMsec;
        }
    }

    private Camera.PreviewCallback mOnPreviewCallback = new Camera.PreviewCallback() {

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            // TODO Auto-generated method stub
            //LogUtil.info(TAG, String.format(
            //        "Java: onPreviewFrame() #%d, data len %d, data %02x %02x",
            //        mPrevFrameCount, data.length, data[0], data[1]));

            if (mbTextureEncode) {

            } else {
                long duration = System.currentTimeMillis() - mStartPreviewMsec;
                double currFPS = mPrevFrameCount * 1000 / (double) duration;

                if (mPrevFrameCount > CAMERA_DEFAULT_FRAMERATE && currFPS > CAMERA_DEFAULT_FRAMERATE) {
                    if (CAMERA_BUFFER_LIST_SIZE > 0)
                        camera.addCallbackBuffer(data);
                    return;
                }

                mPrevFrameCount++;
                if (mPrevFrameCount % 10 == 0)
                    mPrevFps = (double) (mPrevFrameCount * 1000) / (double) (System.currentTimeMillis() - mStartPreviewMsec);

                if (mbRecording && !mbStopping) {
                    try {
                        long timestamp = (System.currentTimeMillis() - mStartRecordMsec) * 1000;
                        mVideoLock.lock();
                        if (mVideoEncoder != null) {
                            if (!mVideoEncoder.addFrame(data, timestamp)) {
                                LogUtil.error(TAG, "Java: failed to addFrame");
                                mMainHandler.sendMessage(
                                        mMainHandler.obtainMessage(MainHandler.MSG_FAIL_TO_WRITE_VIDEO_FRAME));
                            }
                        }
                    } finally {
                        mVideoLock.unlock();
                    }

                    mEncFrameCount++;
                    update_stat();
                }
            }

            if (CAMERA_BUFFER_LIST_SIZE > 0)
                camera.addCallbackBuffer(data);
        }
    };

    private void write_h264_data(byte[] data, int start, int byteCount) {
        try {
            int pos = 0;

            // x264 will add sps and pps with param b_repeat_headers=1
            if (!mEncCfg.mX264Encode) {
                //key frame   编码器生成关键帧时只有 00 00 00 01 65 没有pps sps， 要加上
                if ((data[start + 4] & 0x1f) == AVCNaluType.IDR && mVideoSPSandPPS != null) {
                    // arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
                    System.arraycopy(mVideoSPSandPPS, 0, mH264, 0, mVideoSPSandPPS.length); // copy sps and pps with start code

                    pos += mVideoSPSandPPS.length;
                    LogUtil.info(TAG, "Java: AVCNaluType.IDR add sps_pps");
                }
            }

            System.arraycopy(data, start, mH264, pos, byteCount); // copy pic nalu
            pos += byteCount;

            // write size
            if (mbWriteSize) {
                byte[] byte_size = Util.IntToByte(pos);
                outSteamH264.write(byte_size);
            }

            outSteamH264.write(mH264, 0, pos);
            if (Constants.VERBOSE)
                LogUtil.debug(TAG, String.format("Java: write h264 data len: %d, offset %d", pos, mFileOffset));
            mFileOffset += pos;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if (Constants.VERBOSE) LogUtil.debug(TAG, "frame available");
        mTextureEncHandler.sendEmptyMessage(TextureEncoderHandler.MSG_FRAME_AVAILABLE);
    }



    @Override
    public void handleMessage(final Message msg) {
        switch (msg.what) {
            case CameraHandler.SETUP_CAMERA: {
                final int width = msg.arg1;
                final int height = msg.arg2;
                LogUtil.info(TAG, String.format("Java: SETUP_CAMERA %d x %d", width, height));

                CameraController ins = CameraController.getInstance();
                // will set front-facing camera if mbUseFrontCam is true
                //检查是否支持前置摄像头
                boolean supportFrontFacingCamera = CameraHelper.checkSupportFrontFacingCamera();
                if (mbUseFrontCam & !supportFrontFacingCamera) { // modify use if NON front-cam exists
                    mbUseFrontCam = false;
                    LogUtil.warn(TAG, "NOT support front-facing camera, force use back-facing camera");
                }
                // !!!set back-facing camera HERE if mbUseFrontCam is false
                ins.setCameraIndex(mbUseFrontCam ? 1 : 0);
                if (mOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
                    boolean bOpened = ins.openCamera(
                            getContext().getApplicationContext(),
                            mOrientation);
                    if (bOpened) {
                        int camera_orientation = ins.getCameraOrientation();
                        if (mbUseFrontCam)
                            mDisplayRotation = (360 - mOrientation + camera_orientation) % 360;
                        else
                            mDisplayRotation = (mOrientation + camera_orientation) % 360;
                        LogUtil.info(TAG, "Java: setCameraRotation: " + mDisplayRotation);

                        mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                                CameraHandler.CONFIGURE_CAMERA, width, height));
                    } else {
                        LogUtil.error(TAG, "failed to openCamera");
                        if (mCameraListener != null) {
                            mCameraListener.onCameraOpenFailed(this, mbUseFrontCam, CAMERA_ERROR_FAIL_TO_OPEN);
                        }
                    }
                } else {
                    LogUtil.info(TAG, "mOrientation is ORIENTATION_UNKNOWN, waiting");
                }
            }
            break;
            case CameraHandler.CONFIGURE_CAMERA: {
                final int width = msg.arg1;
                final int height = msg.arg2;

                CameraController cameraController = CameraController.getInstance();
                Camera.Size previewSize = CameraHelper.getOptimalPreviewSize(
                        cameraController.getCameraParameters(),
                        width, height);
                if (previewSize != null) {
                    LogUtil.info(TAG, String.format("Java: previewSize %d x %d",
                            previewSize.width, previewSize.height));
                    mPreviewWidth = previewSize.width;
                    mPreviewHeight = previewSize.height;

//                    if (mVideoInterface != null) {
//                        int camera_orientation = cameraController.getCameraOrientation();
//                        int rotation;
//                        if (mbUseFrontCam)
//                            rotation = (360 - mOrientation + camera_orientation) % 360;
//                        else
//                            rotation = (mOrientation + camera_orientation) % 360;
//                        mVideoInterface.OnCameraParams(mPreviewWidth, mPreviewHeight, rotation);
//                    }

                    boolean setFpsRange = Util.readSettingsBoolean(mContext,
                            "set_camera_fps_range", true);
                    if (!cameraController.configureCameraParameters(previewSize, setFpsRange)) {
                        Util.writeSettingsBoolean(mContext, "set_camera_fps_range", false);

                        // try to set camera parameter without fps range
                        LogUtil.warn(TAG, "try to set camera parameter without fps range");
                        cameraController.openCamera(getContext().getApplicationContext(), mOrientation);
                        if (!cameraController.configureCameraParameters(previewSize, false))
                            throw new RuntimeException("failed to set camera parameter");
                    }
                    if (!mbTextureEncode)
                        mMainHandler.sendEmptyMessage(MainHandler.MSG_UPDATE_LAYOUT);

                    if (mCameraListener != null) {
                        mCameraListener.onCameraPreviewSizeChanged(this, mPreviewWidth, mPreviewHeight);
                    }

                    mCameraHandler.sendEmptyMessage(CameraHandler.START_CAMERA_PREVIEW);
                } else {
                    LogUtil.error(TAG, "failed to get previewSize");
                }
            }
            break;

            case CameraHandler.START_CAMERA_PREVIEW:
                // two mode all need this callback
                CameraController.getInstance().setCameraPreviewCallback(
                        mOnPreviewCallback, CAMERA_BUFFER_LIST_SIZE);
                if (mbTextureEncode) {
                    CameraController.getInstance().startCameraPreview(mCameraTexture);
                } else {
                    CameraController.getInstance().startCameraPreview(
                            getHolder());
                }

                // re-get orientaion HERE!
                // fix mismatch value when switch camera while opening rtmp stream
                setDisplayOrientation();

                // support switch camera when recording
                if (mbRecording && mEncCfg.mX264Encode && mEncCfg.mbRotate) {
                    int rotate;
                    int camera_orientation = CameraController.getInstance().getCameraOrientation();
                    if (mbUseFrontCam) {
                        //rotate = (mOrientation + 360 - camera_orientation) % 360;
                        rotate = (360 - mOrientation + camera_orientation) % 360;
                    } else {
                        rotate = (mOrientation + camera_orientation) % 360;
                    }
                    String option = String.format("rotate=%d", rotate);
                    mVideoEncoder.setEncoderOption(option);
                }

                resetPreviewData();
                mStartPreviewMsec = System.currentTimeMillis();

                if (mCameraListener != null) {
                    mCameraListener.onCameraOpen(this, mbUseFrontCam, mbCameraFirstOpen);
                }
                mbCameraFirstOpen = false;

                break;
            //case CameraHandler.STOP_CAMERA_PREVIEW:
            //    mBackgroundHandler.post(new Runnable() {
            //        @Override public void run() {
            //            CameraController.getInstance().stopCameraPreview();
            //        }
            //    });
            //    break;
            default:
                break;
        }
    }

    @Override
    public void onData(PPEncoder enc, byte[] data, int start, int byteCount, long timestamp/*usec*/) {
        // TODO Auto-generated method stub
        if (Constants.VERBOSE)
            LogUtil.debug(TAG, String.format("Java: Encoder.onData() start %d, byteCount %d, timestamp %d",
                    start, byteCount, timestamp));

        mTotalVideoBytes += byteCount;

        if (mEncFrameCount % 10 == 0) {
            long duration = System.currentTimeMillis() - mStartRecordMsec;
            mLatencyMsec = (int) (duration - timestamp / 1000);
            if (mEncFrameCount > mEncCfg.mFrameRate)
                mVideoKbps = (int) (mTotalVideoBytes * 8 / duration);
        }

        if (mbUseFFmpegMux && mMuxer != null) {
            long startMsec = System.currentTimeMillis();
            if (!mMuxer.nativeWriteFrame(true, data, start, byteCount, timestamp))
                mMainHandler.sendEmptyMessage(MainHandler.MSG_FAIL_TO_WRITE_VIDEO_FRAME);
            long usedMsec = System.currentTimeMillis() - startMsec;
            mAvgWriteFrameMsec = (int) ((mAvgWriteFrameMsec * 4 + usedMsec) / 5);
        } else {
            write_h264_data(data, start, byteCount);
        }
    }

    @Override
    public void onSpsPps(PPEncoder enc, byte[] data, int start, int byteCount) {
        // TODO Auto-generated method stub
        LogUtil.info(TAG, String.format("Java: onSpsPps start %d, byteCount %d", start, byteCount));

        if (mVideoSPSandPPS == null) {
            mVideoSPSandPPS = new byte[byteCount];
            System.arraycopy(data, start, mVideoSPSandPPS, 0, byteCount);

            if (mMuxer != null) {
                if (!mMuxer.nativeSetSpsAndPps(mVideoSPSandPPS)) {
                    mMainHandler.sendEmptyMessage(MainHandler.MSG_FAIL_TO_SET_VIDEO_PARAM);
                }
            }
        }
    }

    @Override
    public void OnAudioData(byte[] data, int start, int byteCount, long timestamp/*usec*/) {
        if (Constants.VERBOSE)
            LogUtil.debug(TAG, String.format("Java: Encoder.OnAudioData() start %d, byteCount %d, timestamp %d",
                    start, byteCount, timestamp));

        if (mMuxer != null) {
            // audio frame must write after sps_and_pps is set
            while (mVideoSPSandPPS == null) {
                LogUtil.info(TAG, "sps_and_pps is not set, waiting");

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (!mMuxer.nativeWriteFrame(false, data, start, byteCount, timestamp))
                mMainHandler.sendEmptyMessage(MainHandler.MSG_FAIL_TO_WRITE_AUDIO_FRAME);
        }
    }

    @Override
    public void OnLATMheader(byte[] data, int start, int byteCount) {
        // TODO Auto-generated method stub
        if (mAudioLATM == null) {
            mAudioLATM = new byte[byteCount];
            System.arraycopy(data, start, mAudioLATM, 0, byteCount);
        }
    }

    //private boolean once = true;
    //private FileOutputStream fos_wav;

    // audio player interface
    @Override
    public void onPlayerPCM(byte[] buf, int size, int timestamp/*msec*/) {
        //LogUtil.info(TAG, String.format(Locale.US,
        //        "audioplayer: onPlayerPCM() %d %d", size, timestamp));

        if (!mbRecording || mAudioTrackBuf == null)
            return;


        mAudioLock.lock();
        try {
            if (mAudioTrackBufWrite + size >= AUDIO_PLAYER_BUF_SIZE) {
                mAudioTrackBufWrite = mAudioTrackBufRead;
                LogUtil.warn(TAG, "audioplayer onPlayerPCM data overflow, do flush");
            }

            System.arraycopy(buf, 0, mAudioTrackBuf, mAudioTrackBufWrite, size);
            mAudioTrackBufWrite += size;
            mPlayerBufTimestamp = timestamp;
        } finally {
            mAudioLock.unlock();
        }
    }

    @Override
    public void onComplete() {
        LogUtil.info(TAG, "audio player complete");
    }

    @Override
    public void onEndofStream() {
        LogUtil.info(TAG, "audio player eof");
        mAudioLock.lock();
        try {
            mPlayerTimeOffset = -1;
            mPlayerBufTimestamp = 0;
            mAudioTrackBufRead = mAudioTrackBufWrite = 0;
        } finally {
            mAudioLock.unlock();
        }
    }
    // end of audio player interface

    // audio recorder interface

    /**
     * 获取的原声 音频数据
     *
     * @param data      音频data
     * @param start     该段数据对应的 时间
     * @param byteCount 数据大小
     * @param timestamp
     */
    @Override
    public void OnPCMData(byte[] data, int start, int byteCount, long timestamp) {
        // TODO Auto-generated method stub
        //LogUtil.debug(TAG, String.format("Java: raw audio pcm data, start %d, size %d", start, byteCount));

        mAudioLock.lock();
        try {
            // mix audio
            if (mbPlayingSong && mAudioTrackBuf != null) {
                if (mPlayerTimeOffset == -1) {
                    // > 0: audioplayer start earlier than recorder
                    // < 0: audioplayer start later than recorder
                    mPlayerTimeOffset = mAudioPlayer.getCurrentPosition() - (int) timestamp / 1000;
                    LogUtil.info(TAG, "audioplayer: mPlayerTimeOffset: " + mPlayerTimeOffset);
                }

                int left = mAudioTrackBufWrite - mAudioTrackBufRead;
                int buf_size = AUDIO_WEBRTC_BUF_SIZE;
                if (mbPlayingSong)
                    buf_size = AUDIO_PLAYER_BUF_SIZE;
                if (mAudioTrackBufWrite > buf_size * 3 / 4) {
                    if (left > buf_size * 3 / 4) {
                        LogUtil.warn(TAG, String.format(Locale.US,
                                "drop audio_track data: read_pos %d, write_pos %d, left %d",
                                mAudioTrackBufRead, mAudioTrackBufWrite, left));
                        left = buf_size * 3 / 4;
                    }
                    if (left > 0) {
                        byte[] tmp = new byte[left];
                        System.arraycopy(mAudioTrackBuf, mAudioTrackBufRead, tmp, 0, left);
                        System.arraycopy(tmp, 0, mAudioTrackBuf, 0, left);
                    }
                    mAudioTrackBufRead = 0;
                    mAudioTrackBufWrite = left;
                }

                boolean need_mix = false;
//                if (mbPeerConnected) {
//                    if (left >= byteCount)
//                        need_mix = true;
//                }
                if (mbPlayingSong) {
                    int player_msec = mPlayerBufTimestamp - mPlayerTimeOffset;
                    if (player_msec < 0)
                        player_msec = 0;
                    int recorder_msec = (int) (timestamp / 1000) - RECORD_LATENCY_MSEC;
                    int one_sec_size = AUDIO_SAMPLE_RATE * AUDIO_CHANNELS * 2/*s16*/;
                    int cache_msec = left * 1000 / one_sec_size;
                    int head_msec = player_msec - cache_msec;
                    if (head_msec < 0)
                        head_msec = 0;
                    //LogUtil.info(TAG, String.format(Locale.US,
                    //        "audioplayer: player %d(cache %d, tail %d), recorder %d",
                    //        head_msec, cache_msec, player_msec, recorder_msec));

                    if (recorder_msec - head_msec > MIX_THRESHOLD_MSEC) {
                        // skip some data
                        int skip_size = one_sec_size * (recorder_msec - head_msec) / 1000;
                        // make sure it's aligned to channel data
                        skip_size = (skip_size + 1) / 2 * 2;
                        if (skip_size > left)
                            skip_size = left;
                        mAudioTrackBufRead += skip_size;
                        left -= skip_size;

                        LogUtil.info(TAG, String.format(Locale.US,
                                "audioplayer: head_msec %d, delta %d, skip_size %d",
                                head_msec, recorder_msec - head_msec, skip_size));
                    }
                    if (head_msec - recorder_msec <= MIX_THRESHOLD_MSEC && left >= byteCount) {
                        need_mix = true;
                    } else {
                        LogUtil.warn(TAG, "audioplayer: fill mute, no mix");
                    }
                }
                if (need_mix) {
                    byte[] audiotrack_data = new byte[byteCount];
                    System.arraycopy(mAudioTrackBuf, mAudioTrackBufRead, audiotrack_data, 0, byteCount);
                    mAudioTrackBufRead += byteCount;

                    for (int i = 0; i < byteCount; i++) {

                        float samplef1 = data[start + i] * 1.4f /*voice input gain*/ / 128.0f;      //     2^7=128
                        float samplef2 = audiotrack_data[i] * mMixVolume / 128.0f;

                        float mixed = samplef1 + samplef2;
                        // reduce the volume a bit:
                        mixed *= 0.8f;
                        // hard clipping
                        if (mixed > 1.0f) mixed = 1.0f;
                        if (mixed < -1.0f) mixed = -1.0f;
                        byte outputSample = (byte) (mixed * 128.0f);
                        data[start + i] = outputSample;
                    }
                } else {
                    LogUtil.info(TAG, String.format(Locale.US,
                            "wait for enough audio track data %d.%d",
                            left, byteCount));
                }
            }

            if (mAudioEncoder != null) {
                mAudioClockUsec = timestamp;
                if (!mAudioEncoder.addAudioData(data, start, byteCount, timestamp))
                    LogUtil.error(TAG, "Java: failed to encode audio data");
            }

//            if (mbPeerConnected) {
//                if (mAudioBufWrite > AUDIO_WEBRTC_BUF_SIZE * 3 / 4) {
//                    int left = mAudioBufWrite - mAudioBufRead;
//                    if (left > AUDIO_WEBRTC_BUF_SIZE / 2) {
//                        LogUtil.warn(TAG, String.format(Locale.US,
//                                "drop audio data: read_pos %d, write_pos %d, left %d(%d)",
//                                mAudioBufRead, mAudioBufWrite, left, AUDIO_WEBRTC_BUF_SIZE / 2));
//                        left = AUDIO_WEBRTC_BUF_SIZE / 2;
//                    }
//                    if (left > 0) {
//                        byte []tmp = new byte[left];
//                        System.arraycopy(mAudioBuf, mAudioBufRead, tmp, 0, left);
//                        System.arraycopy(tmp, 0, mAudioBuf, 0, left);
//                    }
//                    mAudioBufRead = 0;
//                    mAudioBufWrite = left;
//                }
//
//                System.arraycopy(data, start, mAudioBuf, mAudioBufWrite, byteCount);
//                mAudioBufWrite += byteCount;
//            }
        } finally {
            mAudioLock.unlock();
        }
    }
    // end of audio recorder interface

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        LogUtil.info(TAG, "Java: surfaceCreated()");
        mbSurfaceCreated = true;

        if (mbTextureEncode) {

            // Set up everything that requires an EGL context.
            //
            // We had to wait until we had a surface because you can't make an EGL context current
            // without one, and creating a temporary 1x1 pbuffer is a waste of time.
            //
            // The display surface that we use for the SurfaceView, and the encoder surface we
            // use for video, use the same EGL context.
            if (mEglCore == null) {
                mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
            }
            mDisplaySurface = new WindowSurface(mEglCore, this.getHolder().getSurface(), false);
            mDisplaySurface.makeCurrent();

            if (mFullFrameBlit == null) {
                mFullFrameBlit = new FullFrameRect(
                        FilterManager.getCameraFilter(mCurrentFilterType, mContext));
                mTextureId = mFullFrameBlit.createTextureObject();
                //mTextureId = GlUtil.createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
                mCameraTexture = new SurfaceTexture(mTextureId);
                mCameraTexture.setOnFrameAvailableListener(this);

                Matrix.setIdentityM(mTmpMatrix, 0);
            }
//
//            if (mVideoInterface != null)
//                mVideoInterface.onRemoteRendererInit();

            // get texture
            /*
            mFrameBufferObject = new FrameBufferObject();
            mBufferTexID = GlUtil.createTexture(GLES20.GL_TEXTURE_2D, GLES20.GL_RGBA,
                    null, 480, 640);
            */
        }

        if (mbSetupWhenStart) {
            mCameraTexture = new SurfaceTexture(mTextureId);
            mCameraTexture.setOnFrameAvailableListener(this);

            if (mCameraHandler != null) {
                mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                        CameraHandler.SETUP_CAMERA));
            }

            mbSetupWhenStart = false;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        LogUtil.info(TAG, "Java: surfaceChanged() " + width + " x " + height);

        mSurfaceWidth = width;
        mSurfaceHeight = height;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        LogUtil.info(TAG, "Java: surfaceDestroyed()");

        mbSetupWhenStart = true;
        mbSurfaceCreated = false;
    }



























//----------------                 拍照的相关代码---------------------------------------------------
    private Camera.ShutterCallback mShutterCallback = new Camera.ShutterCallback() {
        public void onShutter() {
            /* 按下快门瞬间会调用这里的程序 */
            LogUtil.info(TAG, "Java: onShutter()");
        }
    };


    //在takepicture中调用的回调方法之一，接收jpeg格式的图像
    private Camera.PictureCallback mJpegCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] jpeg, Camera camera) {
            LogUtil.info(TAG, "Java: onPictureTaken() jpeg, size " + jpeg.length);

            if (mbTextureEncode) {
                mTakePictureBitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                LogUtil.info(TAG, String.format(Locale.US,
                        "picture bitmap info: %d x %d",
                        mTakePictureBitmap.getWidth(), mTakePictureBitmap.getHeight()));
            } else {
                new SavePictureTask().execute(jpeg);
            }

            camera.startPreview();
        }
    };

    // 保存至手机卡 中
    private class SavePictureTask extends AsyncTask<byte[], String, String> {

        @Override
        protected void onPostExecute(String s) {
            if (mTakePictureCallback != null) {
                if (s != null)
                    mTakePictureCallback.takePictureOK(s);
                else
                    mTakePictureCallback.takePictureError(-100);
            }
        }

        @Override
        protected String doInBackground(byte[]... params) {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String str_time = format.format((new Date()));
            String file_path = mSaveFolder + "/" + str_time + "_hid.jpg";
            try {
                FileOutputStream fos = new FileOutputStream(file_path);
                fos.write(params[0]);
                fos.close();
                return file_path;
            } catch (Exception e) {
                e.printStackTrace();
                LogUtil.error(TAG, e.getMessage());
            }

            return null;
        }

        //BMP文件头
        private byte[] addBMPImageHeader(int size) {
            byte[] buffer = new byte[14];
            buffer[0] = 0x42;
            buffer[1] = 0x4D;
            buffer[2] = (byte) (size >> 0);
            buffer[3] = (byte) (size >> 8);
            buffer[4] = (byte) (size >> 16);
            buffer[5] = (byte) (size >> 24);
            buffer[6] = 0x00;
            buffer[7] = 0x00;
            buffer[8] = 0x00;
            buffer[9] = 0x00;
            buffer[10] = 0x36;
            buffer[11] = 0x00;
            buffer[12] = 0x00;
            buffer[13] = 0x00;
            return buffer;
        }

        //BMP文件信息头
        private byte[] addBMPImageInfosHeader(int w, int h) {
            byte[] buffer = new byte[40];
            buffer[0] = 0x28;
            buffer[1] = 0x00;
            buffer[2] = 0x00;
            buffer[3] = 0x00;
            buffer[4] = (byte) (w >> 0);
            buffer[5] = (byte) (w >> 8);
            buffer[6] = (byte) (w >> 16);
            buffer[7] = (byte) (w >> 24);
            buffer[8] = (byte) (h >> 0);
            buffer[9] = (byte) (h >> 8);
            buffer[10] = (byte) (h >> 16);
            buffer[11] = (byte) (h >> 24);
            buffer[12] = 0x01;
            buffer[13] = 0x00;
            buffer[14] = 0x18;
            buffer[15] = 0x00;
            buffer[16] = 0x00;
            buffer[17] = 0x00;
            buffer[18] = 0x00;
            buffer[19] = 0x00;
            buffer[20] = 0x00;
            buffer[21] = 0x00;
            buffer[22] = 0x00;
            buffer[23] = 0x00;
            buffer[24] = (byte) 0xE0;
            buffer[25] = 0x01;
            buffer[26] = 0x00;
            buffer[27] = 0x00;
            buffer[28] = 0x02;
            buffer[29] = 0x03;
            buffer[30] = 0x00;
            buffer[31] = 0x00;
            buffer[32] = 0x00;
            buffer[33] = 0x00;
            buffer[34] = 0x00;
            buffer[35] = 0x00;
            buffer[36] = 0x00;
            buffer[37] = 0x00;
            buffer[38] = 0x00;
            buffer[39] = 0x00;
            return buffer;
        }

        private byte[] addBMP_RGB_888(int[] b, int w, int h) {
            int len = b.length;
            System.out.println(b.length);
            byte[] buffer = new byte[w * h * 3];
            int offset = 0;
            for (int i = len - 1; i >= 0; i -= w) {
                //DIB文件格式最后一行为第一行，每行按从左到右顺序
                int end = i, start = i - w + 1;
                for (int j = start; j <= end; j++) {
                    buffer[offset] = (byte) (b[j] >> 0);
                    buffer[offset + 1] = (byte) (b[j] >> 8);
                    buffer[offset + 2] = (byte) (b[j] >> 16);
                    offset += 3;
                }
            }
            return buffer;
        }
    }

    /**
     * 如果有拍照的信息，将纹理模式的效果运用的bitmap上
     */
    private void texturedPictureBitmap() {
        if (mTakePictureBitmap != null) {
            int pictureTextureId;
            int bufferTexID;
            FrameBufferObject frameBufferObject = new FrameBufferObject();
            IntBuffer buffer;
            Bitmap bmp;
            int pic_width, pic_height;

            pic_width = mTakePictureBitmap.getWidth();
            pic_height = mTakePictureBitmap.getHeight();

            pictureTextureId = GlUtil.createTexture(
                    GLES20.GL_TEXTURE_2D, mTakePictureBitmap);

            bufferTexID = GlUtil.createTexture(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_RGBA,
                    null, pic_width, pic_height);
            frameBufferObject.bindTexture(bufferTexID);

            buffer = IntBuffer.allocate(pic_width * pic_height);
            mFullFrameBlit.changeProgram(
                    FilterManager.getImageFilter(mNewFilterType, mContext, mNewFilterParams));
            mFullFrameBlit.getFilter().setSurfaceSize(pic_width, pic_height);
            mFullFrameBlit.getFilter().setTextureSize(pic_width, pic_height);
            // note: if setFrameBuffer, setSurfaceSize is useless
            // but need a mechanism to notify output size
            mFullFrameBlit.getFilter().setFrameBuffer(frameBufferObject.getFrameBufferId());
            mFullFrameBlit.getFilter().setOutput(true);
            mFullFrameBlit.resetMVPMatrix();
            mFullFrameBlit.scaleMVPMatrix(1f, 1f);
            setViewport(pic_width, pic_height);
            mFullFrameBlit.drawFrame(pictureTextureId, IDENTITY_MATRIX);
            mFullFrameBlit.getFilter().setFrameBuffer(0); // reset to normal
            mFullFrameBlit.getFilter().setOutput(false);
            mFullFrameBlit.changeProgram(
                    FilterManager.getCameraFilter(mNewFilterType, mContext, mNewFilterParams));
            GLES20.glReadPixels(0, 0, pic_width, pic_height,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
            bmp = Bitmap.createBitmap(pic_width, pic_height, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buffer);
            LogUtil.info(TAG, String.format(Locale.US, "Java: bitmap info %d x %d",
                    bmp.getWidth(), bmp.getHeight()));
            frameBufferObject.release();

            if (mTakePictureCallback != null) {
                SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
                String str_time = format.format((new Date()));

                String file_path = mSaveFolder + "/" + str_time + "_hid.jpg";
                ImageUtil.saveBitmap(bmp, file_path);
                bmp.recycle();
                mTakePictureCallback.takePictureOK(file_path);
            }

            mTakePictureBitmap.recycle();
            mTakePictureBitmap = null;
        }
    }
//----------------                 拍照的相关代码  end----------------------------------------------

}
