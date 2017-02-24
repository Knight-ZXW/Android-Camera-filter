package com.gotye.bibo.camera.watermark;

import android.content.Context;
import android.opengl.Matrix;

import com.gotye.bibo.filter.FilterManager;
import com.gotye.bibo.gles.FullFrameRect;
import com.gotye.bibo.util.LogUtil;
import com.gotye.gif.GifDelegate;

import java.io.InputStream;

/**
 * Created by zhuoxiuwu on 2017/2/24.
 * email nimdanoob@gmail.com
 */

public class GifWaterFrameHolder extends WaterFrameHolder{
    private GifDelegate mGifDelegate;
    public GifWaterFrameHolder(InputStream inputStream,Context context,int width,int height,int left,int top){
        mGifDelegate = new GifDelegate(inputStream);
        mGifDelegate.decoderGif();
        this.mContext = context;
        this.width = width;
        this.height = height;
        this.left = left;
        this.top = top;
    }

    private int[] textureIds;
    private int mCurrentTextureId;
    @Override
    public void drawFrame(float scaleX, float scaleY, float translateX, float translateY) {
        if (mGifDelegate == null) return;
        if (mFullFrameRect == null){
            mFullFrameRect =new FullFrameRect(FilterManager.getImageFilter(FilterManager.FilterType.Normal, mContext));
            Matrix.setIdentityM(IDENTITY_MATRIX, 0);
            mFullFrameRect.resetMVPMatrix();
            mFullFrameRect.translateMVPMatrix(translateX, translateY);
            mFullFrameRect.scaleMVPMatrix(scaleX, -scaleY);
        }
        if (mGifDelegate.loadFinish){
            if (textureIds == null) {
                textureIds = new int[mGifDelegate.getBitmapLength()];
                for (int i = 0; i < mGifDelegate.getBitmapLength(); i++) {
                    textureIds[i] = mFullFrameRect.createTexture(mGifDelegate.getNextBitmap());
                }
            }
            //drawFrame

            mFullFrameRect.drawFrame(mCurrentTextureId, IDENTITY_MATRIX);
            LogUtil.error("haha","绘制动态图"+mCurrentTextureId);
            //递增
            if (mCurrentTextureId<textureIds.length) {
                mCurrentTextureId++;
            }else {
                mCurrentTextureId = 0;
            }
        } else {
            LogUtil.error("haha","还没加载完呢");
        }

    }
}
