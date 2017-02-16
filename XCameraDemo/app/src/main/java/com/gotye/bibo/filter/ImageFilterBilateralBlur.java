package com.gotye.bibo.filter;

import android.content.Context;

/**
 * Created by Michael.Ma on 2016/1/9.
 */
public class ImageFilterBilateralBlur extends FilterGroup<CameraFilter> {

    public ImageFilterBilateralBlur(Context context,
                                     float distanceNormalizationFactor,
                                     float blurRatio) {
        super();
        addFilter(new ImageFilterBilateralBlurSingle(context,
                distanceNormalizationFactor, blurRatio, false));
        addFilter(new ImageFilterBilateralBlurSingle(context,
                distanceNormalizationFactor, blurRatio, true));
        addFilter(new ImageFilter(context));
    }
}
