package com.gotye.gif;

import android.graphics.Bitmap;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by zhuoxiuwu on 2017/2/23.
 * email nimdanoob@gmail.com
 */

public class GifDelegate {

    private GifImageDecoder mGifImageDecoder;
    public boolean loadFinish = false;
    private InputStream mInputStream;

    public GifDelegate(InputStream inputStream) {
        mInputStream = inputStream;
    }

    private Thread handleThread;

    public void decoderGif() {
        handleThread = new Thread(new DecodeGifTask());
        handleThread.run();
    }

    int mCurrentBitmapIndex =0;
    int mBitmapsCount;

    //注意Bitmap 的内存泄漏问题
    //换成迭代器的实现更好点
    public Bitmap getNextBitmap() {
        if (mCurrentBitmapIndex < mBitmapsCount) {
            mCurrentBitmapIndex++;
        } else {
            mCurrentBitmapIndex = 0;
        }
        return mGifImageDecoder.getFrame(mCurrentBitmapIndex);
    }

    public int getBitmapLength(){
        if (loadFinish)return mBitmapsCount;
        else return -1;//还没加载完
    }


    class DecodeGifTask implements Runnable {
        @Override
        public void run() {
            mGifImageDecoder = new GifImageDecoder();
            try {
                mGifImageDecoder.read(mInputStream);
                mBitmapsCount = mGifImageDecoder.mFrameCount;
                loadFinish = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 强制终止所有任务，避免内泄漏
     */
    public void shutDown() {
        if (handleThread != null && handleThread.isAlive()) {
            //shut down
        }
    }
}


