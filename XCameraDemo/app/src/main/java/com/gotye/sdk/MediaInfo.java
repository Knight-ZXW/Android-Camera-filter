package com.gotye.sdk;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MediaInfo {

	static final String TAG = "player/MediaInfo";
	
	private String mTitle;
	private String mPath;
	private long mDurationMS;
	private long mSizeByte;
	private File mFile;
	private double mFrameRate;
	private int mBitrate; // bit/s
	
	private String mFormatName;
	private HashMap<Integer, String> mChannels;
	
	private String mVideoCodecName;
	private String mVideoCodecProfile;
	private int mWidth;
	private int mHeight;
	
	// video
	private int mVideoChannels; /// should always be 1?
	private int mThumbnailWidth;
	private int mThumbnailHeight;
	private int mThumbnail[]; // picture data
	
	// audio
	private int mAudioChannels;
	private ArrayList<TrackInfo> audioTrackInfos;
	
	// subtitle
	private int mSubTitleChannels;
	private ArrayList<TrackInfo> subtitleTrackInfos;
	
	private Map<String, String> mMetadata;
	private Map<String, String> mVideoMetadata;

	MediaInfo() {
		this("");
	}

	MediaInfo(String s) {
		this(s, 0L, 0L);
	}

	public MediaInfo(String path, long durationMS, long sizeByte) {
		mTitle 				= getTitleImpl(path);
		mPath 				= path;
		mDurationMS 		= durationMS;
		mSizeByte 			= sizeByte;
		mFile				= null;
		mWidth				= 0;
		mHeight				= 0;
		mFrameRate			= 0.0f;
		mBitrate			= 0;
		mFormatName			= null;
		mVideoCodecName		= null;
		mThumbnailWidth		= 0;
		mThumbnailHeight	= 0;
		mThumbnail			= null;
		mAudioChannels		= 0;
		mVideoChannels		= 0;
		mSubTitleChannels	= 0;
		mChannels = new HashMap<Integer, String>();
		audioTrackInfos = new ArrayList<TrackInfo>();
		subtitleTrackInfos = new ArrayList<TrackInfo>();
	}
	
	// common
	@Deprecated
	public void setChannels(String channelName, int index) {
		mChannels.put(Integer.valueOf(index), channelName);
	}

	@Deprecated
	public HashMap<Integer, String> getChannels() {
		return mChannels;
	}
	
	public String getTitle() {
		return mTitle;
	}
	
	private String getTitleImpl(String path) {
		if (path.startsWith("/")) {
			// local file
			int indexStart, indexEnd;
			indexEnd = path.lastIndexOf('.');
			if (indexEnd != -1) {
				indexStart = path.lastIndexOf('/');
				if (indexStart != -1) // /mnt/sdcard/1/test.mp4
					return path.substring(indexStart + 1, indexEnd);
			}
		}
		
		return "N/A";
	}

	public String getPath() {
		if (mPath == null) {
			mPath = "";
		}
		
		return mPath;
	}

	public long getDuration() {
		return mDurationMS;
	}

	public long getSize() {
		return mSizeByte;
	}

	public File getFile() {
		if (null == mFile) {
			mFile = new File(getPath());
		}
		return mFile;
	}
	
	public double getFrameRate() {
		return mFrameRate;
	}
	
	public int getBitrate() {
		return mBitrate;
	}

	public long lastModified() {
		return getFile().lastModified();
	}

	public void setFormatName(String formatName) {
		mFormatName = formatName;
	}
	
	public String getFormatName() {
		return mFormatName;
	}

	// video
	public void setVideoInfo(int width, int height, String codecName, int duration) {
		mWidth			= width;
		mHeight			= height;
		mVideoCodecName	= codecName;
		mDurationMS		= duration;
	}
	
	public int getVideoChannels() {
		return mVideoChannels;
	}
	
	public String getVideoCodecName() {
		return mVideoCodecName;
	}
	
	public String getVideoCodecProfile() {
		return mVideoCodecProfile;
	}
	
	public int getWidth() {
		return mWidth;
	}

	public int getHeight() {
		return mHeight;
	}

	public int getThumbnailWidth() {
		return mThumbnailWidth;
	}

	public int getThumbnailHeight() {
		return mThumbnailHeight;
	}

	public int[] getThumbnail() {
		return mThumbnail;
	}

	public void setAudioChannels(int num) {
		mAudioChannels = num;
	}
	
	public int getAudioChannels() {
		return mAudioChannels;
	}
	
	// audio
	/**
	 * @param id: audio channel count
	 * @param streamIndex: stream index of all stream
	 * @param codecName:
	 * @param lang: language meta data
	 * @param title: title meta data
	 */
	public void setAudioChannelsInfo(int id, int streamIndex,
									 String codecName, String profile, String lang, String title) {
		TrackInfo audiotrackinfo = new TrackInfo();
		audiotrackinfo.setId(id);
		audiotrackinfo.setStreamIndex(streamIndex);
		audiotrackinfo.setCodecName(codecName);
		audiotrackinfo.setCodecProfile(profile);
		audiotrackinfo.setLanguage(lang);
		audiotrackinfo.setTitle(title);
		audioTrackInfos.add(audiotrackinfo);
	}

	public ArrayList<TrackInfo> getAudioChannelsInfo() {
		return audioTrackInfos;
	}

	// subtitle
	public void setExtenalSubtitleChannelsInfo(String s) {
		if (s == null || !s.endsWith(".srt")) {
			return;
		}
		TrackInfo subtitletrackinfo = new TrackInfo();
		subtitletrackinfo.setId(mSubTitleChannels);
		mSubTitleChannels++;
		subtitletrackinfo.setStreamIndex(-1);
		if (s.endsWith(".srt")) {
			subtitletrackinfo.setCodecName("SubRip");
		}
		subtitleTrackInfos.add(subtitletrackinfo);
	}
	
	/**
	 * @param id: subtitle channel count
	 * @param streamIndex: stream index of all stream
	 * @param codecName:
	 * @param lang: language meta data
	 * @param title: title meta data
	 */
	public void setSubtitleChannelsInfo(int id, int streamIndex, String codecName, String lang, String title) {
		TrackInfo subtitletrackinfo = new TrackInfo();
		subtitletrackinfo.setId(id);
		subtitletrackinfo.setStreamIndex(streamIndex);
		subtitletrackinfo.setCodecName(codecName);
		subtitletrackinfo.setLanguage(lang);
		subtitletrackinfo.setTitle(title);
		subtitleTrackInfos.add(subtitletrackinfo);
	}

	public ArrayList<TrackInfo> getSubtitleChannelsInfo() {
		return subtitleTrackInfos;
	}
	
	public void setSubtitleChannels(int num) {
		mSubTitleChannels = num;
	}
	
	public int getSubtitleChannels() {
		return mSubTitleChannels;
	}
	
	public void addMetadataEntry(String key, String value) {
		if (mMetadata == null)
			mMetadata = new HashMap<String, String>();
		
		mMetadata.put(key, value);
	}
	
	public void addVideoMetadataEntry(String key, String value) {
		if (mVideoMetadata == null)
			mVideoMetadata = new HashMap<String, String>();
		
		mVideoMetadata.put(key, value);
	}
	
	public Map<String, String> getMetaData() {
		return mMetadata;
	}
	
	@Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        
        sb.append(mTitle).append('|').
        	append(mPath).append('|').
            append(mDurationMS).append('|').
            append(mSizeByte).append('|').
            append(mWidth).append('x').append(mHeight).append('|').
            append(mFormatName).append('|').
            append(mVideoCodecName).append('|');
        if (audioTrackInfos.size() > 0) {
        	for(int i=0;i<audioTrackInfos.size();i++) {
        		sb.append(audioTrackInfos.get(i).getCodecName()).append('(');
        		sb.append(audioTrackInfos.get(i).getTitle()).append(",");
        		sb.append(audioTrackInfos.get(i).getLanguage()).append(")|");
        	}
        }
        if (subtitleTrackInfos.size() > 0) {
        	for(int i=0;i<subtitleTrackInfos.size();i++) {
        		sb.append(subtitleTrackInfos.get(i).getCodecName()).append('(');
        		sb.append(subtitleTrackInfos.get(i).getTitle()).append(",");
        		sb.append(subtitleTrackInfos.get(i).getLanguage()).append(")|");
        	}
        }
            
        return sb.toString();
    }
}
