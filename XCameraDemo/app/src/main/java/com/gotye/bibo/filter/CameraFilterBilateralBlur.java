package com.gotye.bibo.filter;

import android.content.Context;

/**
 * Created by Michael.Ma on 2016/1/8.
 */

public class CameraFilterBilateralBlur extends FilterGroup<CameraFilter> {

    public CameraFilterBilateralBlur(Context context,
                                     float distanceNormalizationFactor,
                                     float blurRatio) {
        super();
        addFilter(new CameraFilterBilateralBlurSingle(context,
                distanceNormalizationFactor, blurRatio, false));
        addFilter(new ImageFilterBilateralBlurSingle(context,
                distanceNormalizationFactor, blurRatio, true));
        addFilter(new ImageFilter(context));
    }
}
