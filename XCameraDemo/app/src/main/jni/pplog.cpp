#define LOG_TAG "pplog"
#include "pplog.h"
#include <jni.h>
#include <stdio.h>
#include <string.h>

#define LOG_BUF_SIZE	4096

extern JavaVM* g_JavaVM;

jclass gs_clazz;
jmethodID gs_mid_log;
static int gs_inited = 0;

int java_log(int level, const char* tag, const char* msg);

static bool IsUTF8(const void* pBuffer, long size);

int pplog_init()
{
	if (gs_inited)
		return 0;

	if (NULL == g_JavaVM)
		return -1;

	JNIEnv* env = NULL;

	if (g_JavaVM->GetEnv((void**)&env, JNI_VERSION_1_4) != JNI_OK)
		return -1;

	jclass clazz = env->FindClass("com/gotye/bibo/util/LogUtil");
	if (NULL == clazz) {
		jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
		if (exceptionClass != NULL)
			env->ThrowNew(exceptionClass, "failed to find class com/gotye/bibo/util/LogUtil");
		return -1;
	}

	gs_mid_log = env->GetStaticMethodID(clazz, "nativeLog", "(ILjava/lang/String;Ljava/lang/String;)V");
	if (NULL == gs_mid_log) {
		jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
		if (exceptionClass != NULL)
			env->ThrowNew(exceptionClass, "failed to find method nativeLog");
		return -1;
	}

	gs_clazz = (jclass)env->NewGlobalRef(clazz);

	gs_inited = true;
	PPLOGI("pplog inited");
	return 0;
}

void pplog_close()
{
	JNIEnv* env = NULL;

	if (g_JavaVM->GetEnv((void**)&env, JNI_VERSION_1_4) == JNI_OK) {
		env->DeleteGlobalRef(gs_clazz);
	}
}

int __pp_log_print(int prio, const char *tag, const char *fmt, ...)
{
    if (!gs_inited)
		return -1;

	va_list ap;
	char buf[LOG_BUF_SIZE] = {0};

	va_start(ap, fmt);
	vsnprintf(buf, LOG_BUF_SIZE, fmt, ap);
	va_end(ap);

	return java_log(prio, tag, buf);
}

int __pp_log_vprint(int prio, const char *tag, const char *fmt, va_list ap)
{
	if (!gs_inited)
		return -1;

    char buf[LOG_BUF_SIZE];
	vsnprintf(buf, LOG_BUF_SIZE, fmt, ap);
	return java_log(prio, tag, buf);
}

int java_log(int level, const char* tag, const char* msg)
{
	if (!gs_inited)
		return -1;

	JNIEnv* env = NULL;

	if (NULL != g_JavaVM)
		g_JavaVM->GetEnv((void**)&env, JNI_VERSION_1_4);

	if (!env)
		return -1;

	if (!IsUTF8(msg, strlen(msg))) {
		AND_LOGE("string is not utf-8(java_log): %s", msg);
		return -1;
	}

	jstring jtag = env->NewStringUTF(tag);
	jstring jmsg = env->NewStringUTF(msg);

	env->CallStaticVoidMethod(gs_clazz, gs_mid_log, level, jtag, jmsg);
	env->DeleteLocalRef(jtag);
	env->DeleteLocalRef(jmsg);
	return 0;
}

static bool IsUTF8(const void* pBuffer, long size)  
{
	bool IsUTF8 = true;  
    unsigned char* start = (unsigned char*)pBuffer;  
    unsigned char* end = (unsigned char*)pBuffer + size;  
    while (start < end) {  
        if (*start < 0x80) // (10000000): 值小于0×80的为ASCII字符  
        {  
            start++;  
        }  
        else if (*start < (0xC0)) // (11000000): 值介于0×80与0xC0之间的为无效UTF-8字符  
        {  
            IsUTF8 = false;  
            break;  
        }  
        else if (*start < (0xE0)) // (11100000): 此范围内为2字节UTF-8字符  
        {  
            if (start >= end - 1)   
                break;  
            if ((start[1] & (0xC0)) != 0x80)  
            {  
                IsUTF8 = false;  
                break;  
            }  
            start += 2;  
        }   
        else if (*start < (0xF0)) // (11110000): 此范围内为3字节UTF-8字符  
        {  
            if (start >= end - 2)   
                break;  
            if ((start[1] & (0xC0)) != 0x80 || (start[2] & (0xC0)) != 0x80)  
            {  
                IsUTF8 = false;  
                break;  
            }  
            start += 3;  
        }   
        else  
        {  
            IsUTF8 = false;  
            break;  
        }  
    }  

    return IsUTF8;  
}
