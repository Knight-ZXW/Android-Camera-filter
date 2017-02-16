package com.gotye.bibo.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>歌词解析类</b>
 * 
 * @QQ QQ:951868171
 * @version 1.0
 * @email xi_yf_001@126.com
 * */
public class LrcParser2 {
	private String TAG = "LycParser2";
	
	private Hashtable<String, String> lrcTable = null;	//存放解析后的列表
	//private static String charSet = "gbk";		//编码
	
	// added
	private LrcInfo lrcinfo = new LrcInfo();
	private List<TimeLrc> lrcList = new ArrayList<TimeLrc>();
	private int offset = 0; // lrc time shift
	
	/**
	 * 解析Lrc
	 * */
	public LrcInfo readLrc(InputStream is, String charSet) {
		lrcTable = new Hashtable<String, String>();
		try {
			BufferedReader bis = new BufferedReader(new InputStreamReader(is, charSet));
			String str = null;
			while ((str = bis.readLine()) != null) {	//逐行解析
				decodeLine(str);		
			}
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			lrcTable = null;
		}
		
		Collections.sort(lrcList, new Comparator<TimeLrc>() {

			@Override
			public int compare(TimeLrc line1, TimeLrc line2) {
				// TODO Auto-generated method stub
				if (line1.getTimePoint() > line2.getTimePoint())
					return 1;
				else if (line1.getTimePoint() == line2.getTimePoint())
					return 0;
				else
					return -1;
			}
			
		});
		
		lrcinfo.setInfos(lrcList);
		return lrcinfo;
	}

	/**
	 * 单行解析
	 * */
	private void decodeLine(String str) {
		LogUtil.debug(TAG, "Java: parse line " + str);
		
		String regLrcLine = "\\[\\d{2}:\\d{2}";
		Pattern patternLrcLine = Pattern.compile(regLrcLine);

		if (str.startsWith("[ti:")) {// 歌曲名
			String title = str.substring(4, str.lastIndexOf("]"));
			lrcTable.put("ti", title);
			lrcinfo.setTitle(title);
		} else if (str.startsWith("[ar:")) {// 艺术家
			String artist = str.substring(4, str.lastIndexOf("]"));
			lrcTable.put("ar", artist);
			lrcinfo.setArtist(artist);
		} else if (str.startsWith("[al:")) {// 专辑
			String album = str.substring(4, str.lastIndexOf("]"));
			lrcTable.put("al", album);
			lrcinfo.setAlbum(album);
		} else if (str.startsWith("[by:")) {// 作词
			String by = str.substring(4, str.lastIndexOf("]"));
			lrcTable.put("by", by);
			lrcinfo.setBySomeBody(by);
		} else if (str.startsWith("[la:") || str.startsWith(("[lg:"))) {// 语言
			String lang = str.substring(4, str.lastIndexOf("]"));
			lrcTable.put("la", lang);
		} else if (str.startsWith("[offset:")) {// time shift msec
			int pos = str.indexOf("]", 8);
			if (pos > 8) {
				offset = Integer.valueOf(str.substring(8, pos));
				LogUtil.info(TAG, "Java: lrc set offset to " + offset);
			}
		} else if (str.startsWith("[t_time:")) { // fix 费玉清 一剪梅
			String duration = str.substring(8, str.lastIndexOf("]"));
			lrcTable.put("t_time", duration);
		} else if (str.startsWith("[url:")) { // fix 费玉清 一剪梅
			String url = str.substring(4, str.lastIndexOf("]"));
			lrcTable.put("url", url);
		} else if (patternLrcLine.matcher(str).find()){
			// 歌词正文
			LogUtil.debug(TAG, "Java: lrc " + str);
			
			// 设置正则规则
			String reg2 = null;
			if (str.contains("."))
				reg2 = "\\[(\\d{2}:\\d{2}\\.\\d{2})\\]"; // "\[(\d{2}:\d{2}\.\d{2})\]";
			else
				reg2 = "\\[(\\d{2}:\\d{2})\\]"; // "\[(\d{2}:\d{2})\]"

			// 编译
			Pattern pattern2 = Pattern.compile(reg2);
			Matcher matcher = pattern2.matcher(str);

			// 如果存在匹配项，则执行以下操作
			while (matcher.find()) {
				// 得到这个匹配项中的所有内容和组数
				int groupCount = matcher.groupCount();
				//LogUtil.info(TAG, String.format("Java: group %s, count %d", matcher.group(), groupCount));
				
				// 得到这个匹配项开始的索引
				int start = matcher.start();
				// 得到这个匹配项结束的索引
				int end = matcher.end();
				//LogUtil.info(TAG, String.format(Locale.US,
                //        "Java: start\'[\' %d, end\']\'  %d", start, end));
				
				long currentTime = 0;//存放临时时间
				String currentContent = "";//存放临时歌词
				// 得到每个组中内容
				for (int i = 0; i <= groupCount; i++) {
					String timeStr = matcher.group(i);
					//LogUtil.info(TAG, String.format("Java: group #%d %s", i, timeStr));
					
					if (i == 1) {
						// 将第二组中的内容设置为当前的一个时间点
						currentTime = strToLong(timeStr);
					}
				}

				// 得到时间点后的内容
				String[] content = pattern2.split(str); // [aaa], [bbb], [ccc], lrc_text
				if (content.length > 0) {
					currentContent = content[content.length - 1];
					LogUtil.debug(TAG, String.format(Locale.US,
                            "Java: content size %d, repeat %d, text: %s",
                            content.length, content.length - 1, currentContent));
				}
				else {
					currentContent = "";
				}
				
				// 设置时间点和内容的映射
				lrcList.add(new TimeLrc(currentContent, currentTime, 0));
				LogUtil.debug(TAG, String.format("Java: put currentTime %s, currentContent %s",
						currentTime, currentContent));
			}
			
			/*
			int startIndex = -1;
			
			while ((startIndex = str.indexOf("[", startIndex + 1)) != -1) {
				int endIndex = str.indexOf("]", startIndex + 1);
				// 添加时间格式m:ss
				String lrcLine = str.substring(str.lastIndexOf("]") + 1, str.length());
				if (lrcLine.isEmpty() && lastLine != null)
					lrcLine = lastLine;
				String startTime  = strToLongToTime(str.substring(startIndex + 1, endIndex));
				lrcTable.put(startTime, lrcLine);
				lrcList.add(new TimeLrc(lrcLine, strToLong(startTime) - offset, 0));
				Log.i(TAG, String.format("Java: lrc line %s, start at %s", lrcLine, startTime));
				
				if (!lrcLine.isEmpty())
					lastLine = lrcLine;
			}*/
		}
	}

