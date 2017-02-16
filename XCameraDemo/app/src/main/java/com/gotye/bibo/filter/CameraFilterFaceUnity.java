package com.gotye.bibo.filter;

import android.content.Context;

public class CameraFilterFaceUnity extends FilterGroup<CameraFilter> {

    public CameraFilterFaceUnity(Context applicationContext, int index) {
        super();

        addFilter(new CameraFilter(applicationContext));
        addFilter(new ImageFilterFaceUnity(applicationContext, index));
    }
}
