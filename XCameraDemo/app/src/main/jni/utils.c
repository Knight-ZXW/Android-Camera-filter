#ifdef _MSC_VER
#include <time.h>
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
struct timeval {
    time_t tv_sec;
    long tv_usec;
};
int gettimeofday(struct timeval *tp, void *tzp);
#else
#include <sys/time.h>
#endif
#include <stdio.h>
#include <string.h>
#include "utils.h"

#ifdef _MSC_VER
int gettimeofday(struct timeval *tp, void *tzp)
{
    time_t clock;
    struct tm tm;
    SYSTEMTIME wtm;
    GetLocalTime(&wtm);
    tm.tm_year     = wtm.wYear - 1900;
    tm.tm_mon     = wtm.wMonth - 1;
    tm.tm_mday     = wtm.wDay;
    tm.tm_hour     = wtm.wHour;
    tm.tm_min     = wtm.wMinute;
    tm.tm_sec     = wtm.wSecond;
    tm.tm_isdst    = -1;
    clock = mktime(&tm);
    tp->tv_sec = clock;
    tp->tv_usec = wtm.wMilliseconds * 1000;
    return (0);
}
#endif

int64_t getNowSec()
{
    struct timeval tv;
    gettimeofday(&tv, NULL);

    return (int)tv.tv_sec;
}

int64_t getNowMs()
{
    struct timeval tv;
    gettimeofday(&tv, NULL);

    return (int64_t)tv.tv_usec /1000ll + tv.tv_sec * 1000ll;
}

int64_t getNowUs()
{
	/*LARGE_INTEGER li;
    LONGLONG now, freq;
    QueryPerformanceFrequency(&li);
	freq = li.QuadPart;
	QueryPerformanceCounter(&li);
	now = li.QuadPart;
	return now * 1000000 / freq;*/
	
    struct timeval tv;
    gettimeofday(&tv, NULL);

    return (int64_t)tv.tv_sec * 1000000ll + tv.tv_usec;
}

#ifdef __APPLE__
int32_t getStrTime(char* buff)
{
    if(buff == NULL) return 0;
    
    struct timeval tv;
    gettimeofday(&tv, NULL);
    
    time_t nowtime = time(NULL);
    tm *now = localtime(&nowtime);
    sprintf(buff, "%04d-%02d-%02d %02d:%02d:%02d %03d ",
             now->tm_year+1900, now->tm_mon+1, now->tm_mday,
             now->tm_hour, now->tm_min, now->tm_sec, (int)(tv.tv_usec/1000));
    
    return strlen(buff);
}
#endif
