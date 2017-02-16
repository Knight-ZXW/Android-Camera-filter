#include "andsysutil.h"
#include "andfifobuffer.h"  
 
static void reset_impl(FifoBuffer *p_fifo);
 
char* and_fifo_header(FifoBuffer *p_fifo)
{
	pthread_mutex_t *p_mutex = &(p_fifo->mutex);
	pthread_mutex_lock(p_mutex);
	char* p = p_fifo->header;
	pthread_mutex_unlock(p_mutex);
	return p;
}

int and_fifo_create(FifoBuffer *p_fifo, unsigned int size)
{
	if(!p_fifo)
		return -1;
	
	char *p = (char *)and_sysutil_malloc(size);
	if(!p) 
		return -1;
	
	int ret;
	ret = pthread_mutex_init(&(p_fifo->mutex), 0);
	if (ret < 0) {
		and_sysutil_free(p);
		return -1;
	}
		
	pthread_mutex_t *p_mutex = &(p_fifo->mutex);

	pthread_mutex_lock(p_mutex);
	p_fifo->buf = p;
	p_fifo->size= size;
	reset_impl(p_fifo);
	pthread_mutex_unlock(p_mutex);
	return 0;
}

void and_fifo_reset(FifoBuffer *p_fifo)
{
	pthread_mutex_t *p_mutex = &(p_fifo->mutex);
	pthread_mutex_lock(p_mutex);
	reset_impl(p_fifo);
	pthread_mutex_unlock(p_mutex);
}

void and_fifo_close(FifoBuffer *p_fifo)
{
	pthread_mutex_t *p_mutex = &(p_fifo->mutex);
	pthread_mutex_lock(p_mutex);

	if (p_fifo->buf)
		and_sysutil_free(p_fifo->buf);

	reset_impl(p_fifo);
	pthread_mutex_unlock(p_mutex);
	
	pthread_mutex_destroy(&(p_fifo->mutex));
}

int and_fifo_write(FifoBuffer *p_fifo, char* p, unsigned int howmuch)
{
	if(!p_fifo)
		return -1;
		
	pthread_mutex_t *p_mutex = &(p_fifo->mutex);
	pthread_mutex_lock(p_mutex);

	unsigned int nWrite = p_fifo->size - p_fifo->used;
	if (nWrite > howmuch)
		nWrite = howmuch;

	if (0 == nWrite)
	{
		pthread_mutex_unlock(p_mutex);
		return 0;
	}

	char *buf, *tail;
	unsigned int size;
	
	buf = p_fifo->buf;
	tail = p_fifo->tail;
	size = p_fifo->size;
	
	// Copy the data
	if ( tail + nWrite >= buf + size)
	{		
		unsigned int t = size - (unsigned int)(tail - buf);
		and_sysutil_memcpy(tail, p, t);
		and_sysutil_memcpy(buf, p + t, nWrite - t);
		//move m_tail ptr pos
		p_fifo->tail = buf + nWrite - t;
	}
	else
	{
		and_sysutil_memcpy(tail, p, nWrite);
		p_fifo->tail += nWrite;

		if ( p_fifo->tail == buf + size )
			p_fifo->tail = buf;
	}

	p_fifo->used		+= nWrite;
	p_fifo->llTotal		+= nWrite;
	pthread_mutex_unlock(p_mutex);
	return nWrite;
}

