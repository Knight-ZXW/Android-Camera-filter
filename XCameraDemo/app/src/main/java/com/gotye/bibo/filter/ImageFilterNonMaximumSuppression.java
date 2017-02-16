package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;


/**
 * Created by Michael.Ma on 2016/1/9.
 */
class ImageFilterNonMaximumSuppression extends CameraFilterNonMaximumSuppression {

    public ImageFilterNonMaximumSuppression(Context applicationContext) {
        super(applicationContext);
    }

    public ImageFilterNonMaximumSuppression(Context applicationContext,
                                            float ratio, float lineSize) {
        super(applicationContext, ratio, lineSize);
    }

    @Override
    public int getTextureTarget() {
        return GLES20.GL_TEXTURE_2D;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader_3x3_texture_sampling,
                R.raw.fragment_shader_2d_non_maximum_suppression);
    }
}
