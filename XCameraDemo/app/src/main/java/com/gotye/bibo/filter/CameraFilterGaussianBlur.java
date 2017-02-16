package com.gotye.bibo.filter;

import android.content.Context;

/**
 * Created by Michael.Ma on 2016/1/8.
 */

public class CameraFilterGaussianBlur extends FilterGroup<CameraFilter> {

    public CameraFilterGaussianBlur(Context context, float blurSize) {
        super();
        addFilter(new CameraFilterGaussianBlurSingle(context,
                blurSize, false));
        addFilter(new ImageFilterGaussianBlurSingle(context,
                blurSize, true));
        addFilter(new ImageFilter(context));
    }
}
