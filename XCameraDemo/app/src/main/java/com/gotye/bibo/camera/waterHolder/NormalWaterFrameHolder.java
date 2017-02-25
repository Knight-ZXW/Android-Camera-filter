package com.gotye.bibo.camera.waterHolder;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.Matrix;

import com.gotye.bibo.filter.FilterManager;
import com.gotye.bibo.gles.FullFrameRect;
import com.gotye.bibo.util.LogUtil;

/**
 * Created by zhuoxiuwu on 2017/2/24.
 * email nimdanoob@gmail.com
 */

public class NormalWaterFrameHolder extends WaterFrameHolder{

    public NormalWaterFrameHolder(Bitmap bitmap, Context context, int width, int height, int left, int top) {
        super(bitmap, context, width, height, left, top);
    }

    @Override
    public void drawFrame(float scaleX, float scaleY, float translateX, float translateY) {
        if (mFullFrameRect == null) {
            mFullFrameRect = new FullFrameRect(FilterManager.getImageFilter(FilterManager.FilterType.Normal, mContext));
            texturedId = mFullFrameRect.createTexture(mBitmap);
            Matrix.setIdentityM(IDENTITY_MATRIX, 0);
            mFullFrameRect.resetMVPMatrix();
            mFullFrameRect.translateMVPMatrix(translateX, translateY);
            mFullFrameRect.scaleMVPMatrix(scaleX, -scaleY);
            LogUtil.debug("LogoFrameHolder","调整位置");
        }
        mFullFrameRect.drawFrame(texturedId, IDENTITY_MATRIX);
    }
}
