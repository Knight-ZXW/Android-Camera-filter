package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;

/**
 * Created by Michael.Ma on 2016/8/11.
 */

public class CameraFilterMosaicBlur extends CameraFilter {

    private float mBlurRatio = 1f;
    private float mBlurPixels = 8f;

    private int muSamplerStepsLocation;
    private int muBlurPixelsLocation;

    private static float SAMPLER_STEP[];

    public CameraFilterMosaicBlur(Context applicationContext) {
        super(applicationContext);
    }

    public CameraFilterMosaicBlur(Context applicationContext,
                                  float blur_pixels, float blurRatio) {
        super(applicationContext);

        mBlurRatio = blurRatio;
        mBlurPixels = blur_pixels;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_ext_mosaicblur);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        muSamplerStepsLocation = GLES20.glGetUniformLocation(
                mProgramHandle, "uSamplerSteps");
        GlUtil.checkLocation(muSamplerStepsLocation, "uSamplerSteps");
        muBlurPixelsLocation = GLES20.glGetUniformLocation(
                mProgramHandle, "uBlurPixels");
        GlUtil.checkLocation(muBlurPixelsLocation, "uBlurPixels");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        SAMPLER_STEP    = new float[]{
                mIncomingWidth == 0 ? 0 : mBlurRatio / mIncomingWidth,
                mIncomingHeight == 0 ? 0 : mBlurRatio / mIncomingHeight};

        GLES20.glUniform2fv(muSamplerStepsLocation, 1, FloatBuffer.wrap(SAMPLER_STEP));
        GLES20.glUniform1f(muBlurPixelsLocation, mBlurPixels);
    }
}
