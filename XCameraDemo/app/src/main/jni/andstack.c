#include "andstack.h"
#include <stddef.h>
#include "andsysutil.h"
#define LOG_TAG "andstack"
#include "pplog.h"

static int and_stack_get_pos(filesize_t pos, int size);

int and_stack_init(SimpleStack *p_stack, unsigned int data_len, unsigned int max_num)
{
	//and_log_writeline_simple(0, LOG_DEBUG, "stack init");

	if (!p_stack) {
		PPLOGE("stack not allocated");
		return -1;
	}

	p_stack->size		= max_num;
	p_stack->pos			= 0;
	p_stack->data_len	= data_len;

	p_stack->array = (void *)and_sysutil_malloc( data_len * max_num );
	if (!p_stack->array) {
		PPLOGE("failed to alloc stack data"); 
		return -1;
	}
	and_sysutil_memclr(p_stack->array, data_len * max_num);

	PPLOGI("stack inited, every %d byte for %d",
		data_len, max_num);
	return 0;
}
	
int and_stack_push(SimpleStack *p_stack, void *p_data)
{
	if (!p_stack) {
		PPLOGE("stack not allocated");
		return -1;
	}

	if (!p_data) {
		PPLOGE("push null data");
		return -1;
	}

	const char *p_str = (char *)p_data;

	if (p_stack->pos >= p_stack->size) {
		PPLOGE("stack overflowed %d.%d", 
			p_stack->pos, p_stack->size);
		return -1;
	}
	
	int offset = p_stack->data_len * p_stack->pos;
	and_sysutil_memcpy(p_stack->array + offset, p_data, p_stack->data_len);
	p_stack->pos++;
	return 0;
}

int and_stack_pop(SimpleStack *p_stack, void *p_data)
{
	if (!p_stack) {
		PPLOGE("stack not allocated");
		return -1;
	}

	if (p_stack->pos < 1) {
		PPLOGE("stack underflowed");
		return -1;
	}

	p_stack->pos--;
	unsigned int offset = p_stack->data_len * p_stack->pos;
	and_sysutil_memcpy(p_data, p_stack->array + offset, p_stack->data_len);
	return 0;
}

void and_stack_close(SimpleStack *p_stack)
{
	//and_log_writeline_simple(0, LOG_DEBUG, "stack close");

	if (!p_stack) {
		PPLOGE("stack not allocated");
		return;
	}

	if (p_stack->array) {
		and_sysutil_free(p_stack->array);
		p_stack->array = NULL;
	}

	PPLOGI("stack closed.");
}
