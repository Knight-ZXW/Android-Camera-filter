package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;


/**
 * Created by Michael.Ma on 2016/8/12.
 */
public class ImageFilterMosaicBlur extends CameraFilterMosaicBlur {
    public ImageFilterMosaicBlur(Context applicationContext) {
        super(applicationContext);
    }

    public ImageFilterMosaicBlur(Context applicationContext,
                                  float blur_pixels, float blurRatio) {
        super(applicationContext, blur_pixels, blurRatio);
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_2d_mosaicblur);
    }

    @Override
    public int getTextureTarget() {
        return GLES20.GL_TEXTURE_2D;
    }
}
