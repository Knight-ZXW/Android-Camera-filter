package com.gotye.bibo.filter;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.gotye.bibo.gles.Drawable2d;
import com.gotye.bibo.gles.GlUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class FilterGroup<T extends IFilter> implements IFilter {

    protected List<T> mFilters;
    protected int mIncomingWidth, mIncomingHeight;
    protected int mSurfaceWidth, mSurfaceHeight;

    private Drawable2d mDrawableFlipVertical2d;
    private Drawable2d mDrawable2d;

    protected int[] mFrameBuffers;
    protected int[] mRenderBuffers;
    protected int[] mFrameBufferTextures;

    protected int mFrameBufferId = 0;
    protected boolean mOutput = false; // true if filter output is end-point

    public final float[] IDENTITY_MATRIX = new float[16];

    public FilterGroup() {
        this(null);
    }

    public FilterGroup(List<T> filters) {
        if (filters != null) {
            mFilters = filters;
        } else {
            mFilters = new ArrayList<>();
        }
        mDrawableFlipVertical2d = new Drawable2d(Drawable2d.Prefab.FULL_RECTANGLE);
        mDrawable2d = new Drawable2d(Drawable2d.Prefab.FULL_RECTANGLE);

        Matrix.setIdentityM(IDENTITY_MATRIX, 0);
    }

    public void addFilter(T filter) {
        if (filter == null) {
            return;
        }
        mFilters.add(filter);
    }

    @Override
    public int getTextureTarget() {
        //return GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
        return GLES20.GL_TEXTURE_2D;
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

        for (T filter : mFilters) {
            filter.setTextureSize(width, height);
        }

        if (mSurfaceWidth == 0 || mSurfaceHeight == 0) {
            mSurfaceWidth = width;
            mSurfaceHeight = height;
        }

        int size = mFilters.size();
        mFrameBuffers = new int[size - 1];
        mRenderBuffers = new int[size - 1];
        mFrameBufferTextures = new int[size - 1];

        for (int i = 0; i < size - 1; i++) {

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
    public void setTexture(int texture1, int texture2, int texture3) {

    }

    @Override
    public void onDraw(float[] mvpMatrix, FloatBuffer vertexBuffer, int firstVertex,
                       int vertexCount, int coordsPerVertex, int vertexStride, float[] texMatrix,
                       FloatBuffer texBuffer, int textureId, int texStride) {

        // TODO
        int size = mFilters.size();
        int previousTextureId = textureId;
        for (int i = 0; i < size; i++) {
            T filter = mFilters.get(i);
            boolean isNotLast = i < size - 1;

            if (isNotLast) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[i]);
                GLES20.glClearColor(0f, 0f, 0f, 1f);
            }

            if (i == 0) {
                GLES20.glViewport(0, 0, mIncomingWidth, mIncomingHeight);
                filter.onDraw(IDENTITY_MATRIX, vertexBuffer, firstVertex, vertexCount, coordsPerVertex,
                        vertexStride, IDENTITY_MATRIX, texBuffer, previousTextureId, texStride);
            } else if (i == size - 1) {
                if (mFrameBufferId == 0 || mOutput) {
                    // render to DISPLAY with surface size
                    // if output of filter group is end-point, should use surfacesize
                    // as viewport
                    GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
                }
                else {
                    // render to fbo with texture size
                    GLES20.glViewport(0, 0, mIncomingWidth, mIncomingHeight);
                }
                filter.onDraw(mvpMatrix, vertexBuffer, firstVertex, vertexCount, coordsPerVertex,
                        vertexStride, texMatrix, texBuffer, previousTextureId, texStride);
            } else {
                GLES20.glViewport(0, 0, mIncomingWidth, mIncomingHeight);
                filter.onDraw(IDENTITY_MATRIX, vertexBuffer, firstVertex, vertexCount, coordsPerVertex,
                        vertexStride, IDENTITY_MATRIX, texBuffer, previousTextureId, texStride);
            }

            if (isNotLast) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBufferId); // 0 - disable
                previousTextureId = mFrameBufferTextures[i];
            }
        }
    }

    @Override
    public void releaseProgram() {
        destroyFrameBuffers();
        for (T filter : mFilters) {
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
