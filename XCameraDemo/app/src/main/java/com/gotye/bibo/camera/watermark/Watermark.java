package com.gotye.bibo.camera.watermark;

import android.graphics.Bitmap;

import com.gotye.gif.GifDelegate;

import java.io.InputStream;

/**
 * Created by zhuoxiuwu on 2017/2/24.
 * email nimdanoob@gmail.com
 */

public class Watermark extends IWatermark {
    private Bitmap mBitmap;
    private GifDelegate mGifDelegate;

    public Watermark(Bitmap img, int width, int height, int left, int top) {
        mBitmap = img;
        this.mWidth = width;
        mHeight = height;
        mLeft = left;
        mTop = top;
        mType = Type.NORMAL;
    }

    public Watermark(InputStream inputStream,int width,int height,int left,int top){
        this.mGifDelegate = new GifDelegate(inputStream);
        mGifDelegate.decoderGif();
        this.mWidth = width;
        mHeight = height;
        mLeft = left;
        mTop = top;
        mType = Type.GIF;
    }

    public void resetBitmap(Bitmap bitmap) {
        mBitmap = bitmap;
        mType = Type.NORMAL;
    }

    public void resetGif(InputStream inputStream) {
        mGifDelegate = new GifDelegate(inputStream);
        mGifDelegate.decoderGif();
        mType = Type.GIF;
    }

    public Bitmap getNextBitmap() {
        if (mType == Type.NORMAL) {
            return mBitmap;
        } else {
            return mGifDelegate.getNextBitmap();
        }
    }


}
