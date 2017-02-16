package com.gotye.bibo.filter;

import android.content.Context;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

/**
 * Created by Michael.Ma on 2016/6/1.
 */
public class CameraFilterNonMaximumSuppression extends T3x3TextureSamplingFilter {
    public CameraFilterNonMaximumSuppression(Context applicationContext) {
        super(applicationContext);
    }

    public CameraFilterNonMaximumSuppression(Context applicationContext,
                                          float ratio, float lineSize) {
        super(applicationContext, ratio, lineSize);
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader_3x3_texture_sampling,
                R.raw.fragment_shader_ext_non_maximum_suppression);
    }
}