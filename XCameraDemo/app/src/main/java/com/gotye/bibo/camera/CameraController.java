package com.gotye.bibo.camera;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;

import com.gotye.bibo.util.LogUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CameraController
        implements Camera.AutoFocusCallback, Camera.ErrorCallback,
        CommonHandlerListener {

    private static final String TAG = "CameraController";

    public static final String BROADCAST_ACTION_OPEN_CAMERA_ERROR =
            "CameraController.BROADCAST_ACTION_OPEN_CAMERA_ERROR";

    public static final String TYPE_OPEN_CAMERA_ERROR_TYPE =
            "CameraController.TYPE_OPEN_CAMERA_ERROR_TYPE";

    public static final int TYPE_OPEN_CAMERA_ERROR_UNKNOWN = 0;
    public static final int TYPE_OPEN_CAMERA_ERROR_PERMISSION_DISABLE = 1;

    private static volatile CameraController sInstance;

    public final static float sCameraRatio = 4f / 3f;

    private Camera mCamera = null;
    public int mCameraIndex = Camera.CameraInfo.CAMERA_FACING_BACK; //前置 或者后置摄像头
    public boolean mCameraMirrored = false; //表示是否是前置摄像，即可以看点自己
    public int mOrientation = 0;
    public Camera.Size mCameraPictureSize;// 拍照的尺寸

    private final Object mLock = new Object();

    //////////
    private boolean mAutoFocusLocked = false;
    private boolean mIsSupportAutoFocus = false;
    private boolean mIsSupportAutoFocusContinuousVideo = false;
    private boolean mIsSupportAutoFocusContinuousPicture = false;

    private int DEFAULT_PICTURE_WIDTH = 1920;
    private int DEFAULT_PICTURE_HEIGHT = 1080;
    private final CameraControllerHandler mHandler;

    //////////
    public static CameraController getInstance() {
        if (sInstance == null) {
            synchronized (CameraController.class) {
                if (sInstance == null) {
                    sInstance = new CameraController();
                }
            }
        }
        return sInstance;
    }

    private CameraController() {
        mHandler = new CameraControllerHandler(this);
    }

    /**
     * 摄像头 方向
     *
     * @return
     */
    public int getDisplayOrientation() {
        return mOrientation;
    }

    /**
     * 配置Camera
     *
     * @param context
     * @param orientation          角度
     * @return
     */
    public boolean openCamera(Context context,int orientation) {
        if (mCamera != null) {
            release();
        }
        synchronized (mLock) {
            try {
                if (Camera.getNumberOfCameras() > 0) {
                    mCamera = Camera.open(mCameraIndex);
                    if (mCamera == null){
                        mCamera = Camera.open();
                    }
                } else {
                    mCamera = Camera.open();
                }
                if (mCamera == null){
                    LogUtil.error(TAG,"无法打开照相机");
                    return false;
                }
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                Camera.getCameraInfo(mCameraIndex, cameraInfo);

                mCameraMirrored = (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
                LogUtil.info(TAG, "Java: cameraInfo.orientation " + cameraInfo.orientation);
                if (mCameraMirrored) {
                    mOrientation = (orientation + 360 - cameraInfo.orientation) % 360;
                    LogUtil.info(TAG, "mOrientation is " + mOrientation);
                } else {
                    mOrientation = (orientation + cameraInfo.orientation) % 360;
                    LogUtil.info(TAG, "mOrientation is " + mOrientation);
                }
                LogUtil.info(TAG, "Java: setDisplayOrientation " + mOrientation);
                mCamera.setDisplayOrientation(mOrientation);
            } catch (Exception e) {
                e.printStackTrace();
                if (mCamera != null)
                    mCamera.release();
                mCamera = null;
                Intent intent = new Intent(BROADCAST_ACTION_OPEN_CAMERA_ERROR);
                String message = e.getMessage();
                intent.putExtra(TYPE_OPEN_CAMERA_ERROR_TYPE,
                        (!TextUtils.isEmpty(message) && message.contains("permission"))
                                ? TYPE_OPEN_CAMERA_ERROR_PERMISSION_DISABLE
                                : TYPE_OPEN_CAMERA_ERROR_UNKNOWN);
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            }

            if (mCamera == null) {
                LogUtil.error(TAG, "case there, unable init camear");
                //Toast.makeText(mContext, "Unable to start camera", Toast.LENGTH_SHORT).showFromSession();
                return false;
            }

            try {
                findCameraSupportValue(DEFAULT_PICTURE_WIDTH, DEFAULT_PICTURE_HEIGHT);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }

            return false;
        }
    }

    public boolean configureCameraParameters(Camera.Size previewSize, boolean setFpsRange) {
        try {
            Camera.Parameters cp = getCameraParameters();
            if (cp == null || mCamera == null) {
                LogUtil.error(TAG, "failed to get camera parameters");
                return false;
            }
            // 对焦模式
            synchronized (mLock) {
                List<String> focusModes = cp.getSupportedFocusModes();
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    mIsSupportAutoFocusContinuousPicture = true;
                    cp.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);// 自动连续对焦
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) { //有视频对焦 用视频啊
                    mIsSupportAutoFocusContinuousVideo = true;
                    cp.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    mIsSupportAutoFocus = true;
                    cp.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);// 自动对焦
                } else {
                    mIsSupportAutoFocusContinuousPicture = false;
                    mIsSupportAutoFocus = false;
                }
                // 预览尺寸
                if (previewSize != null) {
                    cp.setPreviewSize(previewSize.width, previewSize.height);
                } else {
                    LogUtil.warn(TAG, "Java: previewSize is null");
                }

                //拍照尺寸
                cp.setPictureSize(mCameraPictureSize.width, mCameraPictureSize.height);

                List<Integer> picFormatList = cp.getSupportedPictureFormats();
                for (int i = 0; i < picFormatList.size(); i++) {
                    LogUtil.info(TAG, String.format(Locale.US,
                            "supported picture format #%d: %d", i, picFormatList.get(i)));
                }

                // black list
                // cannot set fps range (15, 15)
                // xiaomi "MI pad"

                // Huawei "PLK-TL01H" range(14000, 25000)

                // Coolpad 5890 supported preview range 5000 - 15000

                // 昂达 V819i
                // supported preview range 10500 - 30304
                // supported preview range 11000 - 30304
                // supported preview range 11500 - 30304

                // HUAWEI C8812 supported preview range 5000 - 31000

                // 华硕 MeMO Pad FHD 10(ME302C)
                // Device model: ME302C
                // supported preview range 10500 - 60304
                // supported preview range 11000 - 60304
                // supported preview range 11500 - 60304

                // Coolpad 5891Q NEED VERIFY

                // 联想 Idea Pad(K2110A)
                // IdeaTab K2110A-F
                // supported preview range 8000 - 30000
                // supported preview range 8500 - 30000
                // supported preview range 10000 - 30000

                // 联想 K80M
                // Lenovo K80M
                // supported preview range 10500 - 30304
                // supported preview range 11000 - 30304
                // supported preview range 11500 - 30304

                // 联想 A5000-E
                // IdeaTabA5000-E
                // supported preview range 2000 - 30000

                // 戴尔 Venue 8 3830
                // Venue 8 3830
                // supported preview range 10500 - 30304
                // supported preview range 11000 - 30304
                // supported preview range 11500 - 30304

                // 台电 X98 3G(HKC1)
                // X98 3G(HKC1)

                // ZTE N909
                // HUAWEI C8812E
                // HONOR H30-L01
                // HUAWEI MT2-L05

                final String[] deviceList = new String[]{
                        "MI PAD", "PLK-TL01H", "MI 3", "Coolpad 5890", "V819i",
                        "HUAWEI C8812", "ME302C", "Coolpad 5891Q", "Coolpad 5891",
                        "IdeaTab K2110A-F", "Lenovo K80M", "IdeaTabA5000-E",
                        "Venue 8 3830", "X98 3G(HKC1)", "ZTE N909", "HUAWEI C8812E",
                        "HONOR H30-L01", "HUAWEI MT2-L05"};
                boolean setRange = setFpsRange;
                LogUtil.info(TAG, "Device model: " + Build.MODEL);
                if (setRange) {
                    for (int i = 0; i < deviceList.length; i++) {
                        if (deviceList[i].equals(Build.MODEL)) {
                            setRange = false;
                            LogUtil.warn(TAG, "DISABLE set preview fps range");
                            break;
                        }
                    }
                }

                List<int[]> range = cp.getSupportedPreviewFpsRange();
                for (int[] r : range) {
                    LogUtil.info(TAG, String.format(Locale.US,
                            "Java: supported preview range %d - %d", r[0], r[1]));
                }


                //我重新设置 fps，我想取最大  by zhuoxiuwu
                //todo 我可以设置一个表示 帧数策略，来让外部选择 by zhuoxiuwu
                if (setRange){
                    ArrayList<int[] > maxRange= new ArrayList<>();
                    for (int[] r:range){
                        if (maxRange.size() == 0){
                            maxRange.add(r);
                         } else {
                            if (r[1]>maxRange.get(0)[1]){
                                maxRange.clear();
                                maxRange.add(r);
                            } else if (r[1]>maxRange.get(0)[1]){
                                maxRange.add(r);
                            }
                        }
                    }
                    int[] iwannaSetRange ={0,0};
                    if (maxRange.size() > 1){
                        for(int[] r : maxRange){
                            if (r[0]>iwannaSetRange[0]){
                                iwannaSetRange = r;
                            }
                        }
                    }
                    cp.setPreviewFpsRange(iwannaSetRange[0],iwannaSetRange[1]);
                }
                int rotation;
                if (isFrontCamera()) {
                    rotation = (360 - mOrientation) % 360;
                } else {  // back-facing camera
                    rotation = mOrientation;
                }
                cp.setRotation(rotation);

                mCamera.setParameters(cp);
                mCamera.setErrorCallback(this);
            }
        } catch (Exception e) {
            e.printStackTrace();
            LogUtil.error(TAG, "failed to set camera parameter: " + e.toString());
            return false;
        }

        mAutoFocusLocked = false;
        return true;
    }

    /**
     * Android 4.0 以上才支持纹理模式  才有滤镜的功能
     * @param surfaceTexture 纹理holder
     * @return 是否启动成功
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public boolean startCameraPreview(SurfaceTexture surfaceTexture) {
        LogUtil.info(TAG, "Java: startCameraPreview()");

        if (mCamera != null) {
            synchronized (mLock) {
                try {
                    mCamera.setPreviewTexture(surfaceTexture);
                    mCamera.startPreview();

                    if (mIsSupportAutoFocusContinuousPicture) {
                        mCamera.cancelAutoFocus();
                    }
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return false;
    }

    public boolean startCameraPreview(SurfaceHolder holder) {
        LogUtil.info(TAG, "Java: startCameraPreview()");

        if (mCamera != null) {
            synchronized (mLock) {
                try {
                    mCamera.setPreviewDisplay(holder);
                    mCamera.startPreview();

                    if (mIsSupportAutoFocusContinuousPicture) {
                        mCamera.cancelAutoFocus();
                    }
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return false;
    }

    /**
     *
     * @param callback preview 数据的回调接口
     * @param bufCount 给camera callback  设置的callbackbuffer 数量，作为一个显示的缓存
     * @return
     */
    public boolean setCameraPreviewCallback(Camera.PreviewCallback callback, int bufCount) {
        LogUtil.info(TAG, "Java: setCameraPreviewCallback()");

        if (mCamera != null) {
            synchronized (mLock) {
                try {
                    if (bufCount > 0) {
                        int prev_fmt = mCamera.getParameters().getPreviewFormat();
                        int bpp = ImageFormat.getBitsPerPixel(prev_fmt);
                        Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
                        int size = previewSize.width * previewSize.height * bpp / 8;
                        // nv21-17, YUY2-20
                        LogUtil.info(TAG, String.format(Locale.US,
                                "Java: camera preview format %d, bpp %d, size %d",
                                prev_fmt, bpp, size));
                        for (int i = 0; i < bufCount; i++) {
                            byte[] cameraBuffer = new byte[size];
                            mCamera.addCallbackBuffer(cameraBuffer);
                        }
                    }

                    mCamera.setPreviewCallbackWithBuffer(callback);
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return false;
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void setFaceDetectionListener(Camera.FaceDetectionListener listener) {
        LogUtil.info(TAG, "Java: setFaceDetectionListener()");

        if (mCamera != null) {
            synchronized (mLock) {
                try {
                    mCamera.setFaceDetectionListener(listener);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 取消预览
     * @return
     */
    public boolean stopCameraPreview() {
        LogUtil.info(TAG, "Java: stopCameraPreview()");
        if (mCamera != null) {
            synchronized (mLock) {
                try {
                    mCamera.stopPreview();
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return false;
    }

    /**
     * 释放 camera 资源
     */
    public void release() {
        if (mCamera != null) {
            synchronized (mLock) {
                try {
                    mCamera.setPreviewCallback(null);
                    mCamera.stopPreview();
                    mCamera.release();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    mCamera = null;
                }
            }
        }
    }

    //todo 改名字
    public void enableTorch(boolean enable) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            boolean supportTorch = false;
            List<String> flash_modes = parameters.getSupportedFlashModes();
            if (flash_modes != null && flash_modes.toString().contains(Camera.Parameters.FLASH_MODE_TORCH))
                supportTorch = true;

            if (enable) {
                if (supportTorch) {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                } else {//fuck it i just wanna try it
                    LogUtil.warn(TAG, "camera NOT support FLASH_MODE_TORCH");
                    LogUtil.warn(TAG, "虽然不支持闪光灯，但是我还是想试一下! 结果呢");
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    return;
                }
            } else {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            }

            mCamera.setParameters(parameters);
        }
    }

    public boolean isTorchOn() {
        return CameraHelper.isTouchOn(mCamera);
    }


    // todo 做一个视频的连续对焦 by zhuoxiuwu
    public boolean startAutoFocusForVideo(Camera.AutoFocusCallback autoFocusCallback){
        return true;
    }
    public boolean startAutoFocus(Camera.AutoFocusCallback autoFocusCallback) {
        if ((mIsSupportAutoFocus || mIsSupportAutoFocusContinuousPicture) && mCamera != null) {
            try {
                String focusMode = getCameraParameters().getFocusMode();

                if (!TextUtils.isEmpty(focusMode) && focusMode.
                        equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {  // 如果是连续自动对焦, 来一次对焦处理
                    mCamera.autoFocus(autoFocusCallback);
                } else {
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }

        return false;
    }

    public void startTouchAutoFocus(View v, MotionEvent event,
                                    Camera.AutoFocusCallback autoFocusCallback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)//api 14 以下不支持?
            return;
        if ((mIsSupportAutoFocus || mIsSupportAutoFocusContinuousPicture)
                && mCamera != null
                && !mAutoFocusLocked) {
            try {
                mAutoFocusLocked = true;

                Camera.Parameters parameters = getCameraParameters();
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                if (parameters.getMaxNumFocusAreas() > 0) {
                    //获得点击的区域
                    Rect focusRect =
                            CameraHelper.calculateTapArea(v, event.getX(), event.getY(), 1f);
                    List<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
                    focusAreas.add(new Camera.Area(focusRect, 1000));
                    parameters.setFocusAreas(focusAreas);//聚焦的区域
                }

                if (parameters.getMaxNumMeteringAreas() > 0) {//getMaxNumMeteringAreas() return maximum length of the list in {@link #setMeteringAreas(List)}
                    Rect meteringRect =
                            CameraHelper.calculateTapArea(v, event.getX(), event.getY(), 1.5f);
                    List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
                    meteringAreas.add(new Camera.Area(meteringRect, 1000));
                    parameters.setMeteringAreas(meteringAreas);
                }

                mCamera.setParameters(parameters);
                mCamera.autoFocus(autoFocusCallback);
            } catch (Exception e) {
                e.printStackTrace();
                mAutoFocusLocked = false;
            }
        }
    }

    public Camera.Parameters getCameraParameters() {
        if (mCamera != null) {
            synchronized (mLock) {
                try {
                    return mCamera.getParameters();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }

    private void findCameraSupportValue(int desiredWidth, int desiredHeight) {
        Camera.Parameters cp = getCameraParameters();
        List<Camera.Size> cs = cp.getSupportedPictureSizes();
        if (cs != null && !cs.isEmpty()) {
            Collections.sort(cs, new CameraPictureSizeComparator());
            for (Camera.Size size : cs) {
                if (size.width <= desiredWidth && size.height <= desiredHeight) {
                    if (size.height>1920|| size.width>1080) //不对1920 x1080以上的做支持，因为在测试总发现在1920想080以上会奔溃
                        continue;
                    mCameraPictureSize = size;
                    LogUtil.info(TAG, String.format(Locale.US,
                            "picture size set to %d x %d",
                            size.width, size.height));
                    break;
                }
            }
        }
    }

    /**
     * 拍张照
     *
     * @param shutter 回调
     * @param raw
     * @param jpeg
     */
    public void takePicture(Camera.ShutterCallback shutter, Camera.PictureCallback raw,
                            Camera.PictureCallback jpeg) {
        if (mCamera != null) {
            mCamera.takePicture(shutter, raw, jpeg);
        }
    }

    public Camera.Size getPictureSize() {
        return mCameraPictureSize;
    }

    public void setZoomIn() {
        setZoomInternal(true);
    }

    public void setZoomOut() {
        setZoomInternal(false);
    }

    /**
     * 设置 zoom
     *
     * @param bZoomIn
     */
    public void setZoomInternal(boolean bZoomIn) {
        try {
            Camera.Parameters cp = getCameraParameters();
            if (!cp.isZoomSupported()) {
                LogUtil.warn(TAG, "camera NOT support zoom in");
                return;
            }

            final int MAX = cp.getMaxZoom();
            if (MAX == 0)
                return;

            int zoomValue = cp.getZoom();

            zoomValue += (bZoomIn ? 2 : -2);
            if (zoomValue > 0 && zoomValue < MAX) {
                cp.setZoom(zoomValue);
                mCamera.setParameters(cp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //////////////////// implements ////////////////////

    //AutoFocusCallback
    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        LogUtil.info(TAG, "onAutoFocus " + success);
        mHandler.sendEmptyMessageDelayed(RESET_TOUCH_FOCUS, RESET_TOUCH_FOCUS_DELAY);

        mAutoFocusLocked = false;
    }

    //ErrorCallback
    @Override
    public void onError(int error, Camera camera) {
        LogUtil.error(TAG, "Camera onError(): " + error);
    }

    public Camera getCamera() {
        return mCamera;
    }


    public boolean isFrontCamera() {
        return mCameraMirrored;
    }

    public int getCameraOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(this.mCameraIndex, info);
        return info.orientation;
    }

    /**
     * 设置摄像头
     *
     * @param cameraIndex 前置或者后置
     */
    public void setCameraIndex(int cameraIndex) {
        this.mCameraIndex = cameraIndex;
    }

    private static final int RESET_TOUCH_FOCUS = 301;
    private static final int RESET_TOUCH_FOCUS_DELAY = 3000;

    private static class CameraControllerHandler extends Handler {

        private CommonHandlerListener listener;

        public CameraControllerHandler(CommonHandlerListener listener) {
            super(Looper.getMainLooper());
            this.listener = listener;
        }

        @Override
        public void handleMessage(Message msg) {
            listener.handleMessage(msg);
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case RESET_TOUCH_FOCUS: {
                if (mCamera == null || mAutoFocusLocked) {
                    return;
                }
                mHandler.removeMessages(RESET_TOUCH_FOCUS);
                try {
                    if (mIsSupportAutoFocusContinuousPicture) {
                        Camera.Parameters cp = getCameraParameters();
                        if (cp != null) {
                            //todo 还有一个是视频的聚焦，这里应该做些区分
                            cp.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);//auto focous for take picture
                            mCamera.setParameters(cp);
                        }
                    }
                    mCamera.cancelAutoFocus();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                break;
            }
        }
    }
}

