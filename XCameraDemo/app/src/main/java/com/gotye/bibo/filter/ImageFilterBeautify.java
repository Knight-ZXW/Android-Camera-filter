package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;
import com.gotye.bibo.util.LogUtil;

import java.nio.FloatBuffer;
import java.util.Locale;

/**
 * Created by Michael.Ma on 2016/6/2.
 */
public class ImageFilterBeautify extends ImageFilter {
    private final static String TAG = "ImageFilterBeautify";

    private int mTexture2Loc;
    private int mTexture3Loc;

    private int mTextureId;
    private int mTexture2Id;
    private int mTexture3Id;

    private int mSmoothDegreeLoc;

    private float mSmoothDegree = 0.5f;

    public ImageFilterBeautify(Context applicationContext) {
        super(applicationContext);
}

    @Override
    public void setTexture(int texture1, int texture2, int texture3) {
        this.mTextureId     = texture1;
        this.mTexture2Id    = texture2;
        this.mTexture3Id    = texture3;

        LogUtil.info(TAG, String.format(Locale.US, "setTexture() %d %d %d",
                texture1, texture2, texture3));
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_2d_beautify);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        mTexture2Loc = GLES20.glGetUniformLocation(mProgramHandle, "uTexture2");
        GlUtil.checkLocation(mTexture2Loc, "uTexture2");
        mTexture3Loc = GLES20.glGetUniformLocation(mProgramHandle, "uTexture3");
        GlUtil.checkLocation(mTexture3Loc, "uTexture3");

        mSmoothDegreeLoc = GLES20.glGetUniformLocation(mProgramHandle, "uSmoothDegree");
        GlUtil.checkLocation(mSmoothDegreeLoc, "uSmoothDegree");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {

        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        GLES20.glUniform1f(mSmoothDegreeLoc, mSmoothDegree);
    }

    @Override
    protected void bindTexture(int textureId) {
        super.bindTexture(textureId);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(getTextureTarget(), mTexture2Id);
        GLES20.glUniform1i(mTexture2Loc, 1); // GL_TEXTURE1 -> 1

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(getTextureTarget(), mTexture3Id);
        GLES20.glUniform1i(mTexture3Loc, 2); // GL_TEXTURE2 -> 2
    }
}
