#include <stdio.h>

#include "andsysutil.h"
#include "andlog.h"
#include "easyencoder.h"

#define IN_FILENAME "in.yuv"
#define IN_WIDTH	320
#define IN_HEIGHT	240
#define IN_FMT		3			//0-BRG565, 1-RGB24, 2-NV21, 3-YUV420p
#define IN_FRAMES	100000

#define OUT_FILENAME "out%03d.h264"
#define RUN_TIMES	1

void print_arg(int argc, char **argv);
int job();

int main(int argc, char* argv[])
{
	int ret;
	
	print_arg(argc, argv);
	and_log_init("andcodec.log", LOG_INFO);
	
	int i = 0;
	char out_file[256] = {0};
	sprintf(out_file, OUT_FILENAME, i);

	return 0;
}

void print_arg(int argc, char **argv)
{
	printf("argc %d\n", argc);

	int i;
	for(i=0;i<argc;i++)
		printf("argv[%d]: %s\n", i, argv[i]);
}

