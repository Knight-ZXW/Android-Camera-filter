package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;

/**
 * Created by Michael.Ma on 2016/6/4.
 */
public class CameraFilterCartoon extends CameraFilter {
    private int mTouchXOffsetLocation;
    private int mEdgeThresLocation;
    private int mEdgeThres2Location;
    private int mTexWidthLocation;
    private int mTexHeightLocation;

    private float touch_x_offset = -1.0f;
    private float edge_thres = 0.2f;
    private float edge_thres2 = 5.0f;

    public CameraFilterCartoon(Context applicationContext) {
        super(applicationContext);
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_ext_cartoon);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        mTouchXOffsetLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "touch_x_offset");
        GlUtil.checkLocation(mTouchXOffsetLocation, "touch_x_offset");
        /*mEdgeThresLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "edge_thres");
        GlUtil.checkLocation(mEdgeThresLocation, "edge_thres");
        mEdgeThres2Location = GLES20.glGetUniformLocation(mProgramHandle,
                "edge_thres2");
        GlUtil.checkLocation(mEdgeThres2Location, "edge_thres2");*/
        mTexWidthLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "u_texWidth");
        GlUtil.checkLocation(mTexWidthLocation, "u_texWidth");
        mTexHeightLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "u_texHeight");
        GlUtil.checkLocation(mTexHeightLocation, "u_texHeight");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        GLES20.glUniform1f(mTouchXOffsetLocation, touch_x_offset);
        //GLES20.glUniform1f(mEdgeThresLocation, edge_thres);
        //GLES20.glUniform1f(mEdgeThres2Location, edge_thres2);

        GLES20.glUniform1f(mTexWidthLocation, (float)mIncomingWidth);
        GLES20.glUniform1f(mTexHeightLocation, (float)mIncomingHeight);
    }
}
