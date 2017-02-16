package com.gotye.bibo.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;


public class Util {
    private static Toast mToast;

	public static final int PREVIEW_PIX_FORMAT_NOFORMAT	= -1;
	public static final int PREVIEW_PIX_FORMAT_NV21		= 0;
	public static final int PREVIEW_PIX_FORMAT_YV12		= 1;
	
	public static final int ENCODE_PIX_FORMAT_YUV420P	= 0;
	public static final int ENCODE_PIX_FORMAT_NV12		= 1;
	
	private final static int ONE_MAGABYTE = 1048576;
	private final static int ONE_KILOBYTE = 1024;

    private final static String TAG = "Util";
	private final static String PREF_NAME = "settings";

    public static final String player_log_path =
            Environment.getExternalStorageDirectory().getAbsolutePath() +
                    "/bibo/player.log";
	public static final String log_path =
			Environment.getExternalStorageDirectory().getAbsolutePath() +
					"/bibo/crash.log";
	public static final String upload_log_path =
			Environment.getExternalStorageDirectory().getAbsolutePath() +
					"/bibo/upload.log";
	
	// int to byte 
    public static byte[] IntToByte(int number) { 
        int temp = number; 
        byte[] b = new byte[4]; 
        for (int i = 0; i < b.length; i++) { 
            b[i] = new Long(temp & 0xff).byteValue();
            temp = temp >> 8; // right shift 8
        } 
        return b; 
    }
    
    // byte to int 
    public static int ByteToInt(byte[] b) { 
        int s = 0; 
        int s0 = b[0] & 0xff;// MSB
        int s1 = b[1] & 0xff; 
        int s2 = b[2] & 0xff; 
        int s3 = b[3] & 0xff; 
 
        // s0 unchange
        s1 <<= 8; 
        s2 <<= 16; 
        s3 <<= 24;  
        s = s0 | s1 | s2 | s3;
        return s;
    }

