/*
 * Copyright (C) 2012 Roger Shen  rogershen@pptv.com
 *
 */

#ifndef FF_UTILS_H
#define FF_UTILS_H

#include <stdint.h>
#include <stdlib.h>

#if __cplusplus
extern "C" {
#endif

int64_t getNowSec();    
int64_t getNowMs();
int64_t getNowUs();

#define SAFE_DELETE(p)       { if(p) { delete (p);     (p)=NULL; } }
#define SAFE_FREE(p)       { if(p) { free (p);     (p)=NULL; } }

typedef struct DictEntry_t DictEntry;
struct DictEntry_t {
    char *key;
    char *value;
	DictEntry *next;
};

#ifdef OS_IOS
int32_t getStrTime(char* buff);
#endif
    
#if __cplusplus
}
#endif

#endif
