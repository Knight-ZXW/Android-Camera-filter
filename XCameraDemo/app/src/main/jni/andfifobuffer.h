#ifndef AND_FIFOBUFFER_H
#define AND_FIFOBUFFER_H

#include "andfilesize.h"
#include <pthread.h>

typedef struct FifoBuffer{
    char*				buf;
	char*				header;
	char*				tail;
	unsigned int		size;
	unsigned int		used;
	filesize_t			llPos;					// total read start pos
	filesize_t			llTotal;				// total write size
	int					eof;
	pthread_mutex_t		mutex;					// protect fifo
}FifoBuffer;

int and_fifo_create(FifoBuffer *p_fifo, unsigned int size);

void and_fifo_reset(FifoBuffer *p_fifo);
void and_fifo_close(FifoBuffer *p_fifo);
void and_fifo_end(FifoBuffer *p_fifo);
	
int and_fifo_is_empty(FifoBuffer *p_fifo);
int and_fifo_size(FifoBuffer *p_fifo);
int and_fifo_used(FifoBuffer *p_fifo);
filesize_t and_fifo_total_write();
filesize_t and_fifo_total_read();
int and_fifo_is_eof(FifoBuffer *p_fifo);
char*	and_fifo_header(FifoBuffer *p_fifo);
	
int and_fifo_write(FifoBuffer *p_fifo, char* p, unsigned int howmuch);
int and_fifo_read(FifoBuffer *p_fifo, char* p, unsigned int howmuch);
int and_fifo_skip(FifoBuffer *p_fifo, unsigned int howmuch);
	
#endif // AND_FIFOBUFFER_H
