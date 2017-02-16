#include <jni.h>
#include <stddef.h>
#define LOG_TAG "andjniload"
#include "pplog.h"

JavaVM *g_JavaVM = NULL;

JNIEXPORT jint 
JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
	g_JavaVM = vm;
	JNIEnv *env = NULL;

	pplog_init();

	int status = g_JavaVM->GetEnv((void **) &env, JNI_VERSION_1_4);
	if (status < 0) {
		PPLOGE("get env failure");
		return -1;
	}

	PPLOGI("JNI_OnLoad()");
	return JNI_VERSION_1_4;
}

JNIEXPORT void 
JNICALL
JNI_OnUnload(JavaVM* vm, void* reserved)
{
	PPLOGI("JNI_OnUnload");
	pplog_close();
}