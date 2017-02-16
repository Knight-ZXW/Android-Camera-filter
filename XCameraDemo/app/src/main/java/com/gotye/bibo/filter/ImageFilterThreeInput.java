package com.gotye.bibo.filter;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.gotye.bibo.gles.Drawable2d;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class ImageFilterThreeInput extends ImageFilter {

    protected List<IFilter> mFilters;
    private IFilter mOutputFilter;
    protected int mIncomingWidth, mIncomingHeight;
    protected int mSurfaceWidth, mSurfaceHeight;

    private Drawable2d mDrawableFlipVertical2d;
    private Drawable2d mDrawable2d;

    private int[] mFrameBuffers;
    private int[] mRenderBuffers;
    private int[] mFrameBufferTextures;

    protected int mFrameBufferId = 0;
    protected boolean mOutput = false; // true if filter output is end-point

    public final float[] IDENTITY_MATRIX = new float[16];

    public ImageFilterThreeInput(Context context,
                                 IFilter inputFilter1, IFilter inputFilter2, IFilter inputFilter3,
                                 IFilter outputFilter) {
        super(context);

        mFilters = new ArrayList<>();
        addFilter(inputFilter1);
        addFilter(inputFilter2);
        addFilter(inputFilter3);
        mOutputFilter = outputFilter;

        mDrawableFlipVertical2d = new Drawable2d(Drawable2d.Prefab.FULL_RECTANGLE);
        mDrawable2d = new Drawable2d(Drawable2d.Prefab.FULL_RECTANGLE);

        Matrix.setIdentityM(IDENTITY_MATRIX, 0);
    }

    private void addFilter(IFilter filter) {
        if (filter == null) {
            return;
        }
        mFilters.add(filter);
    }

    @Override
    public void setSurfaceSize(int width, int height) {
        if (width == 0 || height == 0) {
            return;
        }
        if (width == mSurfaceWidth && height == mSurfaceHeight) {
            return;
        }
        mSurfaceWidth = width;
        mSurfaceHeight = height;
    }

    @Override
    public void setTextureSize(int width, int height) {
        if (width == 0 || height == 0) {
            return;
        }
        if (width == mIncomingWidth && height == mIncomingHeight) {
            return;
        }
        mIncomingWidth = width;
        mIncomingHeight = height;

        if (mFrameBuffers != null) {
            destroyFrameBuffers();
        }

        for (IFilter filter : mFilters) {
            filter.setTextureSize(width, height);
        }

        if (mSurfaceWidth == 0 || mSurfaceHeight == 0) {
            mSurfaceWidth = width;
            mSurfaceHeight = height;
        }

        int size = mFilters.size();
        mFrameBuffers = new int[size];
        mRenderBuffers = new int[size];
        mFrameBufferTextures = new int[size];

        for (int i = 0; i < size; i++) {

            ///////////////// create FrameBufferTextures
            GLES20.glGenTextures(1, mFrameBufferTextures, i);
            GlUtil.checkGlError("glGenTextures");

            GLES20.glBindTexture(getTextureTarget(), mFrameBufferTextures[i]);
            GlUtil.checkGlError("glBindTexture " + mFrameBufferTextures[i]);

            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    mIncomingWidth, mIncomingHeight, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

            GLES20.glTexParameterf(getTextureTarget(), GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameterf(getTextureTarget(), GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameterf(getTextureTarget(), GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(getTextureTarget(), GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
            GlUtil.checkGlError("glTexParameter");

            ////////////////////////// create FrameBuffer
            GLES20.glGenFramebuffers(1, mFrameBuffers, i);
            GlUtil.checkGlError("glGenFramebuffers");

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[i]);
            GlUtil.checkGlError("glBindFramebuffer " + mFrameBuffers[i]);

            ////////////////////////// create DepthBuffer
            GLES20.glGenRenderbuffers(1, mRenderBuffers, i);
            GlUtil.checkGlError("glRenderbuffers");

            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, mRenderBuffers[i]);
            GlUtil.checkGlError("glBindRenderbuffer");

            GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16,
                    mIncomingWidth, mIncomingHeight);
            GlUtil.checkGlError("glRenderbufferStorage");
            /////////////

            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                    GLES20.GL_RENDERBUFFER, mRenderBuffers[i]);
            GlUtil.checkGlError("glFramebufferRenderbuffer");

            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D /*getTextureTarget()*/, mFrameBufferTextures[i], 0);

            GlUtil.checkGlError("glFramebufferTexture2D");

            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw new RuntimeException("Framebuffer not complete, status=" + status);
            }

            // Switch back to the default framebuffer.
            GLES20.glBindTexture(getTextureTarget(), 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

            GlUtil.checkGlError("prepareFramebuffer done");
        }

        for (int i=0;i<2;i++) {
            // NOT single filter MUST setFrameBuffer, not render to DISPLAY
            IFilter filter = mFilters.get(i);
            filter.setFrameBuffer(mFrameBuffers[i]);
        }

        mOutputFilter.setTexture(
                mFrameBufferTextures[0], mFrameBufferTextures[1], mFrameBufferTextures[2]);
    }

    @Override
    public void setFrameBuffer(int FrameBufferId) {
        mFrameBufferId = FrameBufferId;
    }

    @Override
    public void setOutput(boolean ON) {
        mOutput = ON;
    }

    @Override
    public void onDraw(float[] mvpMatrix, FloatBuffer vertexBuffer, int firstVertex,
                       int vertexCount, int coordsPerVertex, int vertexStride, float[] texMatrix,
                       FloatBuffer texBuffer, int textureId, int texStride) {

        int size = mFilters.size();
        if (size != 3) {
            throw new RuntimeException("wrong filter count");
        }
        for (int i = 0;i<size;i++) {
            IFilter filter = mFilters.get(i);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[i]);
            GLES20.glClearColor(0f, 0f, 0f, 1f);

            GLES20.glViewport(0, 0, mIncomingWidth, mIncomingHeight);
            filter.onDraw(IDENTITY_MATRIX, vertexBuffer, firstVertex, vertexCount, coordsPerVertex,
                    vertexStride, IDENTITY_MATRIX, texBuffer, textureId, texStride);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBufferId);
        //GLES20.glClearColor(0f, 0f, 0f, 1f);

        if (mFrameBufferId == 0 || mOutput) {
            // render to DISPLAY with surface size
            GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
        }
        else {
            // render to fbo with texture size
            GLES20.glViewport(0, 0, mIncomingWidth, mIncomingHeight);
        }
        mOutputFilter.onDraw(mvpMatrix, vertexBuffer, firstVertex, vertexCount, coordsPerVertex,
                vertexStride, texMatrix, texBuffer, mFrameBufferTextures[0], texStride);
    }

    @Override
    public void releaseProgram() {
        destroyFrameBuffers();
        for (IFilter filter : mFilters) {
            filter.releaseProgram();
        }
    }

    private void destroyFrameBuffers() {
        if (mFrameBufferTextures != null) {
            GLES20.glDeleteTextures(mFrameBufferTextures.length, mFrameBufferTextures, 0);
            mFrameBufferTextures = null;
        }
        if (mFrameBuffers != null) {
            GLES20.glDeleteFramebuffers(mFrameBuffers.length, mFrameBuffers, 0);
            mFrameBuffers = null;
        }
        if (mRenderBuffers != null) {
            GLES20.glDeleteRenderbuffers(mRenderBuffers.length, mRenderBuffers, 0);
            mRenderBuffers = null;
        }
    }
}
