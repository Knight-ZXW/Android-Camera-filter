package com.gotye.bibo.filter;

import android.content.Context;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

/**
 * Created by Michael.Ma on 2016/6/1.
 */
public class CameraFilterWeakPixelInclusion extends T3x3TextureSamplingFilter {
    public CameraFilterWeakPixelInclusion(Context applicationContext) {
        super(applicationContext);
    }

    public CameraFilterWeakPixelInclusion(Context applicationContext,
                                          float ratio, float lineSize) {
        super(applicationContext, ratio, lineSize);
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader_3x3_texture_sampling,
                R.raw.fragment_shader_ext_weak_pixel_inclusion);
    }
}
