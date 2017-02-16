package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;
import com.gotye.bibo.util.LogUtil;

import java.nio.FloatBuffer;

/**
 * Created by Michael.Ma on 2016/8/4.
 */
public class CameraFilterBeautifyFaceWu extends CameraFilter {
    private final static String TAG = "CameraFilterBeautifyFaceWu";

    private int mSingleStepOffsetLocation;
    private int mParamsLocation;

    private float mRatio = 1.0f;
    private int mLevel = 3;


    public CameraFilterBeautifyFaceWu(Context applicationContext) {
        super(applicationContext);
    }

    public CameraFilterBeautifyFaceWu(Context applicationContext,
                                      float ratio, float strength) {
        super(applicationContext);

        mRatio = ratio;
        mLevel = (int)strength;

        LogUtil.info(TAG, "ratio: " + mRatio + " , level: " + mLevel);
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_ext_beautifyface_wu);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        mSingleStepOffsetLocation = GLES20.glGetUniformLocation(
                mProgramHandle, "singleStepOffset");
        GlUtil.checkLocation(mSingleStepOffsetLocation, "singleStepOffset");
        mParamsLocation = GLES20.glGetUniformLocation(
                mProgramHandle, "params");
        GlUtil.checkLocation(mParamsLocation, "params");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        setBeautyLevel(mLevel);

        float []stepOffset = new float[] {
                mIncomingWidth == 0 ? 0f : mRatio / (float)mIncomingWidth,
                mIncomingHeight == 0 ? 0f : mRatio / (float)mIncomingHeight};

        GLES20.glUniform2fv(mSingleStepOffsetLocation, 1, FloatBuffer.wrap(stepOffset));
    }

    private void setBeautyLevel2(int level){
        switch (level) {
            case 1:
                GLES20.glUniform1f(mParamsLocation, 1.0f);
                break;
            case 2:
                GLES20.glUniform1f(mParamsLocation, 0.8f);
                break;
            case 3:
                GLES20.glUniform1f(mParamsLocation, 0.6f);
                break;
            case 4:
                GLES20.glUniform1f(mParamsLocation, 0.4f);
                break;
            case 5:
                GLES20.glUniform1f(mParamsLocation, 0.33f);
                break;
            default:
                GLES20.glUniform1f(mParamsLocation, 1.0f);
                break;
        }
    }

    public void setBeautyLevel(int level){
        switch (level) {
            case 1:
                setFloatVec4(mParamsLocation, new float[] {1.0f, 1.0f, 0.15f, 0.15f});
                break;
            case 2:
                setFloatVec4(mParamsLocation, new float[] {0.8f, 0.9f, 0.2f, 0.2f});
                break;
            case 3:
                setFloatVec4(mParamsLocation, new float[] {0.6f, 0.8f, 0.25f, 0.25f});
                break;
            case 4:
                setFloatVec4(mParamsLocation, new float[] {0.4f, 0.7f, 0.38f, 0.3f});
                break;
            case 5:
                setFloatVec4(mParamsLocation, new float[] {0.33f, 0.63f, 0.4f, 0.35f});
                break;
            default:
                setFloatVec4(mParamsLocation, new float[] {1.0f, 1.0f, 0.15f, 0.15f});
                break;
        }
    }

    protected void setFloatVec4(final int location, final float[] arrayValue) {
        GLES20.glUniform4fv(location, 1, FloatBuffer.wrap(arrayValue));
    }
}
