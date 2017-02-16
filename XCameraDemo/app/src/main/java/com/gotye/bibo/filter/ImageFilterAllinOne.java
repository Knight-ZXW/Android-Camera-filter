package com.gotye.bibo.filter;

import android.content.Context;

/**
 * Created by Michael.Ma on 2016/6/2.
 */
public class ImageFilterAllinOne extends FilterGroup<IFilter> {
    private IFilter mFilter;

    public ImageFilterAllinOne(Context context) {
        this(context, 6f, 3f);
    }

    public ImageFilterAllinOne(Context context,
                               float distanceNormalizationFactor,
                               float blurRatio) {
        super();

        mFilter = new ImageFilterThreeInput(context,
                new ImageFilterBilateralBlur(context, distanceNormalizationFactor, blurRatio),
                new ImageFilterCannyEdgeDetection(context),
                new ImageFilter(context),
                new ImageFilterBeautify(context));
        addFilter(mFilter);
        addFilter(new ImageFilterSaturation(context, 1.1f));
        addFilter(new ImageFilterBrightness(context, 0.1f));
    }

    @Override
    public void setTextureSize(int width, int height) {
        super.setTextureSize(width, height);

        // let this filter finally render to mFrameBuffers[0], NOT display
        mFilter.setFrameBuffer(mFrameBuffers[0]);
    }
}
