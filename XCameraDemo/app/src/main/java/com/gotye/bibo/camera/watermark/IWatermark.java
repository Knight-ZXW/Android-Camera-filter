package com.gotye.bibo.camera.watermark;

/**
 * Created by zhuoxiuwu on 2017/2/24.
 * email nimdanoob@gmail.com
 */

public abstract class IWatermark {
    public enum Type{
        NORMAL,
        GIF;
    }
    protected int mWidth;
    protected int mHeight;
    protected int mLeft;
    protected int mTop;
    protected Type mType;

    public int getWidth() {
        return mWidth;
    }

    public void setWidth(int mWidth) {
        this.mWidth = mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public void setHeight(int mHeight) {
        this.mHeight = mHeight;
    }

    public int getLeft() {
        return mLeft;
    }

    public void setLeft(int mLeft) {
        this.mLeft = mLeft;
    }

    public int getTop() {
        return mTop;
    }

    public void setTop(int mTop) {
        this.mTop = mTop;
    }

    public Type getType() {
        return mType;
    }

    public void setmType(Type mType) {
        this.mType = mType;
    }
}
