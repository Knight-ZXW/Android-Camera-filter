package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

/**
 * Created by Michael.Ma on 2016/8/12.
 */
public class ImageFilterBeautifyFaceWu extends CameraFilterBeautifyFaceWu {
    public ImageFilterBeautifyFaceWu(Context applicationContext) {
        super(applicationContext);
    }

    public ImageFilterBeautifyFaceWu(Context applicationContext,
                                     float ratio, float strength) {
        super(applicationContext, ratio, strength);
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_2d_beautifyface_wu);
    }

    @Override
    public int getTextureTarget() {
        return GLES20.GL_TEXTURE_2D;
    }
}
