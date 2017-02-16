package com.gotye.bibo.filter;

import android.content.Context;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

/**
 * Created by Michael.Ma on 2016/6/2.
 */
public class ImageFilterBeautifyTest extends ImageFilterBeautify {

    public ImageFilterBeautifyTest(Context applicationContext) {
        super(applicationContext);
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_2d_beautify_test);
    }
}
