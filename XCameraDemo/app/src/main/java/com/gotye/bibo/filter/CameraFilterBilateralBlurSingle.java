package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;

public class CameraFilterBilateralBlurSingle extends CameraFilter {

    private int mDisFactorLocation;
    private int mSingleStepOffsetLocation;

    private float mDistanceNormalizationFactor = 2f;
    private float mBlurRatio = 4f;
    private boolean mWidthOrHeight;

    public CameraFilterBilateralBlurSingle(Context applicationContext, boolean widthOrHeight) {
        this(applicationContext, 2f, 4f, false);
    }

    public CameraFilterBilateralBlurSingle(Context applicationContext,
                                           float distanceNormalizationFactor,
                                           float blurRatio, boolean widthOrHeight) {
        super(applicationContext);
        mWidthOrHeight = widthOrHeight;
        mBlurRatio = blurRatio;
        mDistanceNormalizationFactor = distanceNormalizationFactor;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader_gaussianblur,
                R.raw.fragment_shader_ext_bilateral);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        mDisFactorLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "distanceNormalizationFactor");
        GlUtil.checkLocation(mDisFactorLocation, "distanceNormalizationFactor");
        mSingleStepOffsetLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "singleStepOffset");
        GlUtil.checkLocation(mSingleStepOffsetLocation, "singleStepOffset");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        GLES20.glUniform1f(mDisFactorLocation, mDistanceNormalizationFactor);

        float []stepOffset;
        if (mWidthOrHeight) {
            stepOffset = new float[] {mIncomingWidth == 0 ? 0f : mBlurRatio / (float)mIncomingWidth, 0f};
        } else {
            stepOffset = new float[] {0f, mIncomingHeight == 0 ? 0f : mBlurRatio / (float)mIncomingHeight};
        }
        GLES20.glUniform2fv(mSingleStepOffsetLocation, 1, FloatBuffer.wrap(stepOffset));
    }
}
