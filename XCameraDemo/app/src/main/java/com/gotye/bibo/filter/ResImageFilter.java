package com.gotye.bibo.filter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;

import com.gotye.bibo.util.LogUtil;
import com.gotye.bibo.util.Util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Created by Michael.Ma on 2016/9/12.
 */
public class ResImageFilter extends ImageFilter {
    private final static String TAG = "ResImageFilter";

    private String mIdxPath;
    private String mDatPath;

    private Bitmap mBitmap;
    private int mResWidth, mResHeight;
    private Buffer mBuffer;

    private List<ResItem> mPicList;
    private int mPicIndex = 0;

    private long mFrameCount;

    public ResImageFilter(Context applicationContext, String idxPath, String datPath) {
        super(applicationContext);

        mIdxPath = idxPath;
        mDatPath = datPath;

        mFrameCount = 0L;

        LogUtil.info(TAG, "idx file: " + mIdxPath);
        LogUtil.info(TAG, "dat file: " + mDatPath);

        getResource();

        if (!loadBitmap()) {
            throw new RuntimeException("failed to load bitmap");
        }
    }

    @Override
    protected void bindTexture(int textureId) {
        if (mBuffer != null) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    mResWidth, mResHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
                    mBuffer);
        }

        super.bindTexture(textureId);
    }

    @Override
    public void onDraw(float[] mvpMatrix, FloatBuffer vertexBuffer,
                       int firstVertex, int vertexCount,
                       int coordsPerVertex, int vertexStride,
                       float[] texMatrix, FloatBuffer texBuffer,
                       int textureId, int texStride) {
        super.onDraw(mvpMatrix, vertexBuffer,
                firstVertex, vertexCount,
                coordsPerVertex, vertexStride,
                texMatrix, texBuffer,
                textureId, texStride);

        mFrameCount++;
        if (mFrameCount % 10 == 0)
            updateBitmap();
    }

    public int getResWidth() {
        return mResWidth;
    }

    public int getResHeight() {
        return mResHeight;
    }

    private class ResItem {
        public String filename;
        public int offset;
        public int size;

        public ResItem(String filename, int offset, int size) {
            this.filename = filename;
            this.offset = offset;
            this.size = size;
        }
    }

    private void updateBitmap() {
        mPicIndex++;
        if (mPicIndex > mPicList.size() - 1)
            mPicIndex = 0;

        LogUtil.info(TAG, "update picture index #" + mPicIndex);

        if (mBitmap != null) {
            mBitmap.recycle();
        }

        if (!loadBitmap()) {
            throw new RuntimeException("failed to load bitmap");
        }
    }

    private boolean loadBitmap() {
        if (mPicList != null && !mPicList.isEmpty() &&
                mPicIndex >= 0 && mPicIndex < mPicList.size()) {
            ResItem item = mPicList.get(mPicIndex);
            int offset = item.offset;
            int size = item.size;
            byte[] data = new byte[size];
            File f = new File(mDatPath);
            try {
                FileInputStream fis = new FileInputStream(f);
                fis.skip(offset);
                fis.read(data);
                fis.close();

                ByteArrayInputStream is = new ByteArrayInputStream(data);
                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inScaled = false;

                mBitmap = BitmapFactory.decodeStream(is, null, options);
                mResWidth = mBitmap.getWidth();
                mResHeight = mBitmap.getHeight();
                LogUtil.info(TAG, "resource: " + mBitmap.getWidth() + " x " + mBitmap.getHeight());

                int []buf = new int[mResWidth * mResHeight];
                mBitmap.getPixels(buf, 0, mBitmap.getRowBytes() / 4,
                        0, 0, mResWidth, mResHeight);
                mBuffer = IntBuffer.wrap(buf);
                return true;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    private void getResource() {
        if (mIdxPath == null || mDatPath == null)
            return;

        String file_content = Util.getFileContext(mIdxPath);
        if (file_content == null)
            return;

        StringTokenizer st = new StringTokenizer(file_content, ";", false);
        mPicList = new ArrayList<>();
        while (st.hasMoreElements()) {
            String line = st.nextToken();
            LogUtil.info(TAG, "line: " + line);
            String[]items = line.split(":");
            if (items.length >= 3) {
                String filename = items[0];
                int offset = Integer.valueOf(items[1]);
                int size = Integer.valueOf(items[2]);
                LogUtil.info(TAG, "resource name: " + filename +
                        ", offset " + offset +
                        ", size " + size);

                mPicList.add(new ResItem(filename, offset, size));
            }
        }
    }
}
