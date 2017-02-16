package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

/**
 * Created by Michael.Ma on 2016/1/9.
 */
class ImageFilterDirectionalNonMaximumSuppression extends CameraFilterDirectionalNonMaximumSuppression {

    public ImageFilterDirectionalNonMaximumSuppression(Context applicationContext) {
        super(applicationContext);
    }

    public ImageFilterDirectionalNonMaximumSuppression(Context applicationContext,
                                                       float upper, float lower) {
        super(applicationContext, upper, lower);
    }

    @Override
    public int getTextureTarget() {
        return GLES20.GL_TEXTURE_2D;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_2d_directional_non_maximum_suppression);
    }
}
