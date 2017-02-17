package com.gotye.bibo.ui;

import android.content.DialogInterface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.MediaController.MediaPlayerControl;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.gotye.bibo.R;
import com.gotye.bibo.util.AVCNaluType;
import com.gotye.bibo.util.Constants;
import com.gotye.bibo.util.FileFilterTest;
import com.gotye.bibo.util.LogUtil;
import com.gotye.bibo.util.Util;
import com.gotye.sdk.DecoderInterface.OnDataListener;
import com.gotye.sdk.FFMediaInfo;
import com.gotye.sdk.MediaInfo;
import com.gotye.sdk.PPDecoder;
import com.gotye.sdk.PPDecoder.DecodeMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class DecodeH264Activity extends AppCompatActivity
        implements OnDataListener, Callback {
	private final static String TAG = "DecodeH264Activity";
	private final static String mRootPath = Environment.getExternalStorageDirectory()
			.getAbsolutePath() + "/test2/bibo";

    private LinearLayout mMainLayout;
    private RelativeLayout mPlayerLayout;
	private SurfaceView mSurfaceView;
	private SurfaceHolder mHolder;
	private TextView mTvInfo;
	private Button mBtnDecodeMode;
	private Button mBtnCapture;
	private ListView mLvFile;

	private MediaPlayer mPlayer;
	private MediaController mController;
	
	private SimpleAdapter mAdapter;
	private List<HashMap<String,Object>> mFilelist;
	
	private static String[] names = { "Undefined", "Coded Slice",
		"Partition A", "Partition B", "Partition C", 
		"IDR", "SEI", "SPS", "PPS", "AUD", 
		"", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "FUA" };
	
	private final static int MSG_PLAY_COMPLETE 			= 1001;
	private final static int MSG_DECODE_FRAME 			= 1002;
	private final static int MSG_FAIL_TO_OPEN_DECODER 	= 2001;
	
	private final static int CAM_FPS = 15;
	
	private PPDecoder mDecoder;
	private boolean mStopping = false;
	private Thread mThread;
	private String mDecFilePath;
	private int mWidth, mHeight;
	private int mDecFrameRate;
	private int mDecFrameCnt;
	private long mStartMsec;
	
	private boolean mFoundSPS = false;
	private boolean mFoundPPS = false;
	
	private boolean mDecodeSW = false;
	private boolean mbDecodeWithSize = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        super.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
        setContentView(R.layout.activity_mediacodec);

        mMainLayout = (LinearLayout) this.findViewById(R.id.layout_main);
        mPlayerLayout = (RelativeLayout) this.findViewById(R.id.layout_player);
        mSurfaceView = (SurfaceView) this.findViewById(R.id.movie_view2);
        mTvInfo = (TextView) this.findViewById(R.id.tv_info);
        mBtnDecodeMode = (Button) this.findViewById(R.id.btn_decode_mode);
        mBtnCapture = (Button) this.findViewById(R.id.btn_capture);
        mLvFile = (ListView) this.findViewById(R.id.lv_filelist);
        
        mController = new MediaController(this);
        
        mHolder = mSurfaceView.getHolder();
        mHolder.setKeepScreenOn(true);
        mHolder.addCallback(this);
        
        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
			
			@Override
			public boolean onTouch(View view, MotionEvent event) {
				// TODO Auto-generated method stub
				if (event.getAction() == MotionEvent.ACTION_DOWN && mPlayer != null) {
					if (mController.isShowing())
						mController.hide();
					else
						mController.show(3000);
					
					return true;
				}
				
				return false;
			}
		});
        
        mDecodeSW = Util.readSettingsBoolean(this, "use_ffmpeg_decode");
        if (mDecodeSW)
        	mBtnDecodeMode.setText("软解");
        else
        	mBtnDecodeMode.setText("硬解");
        
        mBtnDecodeMode.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				mDecodeSW = !mDecodeSW;
				if (mDecodeSW)
					mBtnDecodeMode.setText("软解");
				else
					mBtnDecodeMode.setText("硬解");
			}
		});
        
        mBtnCapture.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {

			}
		});
        
        mFilelist = new ArrayList<HashMap<String,Object>>();
        File file = new File(mRootPath);
        String[]exts = new String[]{"h264", "264", "ts", "mpegts", "mp4", "flv"};
        File[] files = file.listFiles(new FileFilterTest(exts));
        Arrays.sort(files, new FileComparator());
        
        int index = 1;
        for (File f:files) {
            HashMap<String,Object> map = new HashMap<String,Object>();
            map.put("id", index++);  
            map.put("filename", f.getName());
            map.put("filepath", f.getAbsolutePath());
            map.put("filesize", Util.getFileSize(f.length()));
            mFilelist.add(map);  
        }  
        
        mAdapter = new SimpleAdapter(
        		this, mFilelist, R.layout.item, new String[]{"id","filename","filesize"},
        		new int[]{R.id.id1, R.id.filename, R.id.filesize});
        mLvFile.setAdapter(mAdapter);
        
        mLvFile.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View view,
									int position, long id) {
				// TODO Auto-generated method stub
				if (mPlayer != null) {
					mController.hide();
					
					mPlayer.stop();
					mPlayer.release();
					mPlayer = null;
				}
				
				stopThread();
				
				mSurfaceView.setVisibility(View.INVISIBLE);
				// just fix sw->hw displayer problem
				// E/MediaCodec(27658): native_window_api_connect returned an error: File exists (-17)
				
				mDecFilePath = mRootPath + "/" + mFilelist.get(position).get("filename");
				if (!mDecFilePath.endsWith(".mp4")) {
					// mp4 mdat moov box reverse
					// will stuck for a long time
					// fix ME!!!
					MediaInfo info = FFMediaInfo.getMediaInfo(mDecFilePath);
					if (info == null) {
						LogUtil.error(TAG, "java: failed to get media info: " + mDecFilePath);
						Toast.makeText(DecodeH264Activity.this, "获取媒体信息失败", Toast.LENGTH_SHORT).show();
						return;
					}

					mWidth = info.getWidth();
					mHeight = info.getHeight();
					mDecFrameRate = (int) info.getFrameRate();
				}
                else {
                    mWidth = mHeight = 0;
                    mDecFrameRate = 0;
                }
				
				mSurfaceView.setVisibility(View.VISIBLE);
			}
		});
        
        mLvFile.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View view,
										   final int position, long id) {
				// TODO Auto-generated method stub
                AlertDialog.Builder builder = new AlertDialog.Builder(DecodeH264Activity.this);
                builder.setTitle("文件删除").setIcon(android.R.drawable.ic_dialog_info)
                        .setNegativeButton("取消", null);
                builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        mDecFilePath = mRootPath + "/" + mFilelist.get(position).get("filename");

                        File f = new File(mDecFilePath);
                        f.delete();
                        Toast.makeText(DecodeH264Activity.this, mDecFilePath + " 已删除", Toast.LENGTH_SHORT).show();

                        mFilelist.remove(position);
                        mAdapter.notifyDataSetChanged();

                        dialog.dismiss();
                    }
                });
                builder.show();
				
				return true;
			}
		});
    }
	
	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		
		 Util.writeSettingsBoolean(this, "use_ffmpeg_decode", mDecodeSW);
		
		if (mPlayer != null) {
			mController.hide();
			
			mPlayer.stop();
			mPlayer.release();
			mPlayer = null;
		}

		stopThread();
	}
	
	@Override
	protected void onResume() {
		super.onResume();

        setup_layout();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_decode, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// TODO Auto-generated method stub
		int id = item.getItemId();
		switch (id) {
		case R.id.action_delete_all:
			for (int i=0;i<mFilelist.size();i++) {
				String path = (String)mFilelist.get(i).get("filepath");
				File f = new File(path);
				f.delete();
			}
			
			mFilelist.clear();
			mAdapter.notifyDataSetChanged();
			Toast.makeText(this, "媒体文件已全部删除", Toast.LENGTH_SHORT).show();
			
			break;
		default:
			LogUtil.warn(TAG, "unknown menu id: " + id);
			break;
		}
		
		return true;
	}
	
	@Override
    public boolean onTouchEvent(MotionEvent event) {
        /*
         * the MediaController will hide after 3 seconds - tap the screen to
         * make it appear again
         */
        return false;
    }
	
	private MediaPlayerControl mPlayerControl = new MediaPlayerControl() {

		@Override
		public boolean canPause() {
			// TODO Auto-generated method stub
			return true;
		}

		@Override
		public boolean canSeekBackward() {
			// TODO Auto-generated method stub
			return true;
		}

		@Override
		public boolean canSeekForward() {
			// TODO Auto-generated method stub
			return true;
		}

		@Override
		public int getAudioSessionId() {
			// TODO Auto-generated method stub
			if (mPlayer != null)
				return mPlayer.getAudioSessionId();
			
			return 0;
		}

		@Override
		public int getBufferPercentage() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public int getCurrentPosition() {
			// TODO Auto-generated method stub
			if (mPlayer != null)
				return mPlayer.getCurrentPosition();
			
			return 0;
		}

		@Override
		public int getDuration() {
			// TODO Auto-generated method stub
			if (mPlayer != null)
				return mPlayer.getDuration();
			
			return 0;
		}

		@Override
		public boolean isPlaying() {
			// TODO Auto-generated method stub
			if (mPlayer != null)
				return mPlayer.isPlaying();
			
			return false;
		}

		@Override
		public void pause() {
			// TODO Auto-generated method stub
			if (mPlayer != null)
				mPlayer.pause();
		}

		@Override
		public void seekTo(int msec) {
			// TODO Auto-generated method stub
			if (mPlayer != null)
				mPlayer.seekTo(msec);
		}

		@Override
		public void start() {
			// TODO Auto-generated method stub
			if (mPlayer != null)
				mPlayer.start();
		}
		
	};
	
	private Handler mHandler = new Handler() {
		@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_PLAY_COMPLETE:
            	Toast.makeText(DecodeH264Activity.this, "解码器 播放完成", Toast.LENGTH_SHORT).show();
            	break;
            case MSG_DECODE_FRAME:
            	if (mDecoder != null) {
            		mTvInfo.setText(String.format("解码(%s) %d x %d @%d fps, #%d",
                		mDecoder.getDecodeMode() == DecodeMode.SYSTEM ? "HW" : "FF", 
                				mWidth, mHeight, mDecFrameRate, mDecFrameCnt));
            	}
            	break;
            case MSG_FAIL_TO_OPEN_DECODER:
            	Toast.makeText(DecodeH264Activity.this, "打开解码器失败", Toast.LENGTH_SHORT).show();
            	break;
            default:
				LogUtil.warn(TAG, "Java: unknown msg.what " + msg.what);
				break;
            }
		}
	};
	
	@Override
	public void onData(PPDecoder dec, int frame_count) {
		// TODO Auto-generated method stub
		mDecFrameCnt = frame_count;
		mHandler.sendEmptyMessage(MSG_DECODE_FRAME);
	}

	private void setup_layout() {
		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		int screen_width = dm.widthPixels;
		int screen_height = dm.heightPixels;
		LogUtil.info(TAG, String.format("Java: screen %d x %d", screen_width, screen_height));

		boolean isLandscape = (screen_width > screen_height);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)mPlayerLayout.getLayoutParams();

		if (isLandscape) {
			mMainLayout.setOrientation(LinearLayout.HORIZONTAL);
            params.width = screen_width * 2 / 3;
		}
		else {
			mMainLayout.setOrientation(LinearLayout.VERTICAL);
            params.height = screen_height / 2;
		}

        mPlayerLayout.setLayoutParams(params);
        mPlayerLayout.requestLayout();
	}
	
	private boolean setup_player(String url) {
		mPlayer = new MediaPlayer();
		mPlayer.setDisplay(mSurfaceView.getHolder());
		mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mPlayer.setScreenOnWhilePlaying(true);
		//mPlayer.setLooping(true);
		mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
			
			@Override
			public boolean onError(MediaPlayer mp, int what, int extra) {
				// TODO Auto-generated method stub
				Toast.makeText(DecodeH264Activity.this, String.format("播放器 错误 %d %d", what, extra),
						Toast.LENGTH_SHORT).show();
				return true; // false will cause onComplete as sequence
			}
		});
		
		mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
			
			@Override
			public void onPrepared(MediaPlayer mp) {
				// TODO Auto-generated method stub

                mp.start();
				
				mController.setMediaPlayer(mPlayerControl);
				mController.setAnchorView(mSurfaceView);
			}
		});
		
		mPlayer.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
			
			@Override
			public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
				// TODO Auto-generated method stub
				mSurfaceView.getHolder().setFixedSize(width, height);

                if (mWidth == 0 || mHeight == 0) {
                    mWidth = width;
                    mHeight = height;
                }
				mTvInfo.setText(String.format("解码 %d x %d @%d fps",
						mWidth, mHeight, mDecFrameRate));

                int layout_w = mPlayerLayout.getMeasuredWidth();
                int layout_h = mPlayerLayout.getMeasuredHeight();

                ViewGroup.LayoutParams params = mSurfaceView.getLayoutParams();

                double ratio = (double)(layout_w * height) / (double)(layout_h * width);
                LogUtil.info(TAG, String.format("ratio %.2f", ratio));

                if (ratio > 1.0f) {
                    // video too high
                    params.height = layout_h;
                    params.width = (int)(layout_w / ratio);
                }
                else if (ratio < 1.0f) {
                    // video too wide
                    params.width = layout_w;
                    params.height = (int)(layout_h * ratio);
                }
                else {
                    params.width = layout_w;
                    params.height = layout_h;
                }

                LogUtil.info(TAG, String.format("layout %d x %d, video %d x %d",
                        layout_w, layout_h, width, height));
                LogUtil.info(TAG, String.format("params %d x %d", params.width, params.height));

                mSurfaceView.setLayoutParams(params);
                mPlayerLayout.requestLayout();
			}
		});
		
		mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
			
			@Override
			public void onCompletion(MediaPlayer mp) {
				// TODO Auto-generated method stub
				mController.hide();
				Toast.makeText(DecodeH264Activity.this, "播放器 播放完成", Toast.LENGTH_SHORT).show();
			}
		});
		
		try {
			mPlayer.setDataSource(url);
			mPlayer.prepareAsync();
			return true;
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return false;
	}
	
	private void stopThread() {
		if (mThread != null) {
			LogUtil.info(TAG, "Java: set mStopping true");
			mStopping = true;
			try {
				LogUtil.info(TAG, "Java: before join");
				mThread.join();
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			mStopping = false;
			mThread = null;
		}
	}
	
	private void decode_file() {
		mThread = new Thread(new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				if (mbDecodeWithSize)
					dec_thread();
				else
					dec_raw_thread();
			}
		});
		mThread.start();
	}
	
	private void dec_thread() {
		LogUtil.info(TAG, "dec_thread started");
		
		DecodeMode mode = DecodeMode.SYSTEM;
		if (!mDecodeSW) //硬解 使用 ffmpeg
			mode = DecodeMode.FFMPEG;

		LogUtil.info(TAG, String.format("create %s decoder", mode == DecodeMode.SYSTEM ? "HW" : "SW"));

		mDecoder = new PPDecoder(this, mode);
		mDecoder.setView(mSurfaceView);
		mDecoder.setOnDataListener(this);
		if (!mDecoder.open(mWidth, mHeight, mDecFrameRate)) {
			LogUtil.error(TAG, "java: failed to open decoder");
			mHandler.sendEmptyMessage(MSG_FAIL_TO_OPEN_DECODER);
			return;
		}
		
		File sdInFile = new File(mDecFilePath);
		FileInputStream fin = null;
		try {
			fin = new FileInputStream(sdInFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		
		byte[] size_buf = new byte[4];
		byte[] decdata = new byte[1048576];
		byte[] opaque = new byte[16];
		int decdata_size;
		mDecFrameCnt = 0;
		
		mStartMsec = System.currentTimeMillis();
		try {
			while (!mStopping) {
            	int ret = fin.read(size_buf, 0, 4);
				if (ret != 4) {
					LogUtil.info(TAG, "Java: eof");
					break;
				}
				
				decdata_size = Util.ByteToInt(size_buf);
				
				if (decdata_size < 0 || decdata_size > 1048576) {
					LogUtil.error(TAG, "Java: Manual h264 data is corrupt: " + decdata_size);
					break;
				}
				
				LogUtil.info(TAG, "Java: read size " + decdata_size);
				
				ret = fin.read(decdata, 0, decdata_size);
				if (ret != decdata_size) {
					LogUtil.info(TAG, "Java: Manual infile eof(data)");
					break;
				}
            
                if (!mDecoder.addData(decdata, 0, decdata_size, opaque)) {
                	LogUtil.error(TAG, "Java: failed to add data");
                	break;
                }
                
                long elapsed_msec = System.currentTimeMillis() - mStartMsec;
                int sleep_msec = (int)(mDecFrameCnt * 1000 / mDecFrameRate - elapsed_msec);
                if (sleep_msec > 5) {
                    try {
                   	 Thread.sleep(sleep_msec);
                    } catch(InterruptedException e) {
                   	 e.printStackTrace();
                    }
                }
			}
		} catch (IOException e){
			e.printStackTrace();
			LogUtil.error(TAG, "an error occured while decoding: " + e);
		}
		
		if (!mStopping)
			mHandler.sendEmptyMessage(MSG_PLAY_COMPLETE);
		
		mDecoder.close();
		mDecoder = null;
		LogUtil.info(TAG, "dec_thread ended");
	}
	
	private void dec_raw_thread() {
		LogUtil.info(TAG, "dec_raw_thread started");

	    //byte[] header_sps = { 0, 0, 0, 1, 103, 100, 0, 40, -84, 52, -59, 1, -32, 17, 31, 120, 11, 80, 16, 16, 31, 0, 0, 3, 3, -23, 0, 0, -22, 96, -108 };
	    //byte[] header_pps = { 0, 0, 0, 1, 104, -18, 60, -128 };
	    //format.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
	    //format.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
	    //format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1920 * 1080);
	    //format.setInteger("durationUs", 63446722);
	    
	    //NAL Units start code: 00 00 01 X Y
	    //X = IDR Picture NAL Units (25, 45, 65)
	    //X = Non IDR Picture NAL Units (01, 21, 41, 61) ; 01 = b-frames, 41 = p-frames
	    //So, if you find 3 bytes [00 00 01] in sequence, 
	    //very likely it's the beginning of the NAL unit. 
	    //Then look at the next two bytes [X Y] to find out the type of frame. 
	    //Look at the spec for more details.

		DecodeMode mode = DecodeMode.SYSTEM;
		if (mDecodeSW)
			mode = DecodeMode.FFMPEG;

        LogUtil.info(TAG, String.format("create %s decoder", mode == DecodeMode.SYSTEM ? "HW" : "SW"));

		mDecoder = new PPDecoder(this, mode);
		mDecoder.setView(mSurfaceView);
		mDecoder.setOnDataListener(this);
		if (!mDecoder.open(mWidth, mHeight, mDecFrameRate)) {
			LogUtil.error(TAG, "java: failed to open decoder");
			mHandler.sendEmptyMessage(MSG_FAIL_TO_OPEN_DECODER);
			return;
		}

		File sdInFile = new File(mDecFilePath);
		FileInputStream fin = null;
		try {
			fin = new FileInputStream(sdInFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		
		final int READ_SIZE = 1048576;
		byte[] decdata = new byte[READ_SIZE];
		boolean eof = false;
		mDecFrameCnt = 0;
		mFoundSPS = mFoundPPS = false;
		int offset = 0;
		int used;
		
		mStartMsec = System.currentTimeMillis();
		try {
			while (!eof && !mStopping) {
				int readed = fin.read(decdata, offset, READ_SIZE - offset);
				LogUtil.info(TAG, "Java: readed " + readed);
				if (readed != READ_SIZE - offset) {
					eof = true;
					LogUtil.info(TAG, "Java: Manual infile eof(data)");
				}
            
				used = offset + readed;
				offset = processNals(decdata, used);
				
				int left = used - offset;
				LogUtil.info(TAG, String.format("Java: offset %d, used %d, left %d", offset, used, left));
				
				if (left > 0) {
					byte[] tmp = new byte[left];
					System.arraycopy(decdata, offset, tmp, 0, left);
					System.arraycopy(tmp, 0, decdata, 0, left);
					offset = left;
					used = left;
				}
				else {
					offset = 0;
					used = 0;
				}
			}
		} catch (IOException e){
			e.printStackTrace();
			LogUtil.error(TAG, "an error occured while decoding: " + e);
		}

		if (!mStopping)
			mHandler.sendEmptyMessage(MSG_PLAY_COMPLETE);
		
		mDecoder.close();
		mDecoder = null;
		LogUtil.info(TAG, "dec_raw_thread ended");
	}
	
	/**
	 * Returns count of NALU's processed for given byte array.
	 * 
	 * @param frame
	 * @return nal process count
	 */
	public int processNals(byte[] frame, int datasize) {
		if (datasize <= 4) {
			LogUtil.warn(TAG, "Java: no more data");
            // flush left data
			return datasize;
		}

		int last_nalu_start = -1;
		byte[] opaque = new byte[16];

        int pos;
        for (pos = 0 ; pos < datasize - 4 ; pos++) {
            if (mStopping)
                break;

            if ((frame[pos] == 0 && frame[pos + 1] == 0 && frame[pos + 2] == 0 && frame[pos + 3] == 1) ||
                    (frame[pos] == 0 && frame[pos + 1] == 0 && frame[pos + 2] == 1))
            {
                boolean bFourStartCode = true;
                if (frame[pos] == 0 && frame[pos + 1] == 0 && frame[pos + 2] == 1)
                    bFourStartCode = false;

                byte header;
                if (bFourStartCode)
                    header = frame[pos + 4];
                else
                    header = frame[pos + 3];
                if (parse_next(header)) {
                    if (last_nalu_start == -1)
                        last_nalu_start = 0;

                    pos += (bFourStartCode ? 3 : 2);
                    continue;
                }

                if (last_nalu_start != -1) {
                    // 00 00 00 01 xx data ...
                    int sizeNAL = pos - last_nalu_start;
                    if (!mDecoder.addData(frame, last_nalu_start, sizeNAL, opaque)) {
                        LogUtil.error(TAG, "Java: failed to add dec data");
                        break;
                    }

                    long elapsed_msec = System.currentTimeMillis() - mStartMsec;
                    int sleep_msec = (int)(mDecFrameCnt * 1000 / mDecFrameRate - elapsed_msec);
                    if (sleep_msec > 5) {
                        try {
                            Thread.sleep(sleep_msec);
                        } catch(InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                // update last nalu position
                last_nalu_start = pos;

                if (bFourStartCode)
                    pos += 3; // +1 by for()
                else
                    pos += 2; // +1 by for()
            }
        }
   		
		return pos;
	}

    private boolean parse_next(byte header) {
        int type = readNalHeader(header);
        if (type == AVCNaluType.SPS && mFoundSPS == false) {
            LogUtil.info(TAG, "Java: found NAL_SPS");
            mFoundSPS = true;
        }
        else if (type == AVCNaluType.PPS && mFoundPPS == false) {
            LogUtil.info(TAG, "Java: found NAL_PPS");
            mFoundPPS = true;
        }
        else if (mFoundSPS && mFoundPPS) {
            return false;
        }

        return true;
    }

    private static int readNalHeader(byte bite) {
        int nalType = bite & 0x1f;
        if (Constants.VERBOSE) LogUtil.debug(TAG, String.format("Java: nalu_type %d[%s]", nalType, names[nalType]));
        return nalType;
    }
	
	private class FileComparator implements Comparator<File> {
		@Override
		public int compare(File f1, File f2) {
			if (f1.isFile() && f2.isDirectory())
				return 1;
			if (f2.isFile() && f1.isDirectory())
				return -1;
				
			String s1=f1.getName().toString().toLowerCase();
			String s2=f2.getName().toString().toLowerCase();
			int ret = s1.compareTo(s2);
			return -ret;
	    }
	}

	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
		// TODO Auto-generated method stub
		if (mDecFilePath != null) {
			if (mDecFilePath.endsWith(".h264") || mDecFilePath.endsWith(".264")) {
				mDecFrameRate = CAM_FPS;
				decode_file();
			}
			else {
				setup_player(mDecFilePath);
			}
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		// TODO Auto-generated method stub
		
	}
	
}