int and_fifo_read(FifoBuffer *p_fifo, char* p, unsigned int howmuch)
{
	if(!p_fifo)
		return -1;
		
	pthread_mutex_t *p_mutex = &(p_fifo->mutex);
	pthread_mutex_lock(p_mutex);

	unsigned int nRead = p_fifo->used;
	if (howmuch <= nRead)
		nRead = howmuch;

	if (nRead==0)
	{
		pthread_mutex_unlock(p_mutex);
		return 0;
	}

	//read data exceed the end of the buffer
	//return to the header,continue reading
	char *buf, *header;
	unsigned int size;
	
	buf = p_fifo->buf;
	header = p_fifo->header;
	size = p_fifo->size;
	
	if ( header + nRead > buf + size )
	{
		//t = the length from header to the tail
		unsigned int t = (unsigned int)(buf + size - header);
		and_sysutil_memcpy(p, header, t);
		and_sysutil_memcpy(p+t, buf, nRead-t);
		p_fifo->header = buf + nRead - t;
	}
	else
	{
		and_sysutil_memcpy(p, header, nRead);
		p_fifo->header += nRead;
		if ( p_fifo->header == buf + size)
		{
			p_fifo->header = buf;
		}
	}

	p_fifo->used  	-= nRead;
	p_fifo->llPos	+= nRead;
	pthread_mutex_unlock(p_mutex);
	return nRead;
}


int and_fifo_skip(FifoBuffer *p_fifo, unsigned int howmuch)
{
	if(!p_fifo)
		return -1;
		
	pthread_mutex_t *p_mutex = &(p_fifo->mutex);
	pthread_mutex_lock(p_mutex);
	
	
	unsigned nRead = p_fifo->used;
	if( howmuch<=nRead )
		nRead = howmuch;

	if( nRead==0 )
	{
		pthread_mutex_unlock(p_mutex);
		return 0;
	}

	//if the data which need to omit exceed the end of the buffer
	//set the header back to the front.
	char *buf, *header;
	unsigned int size;
	
	buf = p_fifo->buf;
	header = p_fifo->header;
	size = p_fifo->size;
	if ( header+nRead > buf + size )
	{
		unsigned int t = (unsigned int)(buf + size - header);
		p_fifo->header = buf + nRead - t;
	}
	else
	{
		p_fifo->header += nRead;
		if ( p_fifo->header == buf + size)
		{
			p_fifo->header = buf;
		}
	}

	p_fifo->used  -= nRead;
	pthread_mutex_unlock(p_mutex);
	return nRead;
}

void and_fifo_end(FifoBuffer *p_fifo)
{
	pthread_mutex_t *p_mutex = &(p_fifo->mutex);
	pthread_mutex_lock(p_mutex);
	p_fifo->eof = 1;
	pthread_mutex_unlock(p_mutex);
}

int and_fifo_is_eof(FifoBuffer *p_fifo)
{
	int ret = 0;
	pthread_mutex_t *p_mutex = &(p_fifo->mutex);
	pthread_mutex_lock(p_mutex);
	if(p_fifo->eof && and_fifo_used(p_fifo) == 0)
		ret = 1;
	pthread_mutex_unlock(p_mutex);
	
	return ret;
}

int and_fifo_is_empty(FifoBuffer *p_fifo)
{
	int ret = 0;
	pthread_mutex_t *p_mutex = &(p_fifo->mutex);
	pthread_mutex_lock(p_mutex);
	if(p_fifo->buf == 0)
		ret = 1;
	pthread_mutex_unlock(p_mutex);
	return ret;
}

int and_fifo_size(FifoBuffer *p_fifo)
{
	unsigned int size;
	pthread_mutex_t *p_mutex = &(p_fifo->mutex);
	pthread_mutex_lock(p_mutex);
	size = p_fifo->size;
	pthread_mutex_unlock(p_mutex);
	return size;
}

int and_fifo_used(FifoBuffer *p_fifo)
{
	unsigned int used;
	pthread_mutex_t *p_mutex = &(p_fifo->mutex);
	pthread_mutex_lock(p_mutex);
	used = p_fifo->used;
	pthread_mutex_unlock(p_mutex);
	return used;
}

static void reset_impl(FifoBuffer *p_fifo)
{
	p_fifo->header	= p_fifo->buf;
	p_fifo->tail	= p_fifo->buf;
	p_fifo->used	= 0;
	p_fifo->eof		= 0;
}

