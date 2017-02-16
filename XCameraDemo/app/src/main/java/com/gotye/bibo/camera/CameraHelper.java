package com.gotye.bibo.camera;

import android.graphics.Rect;
import android.hardware.Camera;
import android.view.View;

import com.gotye.bibo.util.LogUtil;

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
}
