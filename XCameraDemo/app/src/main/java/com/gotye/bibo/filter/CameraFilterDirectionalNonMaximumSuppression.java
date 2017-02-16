package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;

/**
 * Created by Michael.Ma on 2016/6/1.
 */
public class CameraFilterDirectionalNonMaximumSuppression extends CameraFilter {

    private int muTexelWidthLocation;
    private int muTexelHeightLocation;
    private int muUpperThresholdLocation;
    private int muLowerThresholdLocation;

    private float mUpper;
    private float mLower;

    public CameraFilterDirectionalNonMaximumSuppression(Context applicationContext) {
        this(applicationContext, 0.4f, 0.1f);
    }

    public CameraFilterDirectionalNonMaximumSuppression(Context applicationContext,
                                                        float upper, float lower) {
        super(applicationContext);

        mUpper = upper;
        mLower = lower;
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        muTexelWidthLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uTexelWidth");
        GlUtil.checkLocation(muTexelWidthLocation, "uTexelWidth");
        muTexelHeightLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uTexelHeight");
        GlUtil.checkLocation(muTexelHeightLocation, "uTexelHeight");
        muUpperThresholdLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uUpperThreshold");
        GlUtil.checkLocation(muUpperThresholdLocation, "uUpperThreshold");
        muLowerThresholdLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uLowerThreshold");
        GlUtil.checkLocation(muLowerThresholdLocation, "uLowerThreshold");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        float texelWidth, texelHeight;
        texelWidth = (mIncomingWidth == 0 ? 0f : 1f / (float)mIncomingWidth);
        texelHeight = (mIncomingHeight == 0 ? 0f : 1f / (float)mIncomingHeight);
        GLES20.glUniform1f(muTexelWidthLocation, texelWidth);
        GLES20.glUniform1f(muTexelHeightLocation, texelHeight);

        GLES20.glUniform1f(muUpperThresholdLocation, mUpper);
        GLES20.glUniform1f(muLowerThresholdLocation, mLower);
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_ext_directional_non_maximum_suppression);
    }
}