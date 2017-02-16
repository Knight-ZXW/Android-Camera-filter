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
 * Created by Michael.Ma on 2016/9/5.
 */
public class CameraFilterChromaKeyBlend extends CameraFilter {
    private int mExtraTextureId;
    private float muThresholdSensitivity;
    private float muSmoothing;
    private float mRed, mGreen, mBlue;

    private int maExtraTextureCoordLoc;
    private int muExtraTextureLoc;
    private int muThresholdSensitivityLoc;
    private int muSmoothingLoc;
    private int muColorToReplaceLoc;

    public CameraFilterChromaKeyBlend(Context applicationContext,
                                      float red, float green, float blue,
                                      @DrawableRes int drawableId,
                                      float thresholdSensitivity,
                                      float smoothing) {
        super(applicationContext);

        mRed = red;
        mGreen = green;
        mBlue = blue;

        muThresholdSensitivity = thresholdSensitivity;
        muSmoothing = smoothing;

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;    // No pre-scaling
        final Bitmap bitmap =
                BitmapFactory.decodeResource(applicationContext.getResources(),
                        drawableId, options);
        mExtraTextureId = GlUtil.createTexture(GLES20.GL_TEXTURE_2D, bitmap);
        bitmap.recycle();
    }

    public CameraFilterChromaKeyBlend(Context applicationContext,
                                      float red, float green, float blue,
                                      @DrawableRes int drawableId) {
        this(applicationContext, red, green, blue, drawableId, 0.4f, 0.1f);
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader_two_input,
                R.raw.fragment_shader_ext_chromakeyblend);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();
        maExtraTextureCoordLoc = GLES20.glGetAttribLocation(mProgramHandle, "aExtraTextureCoord");
        GlUtil.checkLocation(maExtraTextureCoordLoc, "aExtraTextureCoord");
        muExtraTextureLoc = GLES20.glGetUniformLocation(mProgramHandle, "uExtraTexture");
        GlUtil.checkLocation(muExtraTextureLoc, "uExtraTexture");
        muThresholdSensitivityLoc = GLES20.glGetUniformLocation(mProgramHandle, "uThresholdSensitivity");
        GlUtil.checkLocation(muThresholdSensitivityLoc, "uThresholdSensitivity");
        muSmoothingLoc = GLES20.glGetUniformLocation(mProgramHandle, "uSmoothing");
        GlUtil.checkLocation(muSmoothingLoc, "uSmoothing");
        muColorToReplaceLoc = GLES20.glGetUniformLocation(mProgramHandle, "uColorToReplace");
        GlUtil.checkLocation(muColorToReplaceLoc, "uColorToReplace");
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

        float []color = new float[] {mRed, mGreen, mBlue};
        GLES20.glUniform1f(muThresholdSensitivityLoc, muThresholdSensitivity);
        GLES20.glUniform1f(muSmoothingLoc, muSmoothing);
        GLES20.glUniform3fv(muColorToReplaceLoc, 1, FloatBuffer.wrap(color));
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
