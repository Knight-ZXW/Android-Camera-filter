/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gotye.bibo.gles;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.FloatBuffer;

/**
 * GL program and supporting functions for textured 2D shapes.
 */
public class Texture2dProgram {
    private static final String TAG = "Texture2dProgram";

    public enum ProgramType {
        TEXTURE_2D, TEXTURE_EXT, TEXTURE_EXT_BW, TEXTURE_EXT_FILT
    }

    private static final String BILATERAL_VERTEX_SHADER =
            "#define SAMPLES 9 \n" +
                    " \n" +
                    "uniform mat4 uMVPMatrix; // MVP 的变换矩阵（整体变形） \n" +
                    "uniform mat4 uSTMatrix; // Texture 的变换矩阵 （只对texture变形） \n" +
                    " \n" +
                    "uniform vec2 singleStepOffset; \n" +
                    " \n" +
                    "attribute vec4 aPosition; \n" +
                    "attribute vec4 aTextureCoord; \n" +
                    " \n" +
                    "varying vec2 vTextureCoord; \n" +
                    "varying vec2 vBlurTextureCoord[SAMPLES]; \n" +
                    " \n" +
                    " \n" +
                    "void main() { \n" +
                    " gl_Position = uMVPMatrix * aPosition; \n" +
                    " vTextureCoord = (uSTMatrix * aTextureCoord).xy; \n" +
                    " \n" +
                    " int multiplier = 0; \n" +
                    " vec2 blurStep; \n" +
                    " \n" +
                    " for (int i = 0; i < SAMPLES; i++) \n" +
                    " { \n" +
                    " multiplier = (i - ((SAMPLES-1) / 2)); \n" +
                    " // ToneCurve in x (horizontal) \n" +
                    " blurStep = float(multiplier) * singleStepOffset; \n" +
                    " vBlurTextureCoord[i] = vTextureCoord + blurStep; \n" +
                    " } \n" +
                    "}";

    private static final String BILATERAL_2D_FRAGMENT_SHADER =
            "precision mediump float; \n" +
                    " \n" +
                    "const lowp int GAUSSIAN_SAMPLES = 9; \n" +
                    " \n" +
                    "varying highp vec2 vTextureCoord; \n" +
                    "varying highp vec2 vBlurTextureCoord[GAUSSIAN_SAMPLES]; \n" +
                    " \n" +
                    "uniform mediump float distanceNormalizationFactor; \n" +
                    " \n" +
                    "uniform sampler2D uTexture; \n" +
                    " \n" +
                    "void main() \n" +
                    "{ \n" +
                    " lowp vec4 centralColor; \n" +
                    " lowp float gaussianWeightTotal; \n" +
                    " lowp vec4 sum; \n" +
                    " lowp vec4 sampleColor; \n" +
                    " lowp float distanceFromCentralColor; \n" +
                    " lowp float gaussianWeight; \n" +
                    " \n" +
                    " centralColor = texture2D(uTexture, vBlurTextureCoord[4]); \n" +
                    " gaussianWeightTotal = 0.18; \n" +
                    " sum = centralColor * 0.18; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[0]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.05 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[1]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.09 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[2]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.12 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[3]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.15 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[5]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.15 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[6]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.12 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[7]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.09 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[8]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.05 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " gl_FragColor = sum / gaussianWeightTotal; \n" +
                    "}";

    private static final String BILATERAL_EXT_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require \n" +
                    "precision mediump float; \n" +
                    " \n" +
                    "const lowp int GAUSSIAN_SAMPLES = 9; \n" +
                    " \n" +
                    "varying highp vec2 vTextureCoord; \n" +
                    "varying highp vec2 vBlurTextureCoord[GAUSSIAN_SAMPLES]; \n" +
                    " \n" +
                    "uniform mediump float distanceNormalizationFactor; \n" +
                    " \n" +
                    "uniform samplerExternalOES uTexture; \n" +
                    " \n" +
                    "void main() \n" +
                    "{ \n" +
                    " lowp vec4 centralColor; \n" +
                    " lowp float gaussianWeightTotal; \n" +
                    " lowp vec4 sum; \n" +
                    " lowp vec4 sampleColor; \n" +
                    " lowp float distanceFromCentralColor; \n" +
                    " lowp float gaussianWeight; \n" +
                    " \n" +
                    " centralColor = texture2D(uTexture, vBlurTextureCoord[4]); \n" +
                    " gaussianWeightTotal = 0.18; \n" +
                    " sum = centralColor * 0.18; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[0]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.05 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[1]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.09 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[2]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.12 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[3]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.15 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[5]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.15 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[6]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.12 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[7]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.09 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " \n" +
                    " sampleColor = texture2D(uTexture, vBlurTextureCoord[8]); \n" +
                    " distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0); \n" +
                    " gaussianWeight = 0.05 * (1.0 - distanceFromCentralColor); \n" +
                    " gaussianWeightTotal += gaussianWeight; \n" +
                    " sum += sampleColor * gaussianWeight; \n" +
                    " gl_FragColor = sum / gaussianWeightTotal; \n" +
                    "}";

