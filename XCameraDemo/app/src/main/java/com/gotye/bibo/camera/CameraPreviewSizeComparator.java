package com.gotye.bibo.camera;

import android.hardware.Camera;

import java.util.Comparator;

public class CameraPreviewSizeComparator implements Comparator<Camera.Size> {

    // 预览尺寸建议从小到大，优先获取较小的尺寸
    //modify by zhuoxiuwu ,改成 优先从大到小
    public int compare(Camera.Size size1, Camera.Size size2) {
        return size2.width - size1.width;
    }
}
