package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

/**
 * Created by Michael.Ma on 2016/1/9.
 */
class ImageFilterFaceColor extends CameraFilterFaceColor {

    public ImageFilterFaceColor(Context applicationContext) {
        super(applicationContext);
    }

    public ImageFilterFaceColor(Context applicationContext, float r, float g, float b) {
        super(applicationContext, r, g, b);
    }

    @Override
    public int getTextureTarget() {
        return GLES20.GL_TEXTURE_2D;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_2d_facecolor);
    }
}
