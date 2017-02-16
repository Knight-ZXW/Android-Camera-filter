package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;

/**
 * Created by Michael.Ma on 2016/6/1.
 */
public class CameraFilterGaussianBlurSingle extends CameraFilter {
    private float mBlurSize = 2f;
    private boolean mWidthOrHeight;

    private int mSingleStepOffsetLocation;

    public CameraFilterGaussianBlurSingle(Context applicationContext) {
        this(applicationContext, 2.0f, false);
    }

    public CameraFilterGaussianBlurSingle(Context applicationContext,
                                          float blurSize, boolean widthOrHeight) {
        super(applicationContext);
        mBlurSize = blurSize;
        mWidthOrHeight = widthOrHeight;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader_gaussianblur,
                R.raw.fragment_shader_ext_gaussianblur);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        mSingleStepOffsetLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "singleStepOffset");
        GlUtil.checkLocation(mSingleStepOffsetLocation, "singleStepOffset");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        float []stepOffset;
        if (mWidthOrHeight) {
            stepOffset = new float[] {mIncomingWidth == 0 ? 0f : mBlurSize / (float)mIncomingWidth, 0f};
        } else {
            stepOffset = new float[] {0f, mIncomingHeight == 0 ? 0f : mBlurSize / (float)mIncomingHeight};
        }
        GLES20.glUniform2fv(mSingleStepOffsetLocation, 1, FloatBuffer.wrap(stepOffset));
    }
}
