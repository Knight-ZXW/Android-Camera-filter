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
 * Created by Michael.Ma on 2016/8/23.
 */
public class CameraFilterBulgeDistortion extends CameraFilter {
    private final static String TAG = "CameraFilterBulgeDistortion";

    private int muAspectRatioLocation;
    private float muAspectRatio = 1f;
    private int muCenterLocation;
    private PointF muCenter;
    private int muRadiusLocation;
    private float muRadius = 0.25f;
    private int muScaleLocation;
    private float muScale = 0.5f;

    public CameraFilterBulgeDistortion(Context applicationContext) {
        this(applicationContext, 0.25f, 0.5f, new PointF(0.5f, 0.5f));
    }

    public CameraFilterBulgeDistortion(Context applicationContext,
                                       float radius, float scale, PointF center) {
        super(applicationContext);

        muRadius    = radius;
        muScale     = scale;
        muCenter    = center;

        LogUtil.info(TAG, String.format(Locale.US,
                "radius: %.2f, scale %.2f, center (%.2f, %.2f)",
                radius, scale, center.x, center.y));
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_ext_bulge_distortion);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        muAspectRatioLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uAspectRatio");
        GlUtil.checkLocation(muAspectRatioLocation, "uAspectRatio");
        muCenterLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uCenter");
        GlUtil.checkLocation(muCenterLocation, "uCenter");
        muRadiusLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uRadius");
        GlUtil.checkLocation(muRadiusLocation, "uRadius");
        muScaleLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uScale");
        GlUtil.checkLocation(muScaleLocation, "uScale");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        if (mIncomingWidth != 0 && mIncomingHeight != 0) {
            muAspectRatio = (float)mIncomingHeight / (float)mIncomingWidth;
        }
        float []center = new float[] {muCenter.x, muCenter.y};

        GLES20.glUniform1f(muAspectRatioLocation, muAspectRatio);
        GLES20.glUniform2fv(muCenterLocation, 1, FloatBuffer.wrap(center));
        GLES20.glUniform1f(muRadiusLocation, muRadius);
        GLES20.glUniform1f(muScaleLocation, muScale);
    }
}
