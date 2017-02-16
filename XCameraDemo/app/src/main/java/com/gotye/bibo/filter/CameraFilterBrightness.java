package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;

/**
 * Created by Michael.Ma on 2016/6/3.
 */
public class CameraFilterBrightness extends CameraFilter {
    private int muBrightnessLocation;
    private float mBrightness = 1f;

    public CameraFilterBrightness(Context applicationContext, float brightness) {
        super(applicationContext);

        mBrightness = brightness;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_ext_brightness);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        muBrightnessLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uBrightness");
        GlUtil.checkLocation(muBrightnessLocation, "uBrightness");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        GLES20.glUniform1f(muBrightnessLocation, mBrightness);
    }
}
