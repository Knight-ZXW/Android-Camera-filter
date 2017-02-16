#include "andtiming_c.h"
#include "andsysutil.h"

void and_ticker_reset(AndTicker *tick)
{
	tick->start_sec = and_sysutil_get_time_sec();
	tick->start_usec = and_sysutil_get_time_usec();
}

double and_ticker_msec(AndTicker *tick)
{
	return (double)1000. * and_ticker_sec(tick);
}

double and_ticker_sec(AndTicker *tick)
{
	double elapsed;
	long curr_sec, curr_usec;
	
	curr_sec = and_sysutil_get_time_sec();
	curr_usec = and_sysutil_get_time_usec();
	elapsed = (double) (curr_sec - tick->start_sec);
	elapsed += (double) (curr_usec - tick->start_usec) /
		(double) 1000000;
	if (elapsed <= (double) 0)
	{
		elapsed = (double) 0.01;
	}

	return elapsed;
}

