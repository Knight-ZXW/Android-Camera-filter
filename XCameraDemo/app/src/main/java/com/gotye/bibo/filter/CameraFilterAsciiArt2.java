package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;

/**
 * Created by Michael.Ma on 2016/8/29.
 */
public class CameraFilterAsciiArt2 extends CameraFilter {
    private int muiResolutionLocation;

    public CameraFilterAsciiArt2(Context applicationContext) {
        super(applicationContext);
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_ext_ascii_art2);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        muiResolutionLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uiResolution");
        GlUtil.checkLocation(muiResolutionLocation, "uiResolution");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        float []resolution = new float[] {
                (float)mIncomingWidth, (float)mIncomingHeight};
        GLES20.glUniform2fv(muiResolutionLocation, 1, FloatBuffer.wrap(resolution));
    }
}
