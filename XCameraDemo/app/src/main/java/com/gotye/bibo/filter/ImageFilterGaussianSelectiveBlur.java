package com.gotye.bibo.filter;

import android.content.Context;
import android.graphics.PointF;
import android.opengl.GLES10;

import java.nio.FloatBuffer;

/**
 * Created by Michael.Ma on 2016/8/16.
 */
public class ImageFilterGaussianSelectiveBlur implements IFilter {
    private IFilter mFilter;

    public ImageFilterGaussianSelectiveBlur(Context context) {
        this(context, 4.0f, 0.4f, new PointF(0.5f, 0.5f), 0.2f);
    }

    public ImageFilterGaussianSelectiveBlur(Context context,
                                            float blurRadius,
                                            float excludeCircleRadius,
                                            PointF excludePoint1,
                                            PointF excludePoint2,
                                            float excludeBlurSize) {
        mFilter = new ImageFilterTwoInput(context,
                new ImageFilterGaussianBlur(context, blurRadius),
                new ImageFilter(context),
                new ImageFilterSelectiveBlur(context,
                        excludeCircleRadius, excludePoint1, excludePoint2,
                        excludeBlurSize));
    }

    public ImageFilterGaussianSelectiveBlur(Context context,
                                            float blurRadius,
                                            float excludeCircleRadius,
                                            PointF excludeCirclePoint,
                                            float excludeBlurSize) {
        mFilter = new ImageFilterTwoInput(context,
                new ImageFilterGaussianBlur(context, blurRadius),
                new ImageFilter(context),
                new ImageFilterSelectiveBlur(context,
                        excludeCircleRadius, excludeCirclePoint, excludeBlurSize));
    }

    @Override
    public int getTextureTarget() {
        return GLES10.GL_TEXTURE_2D;
    }

    @Override
    public void setTextureSize(int width, int height) {
        if (mFilter != null)
            mFilter.setTextureSize(width, height);
    }

    @Override
    public void setSurfaceSize(int width, int height) {
        if (mFilter != null)
            mFilter.setSurfaceSize(width, height);
    }

    @Override
    public void setFrameBuffer(int FrameBufferId) {
        if (mFilter != null)
            mFilter.setFrameBuffer(FrameBufferId);
    }

    @Override
    public void setOutput(boolean ON) {
        if (mFilter != null)
            mFilter.setOutput(ON);
    }

    @Override
    public void setTexture(int texture1, int texture2, int texture3) {

    }

    @Override
    public void onDraw(float[] mvpMatrix,
                       FloatBuffer vertexBuffer, int firstVertex,
                       int vertexCount, int coordsPerVertex,
                       int vertexStride, float[] texMatrix,
                       FloatBuffer texBuffer, int textureId, int texStride) {
        mFilter.onDraw(mvpMatrix, vertexBuffer, firstVertex, vertexCount, coordsPerVertex,
                vertexStride, texMatrix, texBuffer, textureId, texStride);
    }

    @Override
    public void releaseProgram() {
        if (mFilter != null)
            mFilter.releaseProgram();
    }
}
