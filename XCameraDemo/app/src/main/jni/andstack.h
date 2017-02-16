#ifndef AND_STACK_H
#define AND_STACK_H

#include "andfilesize.h"

typedef struct SimpleStack {
	void*		array;
	filesize_t	pos;
	int			data_len;
	int			size;
}SimpleStack;

// @param data_len:		stored data len(in byte)
// @param max_num:		max storage size
// @return 0: o.k., return -1 error
int and_stack_init(SimpleStack *p_stack, unsigned int data_len, unsigned int max_num);
	
int and_stack_push(SimpleStack *p_stack, void* p_data);

int and_stack_pop(SimpleStack *p_stack, void *p_data);

void and_stack_close(SimpleStack *p_stack);
	
#endif //AND_STACK_H
