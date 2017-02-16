package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;

/**
 * Created by Michael.Ma on 2016/8/11.
 */
public class CameraFilterToon extends T3x3TextureSamplingFilter {
    private float mThreshold = 0.2f;
    private int muThresholdLocation;
    private float mQuantizationLevels = 10.0f;
    private int muQuantizationLevelsLocation;
    private boolean muProcessHSV = false;
    private int muProcessHSVLocation;

    public CameraFilterToon(Context applicationContext,
                            float threshold, float quantization, boolean processHSV) {
        super(applicationContext);

        mThreshold = threshold;
        mQuantizationLevels = quantization;
        muProcessHSV = processHSV;
    }

    public CameraFilterToon(Context applicationContext) {
        super(applicationContext);
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader_3x3_texture_sampling,
                R.raw.fragment_shader_ext_toon);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        muThresholdLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uThreshold");
        GlUtil.checkLocation(muThresholdLocation, "uThreshold");
        muQuantizationLevelsLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uQuantizationLevels");
        GlUtil.checkLocation(muQuantizationLevelsLocation, "uQuantizationLevels");
        muProcessHSVLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uProcessHSV");
        GlUtil.checkLocation(muProcessHSVLocation, "uProcessHSV");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        GLES20.glUniform1f(muThresholdLocation, mThreshold);
        GLES20.glUniform1f(muQuantizationLevelsLocation, mQuantizationLevels);
        GLES20.glUniform1i(muProcessHSVLocation, muProcessHSV ? 1 : 0);
    }
}
