#ifndef AND_LOG_H
#define AND_LOG_H

enum loglevel
{
	LOG_VERBOSE = 1,
	LOG_DEBUG,
	LOG_INFO,
	LOG_WARN,
	LOG_ERROR
};

struct mystr;

int and_log_init(const char* logname, enum loglevel level);
	
void and_log_writeline(int id, enum loglevel level, struct mystr *p_str);

void and_log_writeline_simple(int id, enum loglevel level, const char* p_msg);

void and_log_writeline_easy(int id, enum loglevel level, const char* szFmt, ...);
	
void and_log_close();
	
#endif //AND_LOG_H
