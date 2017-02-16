package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;

/**
 * Created by Michael.Ma on 2016/8/22.
 */
public class CameraFilterNoiseContour extends CameraFilter {
    private int muiGlobalTimeLocation;

    private long START_TIME;

    public CameraFilterNoiseContour(Context applicationContext) {
        super(applicationContext);

        START_TIME = System.currentTimeMillis();
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_ext_noise_contour);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        muiGlobalTimeLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uiGlobalTime");
        GlUtil.checkLocation(muiGlobalTimeLocation, "uiGlobalTime");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        float time = ((float) (System.currentTimeMillis() - START_TIME)) / 1000.0f;
        GLES20.glUniform1f(muiGlobalTimeLocation, time);
    }
}
