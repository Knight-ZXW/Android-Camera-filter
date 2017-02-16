package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES10;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;


/**
 * Created by Michael.Ma on 2016/8/11.
 */
public class ImageFilterToon extends CameraFilterToon {
    public ImageFilterToon(Context applicationContext,
                           float threshold, float quantization, boolean processHSV) {
        super(applicationContext, threshold, quantization, processHSV);
    }

    public ImageFilterToon(Context applicationContext) {
        super(applicationContext);
    }

    @Override
    public int getTextureTarget() {
        return GLES10.GL_TEXTURE_2D;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader_3x3_texture_sampling,
                R.raw.fragment_shader_2d_toon);
    }
}
