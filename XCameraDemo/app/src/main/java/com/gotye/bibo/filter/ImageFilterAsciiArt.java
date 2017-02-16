package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES10;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;


/**
 * Created by Michael.Ma on 2016/8/30.
 */
public class ImageFilterAsciiArt extends CameraFilterAsciiArt {
    public ImageFilterAsciiArt(Context applicationContext) {
        super(applicationContext);
    }

    public ImageFilterAsciiArt(Context applicationContext, boolean gray) {
        super(applicationContext, gray);
    }

    @Override
    public int getTextureTarget() {
        return GLES10.GL_TEXTURE_2D;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_2d_ascii_art);
    }
}
