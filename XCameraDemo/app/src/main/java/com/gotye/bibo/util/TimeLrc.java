package com.gotye.bibo.util;

public class TimeLrc {  
    private String lrcString;  
    private long sleepTime;  
    private long timePoint; 
    
    TimeLrc() {  
    	this(null, 0, 0);
    }
    
    TimeLrc(String lrc, long time_point, long sleep) {  
        lrcString = lrc;  
        sleepTime = sleep;  
        timePoint = time_point;
    }
    
    TimeLrc(String lrc, long time_point) {  
    	this(lrc, time_point, 0);
    }  
    
    public void setLrcString(String lrc) {  
        lrcString = lrc;  
    }  
    
    public void setSleepTime(long time) {  
        sleepTime = time;  
    }  
    
    public void setTimePoint(long tPoint) {  
        timePoint = tPoint;  
    }  
    
    public String getLrcString() {  
        return lrcString;  
    }  
    
    public long getSleepTime() {  
        return sleepTime;  
    }  
    
    public long getTimePoint() {  
        return timePoint;  
    }  
};  
