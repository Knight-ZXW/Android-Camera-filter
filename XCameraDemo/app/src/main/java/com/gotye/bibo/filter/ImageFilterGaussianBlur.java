package com.gotye.bibo.filter;

import android.content.Context;

/**
 * Created by Michael.Ma on 2016/1/9.
 */
public class ImageFilterGaussianBlur extends FilterGroup<CameraFilter> {

    public ImageFilterGaussianBlur(Context context, float blurSize) {
        super();
        addFilter(new ImageFilterGaussianBlurSingle(context, blurSize, false));
        addFilter(new ImageFilterGaussianBlurSingle(context, blurSize, true));
        addFilter(new ImageFilter(context));
    }
}