	/**
	 * 获取解析成功的歌词
	 * */
	public Hashtable<String, String> getLrcTable() {
		return lrcTable;
	}

	/**
	 * 保证时间格式一致 为m:ss
	 * 
	 * @param str
	 *            时间字符
	 * @return 判断用的时间符
	 * */
	private String strToLongToTime(String str) {
		int m = Integer.parseInt(str.substring(0, str.indexOf(":")));
		int s = 0;
		int ms = 0;

		// 判断歌词时间是否有毫秒
		if (str.indexOf(".") != -1) {
			s = Integer.parseInt(str.substring(str.indexOf(":") + 1, str
					.indexOf(".")));
			ms = Integer.parseInt(str.substring(str.indexOf(".") + 1, str
					.length()));
		} else {
			s = Integer.parseInt(str.substring(str.indexOf(":") + 1, str
					.length()));
		}
		return timeMode(m * 60000 + s * 1000 + ms * 10);
	}
	
	private long strToLong(String str) {
		int m = Integer.parseInt(str.substring(0, str.indexOf(":")));
		int s = 0;
		int ms = 0;

		// 判断歌词时间是否有毫秒
		if (str.indexOf(".") != -1) {
			s = Integer.parseInt(str.substring(str.indexOf(":") + 1, str
					.indexOf(".")));
			ms = Integer.parseInt(str.substring(str.indexOf(".") + 1, str
					.length()));
		} else {
			s = Integer.parseInt(str.substring(str.indexOf(":") + 1, str
					.length()));
		}
		return m * 60000 + s * 1000 + ms * 10;
	}

	/**
	 * 返回时间
	 * 
	 * @param time
	 *            毫秒时间
	 * */
	public static String timeMode(int time) {
		int tmp = (time / 1000) % 60;
		if (tmp < 10)
			return time / 60000 + ":" + "0" + tmp;
		else
			return time / 60000 + ":" + tmp;
	}

}

