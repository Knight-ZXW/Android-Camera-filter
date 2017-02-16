#ifndef ANDSYSUTIL_H
#define ANDSYSUTIL_H

#ifndef AND_FILESIZE_H
#include "andfilesize.h"
#endif

typedef void (*exitfunc_t)(void);

/* Memory allocating/freeing */
void* and_sysutil_malloc(unsigned int size);
void* and_sysutil_realloc(void* p_ptr, unsigned int size);
void and_sysutil_free(void* p_ptr);

/* Various string functions */
unsigned int and_sysutil_strlen(const char* p_text);
char* and_sysutil_strdup(const char* p_str);
void and_sysutil_memclr(void* p_dest, unsigned int size);
void and_sysutil_memset(void* p_dest, int val, unsigned int size);
void and_sysutil_memcpy(void* p_dest, const void* p_src,
                        const unsigned int size);
void and_sysutil_strcpy(char* p_dest, const char* p_src, unsigned int maxsize);
int and_sysutil_memcmp(const void* p_src1, const void* p_src2,
                       unsigned int size);
int and_sysutil_strcmp(const char* p_src1, const char* p_src2);
int and_sysutil_atoi(const char* p_str);
filesize_t and_sysutil_a_to_filesize_t(const char* p_str);
const char* and_sysutil_ulong_to_str(unsigned long the_ulong);
const char* and_sysutil_filesize_t_to_str(filesize_t the_filesize);
const char* and_sysutil_double_to_str(double the_double);
const char* and_sysutil_uint_to_octal(unsigned int the_uint);
unsigned int and_sysutil_octal_to_uint(const char* p_str);
int and_sysutil_toupper(int the_char);
//for case_sensitive cfg
int and_sysutil_tolower(int the_char);
int and_sysutil_isspace(int the_char);
int and_sysutil_isprint(int the_char);
int and_sysutil_isalnum(int the_char);
int and_sysutil_isdigit(int the_char);
	
void and_sysutil_exit(int exit_code);

/* Reading and writing */
filesize_t and_sysutil_lseek(const int fd, filesize_t offset, int whence);
void and_sysutil_lseek_to(const int fd, filesize_t seek_pos);
void and_sysutil_lseek_end(const int fd);
filesize_t and_sysutil_get_file_offset(const int file_fd);
int and_sysutil_read(const int fd, void* p_buf, const unsigned int size);
int and_sysutil_write(const int fd, const void* p_buf,
                      const unsigned int size);
int and_sysutil_read_loop(const int fd, void* p_buf, unsigned int size);
int and_sysutil_write_loop(const int fd, const void* p_buf, unsigned int size);

struct and_sysutil_statbuf;
int and_sysutil_stat(const char* p_name, struct and_sysutil_statbuf** p_ptr);
int and_sysutil_lstat(const char* p_name, struct and_sysutil_statbuf** p_ptr);
void and_sysutil_fstat(int fd, struct and_sysutil_statbuf** p_ptr);

int and_sysutil_statbuf_is_regfile(const struct and_sysutil_statbuf* p_stat);
int and_sysutil_statbuf_is_symlink(const struct and_sysutil_statbuf* p_stat);
int and_sysutil_statbuf_is_socket(const struct and_sysutil_statbuf* p_stat);
int and_sysutil_statbuf_is_dir(const struct and_sysutil_statbuf* p_stat);
filesize_t and_sysutil_statbuf_get_size(
										const struct and_sysutil_statbuf* p_stat);
const char* and_sysutil_statbuf_get_perms(
	const struct and_sysutil_statbuf* p_stat);

int and_sysutil_chmod(const char* p_filename, unsigned int mode);
void and_sysutil_fchown(const int fd, const int uid, const int gid);
void and_sysutil_fchmod(const int fd, unsigned int mode);
int and_sysutil_readlink(const char* p_filename, char* p_dest,
                         unsigned int bufsiz);

/* Get / unget various locks. Lock gets are blocking. Write locks are
 * exclusive; read locks are shared.
 */
int and_sysutil_lock_file_write(int fd);
int and_sysutil_lock_file_read(int fd);
void and_sysutil_unlock_file(int fd);

/* Get / unget various locks. Lock gets are blocking. Write locks are
 * exclusive; read locks are shared.
 */
int and_sysutil_lock_file_write(int fd);
int and_sysutil_lock_file_read(int fd);
void and_sysutil_unlock_file(int fd);

/* Directory related things */
char* and_sysutil_getcwd(char* p_dest, const unsigned int buf_size);
int and_sysutil_mkdir(const char* p_dirname, const unsigned int mode);
int and_sysutil_rmdir(const char* p_dirname);
int and_sysutil_chdir(const char* p_dirname);
int and_sysutil_rename(const char* p_from, const char* p_to);

struct and_sysutil_dir;
struct and_sysutil_dir* and_sysutil_opendir(const char* p_dirname);
void and_sysutil_closedir(struct and_sysutil_dir* p_dir);
const char* and_sysutil_next_dirent(struct and_sysutil_dir* p_dir);

/* File create/open/close etc. */
enum EANDSysUtilOpenMode
{
  kANDSysUtilOpenReadOnly = 1,
  kANDSysUtilOpenWriteOnly,
  kANDSysUtilOpenReadWrite
};
int and_sysutil_open_file(const char* p_filename,
                          const enum EANDSysUtilOpenMode);

/* Creates or appends */
int and_sysutil_create_or_open_file(const char* p_filename, unsigned int mode);	
void and_sysutil_close(int fd);		  
int and_sysutil_close_failok(int fd);
int and_sysutil_unlink(const char* p_dead);
int and_sysutil_write_access(const char* p_filename);
void and_sysutil_ftruncate(int fd);

/* Time handling */
/* Do not call get_time_usec() without calling get_time_sec()
 * first otherwise you will get stale data.
 */
long and_sysutil_get_time_sec(void);
long and_sysutil_get_time_usec(void);
long and_sysutil_parse_time(const char* p_text);
void and_sysutil_sleep(double seconds);
int and_sysutil_setmodtime(const char* p_file, long the_time, int is_localtime);

const char* and_sysutil_get_current_date(void);

#endif //ANDSYSUTIL_H

