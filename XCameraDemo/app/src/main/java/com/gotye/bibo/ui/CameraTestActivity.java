package com.gotye.bibo.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaActionSound;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.gotye.bibo.R;
import com.gotye.bibo.adapter.FilterAdapter;
import com.gotye.bibo.encode.EncoderConfig;
import com.gotye.bibo.filter.FilterManager.FilterType;
import com.gotye.bibo.ui.widget.CameraRecorderView;
import com.gotye.bibo.ui.widget.HorizontialListView;
import com.gotye.bibo.ui.widget.LrcTextView;
import com.gotye.bibo.util.AudioUtil;
import com.gotye.bibo.util.LogUtil;
import com.gotye.bibo.util.LrcDownloadUtil;
import com.gotye.bibo.util.LrcInfo;
import com.gotye.bibo.util.LrcParser2;
import com.gotye.bibo.util.Song;
import com.gotye.bibo.util.TimeLrc;
import com.gotye.bibo.util.Util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;


public class CameraTestActivity extends AppCompatActivity
        implements View.OnClickListener,
        CameraRecorderView.TakePictureCallback,
        LrcTextView.PlayerInterface {

    private static final String TAG = "CameraTestActivity";

    private MainHandler mHandler;

    private CameraRecorderView mView;
    private LinearLayout mCtrlLayout;
    private Button mBtnRecord;
    private ImageButton mBtnBeautify;
    private Button mBtnToggleFilter;
    private TextView mTvRecTime;
    private TextView mTvInfo;
    private SeekBar mBeautifySeekBar;
    private HorizontialListView mHlvFilter;

    private TextView mTvRecordCountdown;
    private int mCountdownRepeat;

    private boolean mStopping = false;
    private boolean mRecording = false;
    private boolean mShowCtrlLayout = true;

    // settings
    private boolean mbUseX264Encode = false;
    private boolean mbUseFdkAACEncode = false;
    private boolean mbUseRotate = false;
    private boolean mbEncodeAudio = true;
    private int mMuxFmt = 1; // 0-ts, 1-mp4, 2-flv, 3-h264, 4-rtmp
    private String mCustomizedOutputPath;
    private boolean mbTextureEncode = true; //默认为纹理编码，只有纹理编码才支持
    private boolean mbTorch = false;


    private int mVideoBitRate = 500 * 1000; // 500k
    private int mVideoFrameRate = 15;

    private ProgressDialog mProgDlg;

    private String mUrl = null;

    private GestureDetector mGestureListener;
    private ScaleGestureDetector mScaleGestureDetector;

    private final static int BEAUTIFY_MAX_RANGE = 100;

    private static final String mRootPath = Environment.getExternalStorageDirectory().
            getAbsolutePath() + "/test2/bibo";

    private MediaActionSound mCameraSound;

    private boolean activityRunning;

    private List<Map<String, String>> nsList;

    // play song
    private Button mBtnSongEffect;
    private TextView mTvMusicTime;
    private LrcTextView mTvLyrics;
    private List<TimeLrc> mLyric;


    private Toast logToast;

    private static final String DEFAULT_VIDEO_CODEC = "VP8"; //视频编码
    private static final String DEFAULT_AUDIO_CODEC = "ISAC"; //音频编码
    private static final int DEFAULT_VIDEO_BITRATE = 300; // kbps
    private GLSurfaceView glview;
    //private RtcRenderer myRenderer;

    private FilterAdapter mAdapter;
    private boolean mbFilterSelectShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        super.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        try {
            super.getWindow().addFlags(
                    WindowManager.LayoutParams.class.
                            getField("FLAG_NEEDS_MENU_KEY").getInt(null));
        } catch (NoSuchFieldException e) {
            // Ignore since this field won't exist in most versions of Android
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null)
            getSupportActionBar().hide();

        mHandler = new MainHandler(this);

        mProgDlg = new ProgressDialog(this);

        readSettings();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mCameraSound = new MediaActionSound();
            mCameraSound.load(MediaActionSound.SHUTTER_CLICK);
            mCameraSound.load(MediaActionSound.FOCUS_COMPLETE);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1){
            mbTextureEncode = false;
        }

        mView = (CameraRecorderView)this.findViewById(R.id.cam_view);
        mView.setEncoderListener(mEncoderListener);
        mView.setCameraListener(mCameraListener);

        mCtrlLayout = (LinearLayout)this.findViewById(R.id.layout_button); //左边的功能布局

        mBtnRecord = (Button)this.findViewById(R.id.button_record);
        mBtnBeautify = (ImageButton)this.findViewById(R.id.button_beautify);
        mBtnToggleFilter = (Button)this.findViewById(R.id.button_toggle_filter);
        mBtnSongEffect = (Button)this.findViewById(R.id.button_song_settings);
        mTvMusicTime = (TextView)this.findViewById(R.id.tv_music_time);
        mTvLyrics = (LrcTextView)this.findViewById(R.id.tv_lyric);
        mHlvFilter = (HorizontialListView)this.findViewById(R.id.hlv_filter);

        initFilterListView();

        mBtnRecord.setOnClickListener(this);
        mBtnSongEffect.setOnClickListener(this);

        this.findViewById(R.id.button_option).setOnClickListener(this);
        this.findViewById(R.id.button_cam_res).setOnClickListener(this);
        this.findViewById(R.id.button_viewclip).setOnClickListener(this);
        this.findViewById(R.id.button_play_song).setOnClickListener(this);
        this.findViewById(R.id.button_cam_switch).setOnClickListener(this);
        this.findViewById(R.id.button_cam_torch).setOnClickListener(this);
        this.findViewById(R.id.button_beautify).setOnClickListener(this);

        if (!mView.supportFrontFacingCam())
            this.findViewById(R.id.button_cam_switch).setEnabled(false);

        mBeautifySeekBar = (SeekBar)this.findViewById(R.id.seekbar_beautify);
        mBeautifySeekBar.setMax(BEAUTIFY_MAX_RANGE);
        mBeautifySeekBar.setProgress(30);
        mBeautifySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float value = (float)seekBar.getProgress() / (float)BEAUTIFY_MAX_RANGE;
                mView.setFilterValue(value);
            }
        });

        mBtnToggleFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mView.isTextureEncode()) {
                    mHlvFilter.setVisibility(mbFilterSelectShowing ? View.GONE : View.VISIBLE);
                    mbFilterSelectShowing = !mbFilterSelectShowing;
                }
            }
        });

        mTvRecTime          = (TextView)this.findViewById(R.id.tv_record_duration);
        mTvInfo             = (TextView)this.findViewById(R.id.tv_info);
        mTvRecordCountdown  = (TextView)this.findViewById(R.id.tv_countdown);

        mGestureListener =
                new GestureDetector(CameraTestActivity.this, new GestureDetector.SimpleOnGestureListener() {

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        LogUtil.info(TAG, "Java: onSingleTapConfirmed()");

                        if (mCameraSound != null)
                            mCameraSound.play(MediaActionSound.SHUTTER_CLICK);
                        // watermark
                        //todo 尝试调整watermark的位置
                        return true;
                    }

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                        // 1xxx - 4xxx
                        final int FLING_MIN_DISTANCE = 200;
                        final float FLING_MIN_VELOCITY = 1000.0f;
                        // 1xxx - 4xxx

                        float distance = e2.getX() - e1.getX();
                        if (Math.abs(distance) > FLING_MIN_DISTANCE
                                && Math.abs(velocityX) > FLING_MIN_VELOCITY) {
                            mShowCtrlLayout = !mShowCtrlLayout;
                            mCtrlLayout.setVisibility(
                                    mShowCtrlLayout ? View.VISIBLE : View.GONE);
                            mTvInfo.setVisibility(
                                    mShowCtrlLayout ? View.VISIBLE : View.GONE);
                            mTvRecTime.setVisibility(
                                    mShowCtrlLayout ? View.VISIBLE : View.GONE);
                            mBtnToggleFilter.setVisibility(
                                    (mShowCtrlLayout && mView.isTextureEncode()) ? View.VISIBLE : View.GONE);
                            mBtnBeautify.setVisibility(
                                    mShowCtrlLayout ? View.VISIBLE : View.GONE);
                            mBeautifySeekBar.setVisibility(
                                    mShowCtrlLayout ? View.VISIBLE : View.GONE);

                            boolean showOtherUI = mShowCtrlLayout && mView.isPlayingSong();
                            mTvLyrics.setVisibility(
                                    showOtherUI ? View.VISIBLE : View.GONE);
                            mBtnSongEffect.setVisibility(
                                    showOtherUI ? View.VISIBLE : View.GONE);
                            mTvMusicTime.setVisibility(
                                    showOtherUI ? View.VISIBLE : View.GONE);
                            mHlvFilter.setVisibility(
                                    showOtherUI && mbFilterSelectShowing
                                            ? View.VISIBLE : View.GONE);
                        }

                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        LogUtil.info(TAG, "Java: onDoubleTap()");

                        int mode = mView.getDisplayMode();
                        mode++;
                        if (mode > CameraRecorderView.SCREEN_CENTER)
                            mode = CameraRecorderView.SCREEN_FIT;
                        else if (mode < CameraRecorderView.SCREEN_FIT)
                            mode = CameraRecorderView.SCREEN_CENTER;
                        mView.setDisplayMode(mode);
                        Toast.makeText(CameraTestActivity.this,
                                "切换显示模式至 " + CameraRecorderView.DISPLAY_MODE_DESC[mode],
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    @Override
                    public void onLongPress(MotionEvent event) {
                        LogUtil.info(TAG, "Java: onLongPress()");

                        mView.touchAutoFocus(event, new CameraRecorderView.onAutoFocusListener() {
                            @Override
                            public void onFocus() {
                                if (mCameraSound != null)
                                    mCameraSound.play(MediaActionSound.FOCUS_COMPLETE);
                            }
                        });
                    }
                });

        mScaleGestureDetector =
                new ScaleGestureDetector(CameraTestActivity.this, new ScaleGestureDetector.OnScaleGestureListener() {

                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector detector) {

                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float scaleFactor = detector.getScaleFactor();
                        if (scaleFactor > 1.0f)
                            mView.setZoomIn();
                        else if (scaleFactor < 1.0f)
                            mView.setZoomOut();

                        return true;
                    }
                });

        mView.setLongClickable(true);// MUST set to enable onDoubleTap
        mView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mGestureListener.onTouchEvent(event);

                mScaleGestureDetector.onTouchEvent(event);

                return true;
            }
        });

        File dir = new File(mRootPath);
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "创建临时目录 " + mRootPath + " 失败", Toast.LENGTH_SHORT).show();
        }
        dir = new File(mRootPath + "/pic");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "创建拍照目录失败", Toast.LENGTH_SHORT).show();
        }

        mView.setPictureParams(mRootPath + "/pic", this);

        try {
            InputStream gifInput = getAssets().open("test2.gif");
            mView.addBoomWater(gifInput,200,200,400,400);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // set logo
        mView.setLogo(BitmapFactory.decodeResource(getResources(),R.drawable.star),200,200,300,400);

    }

    @Override
    protected void onResume() {
        super.onResume();

        LogUtil.info(TAG, "onResume()");

        activityRunning = true;

        //if (glview != null)
        //    glview.onResume();

        mHandler.sendEmptyMessage(MainHandler.MSG_UPDATE_INFO);

        if (mView.isRecording())
            mHandler.sendEmptyMessage(MainHandler.MSG_UPDATE_DURATION);

        mView.onResume();

        updateBeautifyUI();
    }

    @Override
    public void onBackPressed() {
        if (mRecording) {
            if (mStopping) {
                LogUtil.warn(TAG, "already stopping");
                return;
            }

            stop_rec(true);
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();

        activityRunning = false;

        //if (glview != null)
        //    glview.onPause();

        // use external camera souce, stop will CRASH
        //if (pc != null) {
        //    pc.stopVideoSource();
        //}

        if (mRecording)
            mHandler.removeMessages(MainHandler.MSG_UPDATE_DURATION);

        mHandler.removeMessages(MainHandler.MSG_UPDATE_INFO);
        //暂停录制
        mRecording = false;
        mView.onPause();

        writeSettings();
    }

    @Override
    protected void onDestroy() {
        LogUtil.info(TAG, "onDestroy()");

        mView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        String strOrientation;
        if (newConfig.orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            strOrientation = "portait";
        else if (newConfig.orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            strOrientation = "landscape";
        else if (newConfig.orientation == ActivityInfo.SCREEN_ORIENTATION_USER)
            strOrientation = "user";
        else
            strOrientation = "unknown";

        LogUtil.info(TAG, String.format("Java: onConfigurationChanged() orientation %d(%s)",
                newConfig.orientation, strOrientation));
    }

    private void initFilterListView() {
        String []filters_title = {"普通", "美白", "灰度",
                "美颜2", "美颜3",
                "贴纸",
                "卡通1", "卡通2", "卡通3", "卡通4", "卡通5",
                "高斯模糊", "双边模糊", "马赛克", "选择模糊", "边缘检测1", "锐化",
                "破碎", "乐高", "水波纹",
                "边缘检测2", "像素化", "轮廓1",
                "融合", "柔光", "曲线", "线条",
                "凸起", "收缩", "伸展",
                "字符1", "字符2",
                "色度键1", "色度键2"};
        String []filters_name = {"Normal", "White", "Gray",
                "AllinOne", "BeautyFaceWu",
                "FaceUnity",
                "Cartoon", "Toon", "SmoothToon", "BritneyCartoon", "Cartoonish",
                "GaussianBlur", "BilateralBlur",
                "MosaicBlur", "GaussianSelectiveBlur",
                 "CannyEdgeDetection", "Sharpen",
                "Cracked", "Legofield", "BasicDeform",
                "EdgeDetectiondFdx", "Pixelize", "NoiseContour",
                "Blend", "SoftLight", "ToneCurve",
                "Lofify",
                "BulgeDistortion", "PinchDistortion", "StretchDistortion",
                "AsciiArt", "AsciiArt2",
                "ChromaKey", "ChromaKeyBlend"};
        int []filters_pic = {
                R.drawable.filter_thumb_original,
                R.drawable.filter_thumb_beautify,
                R.drawable.filter_thumb_gray,
                R.drawable.filter_thumb_beautify,
                R.drawable.filter_thumb_beautify,
                R.drawable.filter_thumb_faceunity, // 贴纸
                R.drawable.filter_thumb_toon, // 卡通1
                R.drawable.filter_thumb_toon,
                R.drawable.filter_thumb_toon,
                R.drawable.filter_thumb_toon,
                R.drawable.filter_thumb_toon, // 卡通5
                R.drawable.filter_thumb_blur, // 高斯模糊
                R.drawable.filter_thumb_blur,
                R.drawable.filter_thumb_mosaic,
                R.drawable.filter_thumb_blur,
                R.drawable.filter_thumb_edge_detection,
                R.drawable.filter_thumb_sharpen,
                R.drawable.filter_thumb_original, // 破碎
                R.drawable.filter_thumb_original, // 乐高
                R.drawable.filter_thumb_original, // 水波纹
                R.drawable.filter_thumb_original, // 边缘检测2
                R.drawable.filter_thumb_original,
                R.drawable.filter_thumb_original, // 轮廓1
                R.drawable.filter_thumb_blend, // 融合
                R.drawable.filter_thumb_blend_soft_light,
                R.drawable.filter_thumb_tone_curve,
                R.drawable.filter_thumb_original,
                R.drawable.filter_thumb_distortion,
                R.drawable.filter_thumb_distortion,
                R.drawable.filter_thumb_distortion,
                R.drawable.filter_thumb_asciiart,
                R.drawable.filter_thumb_asciiart,
                R.drawable.filter_thumb_original, // 色度键
                R.drawable.filter_thumb_original
        };
        List<Map<String, Object>> filterList = new ArrayList<>();
        for (int i=0;i<filters_title.length;i++) {
            HashMap<String, Object> mapLive = new HashMap<>();
            mapLive.put("title", filters_title[i]);
            mapLive.put("filter_name", filters_name[i]);
            mapLive.put("thumb", filters_pic[i]);
            mapLive.put("selected", i == 0);
            filterList.add(mapLive);
        }

        mAdapter = new FilterAdapter(CameraTestActivity.this, filterList,
                R.layout.filter_item);

        mHlvFilter.setAdapter(mAdapter);

        mHlvFilter.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String filter_name = mAdapter.select(position);
                if (filter_name != null)
                    mView.setFilter(FilterType.fromCanonicalForm(filter_name));
                else
                    Toast.makeText(CameraTestActivity.this, "设置滤镜失败", Toast.LENGTH_SHORT).show();

            }
        });
    }

    private void onAudioManagerChangedState() {
        // TODO(henrika): disable video if AppRTCAudioManager.AudioDevice.EARPIECE
        // is active.
    }


    private void stop_rec(final boolean isFinish) {
        if (!mRecording)
            return;

        mStopping = true;

        // cannot click while stoping
        mBtnRecord.setEnabled(false);

        new Thread(new Runnable() {

            @Override
            public void run() {
                LogUtil.info(TAG, "stop_rec thread started");

                /// sync call
                // would block
                mView.stopRecording();

                LogUtil.info(TAG, "before send MSG_STOP_REC_DONE");

                mHandler.removeMessages(MainHandler.MSG_UPDATE_DURATION);
                mHandler.sendEmptyMessage(MainHandler.MSG_STOP_REC_DONE);

                if (isFinish) {
                    LogUtil.info(TAG, "before send MSG_EXIT");
                    mHandler.sendEmptyMessage(MainHandler.MSG_EXIT);
                }

                LogUtil.info(TAG, "stop_rec thread exited");
            }
        }).start();
    }

    private void stopPlaySong() {
        mView.stopSong();
        mTvLyrics.setVisibility(View.GONE);
        mBtnSongEffect.setVisibility(View.GONE);
        mTvMusicTime.setVisibility(View.GONE);
        mHandler.removeMessages(MainHandler.MSG_UPDATE_MUSIC_TIME);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.button_record:
                if (mRecording) {
                    stop_rec(false);
                }
                else {
                    SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
                    String str_time = format.format((new Date()));
                    mUrl = mRootPath + String.format("/out_%s", str_time);
                    switch (mMuxFmt) {
                        case 1:
                            mUrl += ".mp4";
                            break;
                        case 2:
                            mUrl += ".flv";
                            break;
                        default:
                            mUrl += ".mp4";
                            LogUtil.warn(TAG, "Java: invalid mux format: " + mMuxFmt);
                            break;
                    }

                    start_rec();
                }

                mRecording = !mRecording;
                updateButton();
                break;
            case R.id.button_option:
                popSettingsDlg();
                break;
            case R.id.button_cam_res:
                if (mView.isRecording()) {
                    Toast.makeText(this, "录像期间，请勿调整摄像头分辨率!", Toast.LENGTH_SHORT).show();
                }
                else {
                    mView.selectPreviewSize();
                }
                break;
            case R.id.button_cam_switch:
                mView.switchCamera();
                break;
            case R.id.button_cam_torch:
                mbTorch = !mbTorch;
                mView.enableTorch(mbTorch);
                ((ImageButton)v).setImageResource(
                        mView.isTorchOn() ? R.mipmap.camera_torch : R.mipmap.camera_torch_off);
                break;
            case R.id.button_viewclip:
                if (mView.isRecording()) {
                    Toast.makeText(this, "录像期间，请勿浏览!", Toast.LENGTH_SHORT).show();
                }
                else {
                    stopPlaySong();

                    Intent intent = new Intent(CameraTestActivity.this, DecodeH264Activity.class);
                    startActivity(intent);
                }
                break;
            case R.id.button_play_song:
                popSeletSongDlg();
                break;
            case R.id.button_beautify:
                mView.switchFilter();
                updateBeautifyUI();
                break;
            case R.id.button_song_settings:
                popSongSettingsDlg();
                break;
            default:
                break;
        }
    }

    // music interface
    @Override
    public int getCurrentPosition() {
        return mView.getSongPosition();
    }

    @Override
    public int getDuration() {
        return mView.getSongDuration();
    }

    @Override
    public boolean isPlaying() {
        return false;
    }
    // end of music interface


    private void start_rec() {
        // non-block
        mView.startRecording(
                new EncoderConfig(mUrl,
                        mView.getPreviewWidth(), mView.getPreviewHeight(),
                        mVideoFrameRate, mVideoBitRate,
                        mbEncodeAudio, mbUseX264Encode, mbUseRotate,
                        mbUseFdkAACEncode));

//        RecordCountingDown(3);
        mTvRecTime.setVisibility(View.VISIBLE);
        // kick off msg
        mHandler.sendEmptyMessage(MainHandler.MSG_UPDATE_DURATION);

    }


    private void RecordCountingDown(final int times) {

        mTvRecordCountdown.setText(String.valueOf(times));
        mTvRecordCountdown.setVisibility(View.VISIBLE);

        Animation sAnimation = AnimationUtils.loadAnimation(
                this, R.anim.scale);

        /*Animation sAnimation = new ScaleAnimation(1.0f, 4.0f, 1.0f, 4.0f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        sAnimation.setDuration(1000);
        sAnimation.setRepeatMode(Animation.RESTART);*/


        /*AlphaAnimation aAnimation = new AlphaAnimation(1, 0); //透明度动画效果
        AnimationSet textAnimationSet = new AnimationSet(true);
        textAnimationSet.addAnimation(sAnimation);
        textAnimationSet.addAnimation(aAnimation);*/
        //sAnimation.setRepeatMode(Animation.RESTART);
        sAnimation.setRepeatCount(times - 1);
        //sAnimation.setDuration(1000);

        mCountdownRepeat = 0;

        sAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                //mTvRecordCountdown.setText(String.valueOf(times));
                //mTvRecordCountdown.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                LogUtil.info(TAG, "onAnimationEnd()");
                mTvRecordCountdown.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                mCountdownRepeat++;
                mTvRecordCountdown.setText(
                        String.valueOf(times - mCountdownRepeat));
            }
        });

        mTvRecordCountdown.setAnimation(sAnimation);
        sAnimation.startNow();
    }


    private void readSettings() {
        mbUseX264Encode = Util.readSettingsBoolean(this, "use_x264_encode", false);
        mbUseRotate = Util.readSettingsBoolean(this, "use_rotate", false);
        mbEncodeAudio = Util.readSettingsBoolean(this, "is_encode_audio", true);
        mbUseFdkAACEncode = Util.readSettingsBoolean(this, "use_fdk_aac_encode", false);
        mMuxFmt = Util.readSettingsInt(this, "mux_format", 1);
        mbTextureEncode = Util.readSettingsBoolean(this, "texture_encode", false);
    }

    private void writeSettings() {
        Util.writeSettingsBoolean(this, "use_x264_encode", mbUseX264Encode);
        Util.writeSettingsBoolean(this, "use_rotate", mbUseRotate);
        Util.writeSettingsBoolean(this, "is_encode_audio", mbEncodeAudio);
        Util.writeSettingsBoolean(this, "use_fdk_aac_encode", mbUseFdkAACEncode);
        Util.writeSettingsInt(this, "mux_format", mMuxFmt);
        Util.writeSettingsBoolean(this, "texture_encode", mbTextureEncode);
    }

    private void updateButton() {
        if (mRecording)
            mBtnRecord.setText("停止");
        else
            mBtnRecord.setText("录像");
    }

    private void updateBeautifyUI() {
        if (mView.isFilterEnabled())
            mBtnBeautify.setImageResource(R.mipmap.beautify);
        else
            mBtnBeautify.setImageResource(R.mipmap.no_beautify);

        mBtnBeautify.setEnabled(mbTextureEncode);
        mBeautifySeekBar.setEnabled(mbTextureEncode);

        mBtnToggleFilter.setVisibility(mView.isTextureEncode() ? View.VISIBLE : View.GONE);
    }

    private void scanMedia() {
        String url = "file://" +
                Environment.getExternalStorageDirectory().getAbsolutePath();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Intent mediaScanIntent = new Intent(
                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(new File(url));
            mediaScanIntent.setData(contentUri);
            this.sendBroadcast(mediaScanIntent);
        } else {
            IntentFilter intentfilter = new IntentFilter(Intent.ACTION_MEDIA_SCANNER_STARTED);
            intentfilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
            intentfilter.addDataScheme("file");
            ScanSdReceiver scanSdReceiver = new ScanSdReceiver();
            registerReceiver(scanSdReceiver, intentfilter);
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED,
                    Uri.parse(url)));
        }
    }

    private class ScanSdReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action)) {
                LogUtil.info(TAG, "ACTION_MEDIA_SCANNER_STARTED");
            } else if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)) {
                LogUtil.info(TAG, "ACTION_MEDIA_SCANNER_FINISHED");
            }
        }
    }

    private void popSongSettingsDlg() {
        final int MAX_OFFSET = 100;

        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.music_settings_dialog, null);
        final TextView tv_voice = (TextView)view.findViewById(R.id.tv_voice_volume);
        final SeekBar VoiceVolSeekbar = (SeekBar)view.findViewById(R.id.seekbar_voice);
        final TextView tv_music = (TextView)view.findViewById(R.id.tv_music_volume);
        final SeekBar MusicVolSeekbar = (SeekBar)view.findViewById(R.id.seekbar_music);
        final TextView tv_system = (TextView)view.findViewById(R.id.tv_system_volume);
        final SeekBar SystemVolSeekbar = (SeekBar)view.findViewById(R.id.seekbar_system);

        VoiceVolSeekbar.setMax(MAX_OFFSET);
        VoiceVolSeekbar.setProgress(MAX_OFFSET);
        VoiceVolSeekbar.setEnabled(false);
        VoiceVolSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // TODO Auto-generated method stub
                if (fromUser) {
                    tv_voice.setText(
                            String.format(Locale.US, "人声音量: %d", progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub

            }

        });

        final float origin_vol = mView.getSongVolume();
        MusicVolSeekbar.setMax(MAX_OFFSET);
        if (mView.isPlayingSong()) {
            int progress = (int)(origin_vol * MAX_OFFSET);
            MusicVolSeekbar.setProgress(progress);
            tv_music.setText(
                    String.format(Locale.US, "音乐音量: %d", progress));
        }
        else {
            MusicVolSeekbar.setEnabled(false);
        }
        MusicVolSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // TODO Auto-generated method stub
                if (fromUser) {
                    tv_music.setText(
                            String.format(Locale.US, "音乐音量: %d", progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
                int progress = seekBar.getProgress();
                float vol = (float)progress / MAX_OFFSET;
                if (mView.isPlayingSong()) {
                    mView.setSongVolume(vol);
                }
            }

        });

        final float origin_sys_vol = getSystemVolume();
        SystemVolSeekbar.setMax(MAX_OFFSET);
        int progress = (int)(origin_sys_vol * MAX_OFFSET);
        SystemVolSeekbar.setProgress(progress);
        tv_system.setText(
                String.format(Locale.US, "系统音量: %d", progress));
        SystemVolSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // TODO Auto-generated method stub
                if (fromUser) {
                    tv_system.setText(
                            String.format(Locale.US, "系统音量: %d", progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
                int progress = seekBar.getProgress();
                float vol = (float)progress / MAX_OFFSET;
                setSystemVolume(vol);
            }

        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("设置音效")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setView(view)
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mView.setSongVolume(origin_vol);
                        setSystemVolume(origin_sys_vol);

                        dialog.dismiss();
                    }
                })
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // TODO Auto-generated method stub

                        dialog.dismiss();
                    }

                });

        builder.show();
    }

    private float getSystemVolume() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        LogUtil.info(TAG, "max vol: " + maxVolume + " , current vol: " + currentVolume);
        if (maxVolume == 0)
            return 0.0f;

        return (float)currentVolume / (float)maxVolume;
    }

    private void setSystemVolume(float vol) {
        LogUtil.info(TAG, "setSystemVolume(): " + vol);

        if (vol <= 1.0f && vol >= 0.0f) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int tempVolume = (int)(maxVolume * vol);
            LogUtil.info(TAG, "setStreamVolume: " + tempVolume);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, tempVolume, 0);
        }
        else {
            LogUtil.warn(TAG, "invalid system volume value: "+ vol);
        }
    }

    private void popSeletSongDlg() {
        final List<Song> songList = AudioUtil.getAllSongs(this);
        if (songList == null || songList.size() == 0) {
            Toast.makeText(this, "获取歌曲媒体文件失败 或没有歌曲文件", Toast.LENGTH_SHORT).show();
            return;
        }

        File folder = new File(Environment.getExternalStorageDirectory().toString() + "/test2");
        if (folder.exists()) {
            File []files = folder.listFiles();
            for (int i=0;i<files.length;i++) {
                File f = files[i];
                String filename = f.getName().toLowerCase();
                if (filename.endsWith(".mp3") || filename.endsWith(".flac") ||
                        filename.endsWith(".wav") || filename.endsWith(".ape"))
                {
                    String str_filesize = Util.getFileSize(f.length());
                    songList.add(new Song(f.getName(), f.getName(), 0, "N/A",
                            "N/A", "N/A", "N/A", str_filesize, f.getAbsolutePath()));
                }
            }
        }

        List<String> titleList = new ArrayList<>();
        for (int i=0;i<songList.size();i++) {
            titleList.add(songList.get(i).getFileName());
        }

        final String []titles = titleList.toArray(new String[titleList.size()]);
        new AlertDialog.Builder(this)
                .setTitle("点歌")
                .setItems(titles, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String song_url = songList.get(which).getFileUrl();
                        if (!mView.playSong(song_url)) {
                            Toast.makeText(CameraTestActivity.this,
                                    "播放歌曲失败", Toast.LENGTH_SHORT).show();
                        }
                        else {
                            String filename = titles[which];
                            // special charset . | should add \\
                            String[] tmp = filename.split("\\.");
                            if (tmp.length > 0) {
                                new DownloadLrcTask().execute(tmp[0]);
                            }

                            mBtnSongEffect.setVisibility(View.VISIBLE);
                            mTvMusicTime.setVisibility(View.VISIBLE);

                            // kick off msg
                            mHandler.sendEmptyMessage(MainHandler.MSG_UPDATE_MUSIC_TIME);

                            Toast.makeText(CameraTestActivity.this,
                                    "歌曲" + titles[which] + "已播放", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private class DownloadLrcTask extends AsyncTask<String, Integer, Boolean> {

        @Override
        protected void onPostExecute(Boolean result) {
            // TODO Auto-generated method stub
            if (result) {
                mHandler.sendEmptyMessage(MainHandler.MSG_DOWNLOAD_LRC_DONE);
            }
            else {
                Toast.makeText(CameraTestActivity.this, "查找歌词失败!", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String keyword = params[0];
            LogUtil.info(TAG, "search lrc keyword: " + keyword);

            List<LrcDownloadUtil.SongInfo> songList =
                    LrcDownloadUtil.searchBaiduSong(keyword);
            if (songList == null || songList.isEmpty()) {
                LogUtil.error(TAG, "failed to search song");
                return false;
            }

            String id;
            String lrc_path = "";
            int index = 0;
            boolean success = false;

            while (index < 5 && index < songList.size()) {
                id = songList.get(index).getSongId();
                LrcDownloadUtil.SongInfo info = LrcDownloadUtil.getBaiduLrc2(id);
                if (info == null) {
                    LogUtil.warn(TAG, "failed to get song info");
                    continue;
                }

                lrc_path = info.getLrcPath();
                if (lrc_path != null && !lrc_path.isEmpty()) {
                    success = true;
                    break;
                }

                index++;
                LogUtil.warn(TAG, "failed to get lrc_path, retry #" + index);
            }

            if (!success)
                return false;

            return download_lrc(lrc_path);
        }

        private boolean download_lrc(String lrc_path) {
            LogUtil.info(TAG, "Java: lrc_path: " + lrc_path);

            try {
                LrcParser2 parser = new LrcParser2();

                URL url = new URL(lrc_path);
                HttpURLConnection httpCon = (HttpURLConnection)url.openConnection();
                //conn.setConnectTimeout(3000);
                httpCon.setRequestMethod("GET");
                InputStream inStream = httpCon.getInputStream();

                LrcInfo info = null;
                info = parser.readLrc(inStream, "UTF-8");
                LogUtil.info(TAG, String.format("Java: lrc: artist %s, album %s, title %s",
                        info.getArtist(), info.getAlbum(), info.getTitle()));
                mLyric = info.getInfos();
                int count = mLyric.size();
                StringBuffer sb = new StringBuffer();
                for (int i=0;i<count;i++) {
                    TimeLrc lrc = mLyric.get(i);
                    String strLine = String.format("lrc time %s: text %s", lrc.getTimePoint(), lrc.getLrcString());
                    sb.append(strLine);
                    sb.append("\n");
                }

                LogUtil.info(TAG, "Java: all lrc " + sb.toString());
                return true;

            } catch (MalformedURLException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            return false;
        }
    }

    private void popSettingsDlg() {
        AlertDialog.Builder builder;

        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.all_settings_dialog, null);
        final CheckBox cbEnableAudio = (CheckBox)view.findViewById(R.id.cb_enable_audio);
        final CheckBox cbTextureEncode = (CheckBox)view.findViewById(R.id.cb_texture_encode);
        final CheckBox cbX264Encode = (CheckBox)view.findViewById(R.id.cb_x264_encode);
        final CheckBox cbX264Rotate = (CheckBox)view.findViewById(R.id.cb_x264_rotate);
        final CheckBox cbFdkAACEncode = (CheckBox)view.findViewById(R.id.cb_fdk_aac_encode);
        final SeekBar sbBitrate = (SeekBar)view.findViewById(R.id.seekbar_encode_bps);
        final TextView tv_info = (TextView)view.findViewById(R.id.tv_encode_bps);
        final Spinner spinner = (Spinner)view.findViewById(R.id.spinner_output_type);
        final EditText etFrameRate = (EditText)view.findViewById(R.id.et_framerate);

        // read data
        cbEnableAudio.setChecked(mbEncodeAudio);
        etFrameRate.setText(String.valueOf(mVideoFrameRate));
        cbTextureEncode.setChecked(mbTextureEncode);
        cbX264Encode.setEnabled(!mbTextureEncode);
        cbX264Encode.setChecked(mbUseX264Encode);
        cbX264Rotate.setEnabled(mbUseX264Encode);
        cbX264Rotate.setChecked(mbUseRotate);
        cbFdkAACEncode.setChecked(mbUseFdkAACEncode);

        if (Build.VERSION.SDK_INT <=Build.VERSION_CODES.JELLY_BEAN_MR1){
            cbTextureEncode.setEnabled(false);
            mbTextureEncode = false;
        }

        cbTextureEncode.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                cbX264Encode.setEnabled(!isChecked);
                if (isChecked)
                    cbX264Rotate.setEnabled(false);
            }
        });

        cbX264Encode.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                cbX264Rotate.setEnabled(isChecked);
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
        adapter.addAll(getResources().getStringArray(R.array.output_type_array));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(mMuxFmt);
        spinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position ==
                        CameraTestActivity.this.getResources()
                                .getStringArray(R.array.output_type_array).length - 1) {
                    AlertDialog.Builder builder;

                    final EditText input = new EditText(CameraTestActivity.this);
                    input.setText(CameraTestActivity.this.getResources().getString(R.string.able_rtmp));
                    input.setHint("输入输出文件路径");

                    builder = new AlertDialog.Builder(CameraTestActivity.this);
                    builder.setTitle("设置路径").setIcon(android.R.drawable.ic_dialog_info).setView(input)
                            .setNegativeButton("取消", null);
                    builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            mCustomizedOutputPath = input.getText().toString();
                            dialog.dismiss();
                        }
                    });
                    builder.show();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        tv_info.setText(String.format(getResources().getString(R.string.encode_dislay),
                mVideoBitRate / 1000));

        sbBitrate.setMax(5000);
        sbBitrate.setProgress(mVideoBitRate / 1000);
        sbBitrate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // TODO Auto-generated method stub
                if (fromUser) {
                    int val = progress / 100 * 100;
                    sbBitrate.setProgress(val);
                    tv_info.setText(String.format("编码码率 %d kbps", val));
                }
            }
        });

        builder = new AlertDialog.Builder(this);
        builder.setTitle("设置").setIcon(android.R.drawable.ic_dialog_info).setView(view)
                .setNegativeButton("取消", null);
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                // save data
                mbEncodeAudio = cbEnableAudio.isChecked();
                mVideoFrameRate = Integer.valueOf(etFrameRate.getText().toString());
                mVideoBitRate = sbBitrate.getProgress() * 1000;
                mMuxFmt = spinner.getSelectedItemPosition();

                mbTextureEncode = cbTextureEncode.isChecked();
                mbUseX264Encode = cbX264Encode.isChecked();
                mbUseRotate = cbX264Rotate.isChecked();
                mbUseFdkAACEncode = cbFdkAACEncode.isChecked();
                if (mbTextureEncode)
                    mbUseX264Encode = false;
                if (!mbUseX264Encode)
                    mbUseRotate = false;

                mView.setTextureEncodeMode(mbTextureEncode);
                updateBeautifyUI();

                writeSettings();

                dialog.dismiss();
            }
        });
        builder.show();
    }


    private CameraRecorderView.CameraListener mCameraListener = new CameraRecorderView.CameraListener() {
        @Override
        public void onCameraOpen(CameraRecorderView view, boolean isFront, boolean isFirstTime) {
            LogUtil.info(TAG, "Camera opened");
        }

        @Override
        public void onCameraPreviewSizeChanged(CameraRecorderView view, final int width, final int height) {
            LogUtil.info(TAG, "Camera preview size changed to " + width + " x " + height);

            CameraTestActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // preview size change to OFF face detection automatically
                    //stopArcSoftFaceDetection();
                    //mbEnableFaceDetection = false;
                }
            });
        }

        @Override
        public void onCameraOpenFailed(CameraRecorderView view, final boolean isFront, int error) {
            LogUtil.error(TAG, "Camera failed to open");

            CameraTestActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(CameraTestActivity.this,
                            "打开" + (isFront ? "前置" : "后置") + "摄像头 失败",
                            Toast.LENGTH_SHORT).show();
                }
            });

        }
    };

    private CameraRecorderView.EncoderListener mEncoderListener = new CameraRecorderView.EncoderListener() {
        @Override
        public void onConnected(CameraRecorderView view) {

        }

        @Override
        public void onError(CameraRecorderView view, int what, int extra) {
            LogUtil.error(TAG, String.format(Locale.US,
                    "Java: CameraRecorderView.onError %d %d",
                    what, extra));

            mHandler.sendMessage(mHandler.obtainMessage(
                    MainHandler.MSG_STOP_RECORD, what, extra));
        }

        @Override
        public void onInfo(CameraRecorderView view, int what, int extra) {

        }
    };


    @Override
    public void takePictureOK(Bitmap bmp) {

    }

    @Override
    public void takePictureOK(String filename) {
        Toast.makeText(this, "照片保存到 " + filename, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void takePictureError(int error_msg) {
        Toast.makeText(this, "保存照片失败: " + error_msg, Toast.LENGTH_SHORT).show();
    }

    private static class MainHandler extends Handler {
        private WeakReference<CameraTestActivity> mWeakActivity;

        private static final int MSG_UPDATE_INFO            = 1002;
        private static final int MSG_UPDATE_DURATION        = 1003;
        private static final int MSG_UPDATE_MUSIC_TIME      = 1004;
        private static final int MSG_STOP_RECORD            = 2001;
        private static final int MSG_STOP_REC_DONE          = 2002;
        private static final int MSG_EXIT                   = 3001;
        private static final int MSG_DOWNLOAD_LRC_DONE      = 4001;

        public MainHandler(CameraTestActivity activity) {
            mWeakActivity = new WeakReference<CameraTestActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            CameraTestActivity activity = mWeakActivity.get();
            if (activity == null) {
                LogUtil.debug(TAG, "Got message for dead activity");
                return;
            }

            switch (msg.what) {
                case MSG_UPDATE_DURATION:
                    SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss SSS", Locale.US);
                    formatter.setTimeZone(TimeZone.getTimeZone("GMT+00:00")); // fix 08:00:01 322 bug
                    String str_time = formatter.format(activity.mView.getRecordDurationMsec());
                    activity.mTvRecTime.setText(str_time);

                    sendEmptyMessageDelayed(MSG_UPDATE_DURATION, 100);
                    break;
                case MSG_UPDATE_MUSIC_TIME:
                    SimpleDateFormat formatter2 = new SimpleDateFormat("mm:ss", Locale.US);
                    formatter2.setTimeZone(TimeZone.getTimeZone("GMT+00:00")); // fix 08:00:01 322 bug
                    String str_position = formatter2.format(activity.mView.getSongPosition());
                    String str_left = formatter2.format(
                            activity.mView.getSongDuration() - activity.mView.getSongPosition());
                    String str_music_time = str_position + " / -" + str_left;
                    activity.mTvMusicTime.setText(str_music_time);

                    sendEmptyMessageDelayed(MSG_UPDATE_MUSIC_TIME, 1000);
                    break;
                case MSG_STOP_REC_DONE:
                    LogUtil.info(TAG, "handleMessage MSG_STOP_REC_DONE");

                    activity.mBtnRecord.setText("录像");
                    activity.mBtnRecord.setEnabled(true);
                    activity.mStopping = false;
                    break;
                case MSG_EXIT:
                    activity.finish();
                    break;
                case MSG_UPDATE_INFO:
                    String str_info = String.format(Locale.US,
                            "#%06d prev/enc fps: %.2f/%.2f\n" +
                                    "v-a %03d, write %d, swap %d msec\n" +
                                    "drawframe %d msec\n" +
                                    "buffer %d kB\n" +
                                    "latency %d msec, br %d/%d kbps",
                            activity.mView.getPreviewFrameCount(),
                            activity.mView.getPreviewFps(), activity.mView.getEncodeFps(),
                            activity.mView.getAVDiffMsec(),
                            activity.mView.getAvgWriteFrameMsec(),
                            activity.mView.getAvgSwapBuffersMsec(),
                            activity.mView.getAvgDrawFrameMsec(),
                            activity.mView.getBufferingSize() / 1024,
                            activity.mView.getLatencyMsec(),
                            activity.mView.getVideoKbps(),
                            activity.mView.getTotalKbps());
                    activity.mTvInfo.setText(str_info);
                    sendEmptyMessageDelayed(MSG_UPDATE_INFO, 50);
                    break;
                case MSG_STOP_RECORD:
                    String error_msg = "通用错误";
                    if (msg.arg1 == CameraRecorderView.RECORD_ERROR_WRITE_VIDEO_FRAME)
                        error_msg = "编码视频失败";
                    else if (msg.arg1 == CameraRecorderView.RECORD_ERROR_WRITE_AUDIO_FRAME)
                        error_msg = "编码音频失败";
                    else if (msg.arg1 == CameraRecorderView.RECORD_ERROR_INIT_AUDIO_ENC)
                        error_msg = "初始化音频编码器失败";
                    else if (msg.arg1 == CameraRecorderView.RECORD_ERROR_INIT_VIDEO_ENC)
                        error_msg = "初始化视频编码器失败";
                    else if (msg.arg1 == CameraRecorderView.RECORD_ERROR_INIT_MUXER)
                        error_msg = "初始化复用器失败";
                    else if (msg.arg1 == CameraRecorderView.RECORD_ERROR_INIT_AUDIO_REC)
                        error_msg = "初始化录音设备失败";
                    else if (msg.arg1 == CameraRecorderView.RECORD_ERROR_SET_VIDEO_PARAM)
                        error_msg = "设置视频编码参数失败";

                    error_msg = error_msg + " 错误代码 " + msg.arg2;
                    Toast.makeText(activity, error_msg, Toast.LENGTH_SHORT).show();
                    activity.mView.stopRecording();

                    activity.mRecording = false;
                    activity.updateButton();
                    break;
                case MSG_DOWNLOAD_LRC_DONE:
                    Toast.makeText(activity,
                            "歌词已加载", Toast.LENGTH_SHORT).show();
                    activity.mTvLyrics.stop();

                    activity.mTvLyrics.setVisibility(View.VISIBLE);

                    activity.mTvLyrics.setData(activity.mLyric);
                    activity.mTvLyrics.setPlayer(activity);
                    activity.mTvLyrics.start();
                    break;
                default:
                    break;
            }
        }
    }

    // Log |msg| and Toast about it.
    private void logAndToast(String msg) {
        LogUtil.info(TAG, msg);
        if (logToast != null) {
            logToast.cancel();
        }
        logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        logToast.show();
    }


}
