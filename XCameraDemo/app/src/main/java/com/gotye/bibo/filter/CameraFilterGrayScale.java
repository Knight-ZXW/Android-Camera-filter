package com.gotye.bibo.filter;

import android.content.Context;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

public class CameraFilterGrayScale extends CameraFilter {

    public CameraFilterGrayScale(Context applicationContext) {
        super(applicationContext);
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_ext_grayscale);
    }
}
