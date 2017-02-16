package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;


/**
 * Created by Michael.Ma on 2016/8/12.
 */
public class ImageFilterWhite extends CameraFilterWhite {
    public ImageFilterWhite(Context applicationContext) {
        super(applicationContext);
    }

    public ImageFilterWhite(Context applicationContext,
                            float temperature, float white) {
        super(applicationContext, temperature, white);
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_2d_white);
    }

    @Override
    public int getTextureTarget() {
        return GLES20.GL_TEXTURE_2D;
    }
}
