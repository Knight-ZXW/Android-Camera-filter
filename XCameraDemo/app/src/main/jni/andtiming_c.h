#ifndef And_TIMING_C_H
#define And_TIMING_C_H

typedef struct AndTicker_t{
	long start_sec;
	long start_usec;
}AndTicker;

void and_ticker_reset(AndTicker *tick);

double and_ticker_msec(AndTicker *tick);

double and_ticker_sec(AndTicker *tick);
	
#endif //And_TIMING_C_H
