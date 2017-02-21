package com.gotye.bibo.camera;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.view.View;

import com.gotye.bibo.camera.blacklist.BlackListHelper;
import com.gotye.bibo.camera.exception.CameraDisabledException;
import com.gotye.bibo.camera.exception.CameraNotSupportException;
import com.gotye.bibo.camera.exception.NoCameraException;
import com.gotye.bibo.util.LogUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CameraHelper {

    private static final String TAG = "CameraHelper";

    // new
    public static Camera.Size getBestPreviewSize(Camera.Parameters parameters,
                                                 int viewWidth, int viewHeight) {
        if (parameters == null) {
            LogUtil.error(TAG, "Camera.parameters is null");
            return null;
        }

        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();

        Collections.sort(sizes, new CameraPreviewSizeComparator());

        for (Camera.Size size : sizes) {
            if (size.width == viewWidth && size.height == viewHeight) {
                LogUtil.info(TAG, String.format("Java: found match preview size %d x %d",
                        size.width, size.height));
                return size;
            }
        }

        for (Camera.Size size : sizes) {
            if (size.width == viewWidth || size.height == viewHeight) {
                LogUtil.info(TAG, String.format("Java: found half-match preview size %d x %d",
                        size.width, size.height));
                return size;
            }
        }

        return sizes.get(0);
    }

    public static List<Camera.Size> getSupportedPreviewSize(Camera.Parameters parameters) {
        if (parameters == null) {
            return null;
        }

        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();

        Collections.sort(sizes, new CameraPreviewSizeComparator());
        return sizes;
    }

    //
    public static Camera.Size getOptimalPreviewSize(Camera.Parameters parameters,
                                                    int viewWidth, int viewHeight) {

        if (parameters == null) {
            LogUtil.error(TAG, "camera parameters is null");
            return null;
        }

        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        if (sizes == null) return null;

        Collections.sort(sizes, new CameraPreviewSizeComparator());

        final double ASPECT_TOLERANCE = 0.05;
        double targetRatio = (double) viewWidth / viewHeight;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        int targetHeight = viewHeight;
        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;

            if (optimalSize != null && size.height > viewHeight) {
                break;
            }

            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }

        //Log.e("CameraHelper",
        //        "optimalSize : width=" + optimalSize.width + " height=" + optimalSize.height);
        return optimalSize;
    }

    //  这里只使用于旋转了90度
    public static Rect calculateTapArea(View v, float oldx, float oldy, float coefficient) {

        float x = oldy;
        float y = v.getHeight() - oldx;

        float focusAreaSize = 300;

        int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();
        int centerX = (int) (x / v.getWidth() * 2000 - 1000);
        int centerY = (int) (y / v.getHeight() * 2000 - 1000);

        int left = clamp(centerX - areaSize / 2, -1000, 1000);
        int right = clamp(left + areaSize, -1000, 1000);
        int top = clamp(centerY - areaSize / 2, -1000, 1000);
        int bottom = clamp(top + areaSize, -1000, 1000);

        return new Rect(left, top, right, bottom);
    }

    private static int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }



    /**
     * 是否支持脸部检测
     *
     * @return
     */
    public static boolean supportFaceDetection(Camera camera) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            return false;
        // check if face detection is supported or not
        // using Camera.Parameters
        if (camera.getParameters().getMaxNumDetectedFaces() <= 0) {
            LogUtil.warn(TAG, "Face Detection not supported");
            return false;
        }
        return true;
    }
    public static void setPreviewFormat(Camera camera, Camera.Parameters parameters) throws CameraNotSupportException{
        //设置预览回调的图片格式
        try {
            parameters.setPreviewFormat(ImageFormat.NV21);
            camera.setParameters(parameters);
        } catch (Exception e) {
            throw new CameraNotSupportException();
        }
    }
    public static void initCameraParams(Camera camera, CameraData cameraData, boolean isTouchMode, CameraConfiguration configuration)
            throws CameraNotSupportException {
        boolean isLandscape = (configuration.orientation != CameraConfiguration.Orientation.PORTRAIT);
        int cameraWidth = Math.max(configuration.height, configuration.width);
        int cameraHeight = Math.min(configuration.height, configuration.width);
        Camera.Parameters parameters = camera.getParameters();
        setPreviewFormat(camera, parameters);
        setPreviewFps(camera, configuration.fps, parameters);
        setPreviewSize(camera, cameraData, cameraWidth, cameraHeight, parameters);
        cameraData.hasLight = supportFlash(camera);
        setOrientation(cameraData, isLandscape, camera);
        setFocusMode(camera, cameraData, isTouchMode);
    }

    public static void setPreviewFps(Camera camera, int fps, Camera.Parameters parameters) {
        //设置摄像头预览帧率
        if(BlackListHelper.deviceInFpsBlacklisted()) {
            LogUtil.debug("Camera", "Device in fps setting black list, so set the camera fps 15");
            fps = 15;
        }
        try {
            parameters.setPreviewFrameRate(fps);
            camera.setParameters(parameters);
        } catch (Exception e) {
            e.printStackTrace();
        }

        int[] range = adaptPreviewFps(fps, parameters.getSupportedPreviewFpsRange());

        try {
            parameters.setPreviewFpsRange(range[0], range[1]);
            camera.setParameters(parameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static int[] adaptPreviewFps(int expectedFps, List<int[]> fpsRanges) {
        expectedFps *= 1000;
        int[] closestRange = fpsRanges.get(0);
        int measure = Math.abs(closestRange[0] - expectedFps) + Math.abs(closestRange[1] - expectedFps);
        for (int[] range : fpsRanges) {
            if (range[0] <= expectedFps && range[1] >= expectedFps) {
                int curMeasure = Math.abs(range[0] - expectedFps) + Math.abs(range[1] - expectedFps);
                if (curMeasure < measure) {
                    closestRange = range;
                    measure = curMeasure;
                }
            }
        }
        return closestRange;
    }

    public static void setPreviewSize(Camera camera, CameraData cameraData, int width, int height,
                                      Camera.Parameters parameters) throws CameraNotSupportException {
        Camera.Size size = getOptimalPreviewSize(camera, width, height);
        if(size == null) {
            throw new CameraNotSupportException();
        }else {
            cameraData.cameraWidth = size.width;
            cameraData.cameraHeight = size.height;
        }
        //设置预览大小
        try {
            parameters.setPreviewSize(cameraData.cameraWidth, cameraData.cameraHeight);
            camera.setParameters(parameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static List<CameraData> getAllCamerasData(boolean isBackFirst) {
        ArrayList<CameraData> cameraDatas = new ArrayList<>();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                CameraData cameraData = new CameraData(i, CameraData.FACING_FRONT);
                if(isBackFirst) {
                    cameraDatas.add(cameraData);
                } else {
                    cameraDatas.add(0, cameraData);
                }
            } else if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                CameraData cameraData = new CameraData(i, CameraData.FACING_BACK);
                if(isBackFirst) {
                    cameraDatas.add(0, cameraData);
                } else {
                    cameraDatas.add(cameraData);
                }
            }
        }
        return cameraDatas;
    }

    /**
     * 检查是否支持前置摄像头
     *
     * @param
     * @return
     */
    public static boolean checkSupportFrontFacingCamera() {
        try {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            int cameraCount = Camera.getNumberOfCameras();
            for (int i = 0; i < cameraCount; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void setOrientation(CameraData cameraData, boolean isLandscape, Camera camera) {
        int orientation = getDisplayOrientation(cameraData.cameraID);
        if(isLandscape) {
            orientation = orientation - 90;
        }
        camera.setDisplayOrientation(orientation);
    }

    private static void setFocusMode(Camera camera, CameraData cameraData, boolean isTouchMode) {
        cameraData.supportTouchFocus = supportTouchFocus(camera);
        if(!cameraData.supportTouchFocus) {
            setAutoFocusMode(camera);
        } else {
            if(!isTouchMode) {
                cameraData.touchFocusMode = false;
                setAutoFocusMode(camera);
            }else {
                cameraData.touchFocusMode = true;
            }
        }
    }

    public static boolean supportTouchFocus(Camera camera) {
        if(camera != null) {
            return (camera.getParameters().getMaxNumFocusAreas() != 0);
        }
        return false;
    }
    public static void setAutoFocusMode(Camera camera) {
        try {
            Camera.Parameters parameters = camera.getParameters();
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.size() > 0 && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                camera.setParameters(parameters);
            } else if (focusModes.size() > 0) {
                parameters.setFocusMode(focusModes.get(0));
                camera.setParameters(parameters);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void setTouchFocusMode(Camera camera) {
        try {
            Camera.Parameters parameters = camera.getParameters();
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.size() > 0 && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                camera.setParameters(parameters);
            } else if (focusModes.size() > 0) {
                parameters.setFocusMode(focusModes.get(0));
                camera.setParameters(parameters);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static Camera.Size getOptimalPreviewSize(Camera camera, int width, int height) {
        Camera.Size optimalSize = null;
        double minHeightDiff = Double.MAX_VALUE;
        double minWidthDiff = Double.MAX_VALUE;
        List<Camera.Size> sizes = camera.getParameters().getSupportedPreviewSizes();
        if (sizes == null) return null;
        //找到宽度差距最小的
        for(Camera.Size size:sizes){
            if (Math.abs(size.width - width) < minWidthDiff) {
                minWidthDiff = Math.abs(size.width - width);
            }
        }
        //在宽度差距最小的里面，找到高度差距最小的
        for(Camera.Size size:sizes){
            if(Math.abs(size.width - width) == minWidthDiff) {
                if(Math.abs(size.height - height) < minHeightDiff) {
                    optimalSize = size;
                    minHeightDiff = Math.abs(size.height - height);
                }
            }
        }
        return optimalSize;
    }
    public static int getDisplayOrientation(int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation + 360) % 360;
        }
        return result;
    }

    public static void checkCameraService(Context context)
            throws CameraDisabledException, NoCameraException {
        // Check if device policy has disabled the camera.
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        if (dpm.getCameraDisabled(null)) {
            throw new CameraDisabledException();
        }
        List<CameraData> cameraDatas = getAllCamerasData(false);
        if(cameraDatas.size() == 0) {
            throw new NoCameraException();
        }
    }

    /**
     * 是否支持闪光灯
     * @param camera
     * @return
     */
    public static boolean supportFlash(Camera camera){
        Camera.Parameters params = camera.getParameters();
        List<String> flashModes = params.getSupportedFlashModes();
        if(flashModes == null) {
            return false;
        }
        for(String flashMode : flashModes) {
            if(Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
                return true;
            }
        }
        return false;
    }
}
