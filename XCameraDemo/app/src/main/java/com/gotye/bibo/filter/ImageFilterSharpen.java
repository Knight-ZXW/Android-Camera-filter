package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;


/**
 * Created by Michael.Ma on 2016/1/9.
 */
class ImageFilterSharpen extends CameraFilterSharpen {

    public ImageFilterSharpen(Context applicationContext) {
        super(applicationContext, 2f);
    }

    public ImageFilterSharpen(Context applicationContext, float sharpness) {
        super(applicationContext, sharpness);
    }

    @Override
    public int getTextureTarget() {
        return GLES20.GL_TEXTURE_2D;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader_sharpen,
                R.raw.fragment_shader_2d_sharpen);
    }
}
