package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;

/**
 * Created by Michael.Ma on 2016/8/12.
 */
public class CameraFilterWhite extends CameraFilter {

    private int muTemperatureLocation;
    private float muTemperature = 0f;
    private int muWhiteLocation;
    private float muWhite = 0f;

    public CameraFilterWhite(Context applicationContext) {
        super(applicationContext);
    }

    public CameraFilterWhite(Context applicationContext,
                             float temperature, float white) {
        super(applicationContext);

        muTemperature = temperature;
        muWhite = white;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_ext_white);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        muTemperatureLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "muTemperature");
        GlUtil.checkLocation(muTemperatureLocation, "muTemperature");
        muWhiteLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "muWhite");
        GlUtil.checkLocation(muWhiteLocation, "muWhite");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        GLES20.glUniform1f(muTemperatureLocation, muTemperature);
        GLES20.glUniform1f(muWhiteLocation, muWhite);
    }
}
