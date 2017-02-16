package com.gotye.bibo.util;

import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;


public class LogUtil {
	
	private static final String TAG = "LogUtil";
	
    private static final SimpleDateFormat SDF =
    		new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS");
    
    private static String outputfile;

    private static String logpath;

    private static String infopath;

    private static int fileLimit = 100 * 1024; // 100k

    private static long offset = 0;

    private static BufferedRandomAccessFile braf = null;
    
    private static boolean inited;
    
    public static boolean init(String logfile, String tempPath) {
    	Log.i(TAG, String.format("Java: init() logfile %s, tempPath %s", logfile, tempPath));
    	
        outputfile = logfile;
        infopath = tempPath + "/deviceinfo";
        logpath = tempPath + "/bibo.log";
        boolean hasLogPath = makeParentPath(outputfile);
        boolean hasTempPath = makePath(tempPath);
        inited = hasLogPath && hasTempPath;
        
        return inited;
    }
	
	public static void verbose(String TAG, String msg) {
        log(Log.VERBOSE, TAG, msg);
    }

    public static void debug(String TAG, String msg) {
        log(Log.DEBUG, TAG, msg);
    }

    public static void info(String TAG, String msg) {
        log(Log.INFO, TAG, msg);
    }

    public static void warn(String TAG, String msg) {
        log(Log.WARN, TAG, msg);
    }

    public static void error(String TAG, String msg) {
        log(Log.ERROR, TAG, msg);
    }
    
	private static void log(int level, String tag, String msg) {
        if (level >= Log.INFO) {
            writeFile(String.format("%s [%s] %s: %s",
            		SDF.format(new Date()), getLevelString(level), tag, msg));
        }

        if (level == Log.ERROR)
			Log.e(tag, msg);
		else if(level == Log.WARN)
			Log.w(tag, msg);
		else if(level == Log.INFO)
			Log.i(tag, msg);
		else if(level == Log.DEBUG)
			Log.d(tag, msg);
    }

    public static void nativeLog(int level, String tag, String msg) {
        log(level, tag, msg);
    }
	
	public static void makeUploadLog() {
        if (!inited) {
        	Log.w(TAG, "log is not inited");
            return;
        }

        if (braf == null) {
            Log.w(TAG, "log is empty");
            return;
        }
        
        try {
        	Log.i(TAG, "Java: begin to write log file: " + outputfile);
            BufferedWriter bw = new BufferedWriter(
            		new OutputStreamWriter(new FileOutputStream(outputfile)));
            String line = "";
            File logfile = new File(logpath);
            if (logfile.exists()) {
                bw.write("-----------------\n");
                BufferedReader br = new BufferedReader(
                		new InputStreamReader(new FileInputStream(logfile)));
                while ((line = br.readLine()) != null) {
                    bw.write(line);
                    bw.write('\n');
                }
                br.close();
            }
            bw.flush();
            bw.close();
            
            braf.flush();
            braf.close();
            braf = null;
            logfile.delete();
            Log.i(TAG, "Java: end write log file");
        } catch (IOException e) {
        	e.printStackTrace();
        }
    }
	
	public static synchronized void writeFile(String msg) {
        if (!inited) {
            return;
        }

        try {
            if (braf == null) {
            	// open/create log file
                braf = new BufferedRandomAccessFile(logpath, "rw");
                try {
                    offset = braf.length();
                    if (offset >= fileLimit) {
                        String firstline = braf.readLine();
                        int index = firstline.indexOf('#');
                        offset = (index == -1) ? 0 : Integer.parseInt(firstline.substring(0, index));
                    }
                } catch (Exception e) {
                	e.printStackTrace();
                    offset = 0;
                }
            }
            
            msg += '\n';
            try {
                braf.seek(offset);
                braf.write(msg.getBytes());
                offset = (offset + msg.length()) >= fileLimit ? 0 : offset + msg.length();
                braf.seek(0);
                braf.write((offset + "#").getBytes());
                braf.flush();
            } catch (IOException e) {
            	e.printStackTrace();
            }
        } catch (Exception e) {
        	e.printStackTrace();
        }
    }
	
    private static String getLevelString(int level) {

        switch (level) {
        case Log.VERBOSE:
            return "V";
        case Log.DEBUG:
            return "D";
        case Log.INFO:
            return "I";
        case Log.WARN:
            return "W";
        case Log.ERROR:
            return "E";
        }

        return "U";
    }
    
    static private boolean makePath(String path) {
        if (TextUtils.isEmpty(path)) {
            return false;
        }
        File dir = new File(path);
        if (!dir.exists()) {
            return dir.mkdirs();
        } else
            return true;
    }

    static private boolean makeParentPath(String filename) {
        if (TextUtils.isEmpty(filename)) {
            return false;
        }
        File file = new File(filename);
        return makePath(file.getParentFile().getAbsolutePath());
    }
}
