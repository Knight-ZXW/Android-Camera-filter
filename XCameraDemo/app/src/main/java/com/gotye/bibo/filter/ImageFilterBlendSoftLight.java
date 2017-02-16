package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES10;
import android.support.annotation.DrawableRes;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

public class ImageFilterBlendSoftLight extends CameraFilterBlendSoftLight {

    public ImageFilterBlendSoftLight(Context context, @DrawableRes int drawableId) {
        super(context, drawableId);
    }

    @Override
    public int getTextureTarget() {
        return GLES10.GL_TEXTURE_2D;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader_two_input,
                R.raw.fragment_shader_2d_blend_soft_light);
    }
}