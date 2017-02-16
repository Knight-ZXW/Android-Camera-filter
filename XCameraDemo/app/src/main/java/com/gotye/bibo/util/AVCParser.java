package com.gotye.bibo.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.util.Log;

public class AVCParser {
	private static final String TAG = "AVCParser";
	
	private String mPath;
	private File mFile;
	private FileInputStream mFin;
	private boolean mEof = false;
	private byte[] mDecData;
	private int mUsedSize;
	private int mOffset;
	private boolean mFoundSPS = false;
	private boolean mFoundPPS = false;
	
	private static final int READ_SIZE = 1048576;
	private static String[] names = { "Undefined", "Coded Slice",
		"Partition A", "Partition B", "Partition C", 
		"IDR", "SEI", "SPS", "PPS", "AUD", 
		"", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "FUA" };
	
	// NAL unit types
	private final static int NAL_SLICE		= 1;
	private final static int NAL_DPA			= 2;
	private final static int NAL_DPB			= 3;
	private final static int NAL_DPC			= 4;
	private final static int NAL_IDR_SLICE	= 5;
	private final static int NAL_SEI			= 6;
	private final static int NAL_SPS			= 7;
	private final static int NAL_PPS			= 8;
	private final static int NAL_AUD			= 9;
	private final static int NAL_END_SEQUENCE	= 10;
	private final static int NAL_END_STREAM	= 11;
	private final static int NAL_FILLER_DATA	= 12;
	private final static int NAL_SPS_EXT		= 13;
	private final static int NAL_AUXILIARY_SLICE = 19;
	
	public AVCParser(String path) throws FailtoOpenException {
		mPath = path;
		mFile = new File(path);
		try {
			mFin = new FileInputStream(mFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new FailtoOpenException("Failed to open file" + path);
		}
		
		mDecData = new byte[READ_SIZE];
		mEof = false;
	}
	
	byte[] readFrame() {
		if (mUsedSize - mOffset <= 4) {
			// move left data
			int left = mUsedSize - mOffset;
			byte[] tmp = new byte[left];
			System.arraycopy(mDecData, mOffset, tmp, 0, left);
			System.arraycopy(tmp, 0, mDecData, 0, left);
			mOffset = left;
			
			int readed;
			try {
				readed = mFin.read(mDecData, mOffset, READ_SIZE - mOffset);
				if (readed != READ_SIZE - mOffset) {
					mEof = true;
					Log.i(TAG, "Java: Manual infile eof(data)");
				}
				mUsedSize = mOffset + readed;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		int frame_size = getNal(mDecData, mOffset, mUsedSize - mOffset, 0);
		if (frame_size < 0) {
			Log.w(TAG, "Java: getNal < 0");
			return null;
		}
		
		Log.i(TAG, "Java: getNal framesize " + frame_size);
		byte []nal = new byte[frame_size];
		System.arraycopy(mDecData, mOffset, nal, 0, frame_size);
		return nal;
	}
	
	private int getNal(byte[] frame, int start, int datasize, long timestamp) {
		int count = 0;
		int frameSize = 0;
		int i = 0;
		for (i = 0; i < datasize - 4; i++) {
			if (frame[i] == 0 && frame[i + 1] == 0 && frame[i + 2] == 0 && frame[i + 3] == 1) {
				Log.i(TAG, "Java: Found start marker");
				i += 4; // cursor past 0001
				// look for next 8_bit_zero marker
				int size = findFrameEnd(frame, i);
				if (size == -1) {
					// from i to end of segment
					size = datasize - i;
				} else {
					// size is from start of segment to next 8_bit_zero marker
					size = size - i;
				}
				// process an individual nal
				count++;
				frameSize += (size + 4);
				return frameSize;
			} else if (frame[i] == 0 && frame[i + 1] == 0 && frame[i + 2] == 1) {
				Log.i(TAG, "Java: Found slice marker");
				i += 3; // cursor past 001
				int size = findFrameEnd(frame, i);
				if (size == -1) {
					// from i to end of segment
					size = frame.length - i;
				} else {
					// size is from start of segment to next 8_bit_zero marker
					size = size - i;
				}
				// process an individual nal
				count++;
				frameSize += (size + 3);
				return frameSize;
			}
		}
   		
		return -1;
	}
	
	/**
	 * Returns point of '0'001' marker or -1.
	 * 
	 * @param frame
	 *            The NALU stream
	 * @param offset
	 *            The point to search from
	 * @return The point before the next marker
	 */
	public int findFrameEnd(byte[] frame, int offset) {
		int last_pos = offset;
		for (int i = offset; i < frame.length - 3; i++) {
			if (frame[i] == 0 && frame[i + 1] == 0 && frame[i + 2] == 0 && frame[i + 3] == 1) {
				Log.i(TAG, "Java: 00 00 00 01 found while looking for end of unit");
				i += 4;
				if (parse_next((byte) frame[last_pos])) {
					Log.i(TAG, "Java: parse_next 4");
					last_pos = i;
					continue;
				}
				
				return i;
			} else if (frame[i] == 0 && frame[i + 1] == 0 && frame[i + 2] == 1) {
				Log.i(TAG, "Java: 00 00 01 found while looking for end of unit");
				i += 3;
				if (parse_next((byte) frame[last_pos])) {
					Log.i(TAG, "Java: parse_next 3");
					last_pos = i;
					continue;
				}
				
				return i;
			}
		}
		Log.w(TAG, "Java: Frame end not found");
		return -1;
	}
	
	private boolean parse_next(byte header) {
		int type = readNalHeader(header);
		if (type == NAL_SPS) {
			Log.i(TAG, "Java: found NAL_SPS");
			mFoundSPS = true;
		}
		else if (type == NAL_PPS) {
			Log.i(TAG, "Java: found NAL_PPS");
			mFoundPPS = true;
		}
		else if (mFoundSPS && mFoundPPS) {
			return false;
		}	
		
		return true;
	}
	
	/**
	 * Returns nal header type.
	 * 
	 * @param data
	 * @param position
	 * @return
	 */
	private int readNalHeader(byte bite) {
		int nalType = bite & 0x1f;
		Log.i(TAG, String.format("Java: nalu_type %d[%s]", nalType, names[nalType]));
		return nalType;
	}
	
	public void close() {
		
	}
	
	class FailtoOpenException extends Exception {
		private static final long serialVersionUID = 1827167593562351779L;

		public FailtoOpenException() {
			super();
		}

		public FailtoOpenException(String msg) {
			super(msg);
		}
		
		public FailtoOpenException(String msg, Throwable cause) {
			super(msg, cause);
		}

		public FailtoOpenException(Throwable cause) {
			super(cause);
		}

	};
}
