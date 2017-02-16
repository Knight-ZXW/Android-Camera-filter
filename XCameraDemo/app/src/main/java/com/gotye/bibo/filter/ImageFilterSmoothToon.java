package com.gotye.bibo.filter;

import android.content.Context;

/**
 * Created by Michael.Ma on 2016/8/11.
 */
public class ImageFilterSmoothToon extends FilterGroup<CameraFilter> {

    public ImageFilterSmoothToon(Context context,
                                 float threshold, float quantization, float blurSize) {
        super();

        addFilter(new ImageFilterGaussianBlurSingle(context, blurSize, false));
        addFilter(new ImageFilterGaussianBlurSingle(context, blurSize, true));
        addFilter(new ImageFilterToon(context, threshold, quantization, false));
        addFilter(new ImageFilter(context));
    }

    public ImageFilterSmoothToon(Context context) {
        super();

        addFilter(new ImageFilterGaussianBlurSingle(context, 0.5f, false));
        addFilter(new ImageFilterGaussianBlurSingle(context, 0.5f, true));
        addFilter(new ImageFilterToon(context, 0.2f, 10f, false));
        addFilter(new ImageFilter(context));
    }
}
