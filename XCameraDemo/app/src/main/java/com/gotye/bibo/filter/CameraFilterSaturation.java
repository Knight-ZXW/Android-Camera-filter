package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;

/**
 * Created by Michael.Ma on 2016/6/3.
 */
public class CameraFilterSaturation extends CameraFilter {
    private int muSaturationLocation;

    private float mSaturation = 1f;

    public CameraFilterSaturation(Context applicationContext, float saturation) {
        super(applicationContext);

        mSaturation = saturation;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_ext_saturation);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        muSaturationLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uSaturation");
        GlUtil.checkLocation(muSaturationLocation, "uSaturation");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        GLES20.glUniform1f(muSaturationLocation, mSaturation);
    }
}
