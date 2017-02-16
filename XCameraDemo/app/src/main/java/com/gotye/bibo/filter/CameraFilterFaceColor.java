package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;

/**
 * Created by Michael.Ma on 2016/6/4.
 */
public class CameraFilterFaceColor extends CameraFilter {
    private int mRedThresLocation;
    private int mBlueThresLocation;
    private int mGreenThresLocation;

    private float red_thres;
    private float green_thres;
    private float blue_thres;

    public CameraFilterFaceColor(Context applicationContext) {
        this(applicationContext, 0.3725f, 0.1568f, 0.0784f);
    }

    public CameraFilterFaceColor(Context applicationContext, float r, float g, float b) {
        super(applicationContext);

        this.red_thres = r;
        this.green_thres = g;
        this.blue_thres = b;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_ext_facecolor);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        mRedThresLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "red_thres");
        GlUtil.checkLocation(mRedThresLocation, "red_thres");
        mBlueThresLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "green_thres");
        GlUtil.checkLocation(mBlueThresLocation, "green_thres");
        mGreenThresLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "blue_thres");
        GlUtil.checkLocation(mGreenThresLocation, "blue_thres");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        GLES20.glUniform1f(mRedThresLocation, red_thres);
        GLES20.glUniform1f(mGreenThresLocation, green_thres);
        GLES20.glUniform1f(mBlueThresLocation, blue_thres);
    }
}
