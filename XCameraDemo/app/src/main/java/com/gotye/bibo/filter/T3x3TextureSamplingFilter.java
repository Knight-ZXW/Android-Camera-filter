package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.gles.GlUtil;
import com.gotye.bibo.util.LogUtil;

import java.nio.FloatBuffer;
import java.util.Locale;

public abstract class T3x3TextureSamplingFilter extends CameraFilter {
    private final static String TAG = "T3x3TextureSamplingFilter";

    private int mUniformTexelWidthLocation;
    private int mUniformTexelHeightLocation;

    private float mTexelSpacingMultiplier;
    private float mLineSize;

    public T3x3TextureSamplingFilter(Context applicationContext) {
        this(applicationContext, 1.0f, 1.0f);
    }

    public T3x3TextureSamplingFilter(Context applicationContext,
                                     float ratio, float lineSize) {
        super(applicationContext);

        mTexelSpacingMultiplier = ratio;
        mLineSize               = lineSize;

        LogUtil.info(TAG, String.format(Locale.US,
                "mTexelSpacingMultiplier: %.3f, mLineSize %.3f",
                mTexelSpacingMultiplier, mLineSize));
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        mUniformTexelWidthLocation = GLES20.glGetUniformLocation(mProgramHandle, "uTexelWidth");
        GlUtil.checkLocation(mUniformTexelWidthLocation, "uTexelWidth");
        mUniformTexelHeightLocation = GLES20.glGetUniformLocation(mProgramHandle, "uTexelHeight");
        GlUtil.checkLocation(mUniformTexelHeightLocation, "uTexelHeight");

    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        float t_width = mIncomingWidth == 0 ?
                0f : mTexelSpacingMultiplier / (float)mIncomingWidth;
        float t_height = mIncomingWidth == 0 ?
                0f : mTexelSpacingMultiplier / (float)mIncomingWidth;
        GLES20.glUniform1f(mUniformTexelWidthLocation, t_width);
        GLES20.glUniform1f(mUniformTexelHeightLocation, t_height);
    }
}
