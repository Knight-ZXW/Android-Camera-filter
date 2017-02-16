/*
 * Copyright (C) 2012 Roger Shen  rogershen@pptv.com
 *
 */

#ifndef FF_AUTOLOCK_H
#define FF_AUTOLOCK_H

#include <pthread.h>

class AutoLock
{
public:
	AutoLock(pthread_mutex_t* lock) : mLock(lock)
	{
		pthread_mutex_lock(mLock);
	}
	~AutoLock()
	{
    	pthread_mutex_unlock(mLock);
	}
private:
    pthread_mutex_t* mLock;
};

#endif // FF_AUTOLOCK_H
