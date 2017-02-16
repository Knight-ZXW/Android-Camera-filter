// Copyright (c) Philipp Wagner. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

package com.gotye.bibo.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera.Face;
import android.util.AttributeSet;
import android.view.View;

import com.gotye.bibo.util.FaceUtil;
import com.gotye.bibo.util.LogUtil;

import java.util.Locale;

/**
 * This class is a simple View to display the faces.
 */
public class FaceOverlayView extends View {

    private final static String TAG = "FaceOverlayView";

    private Paint mPaint, mPaint2;
    private Paint mTextPaint;
    private int mPreviewWidth = -1;
    private int mPreviewHeight = -1;
    private int mDisplayOrientation = -1;
    private int mOrientation = -1;
    private boolean mFrontFacing = false;

    private Face[] mFaces;

    private Rect[] mRects;
    private Point[] mPoints;
    private int[] mFaceOrientations;

    private Matrix mMatrix;
    private RectF mRectF;

    public FaceOverlayView(Context context) {
        super(context);
        initialize();
    }

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    private void initialize() {
        // We want a green box around the face:
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(Color.GREEN);
        mPaint.setAlpha(128);
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        mPaint2 = new Paint();
        mPaint2.setColor(Color.RED);
        mPaint2.setAntiAlias(true);
        mPaint2.setStyle(Paint.Style.FILL);

        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setDither(true);
        mTextPaint.setTextSize(12);
        mTextPaint.setColor(Color.RED);
        mTextPaint.setStyle(Paint.Style.FILL);

        mMatrix = new Matrix();
        mRectF = new RectF();
    }

    public void setFaces2(Rect[] rects, Point[] points, int[] faceOrientations) {
        mRects = rects;
        mPoints = points;
        mFaceOrientations = faceOrientations;

        invalidate();
    }

    public void setFaces(Face[] faces) {
        mFaces = faces;
        invalidate();
    }

    public void setPreviewSize(int width, int height) {
        LogUtil.info(TAG, "setPreviewSize: " + width + " x " + height);

        mPreviewWidth = width;
        mPreviewHeight = height;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mDisplayOrientation == -1 ||
                mPreviewWidth == -1 || mPreviewHeight == -1) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        int camera_width = mPreviewWidth;
        int camera_height = mPreviewHeight;
        if (mDisplayOrientation == 90 || mDisplayOrientation == 270) {
            camera_width = mPreviewHeight;
            camera_height = mPreviewWidth;
        }

        int width = getDefaultSize(camera_width, widthMeasureSpec);
        int height = getDefaultSize(camera_height, heightMeasureSpec);

        if (camera_width > 0 && camera_height > 0) {
            if (camera_width * height > width * camera_height) {
                height = width * camera_height / camera_width;
            } else if (camera_width * height < width * camera_height) {
                width = height * camera_width / camera_height;
            }
        }

        LogUtil.info(TAG, "onMeasure set final " + width + " x " + height);

        super.setMeasuredDimension(width, height);
    }

    public void setOrientation(int orientation) {
        LogUtil.info(TAG, "setOrientation: " + orientation);
        mOrientation = orientation;
    }

    public void setDisplayOrientation(int displayOrientation) {
        LogUtil.info(TAG, "setDisplayOrientation: " + displayOrientation);
        mDisplayOrientation = displayOrientation;
        //invalidate();
        requestLayout();
    }

    public void setFontFacing(boolean FrontFacing) {
        LogUtil.info(TAG, "setFontFacing: " + FrontFacing);
        mFrontFacing = FrontFacing;
    }

    private void drawPoint(Canvas canvas, Matrix matrix, Point p) {
        float []pos = new float[2];
        pos[0] = p.x;
        pos[1] = p.y;
        matrix.mapPoints(pos);
        canvas.drawCircle(pos[0], pos[1], 5f, mPaint2);
    }

    private void drawText(Canvas canvas, Matrix matrix, Point p, String text) {
        float []pos = new float[2];
        pos[0] = p.x;
        pos[1] = p.y;
        matrix.mapPoints(pos);
        canvas.drawText(text, pos[0], pos[1], mTextPaint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mFaces != null && mFaces.length > 0) {
            FaceUtil.prepareMatrix(mMatrix, mFrontFacing,
                    mDisplayOrientation, getWidth(), getHeight());
            canvas.save();
            mMatrix.postRotate(mOrientation);
            canvas.rotate(-mOrientation);
            for (Face face : mFaces) {
                mRectF.set(face.rect);
                mMatrix.mapRect(mRectF);
                canvas.drawRect(mRectF, mPaint);
                canvas.drawText("Score " + face.score,
                        mRectF.right, mRectF.top, mTextPaint);

                if (face.leftEye != null) {
                    drawPoint(canvas, mMatrix, face.leftEye);
                }
                if (face.rightEye != null) {
                    drawPoint(canvas, mMatrix, face.rightEye);
                }
                if (face.mouth != null) {
                    drawPoint(canvas, mMatrix, face.mouth);
                }

            }
            canvas.restore();
        }
        else if (mRects != null && mRects.length > 0) {
            FaceUtil.prepareArcSoftMatrix(mMatrix,
                    mFrontFacing, mDisplayOrientation,
                    mPreviewWidth, mPreviewHeight,
                    getWidth(), getHeight());
            canvas.save();
            mMatrix.postRotate(mOrientation);
            canvas.rotate(-mOrientation);
            for (Rect r : mRects) {
                mRectF.set(r);
                mMatrix.mapRect(mRectF);
                LogUtil.debug(TAG, String.format(Locale.US,
                        "face rect [%d %d %d %d] -> [%.0f %.0f %.0f %.0f]",
                        r.left, r.top, r.right, r.bottom,
                        mRectF.left, mRectF.top, mRectF.right, mRectF.bottom));
                canvas.drawRect(mRectF, mPaint);
            }
            int index = 0;
            for (Point p : mPoints) {
                //drawPoint(canvas, mMatrix, p);
                drawText(canvas, mMatrix, p, String.valueOf(index++));
            }
            canvas.restore();
        }
    }
}