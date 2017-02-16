package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;

public class CameraFilterSharpen extends CameraFilter {

    private int mSharpnessLocation;
    private float mSharpness = 0f;
    private int mImageWidthFactorLocation;
    private int mImageHeightFactorLocation;

    public CameraFilterSharpen(Context applicationContext) {
        this(applicationContext, 0f);
    }

    public CameraFilterSharpen(Context applicationContext, float sharpness) {
        super(applicationContext);

        mSharpness = sharpness;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader_sharpen,
                R.raw.fragment_shader_ext_sharpen);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        mSharpnessLocation = GLES20.glGetUniformLocation(mProgramHandle, "sharpness");
        mImageWidthFactorLocation = GLES20.glGetUniformLocation(mProgramHandle, "imageWidthFactor");
        mImageHeightFactorLocation = GLES20.glGetUniformLocation(mProgramHandle, "imageHeightFactor");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        GLES20.glUniform1f(mSharpnessLocation, mSharpness);
        GLES20.glUniform1f(mImageWidthFactorLocation,
                mIncomingWidth == 0 ? 0f : 1.0f / mIncomingWidth);
        GLES20.glUniform1f(mImageHeightFactorLocation,
                mIncomingHeight == 0 ? 0f : 1.0f / mIncomingHeight);
    }

}
