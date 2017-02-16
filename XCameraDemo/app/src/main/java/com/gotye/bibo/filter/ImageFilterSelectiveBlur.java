package com.gotye.bibo.filter;

import android.content.Context;
import android.graphics.PointF;
import android.opengl.GLES20;

import com.gotye.bibo.R;
import com.gotye.bibo.gles.GlUtil;
import com.gotye.bibo.util.LogUtil;

import java.nio.FloatBuffer;
import java.util.Locale;

/**
 * Created by Michael.Ma on 2016/8/16.
 */
public class ImageFilterSelectiveBlur extends ImageFilter {
    private final static String TAG = "ImageFilterSelectiveBlur";

    private int muExcludeCircleRadiusLocation;
    private float muExcludeCircleRadius;
    private int muExcludeCirclePointLocation;
    private PointF muExcludeCirclePoint;
    private int muExcludePoint1Location;
    private PointF muExcludePoint1;
    private int muExcludePoint2Location;
    private PointF muExcludePoint2;
    private int muExcludeBlurSizeLocation;
    private float muExcludeBlurSize;
    private int muAspectRatioLocation;
    private float muAspectRatio = 1.0f;
    private int muBlurModeLocation;
    private int muBlurMode = 0;

    private int mTexture2Loc;

    private int mTextureId;
    private int mTexture2Id;

    public ImageFilterSelectiveBlur(Context applicationContext,
                                    float excludeCircleRadius,
                                    PointF excludePoint1,
                                    PointF excludePoint2,
                                    float excludeBlurSize) {
        super(applicationContext);

        muExcludeCircleRadius = excludeCircleRadius;
        muExcludePoint1 = excludePoint1;
        muExcludePoint2 = excludePoint2;
        muExcludeBlurSize = excludeBlurSize;

        muBlurMode = 1;
    }

    public ImageFilterSelectiveBlur(Context applicationContext,
                             float excludeCircleRadius,
                             PointF excludeCirclePoint,
                             float excludeBlurSize) {
        super(applicationContext);

        muExcludeCircleRadius = excludeCircleRadius;
        muExcludeCirclePoint = excludeCirclePoint;
        muExcludeBlurSize = excludeBlurSize;

        muBlurMode = 0;
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_2d_selective_blur);
    }

    @Override
    public void setTexture(int texture1, int texture2, int texture3) {
        this.mTextureId     = texture1;
        this.mTexture2Id    = texture2;

        LogUtil.info(TAG, String.format(Locale.US, "setTexture() %d %d",
                texture1, texture2));
    }

    @Override
    public void setTextureSize(int width, int height) {
        super.setTextureSize(width, height);

        if (mIncomingWidth != 0 && mIncomingHeight != 0) {
            muAspectRatio = mIncomingWidth / mIncomingHeight;
            LogUtil.info(TAG, String.format(Locale.US,
                    "set muAspectRatio to: %.3f", muAspectRatio));
        }
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        mTexture2Loc = GLES20.glGetUniformLocation(mProgramHandle, "uTexture2");
        GlUtil.checkLocation(mTexture2Loc, "uTexture2");

        muExcludeCircleRadiusLocation = GLES20.glGetUniformLocation(mProgramHandle, "uExcludeCircleRadius");
        GlUtil.checkLocation(muExcludeCircleRadiusLocation, "uExcludeCircleRadius");
        muExcludeCirclePointLocation = GLES20.glGetUniformLocation(mProgramHandle, "uExcludeCirclePoint");
        GlUtil.checkLocation(muExcludeCirclePointLocation, "uExcludeCirclePoint");
        muExcludePoint1Location = GLES20.glGetUniformLocation(mProgramHandle, "uExcludePoint1");
        GlUtil.checkLocation(muExcludePoint1Location, "uExcludePoint1");
        muExcludePoint2Location = GLES20.glGetUniformLocation(mProgramHandle, "uExcludePoint2");
        GlUtil.checkLocation(muExcludePoint2Location, "uExcludePoint2");
        muExcludeBlurSizeLocation = GLES20.glGetUniformLocation(mProgramHandle, "uExcludeBlurSize");
        GlUtil.checkLocation(muExcludeBlurSizeLocation, "uExcludeBlurSize");
        muAspectRatioLocation = GLES20.glGetUniformLocation(mProgramHandle, "uAspectRatio");
        GlUtil.checkLocation(muAspectRatioLocation, "uAspectRatio");
        muBlurModeLocation = GLES20.glGetUniformLocation(mProgramHandle, "uBlurMode");
        GlUtil.checkLocation(muBlurModeLocation, "uBlurMode");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {

        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        if (muBlurMode == 0) {
            float []point = new float[]{muExcludeCirclePoint.x, muExcludeCirclePoint.y};
            GLES20.glUniform2fv(muExcludeCirclePointLocation, 1, FloatBuffer.wrap(point));
        }
        else {
            float []point1 = new float[]{muExcludePoint1.x, muExcludePoint1.y};
            float []point2 = new float[]{muExcludePoint2.x, muExcludePoint2.y};
            GLES20.glUniform2fv(muExcludePoint1Location, 1, FloatBuffer.wrap(point1));
            GLES20.glUniform2fv(muExcludePoint2Location, 1, FloatBuffer.wrap(point2));
        }

        GLES20.glUniform1f(muExcludeCircleRadiusLocation, muExcludeCircleRadius);
        GLES20.glUniform1f(muExcludeBlurSizeLocation, muExcludeBlurSize);
        GLES20.glUniform1f(muAspectRatioLocation, muAspectRatio);
        GLES20.glUniform1i(muBlurModeLocation, muBlurMode);

    }

    @Override
    protected void bindTexture(int textureId) {
        super.bindTexture(textureId);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(getTextureTarget(), mTexture2Id);
        GLES20.glUniform1i(mTexture2Loc, 1); // GL_TEXTURE1 -> 1
    }
}