    public static String[] nsLookup(String hostname) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(hostname);
            if (addresses.length > 0) {
                String[]ips = new String[addresses.length];
                for (int i = 0; i < addresses.length; i++) {
                    LogUtil.info(TAG, hostname + "[" + i + "]: "
                            + addresses[i].getHostAddress());
                    ips[i] = addresses[i].getHostAddress();
                }

                return ips;
            }
            else {
                LogUtil.error(TAG, "none ip addr resolved");
            }
        } catch (UnknownHostException uhe) {
            LogUtil.warn(TAG, "Unable to find: " +hostname);
        }

        return null;
    }


    public static boolean IsHaveInternet(final Context context) {
        try {
            ConnectivityManager manger = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo info = manger.getActiveNetworkInfo();
            return (info != null && info.isConnected());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isWifiConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiNetworkInfo = connectivityManager
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiNetworkInfo != null)
            return wifiNetworkInfo.isConnected();

        return false;
    }


    public static String getFileSize(long size) {
	    String strFilesize;
		if (size > ONE_MAGABYTE)
			strFilesize = String.format("%.3f MB",
					(float) size / (float) ONE_MAGABYTE);
		else if (size > ONE_KILOBYTE)
			strFilesize = String.format("%.3f kB",
					(float) size / (float) ONE_KILOBYTE);
		else
			strFilesize = String.format("%d Byte", size);
		
		return strFilesize;
    }

	public static boolean writeSettingsString(Context ctx, String key, String value) {
		SharedPreferences settings = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE); // create it if NOT exist
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(key, value);
		return editor.commit();
	}

	public static String readSettingsString(Context ctx, String key) {
		SharedPreferences settings = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE); // create it if NOT exist
		return settings.getString(key, null);
	}

    public static boolean writeSettingsInt(Context ctx, String key, int value) {
		SharedPreferences settings = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE); // create it if NOT exist
    	SharedPreferences.Editor editor = settings.edit();
    	editor.putInt(key, value);
    	return editor.commit();
	}
	
	public static int readSettingsInt(Context ctx, String key) {
		return readSettingsInt(ctx, key, 0);
	}
	
	public static int readSettingsInt(Context ctx, String key, int default_value) {
		SharedPreferences settings = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE); // create it if NOT exist
    	return settings.getInt(key, default_value);
	}
	
    public static boolean writeSettingsBoolean(Context ctx, String key, boolean isOn) {
		SharedPreferences settings = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE); // create it if NOT exist
    	SharedPreferences.Editor editor = settings.edit();
    	editor.putBoolean(key, isOn);
    	return editor.commit();
	}
	
    public static Boolean readSettingsBoolean(Context ctx, String key) {
    	return readSettingsBoolean(ctx, key, false);
	}
    
	public static Boolean readSettingsBoolean(Context ctx, String key, boolean default_value) {
		SharedPreferences settings = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE); // create it if NOT exist
    	return settings.getBoolean(key, default_value);
	}


	/**
	 * 打印消息并且用Toast显示消息
	 *
	 * @param activity
	 * @param message
	 * @param logLevel
	 *            填d, w, e分别代表debug, warn, error; 默认是debug
	 */
	public static final void toastMessage(final Activity activity,
                                          final String message, String logLevel) {
		if ("w".equals(logLevel)) {
			LogUtil.warn(TAG, message);
		} else if ("e".equals(logLevel)) {
            LogUtil.error(TAG, message);
		} else {
            LogUtil.debug(TAG, message);
		}
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				if (mToast != null) {
					mToast.cancel();
					mToast = null;
				}
				mToast = Toast.makeText(activity, message, Toast.LENGTH_SHORT);
				mToast.show();
			}
		});
	}

	/**
	 * 打印消息并且用Toast显示消息
	 *
	 * @param activity
	 * @param message
	 */
	public static final void toastMessage(final Activity activity,
										  final String message) {
		toastMessage(activity, message, null);
	}

	/**
	 * 将String数据存为文件
	 */
	public static boolean writeBytestoFile(String strContext, String path) {
		byte[] b = strContext.getBytes();
		BufferedOutputStream stream = null;
		try {
            File file = new File(path);
			FileOutputStream fstream = new FileOutputStream(file);
			stream = new BufferedOutputStream(fstream);
			stream.write(b);
            return true;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}

		return false;
	}

	public static String getHttpPage(String url) {
		InputStream is = null;
		ByteArrayOutputStream os = null;

		try {
			URL realUrl = new URL(url);
			// 打开和URL之间的连接
			HttpURLConnection conn = (HttpURLConnection)realUrl.openConnection();
            conn.setRequestMethod("GET");
			conn.setReadTimeout(5000);// 设置超时的时间
			conn.setConnectTimeout(5000);// 设置链接超时的时间

			conn.connect();

			if (conn.getResponseCode() != 200) {
				LogUtil.error(TAG, "http response is not 200 " + conn.getResponseCode());
				return null;
			}

			// 获取响应的输入流对象
			is = conn.getInputStream();

			// 创建字节输出流对象
			os = new ByteArrayOutputStream();
			// 定义读取的长度
			int len = 0;
			// 定义缓冲区
			byte buffer[] = new byte[1024];
			// 按照缓冲区的大小，循环读取
			while ((len = is.read(buffer)) != -1) {
				// 根据读取的长度写入到os对象中
				os.write(buffer, 0, len);
			}

			// 返回字符串
			return new String(os.toByteArray());
		} catch (IOException e) {
			e.printStackTrace();
            LogUtil.error(TAG, "IOException: " + e.getMessage());
		} finally {
			try {
				// 释放资源
				if (is != null) {
					is.close();
				}
				if (os != null) {
					os.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return null;
	}

    public static String postHttpPage(String url, String params) {
        InputStream is = null;
        ByteArrayOutputStream os = null;

        try {
            URL realUrl = new URL(url);
            // 打开和URL之间的连接
            HttpURLConnection conn = (HttpURLConnection)realUrl.openConnection();
            conn.setRequestMethod("POST");
            byte[] bypes = params.getBytes();
            conn.getOutputStream().write(bypes);// 输入参数
            conn.setReadTimeout(5000);// 设置超时的时间
            conn.setConnectTimeout(5000);// 设置链接超时的时间

            conn.connect();

            if (conn.getResponseCode() != 200) {
                LogUtil.error(TAG, "http response is not 200 " + conn.getResponseCode());
                return null;
            }

            // 获取响应的输入流对象
            is = conn.getInputStream();

            // 创建字节输出流对象
            os = new ByteArrayOutputStream();
            // 定义读取的长度
            int len = 0;
            // 定义缓冲区
            byte buffer[] = new byte[1024];
            // 按照缓冲区的大小，循环读取
            while ((len = is.read(buffer)) != -1) {
                // 根据读取的长度写入到os对象中
                os.write(buffer, 0, len);
            }

            // 返回字符串
            return new String(os.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                // 释放资源
                if (is != null) {
                    is.close();
                }
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public static String getFileContext(String path) {
        String strContext = null;
        byte[] strBuffer = null;
        int len = 0;
        File file = new File(path);
        try {
            InputStream in = new FileInputStream(file);
            len = (int)file.length();
            strBuffer = new byte[len];
            in.read(strBuffer, 0, len);
            strContext = new String(strBuffer);      //构建String时，可用byte[]类型，
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return strContext;
    }

    public static String getIMEI(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
        return tm.getDeviceId();
    }

    public static String getPhoneNumber(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
        String number = tm.getLine1Number();
        if (number == null)
            return "undefined";

        return number;
    }

    /**
     *
     * @param log
     * @param filename
     * @return 返回写入的文件路径
     * 写入Log信息的方法，写入到SD卡里面
     */
    private static boolean writeLog(String log, String filename) {
        File file = new File(filename);

        if (!file.getParentFile().exists())
            file.getParentFile().mkdirs();

        try  {
            file.createNewFile();
            FileWriter fw = new FileWriter(file, false);
            BufferedWriter bw = new BufferedWriter(fw);
            //写入相关Log到文件
            bw.write(log);
            bw.newLine();
            bw.close();
            fw.close();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "an error occured while writing file: " + e);
            e.printStackTrace();
        }

        return false;
    }

    private static String intToIp(int i) {
        return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "." + ((i >> 16) & 0xFF)
                + "." + (i >> 24 & 0xFF);
    }
}
