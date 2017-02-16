#ifndef AND_PARSECONF_H
#define AND_PARSECONF_H

int and_parseconf_parse(const char* conf_str);

// @return <0 error
int and_parseconf_load_setting(const char* p_setting, int errs_fatal);

#endif /* AND_PARSECONF_H */


