package com.gotye.bibo.filter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.support.annotation.DrawableRes;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;

/**
 * Created by Michael.Ma on 2016/8/22.
 */
public class CameraFilterLofify extends CameraFilter {

    private int mExtraTextureId;
    private int maExtraTextureCoordLoc;
    private int muExtraTextureLoc;

    private int muiGlobalTimeLocation;
    private int muiResolutionLocation;

    private long START_TIME;

    public CameraFilterLofify(Context applicationContext, @DrawableRes int drawableId) {
        super(applicationContext);

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;    // No pre-scaling
        final Bitmap bitmap =
                BitmapFactory.decodeResource(applicationContext.getResources(), drawableId, options);
        mExtraTextureId = GlUtil.createTexture(GLES20.GL_TEXTURE_2D, bitmap, true);

        START_TIME = System.currentTimeMillis();
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader_two_input,
                R.raw.fragment_shader_ext_lofify);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        maExtraTextureCoordLoc =
                GLES20.glGetAttribLocation(mProgramHandle, "aExtraTextureCoord");
        GlUtil.checkLocation(maExtraTextureCoordLoc, "aExtraTextureCoord");
        muExtraTextureLoc =
                GLES20.glGetUniformLocation(mProgramHandle, "uExtraTexture");
        GlUtil.checkLocation(muExtraTextureLoc, "uExtraTexture");
        muiGlobalTimeLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uiGlobalTime");
        GlUtil.checkLocation(muiGlobalTimeLocation, "uiGlobalTime");
        muiResolutionLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uiResolution");
        GlUtil.checkLocation(muiResolutionLocation, "uiResolution");
    }

    @Override
    protected void bindTexture(int textureId) {
        super.bindTexture(textureId);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mExtraTextureId);
        GLES20.glUniform1i(muExtraTextureLoc, 1);
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        GLES20.glEnableVertexAttribArray(maExtraTextureCoordLoc);
        GLES20.glVertexAttribPointer(maExtraTextureCoordLoc, 2, GLES20.GL_FLOAT, false, texStride,
                texBuffer);

        float time = ((float) (System.currentTimeMillis() - START_TIME)) / 1000.0f;
        GLES20.glUniform1f(muiGlobalTimeLocation, time);

        float []resolution = new float[] {
                (float)mIncomingWidth, (float)mIncomingHeight};
        GLES20.glUniform2fv(muiResolutionLocation, 1, FloatBuffer.wrap(resolution));
    }

    @Override
    protected void unbindGLSLValues() {
        super.unbindGLSLValues();

        GLES20.glDisableVertexAttribArray(maExtraTextureCoordLoc);
    }

    @Override
    protected void unbindTexture() {
        super.unbindTexture();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }
}

