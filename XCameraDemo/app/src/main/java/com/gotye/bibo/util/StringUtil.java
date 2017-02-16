package com.gotye.bibo.util;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class StringUtil {
	
	static String ENCODING = "utf-8";
	/**
	 * byte转字符串
	 * @param text
	 * @return
	 */
	public static String getString(byte[] text){
		if(text == null){
			return null;
		}
		try {
			return new String(text, ENCODING);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return "";
	}
	
	public static String getMD5Str(String data){
		return StringUtil.getString(getMD5Str(StringUtil.getBytes(data)));
	}
	
	/**  
     * MD5 加密  
     */   
    public static byte[] getMD5Str(byte[] data) {   
        MessageDigest messageDigest = null;
   
        try {   
            messageDigest = MessageDigest.getInstance("MD5");
   
            messageDigest.reset();   
   
            messageDigest.update(data);   
        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgorithmException caught!");
            System.exit(-1);
        }   
   
        byte[] byteArray = messageDigest.digest();   
   
        StringBuffer md5StrBuff = new StringBuffer();
   
        for (int i = 0; i < byteArray.length; i++) {               
            if (Integer.toHexString(0xFF & byteArray[i]).length() == 1)
                md5StrBuff.append("0").append(Integer.toHexString(0xFF & byteArray[i]));
            else   
                md5StrBuff.append(Integer.toHexString(0xFF & byteArray[i]));
        }   
   
        try {
			return md5StrBuff.toString().getBytes("utf-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}   
        return null;
    }   

	
	/**
	 * byte转字符串
	 * @param text
	 * @return
	 */
	public static String getString(byte[] text, int offset, int len){
		if(text == null){
			return null;
		}
		try {
			return new String(text, offset, len, ENCODING);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return "";
	}
	
	/**
	 * 字符串转byte
	 * @param str
	 * @return
	 */
	public static byte[] getBytes(String str){
		if(str == null){
			str = "";
		}
		try {
			return str.getBytes(ENCODING);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * 避免字符串为null
	 * @param str
	 * @return
	 */
	public static String escapeNull(String str){
		return str == null ? "" : str;
	}
	
	/**
	 * 避免字符串为null
	 * @param str
	 * @return
	 */
	public static CharSequence escapeNull(CharSequence str){
		return str == null ? "" : str;
	}
	
	public static String[] splitString(String str, String by) {
		return str.split(by);
	}

//	/**
//	 * 检查电话格式
//	 * 
//	 * @param phone
//	 * @return
//	 */
//	public static boolean checkPhoneNum(CharSequence phone) {
//		if (TextUtils.isEmpty(phone)) {
//			return false;
//		}
//		if (phone.length() != 11) {
//			return false;
//		}
//		return true;
//	}
//
//	/**
//	 * 检查验证码格式
//	 * 
//	 * @param code
//	 * @return
//	 */
//	public static boolean checkRegNum(CharSequence code) {
//		if (TextUtils.isEmpty(code) || code.length() != 4) {
//			return false;
//		}
//		return true;
//	}
	
	public static boolean isEmpty(String str){
		return str == null || str.trim().length() == 0;
	}
}
