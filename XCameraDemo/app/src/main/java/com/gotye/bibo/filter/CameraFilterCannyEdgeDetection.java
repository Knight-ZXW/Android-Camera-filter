package com.gotye.bibo.filter;

import android.content.Context;

/**
 * Created by Michael.Ma on 2016/6/1.
 */
public class CameraFilterCannyEdgeDetection extends FilterGroup<CameraFilter> {

    public CameraFilterCannyEdgeDetection(Context context) {
        this(context, 2.0f);
    }
    public CameraFilterCannyEdgeDetection(Context context, float blurSize) {
        super();

        addFilter(new CameraFilterGrayScale(context));
        addFilter(new ImageFilterGaussianBlurSingle(context,
                blurSize, false));
        addFilter(new ImageFilterGaussianBlurSingle(context,
                blurSize, true));
        addFilter(new ImageFilterDirectionalSobelEdgeDetection(context,
                1f, 1f));
        addFilter(new ImageFilterDirectionalNonMaximumSuppression(context));
        //addFilter(new ImageFilterWeakPixelInclusion(context, 1f, 1f));
    }
}