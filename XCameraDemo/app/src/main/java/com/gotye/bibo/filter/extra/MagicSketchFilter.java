package com.gotye.bibo.filter.extra;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.filter.CameraFilter;
import com.gotye.bibo.gles.GlUtil;
import com.gotye.bibo.util.LogUtil;

import java.nio.FloatBuffer;

/**
 * Created by zhuoxiuwu on 2017/2/24.
 * email nimdanoob@gmail.com
 */

public class MagicSketchFilter extends CameraFilter {
    public MagicSketchFilter(Context applicationContext) {
        super(applicationContext);
    }

    private int mSingleStepOffsetLocation;
    //0.0 - 1.0
    private int mStrengthLocation;

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.sketch);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();
        mSingleStepOffsetLocation = GLES20.glGetUniformLocation(mProgramHandle, "singleStepOffset");
        GlUtil.checkLocation(mSingleStepOffsetLocation,"singleStepOffset");
        mStrengthLocation = GLES20.glGetUniformLocation(mProgramHandle, "strength");
        GlUtil.checkLocation(mStrengthLocation,"strength");

    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex, int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix, texBuffer, texStride);
        LogUtil.error("MagicSketchFilter","bindGlsValues");
//        GLES20.glUniform1f(mStrengthLocation, 0.5f);
//        GLES20.glUniform2fv(mSingleStepOffsetLocation, 1, FloatBuffer.wrap(new float[]{1.0f / mIncomingWidth, 1.0f / mIncomingHeight}));
    }
}
