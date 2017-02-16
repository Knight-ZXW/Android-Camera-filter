#ifndef AND_QUEUE_H
#define AND_QUEUE_H

#include "andfilesize.h"

typedef struct SimpleQueue {
	void*		array;
	filesize_t	abs_r_pos;
	filesize_t	abs_w_pos;
	int			data_len;
	int			size;
}SimpleQueue;

// @param data_len:		stored data len(in byte)
// @param max_num:		max storage size
// @return 0: o.k., return -1 error
int and_queue_init(SimpleQueue *p_queue, unsigned int data_len, unsigned int max_num);
	
int and_queue_put(SimpleQueue *p_queue, void* p_data);

int and_queue_get(SimpleQueue *p_queue, void *p_data);

int and_queue_used(SimpleQueue *p_queue);

void and_queue_close(SimpleQueue *p_queue);
	
#endif //AND_QUEUE_H
