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
public class CameraFilterStretchDistortion extends CameraFilter {
    private final static String TAG = "CameraFilterStretchDistortion";

    private int muCenterLocation;
    private PointF muCenter;

    public CameraFilterStretchDistortion(Context applicationContext) {
        this(applicationContext, new PointF(0.5f, 0.5f));
    }

    public CameraFilterStretchDistortion(Context applicationContext,
                                         PointF center) {
        super(applicationContext);

        muCenter    = center;

        LogUtil.info(TAG, String.format(Locale.US,
                "center (%.2f, %.2f)", center.x, center.y));
    }

    @Override
    protected int createProgram(Context applicationContext) {
        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader,
                R.raw.fragment_shader_ext_stretch_distortion);
    }

    @Override
    protected void getGLSLValues() {
        super.getGLSLValues();

        muCenterLocation = GLES20.glGetUniformLocation(mProgramHandle,
                "uCenter");
    }

    @Override
    protected void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int coordsPerVertex,
                                  int vertexStride, float[] texMatrix, FloatBuffer texBuffer, int texStride) {
        super.bindGLSLValues(mvpMatrix, vertexBuffer, coordsPerVertex, vertexStride, texMatrix,
                texBuffer, texStride);

        float []center = new float[] {muCenter.x, muCenter.y};
        GLES20.glUniform2fv(muCenterLocation, 1, FloatBuffer.wrap(center));
    }
}
