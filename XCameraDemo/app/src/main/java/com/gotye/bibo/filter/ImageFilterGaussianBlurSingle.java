package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

/**
 * Created by Michael.Ma on 2016/6/1.
 */
public class ImageFilterGaussianBlurSingle extends CameraFilterGaussianBlurSingle {

    public ImageFilterGaussianBlurSingle(Context applicationContext, boolean widthOrHeight) {
        super(applicationContext, 4f, widthOrHeight);
    }

    public ImageFilterGaussianBlurSingle(Context applicationContext,
                                         final float blurSize, boolean widthOrHeight) {
        super(applicationContext, blurSize, widthOrHeight);
    }

    @Override
    public int getTextureTarget() {
        return GLES20.GL_TEXTURE_2D;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader_gaussianblur,
                R.raw.fragment_shader_2d_gaussianblur);
    }
}
