#ifndef _PP_LOG_H_
#define _PP_LOG_H_

#ifndef LOG_TAG
#error "LOG_TAG not defined"
#endif

#include <android/log.h>

#ifdef NO_DEBUG
#define LOG_NDEBUG 1
#else
#define LOG_NDEBUG 0
#endif

#if NO_DEBUG
#define PPLOGV(...) ((void)0)
#define PPLOGD(...) ((void)0)
#else
#define PPLOGV(...) __pp_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define PPLOGD(...) __pp_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#endif

#define PPLOGI(...) __pp_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define PPLOGW(...) __pp_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#define PPLOGE(...) __pp_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define AND_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef __cplusplus
extern "C" {
#endif

int pplog_init();

int __pp_log_print(int prio, const char *tag, const char *fmt, ...);

int __pp_log_vprint(int prio, const char *tag, const char *fmt, va_list ap);

void pplog_close();

#ifdef __cplusplus
}
#endif

#endif // _PP_LOG_H_
