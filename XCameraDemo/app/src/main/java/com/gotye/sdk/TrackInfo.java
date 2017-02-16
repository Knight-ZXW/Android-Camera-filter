package com.gotye.sdk;

public class TrackInfo {

	private int mId;
	private int mStreamIndex;
	private String mCodecName;
	private String mCodecProfile;
	private String mLanguage;
	private String mTitle;

	public TrackInfo() {
		mId = -1;
		mStreamIndex = -1;
	}

	public void setId(int id) {
		mId = id;
	}

	public int getId() {
		return mId;
	}

	public void setStreamIndex(int streamIndex) {
		mStreamIndex = streamIndex;
	}

	public int getStreamIndex() {
		return mStreamIndex;
	}

	public void setCodecName(String codecName) {
		mCodecName = codecName;
	}

	public String getCodecName() {
		return mCodecName;
	}
	
	public void setCodecProfile(String profile) {
		mCodecProfile = profile;
	}

	public String getCodecProfile() {
		return mCodecProfile;
	}

	public void setLanguage(String lang) {
		mLanguage = lang;
	}

	public String getLanguage() {
		return mLanguage;
	}

	public void setTitle(String title) {
		mTitle = title;
	}

	public String getTitle() {
		return mTitle;
	}
}
