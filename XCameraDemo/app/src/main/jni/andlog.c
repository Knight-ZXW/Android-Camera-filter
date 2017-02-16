#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>//for var list

#include "anddefs.h"
#ifdef __ANDROID__
#include <jni.h>
#include <android/log.h>
#endif

#include "andsysutil.h"
#include "andstr.h"
#include "andlog.h"

#define LOG_TAG "AndCodec"

#ifdef __ANDROID__
#ifdef NO_DEBUG
#define LOGV(...) do {} while (0)
#define LOGD(...) do {} while (0)
#else
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG , LOG_TAG, __VA_ARGS__)
#endif
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO  , LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN  , LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR  , LOG_TAG, __VA_ARGS__)
#else
#define LOGV(...) do {} while (0)
#define LOGD(...) do {printf(__VA_ARGS__);printf("\n");} while (0)
#define LOGI(...) do {printf(__VA_ARGS__);printf("\n");} while (0)
#define LOGW(...) do {printf(__VA_ARGS__);printf("\n");} while (0)
#define LOGE(...) do {printf(__VA_ARGS__);printf("\n");} while (0)
#endif

int				g_log_fd 		= -1;
enum loglevel 	g_loglevel		= LOG_VERBOSE;
const filesize_t MAX_LOG_SIZE	= 1048576;//1MB

int and_log_init(const char* log_filename, enum loglevel level)
{
	g_log_fd = and_sysutil_create_or_open_file(log_filename, 0644);
	if (g_log_fd < 0) {
		LOGW("failed to open log file %s", log_filename);
		return -1;
	}
	
	g_loglevel = level;
	and_sysutil_lseek_end(g_log_fd);
	and_log_writeline_simple(0, LOG_INFO, "-----------------------------------------");
	  
	return 0;
}

void and_log_writeline_easy(int id, enum loglevel level, const char* szFmt, ...)
{
	va_list args;
	char szBuf[STRING_BUF_LEN] = {0};
	va_start(args, szFmt);
	vsprintf(szBuf, szFmt, args);
	va_end(args);
	and_log_writeline_simple(id, level, szBuf);
}

void and_log_writeline_simple(int id, enum loglevel level, const char* p_msg)
{
	struct mystr str = INIT_MYSTR;
	
	str_alloc_text(&str, p_msg);
	and_log_writeline(id, level, &str);
	str_free(&str);
}

	
void and_log_writeline(int id, enum loglevel level, struct mystr *p_str)
{
	char *pstr_level;
	switch (level)
	{
	case LOG_VERBOSE:
		pstr_level = "[verbose]";
		LOGV("%s", str_getbuf(p_str));
		break;
	case LOG_DEBUG:
		pstr_level = "[debug]";
		LOGD("%s", str_getbuf(p_str));
		break;
	case LOG_INFO:
		pstr_level = "[info]";
		LOGI("%s", str_getbuf(p_str));
		break;
	case LOG_WARN:
		pstr_level = "[warn]";
		LOGW("%s", str_getbuf(p_str));
		break;
	case LOG_ERROR:
		pstr_level = "[error]";
		LOGE("%s", str_getbuf(p_str));
		break;
	default:
		pstr_level = "[unknown]";
		break;
	}
	
	if (level < g_loglevel)//no log
		return;
	
	if (g_log_fd < 0)//log file not opened
		return;
	
	struct mystr str = INIT_MYSTR;
	int ret;
	
	str_alloc_text(&str, and_sysutil_get_current_date());
	long curr_msec;
	and_sysutil_get_time_sec();
	curr_msec = (and_sysutil_get_time_usec() / 1000) % 1000;//0-999
	str_append_char(&str,'[');
	char str_msec[4];//000-999
	sprintf(str_msec,"%.3ld",curr_msec);
	str_append_text(&str,str_msec);
	str_append_text(&str,"]");
	str_append_text(&str, "[id ");
    str_append_ulong(&str, id);
    str_append_text(&str, "] ");
	str_append_text(&str, pstr_level);//level
	str_append_str(&str, p_str);
	str_append_char(&str,'\n');
	
	ret = and_sysutil_write_loop(g_log_fd, str_getbuf(&str), str_getlen(&str));
	if (ret < 0)
		LOGW("failed to write log line");
	else if(ret < (int)str_getlen(&str))
		LOGW("write log line not finished");
	
	filesize_t cur_file_offset = and_sysutil_get_file_offset(g_log_fd);
	if (cur_file_offset >= MAX_LOG_SIZE)
		and_sysutil_lseek_to(g_log_fd, 0);

	str_free(&str);
}	

void and_log_close()
{
	if (g_log_fd > 0) {
		and_sysutil_close(g_log_fd);
		g_log_fd = -1;
	}
}

