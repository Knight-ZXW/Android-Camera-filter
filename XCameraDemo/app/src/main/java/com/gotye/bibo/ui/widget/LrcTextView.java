package com.gotye.bibo.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;

import com.gotye.bibo.util.LogUtil;
import com.gotye.bibo.util.TimeLrc;

import java.io.IOException;
import java.util.List;

public class LrcTextView extends TextView {
	private static final String TAG = "LrcTextView";
	
	private static final int MSG_UPDATE = 1;
	
	private List<TimeLrc> mLrcList;
	private Paint mLoseFocusPaint;
	private Paint mOnFocusePaint;
	private float mX = 0;
	private float mMiddleY = 0;
	private float mY = 0;
	private static final int DY = 50;
	private int mIndex = 0;
	
	private boolean isPlaying = false;
	private boolean isSeeking = false;
	private int mSeekMsec;
	private static final int DELAY = 100; // ms
	private UpdateLrcThread updateThread;
	private PlayerInterface mPlayer;

	public interface PlayerInterface {
		int getCurrentPosition();

		int getDuration();

        boolean isPlaying();
	}

	public LrcTextView(Context context) throws IOException {
		super(context);
		init();
	}

	public LrcTextView(Context context, AttributeSet attrs) throws IOException {
		super(context, attrs);
		init();
	}

	public LrcTextView(Context context, AttributeSet attrs, int defStyle)
			throws IOException {
		super(context, attrs, defStyle);
		init();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		//canvas.drawColor(Color.BLACK);
		
		Paint p = mLoseFocusPaint;
		Paint p2 = mOnFocusePaint;
		
		if (mLrcList == null)
			return;

		if (mIndex >= mLrcList.size())
			return;
		
		if (mIndex < mLrcList.size()) {
			String text = mLrcList.get(mIndex).getLrcString();
			if (text == null)
				text = "";
			canvas.drawText(text, mX, mMiddleY, p2);
		}

		int step = 30;//255 / (mIndex + 1);
		int alphaValue = 25;
		float tempY = mMiddleY;
		for (int i = mIndex - 1; i >= 0; i--) {
			tempY -= DY;
			if (tempY < 0) {
				break;
			}
			p.setColor(Color.argb(255 - alphaValue, 245, 245, 245));
			String text = mLrcList.get(i).getLrcString();
			if (text == null)
				text = "";
			canvas.drawText(text, mX, tempY, p);
			alphaValue += step;
			if (alphaValue > 255)
				alphaValue = 255;
		}
		step = 30;//255 / (mLrcList.size() - mIndex + 1);
		alphaValue = 25;
		tempY = mMiddleY;
		for (int i = mIndex + 1, len = mLrcList.size(); i < len; i++) {
			tempY += DY;
			if (tempY > mY)
				break;
			
			p.setColor(Color.argb(255 - alphaValue, 245, 245, 245));
			String text = mLrcList.get(i).getLrcString();
			if (text == null)
				text = "";
			canvas.drawText(text, mX, tempY, p);
			alphaValue += step;
			if (alphaValue > 255)
				alphaValue = 255;
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int ow, int oh) {
		super.onSizeChanged(w, h, ow, oh);

		mX = w * 0.5f;
		mY = h;
		mMiddleY = h * 0.3f;
	}

	public void setData(List<TimeLrc> lrcList) {
		mLrcList = lrcList;
	}
	
	public void setPlayer(PlayerInterface player) {
		mPlayer = player;
	}
	
	private void init() {
		setFocusable(true);
		
		mLoseFocusPaint = new Paint();
		mLoseFocusPaint.setAntiAlias(true);
		mLoseFocusPaint.setTextAlign(Paint.Align.CENTER);
		mLoseFocusPaint.setTextSize(36);
		mLoseFocusPaint.setColor(Color.WHITE);
		mLoseFocusPaint.setTypeface(Typeface.SERIF);

		mOnFocusePaint = new Paint();
		mOnFocusePaint.setAntiAlias(true);
		mOnFocusePaint.setTextAlign(Paint.Align.CENTER);
		mOnFocusePaint.setColor(Color.YELLOW);
		mOnFocusePaint.setTextSize(44);
		mOnFocusePaint.setTypeface(Typeface.SANS_SERIF);
	}
	
	public void start() {
        seekTo(mPlayer.getCurrentPosition());
        mIndex = 0;
        
        updateThread = new UpdateLrcThread();
        updateThread.start();
        
        this.isPlaying = true;
	}
	
	public void stop() {
        if (updateThread != null) {
        	updateThread.interrupt();
        	
        	try {
        		Log.i(TAG, "Java: lrc before join");
				updateThread.join();
				updateThread = null;
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        
        mLrcList = null;
        invalidate();

        this.isPlaying = false;
        Log.i(TAG, "Java: lrc stoped");
	}
	
	public void seekTo(int msec) {
		mSeekMsec = msec;
		isSeeking = true;
	}
	
	private Handler mHandler = new Handler(){
		  
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
            case MSG_UPDATE:
            	invalidate();
            	break;
            default:
            	break;
            }
        }
	};
	
	private class UpdateLrcThread extends Thread {
		public boolean mIsStopping;
		
		public void interrupt() {
			mIsStopping = true;
		}
		
		@Override
        public void run()
        {
            Log.i(TAG, "Java: lrc thread started...");
            
            if (mPlayer == null || mLrcList == null) {
            	Log.e(TAG, "Java: data was not set");
            	return;
        	}
		
            while (!mIsStopping) {
                if (isSeeking) {
                	int curr_pos = mPlayer.getCurrentPosition();
                	Log.i(TAG, "Java: search lrc: curr_pos " + curr_pos);
                	int i = 0;
                	if (mSeekMsec >= curr_pos) {
                		i = mIndex;
                	}
                	
                	while (i < mLrcList.size() - 1 && !mIsStopping) {
                		long start_time = mLrcList.get(i+1).getTimePoint();
                		LogUtil.debug(TAG, "Java: search lrc: item start_time " + start_time);
                		
                		if (start_time >= mSeekMsec) {
							LogUtil.info(TAG, "Java: search lrc: (found) " + start_time);
                			break;
                		}
                		
                		i++;
                	}
                	
                	mIndex = i;
                	isSeeking = false;

					mHandler.sendEmptyMessage(MSG_UPDATE);
                }

                if (mLrcList == null) {
                    LogUtil.info(TAG, "mLrcList is null, exit thread");
                    break;
                }

                if (mIndex < (mLrcList.size() - 1) &&
						(mPlayer.getCurrentPosition() >= mLrcList.get(mIndex + 1).getTimePoint())) {
                	mIndex++;
                	mHandler.sendEmptyMessage(MSG_UPDATE);
                	continue;
                }
                
                try {
                    Thread.sleep(DELAY);
                }
                catch (Exception e) {
                	e.printStackTrace();
                    LogUtil.error(TAG, "Java: subtitle " + e.getMessage());
                    break;
                }
            }
            
            LogUtil.info(TAG, "Java: subtitle thread exit");
        }
	}
}
