package com.gotye.bibo.util;

public class LrcData {
	private int mArtistId;
	private String mArtistName;
	private int mAlbumId;
	private int mSongId;
	private String mSongName;
	private String mUrl;
	
	LrcData(String artist_name, String name, String url) {
		this(0, artist_name, 0, 0, name, url);
	}
	
	LrcData(int artist_id, String artist_name, int aid , int sid, String name, String url) {
		this.mArtistId 		= artist_id;
		this.mArtistName	= artist_name;
		this.mAlbumId		= aid;
		this.mSongId		= sid;
		this.mSongName		= name;
		this.mUrl			= url;
	}
	
	public int getArtistId() {
		return mArtistId;
	}
	
	public String getArtistName() {
		return mArtistName;
	}
	
	public int getAlbumId() {
		return mAlbumId;
	}
	
	public int getSongId() {
		return mSongId;
	}
	
	public String getSongName() {
		return mSongName;
	}
	
	public String getUrl() {
		return mUrl;
	}
	
	public void setArtistName(String name) {
		mArtistName = name;
	}
	
}