    // Handles to the GL program and various components of it.
    private float[] mIdentityMatrix = new float[16];

    private int []mProgram = new int[2];
    private int muMVPMatrixHandle;
    private int muSTMatrixHandle;
    private int maPositionHandle;
    private int maTextureHandle;

    private int mIncomingWidth = 640;
    private int mIncomingHeight = 480;
    private int mDisFactorLocation;
    private int mSingleStepOffsetLocation;
    private float mDistanceNormalizationFactor = 6f;
    private float mBlurRatio = 3f;

    private int[] mFrameBuffers;
    private int[] mRenderBuffers;
    private int[] mFrameBufferTextures;

    private void bindGLSLValues(float[] mvpMatrix, FloatBuffer vertexBuffer, int firstVertex,
                                int vertexCount, int coordsPerVertex, int vertexStride,
                                float[] texMatrix, FloatBuffer texBuffer, int textureId, int texStride,
                                boolean widthOrHeight) {

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionHandle, coordsPerVertex,
                GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);
        GlUtil.checkGlError("glVertexAttribPointer");

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureHandle);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureHandle, 2,
                GLES20.GL_FLOAT, false, texStride, texBuffer);
        GlUtil.checkGlError("glVertexAttribPointer");

        Matrix.setIdentityM(mIdentityMatrix, 0);
        if (widthOrHeight) {
            GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mvpMatrix, 0);
            GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, texMatrix, 0);
        }
        else {
            GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mIdentityMatrix, 0);
            GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mIdentityMatrix, 0);
        }

        // added
        GLES20.glUniform1f(mDisFactorLocation, mDistanceNormalizationFactor);
        float []stepOffset = null;
        if (widthOrHeight)
            stepOffset = new float[] {mBlurRatio / (float)mIncomingWidth, 0f};
        else
            stepOffset = new float[] {0f, mBlurRatio / (float)mIncomingHeight};
        GLES20.glUniform2fv(mSingleStepOffsetLocation, 1, FloatBuffer.wrap(stepOffset));
    }

    private void getGLSLValues(int programHandle) {
        maPositionHandle = GLES20.glGetAttribLocation(programHandle, "aPosition");
        GlUtil.checkLocation(maPositionHandle, "aPosition");
        maTextureHandle = GLES20.glGetAttribLocation(programHandle, "aTextureCoord");
        GlUtil.checkLocation(maTextureHandle, "aTextureCoord");

        muMVPMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix");
        GlUtil.checkLocation(muMVPMatrixHandle, "uMVPMatrix");
        muSTMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uSTMatrix");
        GlUtil.checkLocation(muSTMatrixHandle, "uSTMatrix");

        // added
        mDisFactorLocation = GLES20.glGetUniformLocation(programHandle,
                "distanceNormalizationFactor");
        mSingleStepOffsetLocation = GLES20.glGetUniformLocation(programHandle,
                "singleStepOffset");
    }

    private void createOffScreen() {
        mFrameBuffers = new int[1];
        mRenderBuffers = new int[1];
        mFrameBufferTextures = new int[1];

        ///////////////// create FrameBufferTextures
        GLES20.glGenTextures(1, mFrameBufferTextures, 0);
        GlUtil.checkGlError("glGenTextures");

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrameBufferTextures[0]);
        GlUtil.checkGlError("glBindTexture " + mFrameBufferTextures[0]);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                mIncomingWidth, mIncomingHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GlUtil.checkGlError("glTexParameter");

        ////////////////////////// create FrameBuffer
        GLES20.glGenFramebuffers(1, mFrameBuffers, 0);
        GlUtil.checkGlError("glGenFramebuffers");

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
        GlUtil.checkGlError("glBindFramebuffer " + mFrameBuffers[0]);

        ////////////////////////// create DepthBuffer
        GLES20.glGenRenderbuffers(1, mRenderBuffers, 0);
        GlUtil.checkGlError("glRenderbuffers");

        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, mRenderBuffers[0]);
        GlUtil.checkGlError("glBindRenderbuffer");

        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16,
                mIncomingWidth, mIncomingHeight);
        GlUtil.checkGlError("glRenderbufferStorage");
        /////////////

        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                GLES20.GL_RENDERBUFFER, mRenderBuffers[0]);
        GlUtil.checkGlError("glFramebufferRenderbuffer");

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mFrameBufferTextures[0], 0);

        GlUtil.checkGlError("glFramebufferTexture2D");

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer not complete, status=" + status);
        }

        // Switch back to the default framebuffer.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        GlUtil.checkGlError("prepareFramebuffer done");
    }

    /**
     * Prepares the program in the current EGL context.
     */
    public Texture2dProgram(ProgramType programType, int incomingWidth, int incommingHeight) {
        mIncomingWidth = incomingWidth;
        mIncomingHeight = incommingHeight;

        mProgram[0] = GlUtil.createProgram(BILATERAL_VERTEX_SHADER, BILATERAL_EXT_FRAGMENT_SHADER);
        if (mProgram[0] == 0) {
            throw new RuntimeException("failed creating program 0");
        }

        mProgram[1] = GlUtil.createProgram(BILATERAL_VERTEX_SHADER, BILATERAL_2D_FRAGMENT_SHADER);
        if (mProgram[1] == 0) {
            throw new RuntimeException("failed creating program 1");
        }

        createOffScreen();
    }

    /**
     * Releases the program.
     * <p>
     * The appropriate EGL context must be current (i.e. the one that was used to create
     * the program).
     */
    public void release() {
        for (int i = 0; i < mProgram.length; i++) {
            GLES20.glDeleteProgram(mProgram[i]);
            mProgram[i] = -1;
        }
    }

    /**
     * Returns the program type.
     */
    public ProgramType getProgramType() {
        return ProgramType.TEXTURE_EXT;
    }

    /**
     * Creates a texture object suitable for use with this program.
     * <p>
     * On exit, the texture will be bound.
     */
    public int createTextureObject() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GlUtil.checkGlError("glGenTextures");

        int texId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
        GlUtil.checkGlError("glBindTexture " + texId);

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GlUtil.checkGlError("glTexParameter");

        return texId;
    }

    /**
     * Sets the size of the texture.  This is used to find adjacent texels when filtering.
     */
    public void setTexSize(int width, int height) {
        float rw = 1.0f / width;
        float rh = 1.0f / height;
    }

    /**
     * Issues the draw call.  Does the full setup on every call.
     *
     * @param mvpMatrix The 4x4 projection matrix.
     * @param vertexBuffer Buffer with vertex position data.
     * @param firstVertex Index of first vertex to use in vertexBuffer.
     * @param vertexCount Number of vertices in vertexBuffer.
     * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
     * @param vertexStride Width, in bytes, of the position data for each vertex (often
     *        vertexCount * sizeof(float)).
     * @param texMatrix A 4x4 transformation matrix for texture coords.  (Primarily intended
     *        for use with SurfaceTexture.)
     * @param texBuffer Buffer with vertex texture data.
     * @param texStride Width, in bytes, of the texture data for each vertex.
     */
    public void draw(float[] mvpMatrix, FloatBuffer vertexBuffer, int firstVertex,
                     int vertexCount, int coordsPerVertex, int vertexStride,
                     float[] texMatrix, FloatBuffer texBuffer, int textureId, int texStride) {
        GlUtil.checkGlError("onDrawFrame start");

        // program 0
        GLES20.glUseProgram(mProgram[0]);
        GlUtil.checkGlError("glUseProgram 0");

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
        GLES20.glClearColor(0, 0, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);

        getGLSLValues(mProgram[0]);

        bindGLSLValues(mvpMatrix, vertexBuffer, firstVertex, vertexCount, coordsPerVertex, vertexStride,
                texMatrix, texBuffer, textureId, texStride, false);

        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GlUtil.checkGlError("glDrawArrays 0");

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        int previousTextureId = mFrameBufferTextures[0];

        // program 1
        GLES20.glUseProgram(mProgram[1]);
        GlUtil.checkGlError("glUseProgram 1");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTextureId);

        getGLSLValues(mProgram[1]);

        bindGLSLValues(mvpMatrix, vertexBuffer, firstVertex, vertexCount, coordsPerVertex, vertexStride,
                texMatrix, texBuffer, textureId, texStride, true);

        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GlUtil.checkGlError("glDrawArrays 1");

        // IMPORTANT: on some devices, if you are sharing the external texture between two
        // contexts, one context may not see updates to the texture unless you un-bind and
        // re-bind it.  If you're not using shared EGL contexts, you don't need to bind
        // texture 0 here.
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
    }
}
