package com.gotye.bibo.filter;

import android.content.Context;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

/**
 * Created by Michael.Ma on 2016/8/19.
 */
public class CameraFilterEdgeDetectiondFdx extends CameraFilter {
    public CameraFilterEdgeDetectiondFdx(Context applicationContext) {
        super(applicationContext);
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_ext_edge_detection_dfdx);
    }
}
