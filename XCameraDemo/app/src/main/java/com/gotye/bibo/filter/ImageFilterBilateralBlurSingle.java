package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

/**
 * Created by Michael.Ma on 2016/1/9.
 */
class ImageFilterBilateralBlurSingle extends CameraFilterBilateralBlurSingle {

    public ImageFilterBilateralBlurSingle(Context applicationContext, boolean widthOrHeight) {
        super(applicationContext, 2f, 4f, widthOrHeight);
    }

    public ImageFilterBilateralBlurSingle(Context applicationContext,
                                          final float distanceNormalizationFactor,
                                          final float blurRatio, boolean widthOrHeight) {
        super(applicationContext, distanceNormalizationFactor, blurRatio, widthOrHeight);
    }

    @Override
    public int getTextureTarget() {
        return GLES20.GL_TEXTURE_2D;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader_gaussianblur,
                R.raw.fragment_shader_2d_bilateralblur);
    }
}
