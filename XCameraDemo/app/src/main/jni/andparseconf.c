#include "andparseconf.h"
#include "andtunables.h"
#include "andstr.h"
#include "andsysutil.h"
#include "andutility.h"
#define LOG_TAG "parseconf"
#include "pplog.h"

int prosess_gopsize(const struct mystr *str_gop);

/* Tables mapping setting names to runtime variables */
/* Boolean settings */
static struct parseconf_bool_setting
{
  const char* p_setting_name;
  int* p_variable;
}
parseconf_bool_array[] =
{
	{ "cabac", &tunable_cabac },
	{ "open_gop", &tunable_open_gop },
    { "repeat_headers", &tunable_repeat_headers },
	{ "repeat_annexb", &tunable_repeat_annexb },
	{ "intra_refresh", &tunable_intra_refresh },
	{ 0, 0 }
};

static struct parseconf_uint_setting
{
	const char* p_setting_name;
	unsigned int* p_variable;
}
parseconf_uint_array[] =
{
	{ "fps_num", &tunable_fps_num },
	{ "fps_den", &tunable_fps_den },
	{ "bitrate", &tunable_bitrate },
	{ "crf", &tunable_crf_constant },
	{ "crf-max", &tunable_crf_constant_max },
	{ "ref", &tunable_frame_reference },
	{ "bframe", &tunable_bframe },
	{ "threads", &tunable_threads },
	{ "qp", &tunable_qp_constant},
	{ "quality", &tunable_quality},
	{ "rotate", &tunable_rotate},
	{ 0, 0 }
};

static struct parseconf_str_setting
{
	const char* p_setting_name;
	const char** p_variable;
}
parseconf_str_array[] =
{
	{ "preset", &tunable_preset },
	{ "profile", &tunable_profile },
	{ "tune", &tunable_tune },
	{ "gop_size", &tunable_gop_size },
	{ 0, 0 }
};

int and_parseconf_parse(const char* conf_str)
{
	struct mystr config_conf_str = INIT_MYSTR;
	struct mystr config_setting_str = INIT_MYSTR;
	unsigned int str_pos = 0;
	int ret;

	if (!conf_str) {
		PPLOGI("null conf string");
		return 0;
	}

	PPLOGI("parse conf: %s", conf_str);

	str_alloc_text(&config_conf_str, conf_str);
	str_replace_char(&config_conf_str, ',', '\n');
	
	while (str_getline(&config_conf_str, &config_setting_str, &str_pos))
	{
		if (str_isempty(&config_setting_str) ||
			str_get_char_at(&config_setting_str, 0) == '#' ||
			str_all_space(&config_setting_str))
		{
			continue;
		}
		ret = and_parseconf_load_setting(str_getbuf(&config_setting_str), 1);
		if(ret < 0) {
			PPLOGE("failed to parse conf");
			return -1;
		}
	}

	str_free(&config_conf_str);
	str_free(&config_setting_str);
	return 0;
}

int
and_parseconf_load_setting(const char* p_setting, int errs_fatal)
{
	static struct mystr s_setting_str;
	static struct mystr s_value_str;
	while (and_sysutil_isspace(*p_setting))
	{
		p_setting++;
	}
	str_alloc_text(&s_setting_str, p_setting);
	str_split_char(&s_setting_str, &s_value_str, '=');
	/* Is it a string setting? */
	{
		const struct parseconf_str_setting* p_str_setting = parseconf_str_array;
		while (p_str_setting->p_setting_name != 0)
		{
			if (str_equal_text(&s_setting_str, p_str_setting->p_setting_name))
			{
				/* Got it */
				const char** p_curr_setting = p_str_setting->p_variable;
				if (*p_curr_setting)
				{
					and_sysutil_free((char*) *p_curr_setting);
				}
				if (str_isempty(&s_value_str))
				{
					*p_curr_setting = 0;
				}
				else
				{
					*p_curr_setting = str_strdup(&s_value_str);
					if (str_equal_text(&s_setting_str, "gop_size")) {
						if ( prosess_gopsize(&s_value_str) < 0) {
							PPLOGE("failed to set gop_size");						
							return -1;
						}
					}
				}
				return 0;
			}
			p_str_setting++;
		}
	}
	if (str_isempty(&s_value_str))
	{
		if (errs_fatal)
		{
			PPLOGE("missing value in config file for: %s",
				str_getbuf(&s_setting_str));
			return -1;
		}
		else
		{
			return 0;
		}
	}
	/* Is it a boolean value? */
	{
		const struct parseconf_bool_setting* p_bool_setting = parseconf_bool_array;
		while (p_bool_setting->p_setting_name != 0)
		{
			if (str_equal_text(&s_setting_str, p_bool_setting->p_setting_name))
			{
				/* Got it */
				str_upper(&s_value_str);
				if (str_equal_text(&s_value_str, "YES") ||
					str_equal_text(&s_value_str, "TRUE") ||
					str_equal_text(&s_value_str, "1"))
				{
					*(p_bool_setting->p_variable) = 1;
				}
				else if (str_equal_text(&s_value_str, "NO") ||
					str_equal_text(&s_value_str, "FALSE") ||
					str_equal_text(&s_value_str, "0"))
				{
					*(p_bool_setting->p_variable) = 0;
				}
				else if (errs_fatal)
				{
					PPLOGE("bad bool value in config file for: ",
						str_getbuf(&s_setting_str));
					return -1;
				}
				// should not be here!
				return 0;
			}
			p_bool_setting++;
		}
	}
	/* Is it an unsigned integer setting? */
	{
		const struct parseconf_uint_setting* p_uint_setting = parseconf_uint_array;
		while (p_uint_setting->p_setting_name != 0)
		{
			if (str_equal_text(&s_setting_str, p_uint_setting->p_setting_name))
			{
				/* Got it */
				/* If the value starts with 0, assume it's an octal value */
				if (!str_isempty(&s_value_str) &&
					str_get_char_at(&s_value_str, 0) == '0')
				{
					*(p_uint_setting->p_variable) = str_octal_to_uint(&s_value_str);
				}
				else
				{
					*(p_uint_setting->p_variable) = str_atoi(&s_value_str);
				}
				return 0;
			}
			p_uint_setting++;
		}
	}
	if (errs_fatal)
	{
		struct mystr info_str = INIT_MYSTR;
		str_alloc_text(&info_str, "unrecognized variable in config file: ");
		str_append_str(&info_str, &s_setting_str);
		PPLOGE(str_getbuf(&info_str));
		str_free(&info_str);
		PPLOGE("unrecognized variable in config file: ",
			str_getbuf(&s_setting_str));
		return -1;
	}
	//should not be here!
	return 0;
}

int prosess_gopsize(const struct mystr *str_gop)
{
	int str_len = str_getlen(str_gop);
	if(str_len < 1) {
		return -1;
	}

	if (str_get_char_at(str_gop, str_len - 1) == 'x') {
		int ratio = str_atoi(str_gop);
		tunable_keyint_max = tunable_fps_num * ratio / tunable_fps_den;
		PPLOGI("gop_size rate mode: gop %d(rate %d)", 
			tunable_keyint_max, ratio);
	}
	else {
		tunable_keyint_max = str_atoi(str_gop);
		PPLOGI("gop_size normal mode: gop %d", 
			tunable_keyint_max);
	}

	return 0;
}

