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

import android.graphics.Bitmap;
import android.opengl.GLES11Ext;
import android.opengl.Matrix;

import com.gotye.bibo.filter.IFilter;


/**
 * This class essentially represents a viewport-sized sprite that will be rendered with
 * a texture, usually from an external source like the camera or video decoder.
 */
public class FullFrameRect {
    private final Drawable2d mRectDrawable = new Drawable2d(Drawable2d.Prefab.FULL_RECTANGLE);
    private IFilter mFilter;
    public final float[] IDENTITY_MATRIX = new float[16];

    /**
     * Prepares the object.
     *
     * @param program The program to use.  FullFrameRect takes ownership, and will release
     * the program when no longer needed.
     */
    public FullFrameRect(IFilter program) {
        mFilter = program;
        Matrix.setIdentityM(IDENTITY_MATRIX, 0);
    }

    /**
     * Releases resources.
     * <p>
     * This must be called with the appropriate EGL context current (i.e. the one that was
     * current when the constructor was called).  If we're about to destroy the EGL context,
     * there's no value in having the caller make it current just to do this cleanup, so you
     * can pass a flag that will tell this function to skip any EGL-context-specific cleanup.
     */
    public void release(boolean doEglCleanup) {
        if (mFilter != null) {
            if (doEglCleanup) {
                mFilter.releaseProgram();
            }
            mFilter = null;
        }
    }

    /**
     * Returns the program currently in use.
     */
    public IFilter getFilter() {
        return mFilter;
    }

    /**
     * Changes the program.  The previous program will be released.
     * <p>
     * The appropriate EGL context must be current.
     */
    public void changeProgram(IFilter newFilter) {
        mFilter.releaseProgram();
        mFilter = newFilter;
    }

    public int createTextureObject() {
        return GlUtil.createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
    }

    /**
     * Creates a texture object suitable for use with drawFrame().
     */
    public int createTexture() {
        return GlUtil.createTexture(mFilter.getTextureTarget());
    }

    /**
     *
     * @param bitmap
     * @return
     */
    public int createTexture(Bitmap bitmap) {
        return GlUtil.createTexture(mFilter.getTextureTarget(), bitmap);
    }

    //-----------gif 做个缓存吧------------------
    public int createGifTexture(Bitmap bitmap){
        return -1;
    }
    //-----------end------------------------------

    public int createTexture(String text, int textsize) {
        return GlUtil.createTextureWithTextContent(text, textsize);
    }

    public void resetMVPMatrix() {
        Matrix.setIdentityM(IDENTITY_MATRIX, 0);
    }

    public void scaleMVPMatrix(float x, float y) {
        Matrix.scaleM(IDENTITY_MATRIX, 0, x, y, 1f);
    }

    public void translateMVPMatrix(float x, float y) {
        Matrix.translateM(IDENTITY_MATRIX, 0, x, y, 0);
    }

    public void rotateMVPMatrix(float degree) {
        Matrix.rotateM(IDENTITY_MATRIX, 0, degree, 0, 0, 1);
    }

    /**
     * Draws a viewport-filling rect, texturing it with the specified texture object.
     */

    public void drawFrame(int textureId, float[] texMatrix) {

        // Use the identity matrix for MVP so our 2x2 FULL_RECTANGLE covers the viewport.
        mFilter.onDraw(IDENTITY_MATRIX, mRectDrawable.getVertexArray(), 0,
                mRectDrawable.getVertexCount(), mRectDrawable.getCoordsPerVertex(),
                mRectDrawable.getVertexStride(), texMatrix, mRectDrawable.getTexCoordArray(),
                textureId, mRectDrawable.getTexCoordStride());
    }
}
