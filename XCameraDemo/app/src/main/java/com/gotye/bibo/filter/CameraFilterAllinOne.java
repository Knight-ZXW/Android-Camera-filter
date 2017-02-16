package com.gotye.bibo.filter;

import android.content.Context;

/**
 * Created by Michael.Ma on 2016/1/8.
 */

public class CameraFilterAllinOne extends FilterGroup<IFilter> {
    private IFilter mFilter;

    public CameraFilterAllinOne(Context context) {
        this(context, 6f, 3f);
    }

    public CameraFilterAllinOne(Context context,
                                float distanceNormalizationFactor,
                                float blurRatio) {
        super();

        mFilter = new ImageFilterThreeInput(context,
                new CameraFilterBilateralBlur(context, distanceNormalizationFactor, blurRatio),
                new CameraFilterCannyEdgeDetection(context),
                new CameraFilter(context),
                new ImageFilterBeautify(context));
        addFilter(mFilter);
        addFilter(new ImageFilterSaturation(context, 1.1f));
        addFilter(new ImageFilterBrightness(context, 0.1f));
    }

    @Override
    public void setTextureSize(int width, int height) {
        super.setTextureSize(width, height);

        mFilter.setFrameBuffer(mFrameBuffers[0]);
    }
}
