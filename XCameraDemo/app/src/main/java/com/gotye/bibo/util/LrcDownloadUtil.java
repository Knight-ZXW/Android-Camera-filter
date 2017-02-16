package com.gotye.bibo.util;

import android.util.Log;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LrcDownloadUtil {
	private static String TAG = "LrcDownloadUtil";
	private static String API_GECIME_LYRIC_URL = "http://geci.me/api/lyric/";
	private static String API_GECIME_LYRIC_ATRIST = "http://geci.me/api/artist/";
	private static String API_BAIDU_LYRIC_URL = "http://box.zhangmen.baidu.com" +
			"/x?op=12" +
			"&count=1" +
			"&title="; //%B2%BB%B5%C3%B2%BB%B0%AE$$%C5%CB%E7%E2%B0%D8$$$$";

    private static String BAIDU_LRC_URL_FMT = "http://box.zhangmen.baidu.com/bdlrc/%d/%d.lrc";
    private static String BAIDU_LRC2_URL_FMT = "http://music.baidu.com/data/music/links?songIds=";
    private static String BAIDU_SONG_SEARCH_URL_FMT = "http://musicmini.baidu.com/app/search/searchList.php?qword=";
    private static String BAIDU_LRC_HOST = "http://qukufile2.qianqian.com";

	public static List<LrcData> getLrc(String song_name) {
		return getLrc(song_name, null);
	}
	
	public static List<LrcData> getLrc(String song_name, String artist) {
		Log.i(TAG, String.format("Java: getLyc(): song %s, artist %s", song_name, artist));
		
		String encoded_str;
		try {
			encoded_str = URLEncoder.encode(song_name, "UTF-8");
			String url = API_GECIME_LYRIC_URL + encoded_str;
			
			if (artist != null) {
				encoded_str = URLEncoder.encode(artist, "UTF-8");
				url += "/";
				url += encoded_str;
			}
			
			Log.i(TAG, "Java: getLyc(): " + url);

			String result = Util.getHttpPage(url);
			Log.d(TAG, "Java: result: " + result);
			
			JSONTokener jsonParser = new JSONTokener(result);
			JSONObject root = (JSONObject) jsonParser.nextValue();
			int count = root.getInt("count");
			JSONArray lyrics = root.getJSONArray("result");
			int num = lyrics.length();
			
			List<LrcData> lrcList = new ArrayList<LrcData>();
			
			for (int i=0;i<num;i++) {
				JSONObject lyc = lyrics.getJSONObject(i);
				String lrc_path = lyc.getString("lrc");
				String lrc_song = lyc.getString("song");
				int artist_id = lyc.getInt("artist_id");
				int sid = lyc.getInt("sid");
				
				LrcData lrc = new LrcData(artist_id, "", sid, 0, lrc_song, lrc_path);
				lrcList.add(lrc);
				
				Log.i(TAG, String.format("Java: get lrc_path: %s, lrc_song: %s, artist_id: %d, sid %d",
						lrc_path, lrc_song, artist_id, sid));
			}
			
			return lrcList;
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
		
	}
	
	public static String getBaiduLrc(String song_name, String artist) {
		Log.i(TAG, String.format("Java: getBaiduLyc(): song %s, artist %s", song_name, artist));
		
		String encoded_str;
		try {
			encoded_str = URLEncoder.encode(song_name, "GB2312");
			String url = API_BAIDU_LYRIC_URL + encoded_str;
			
			if (artist != null) {
				encoded_str = URLEncoder.encode(artist, "GB2312");
				url += "$$";
				url += encoded_str;
				url += "$$$$";
			}
			
			Log.i(TAG, "Java: getBaiduLyc() url: " + url);

			String response = Util.getHttpPage(url);
			String result = new String(response.getBytes("gb2312"), "utf-8");
			Log.d(TAG, "Java: result: " + result);
			
			SAXBuilder builder = new SAXBuilder();
			Reader returnQuote = new StringReader(result);
	        Document doc = builder.build(returnQuote);
			Element root = doc.getRootElement();
			
			String count = root.getChildText("count");
			int c = Integer.valueOf(count);
			if (c == 0) {
				Log.e(TAG, "lrc count is zero");
				return null;
			}
			
			List<Element> xml_url_list = root.getChildren("url");
			if (xml_url_list == null || xml_url_list.size() == 0) {
				Log.e(TAG, "failed to get xml_url_list");
				return null;
			}
			
			Element first_item = xml_url_list.get(0);
			String lrcid = first_item.getChildText("lrcid");
			int id = Integer.valueOf(lrcid);
			if (id ==0) {
				Log.e(TAG, "Java: error lrcid is 0");
				return null;
			}
			
			String lrc_url = String.format(BAIDU_LRC_URL_FMT, id / 100, id);
			Log.i(TAG, "Java: get lrc_url " + lrc_url);
			return lrc_url;
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JDOMException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}

    public static class SongInfo {
        private String mSongId;
        private String mSongName;
        private String mArtist;
        private String mAlbumName;
        private String mLrcPath;
        private String mSongPicPath;
		private String mSongLink;

        public SongInfo(String songId, String songName, String artist, String albumName) {
            this.mSongId = songId;
            this.mSongName = songName;
            this.mArtist = artist;
            this.mAlbumName = albumName;
        }

        public SongInfo(String songId, String songName,
						String artist, String lrcpath, String songPic, String songLink) {
            this.mSongId = songId;
            this.mSongName = songName;
            this.mArtist = artist;
            this.mLrcPath = lrcpath;
            this.mSongPicPath = songPic;
			this.mSongLink = songLink;
        }

        public String getSongId() {
            return mSongId;
        }

        public String getSongName() {
            return mSongName;
        }

        public String getArtist() {
            return mArtist;
        }

        public String getAlbumName() {
            return mAlbumName;
        }

        public String getLrcPath() {
            return mLrcPath;
        }

        public String getSongPicPath() {
            return mSongPicPath;
        }

        public String getSongLink() {
            return mSongLink;
        }
    };

    public static SongInfo getBaiduLrc2(String songIds) {
        String path = BAIDU_LRC2_URL_FMT + songIds;
        Log.i(TAG, "Java: getBaiduLrc2() url: " + path);
        try {
            URL url = new URL(path);
            URLConnection conn = url.openConnection();
            conn.connect();
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            StringBuffer sb = new StringBuffer();
            while((line = in.readLine()) != null){
                sb.append(line);
            }

            String result = sb.toString();
            Log.d(TAG, "Java: result: " + result);

            JSONTokener jsonParser = new JSONTokener(result);
            JSONObject root = (JSONObject) jsonParser.nextValue();
            JSONObject data = root.getJSONObject("data");
            JSONArray songlist = data.getJSONArray("songList");
            int count = songlist.length();
            for (int i=0;i<count;i++) {
                /*queryId: "1262598",
                songId: 1262598,
                songName: "千千阙歌",
                artistId: "11699",
                artistName: "陈慧娴",
                albumId: 197096,
                albumName: "千千阙歌",
                songPicSmall: "http://musicdata.baidu.com/data2/pic/88579378/88579378.jpg",
                songPicBig: "http://musicdata.baidu.com/data2/pic/88579352/88579352.jpg",
                songPicRadio: "http://musicdata.baidu.com/data2/pic/88579342/88579342.jpg",
                lrcLink: "/data2/lrc/240890428/240890428.lrc",
                version: "",
                copyType: 1,
                time: 298,
                linkCode: 22000,
                songLink: "http://file.qianqian.com//data2/music/134380688/134380688.mp3?xcode=1e33e62cd60a750b6a37d80a2c02fc5b&src="http%3A%2F%2Fpan.baidu.com%2Fshare%2Flink%3Fshareid%3D1368254163%26uk%3D2605942610"",
                showLink: "http://pan.baidu.com/share/link?shareid=1368254163&uk=2605942610",
                format: "mp3",
                rate: 128,
                size: 4779264,
                relateStatus: "0",
                resourceType: "2",
                source: "web"*/

                JSONObject song = songlist.getJSONObject(i);
                String queryId = song.getString("queryId");
                int songId = song.getInt("songId");
                String songName = song.getString("songName");
                String artistName = song.getString("artistName");
                String artistId = song.getString("artistId");
                int albumId = song.getInt("albumId");
                String albumName = song.getString("albumName");
                String songPic = song.getString("songPicBig");
                String lrcLink = song.getString("lrcLink");
                String songLink = song.getString("songLink");
                String showLink = song.getString("showLink");
                String format = song.getString("format");
                int rate = song.optInt("rate");
                int size = song.optInt("size");

                //歌曲地址里如果有
                // http://qukufile2.qianqian.com/data2/pic/和
                // http://c.hiphotos.baidu.com/ting/pic/item/
                // 那就需要将http://c.hiphotos.baidu.com/ting/pic/item/给去掉

                //歌词地址：http://qukufile2.qianqian.com+获取到的url
                LogUtil.info(TAG, "lrcLink: " + lrcLink);
                String lrcPath;
				if (lrcLink.startsWith("http://qukufile2.qianqian.com")) {
					lrcPath = lrcLink.replace("http://qukufile2.qianqian.com", "");
				}
				else if (lrcLink.startsWith("http://c.hiphotos.baidu.com/ting/pic/item")) {
					lrcPath = BAIDU_LRC_HOST + lrcLink.replace("http://c.hiphotos.baidu.com/ting/pic/item", "");
				}
				else if (lrcLink.startsWith("http://")) {
                    lrcPath = lrcLink;
                }
                else {
					lrcPath = lrcLink;
				}
                LogUtil.info(TAG, String.format(Locale.US,
						"Java: queryId %s, songId %d, songName %s, " +
                                "artistName %s, artistid %s, albumId %d, albumName %s, " +
                                "lrcPath %s, songLink %s, " +
                                "songPic %s, " +
                                "format %s, bitrate %d, filesize %d",
                        queryId, songId, songName,
                        artistName, artistId, albumId, albumName,
                        lrcPath, songLink,
                        songPic,
                        format, rate, size));
                return new SongInfo(String.valueOf(songId), songName, artistName, lrcPath, songPic, songLink);
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static List<SongInfo> searchBaiduSong(String keyword) {
        return searchBaiduSong(keyword, 0);
    }

	public static List<SongInfo> searchBaiduSong(String keyword, int pageIndex) {
		LogUtil.info(TAG, String.format("Java: getBaiduSongId(): keyword %s", keyword));

		String encoded_str;
		try {
			encoded_str = URLEncoder.encode(keyword, "UTF-8");
			String path = BAIDU_SONG_SEARCH_URL_FMT + encoded_str;
            path += "&ie=utf-8&page=";
            path += pageIndex;

            LogUtil.info(TAG, "Java: getBaiduSongId() url: " + path);

			org.jsoup.nodes.Document doc = Jsoup.connect(path).timeout(5000).get();
			org.jsoup.nodes.Element test = doc.select("tbody").first();
			if (test == null) {
                LogUtil.info(TAG, "Java: failed to find tbody of song list");
                return null;
            }

            List<SongInfo> songList = new ArrayList<SongInfo>();
            int size = test.childNodeSize();
			for (int i = 2; i < size / 2; i++) {
				org.jsoup.nodes.Element song = test.child(i);
                String song_id = song.child(0).child(0).child(0).attr("id");
				String song_name = song.child(2).attr("key");
				String artist = song.child(3).attr("key");
				String album = song.child(4).child(0).attr("title");
				LogUtil.info(TAG, String.format(
                        "song name: %s, artist %s, album %s",
                        song_name, artist, album));
                songList.add(new SongInfo(song_id, song_name, artist, album));
			}

            return songList;
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
            e.printStackTrace();
        }

        return null;
	}
	
	public static String getArtist(int artist_id) {
		try {
			String url = API_GECIME_LYRIC_ATRIST + String.valueOf(artist_id);
			Log.i(TAG, "Java: getArtist() " + url);
			
			String result = Util.getHttpPage(url);
			Log.d(TAG, "Java: result: " + result);
			
			JSONTokener jsonParser = new JSONTokener(result);
			JSONObject root = (JSONObject) jsonParser.nextValue();
			int count = root.getInt("count");
			int code = root.getInt("code");
			if (code != 0) {
				Log.e(TAG, "Java: code is wrong " + code);
				return null;
			}
			
			JSONObject artist = root.getJSONObject("result");
			return artist.getString("name");
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}

    private static String getHtmlString(String urlString) {
        try {
            URL url = new URL(urlString);
            URLConnection conn = url.openConnection();
            conn.connect();
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            StringBuffer sb = new StringBuffer();
            while((line = in.readLine()) != null){
                sb.append(line);
            }

            String html = sb.toString();
            return html;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isNumeric(String str){
        Pattern pattern = Pattern.compile("[0-9]*");
        Matcher isNum = pattern.matcher(str);
        if( !isNum.matches() ){
            return false;
        }
        return true;
    }
}
