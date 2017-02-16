package com.gotye.bibo.util;

import android.content.Context;

public class DensityUtil {
	/** * 根据手机的分辨率从 dp 的单位 转成为 px(像素) */
	public static int dip2px(Context context, float dpValue) {
		final float scale = context.getResources().getDisplayMetrics().density;
		return (int) (dpValue * scale + 0.5f);
	}

	/** * 根据手机的分辨率从 px(像素) 的单位 转成为 dp */
	public static int px2dip(Context context, float pxValue) {
		final float scale = context.getResources().getDisplayMetrics().density;
		return (int) (pxValue / scale + 0.5f);
	}
	
	/** 
	 * sp转成px 
	 * @param spValue 
	 * @param context
	 * @return 
	 */  
	public static int sp2px(Context context, float spValue) {
		final float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
		return (int) (spValue * scaledDensity);
	}
	
	public static int px2sp(Context context, float pxValue) {
		final float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
		return (int) (pxValue / scaledDensity);
	}
}