package com.gotye.bibo.camera.waterHolder;

import android.content.Context;
import android.graphics.Bitmap;

import com.gotye.bibo.gles.FullFrameRect;

/**
 * Created by zhuoxiuwu on 2017/2/24.
 * email nimdanoob@gmail.com
 */

public abstract class WaterFrameHolder {
    protected Bitmap mBitmap;
    FullFrameRect mFullFrameRect;
    protected float width;
    protected float height;
    protected float left;
    protected float top;
    protected int texturedId;
    protected Context mContext;
    protected final float[] IDENTITY_MATRIX = new float[16];

    public WaterFrameHolder(Bitmap bitmap, Context context, int width, int height, int left, int top) {
        mBitmap = bitmap;
        this.width = width;
        this.height = height;
        this.left = left;
        this.top = top;
        mContext = context;
    }
    protected WaterFrameHolder(){}

    public abstract void drawFrame(float scaleX, float scaleY, float translateX, float translateY);
    public float getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public float getLeft() {
        return left;
    }

    public void setLeft(int left) {
        this.left = left;
    }

    public float getTop() {
        return top;
    }

    public void setTop(int top) {
        this.top = top;
    }
}
