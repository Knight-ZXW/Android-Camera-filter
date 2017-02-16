#include "andsysutil.h"
#include "anddefs.h"
#include "andutility.h"

#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <dirent.h> //for dir
#include <limits.h>
#include <time.h>
#include <utime.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>
#include <ctype.h>
#include <sys/time.h> // for gettimeofday

/* Exit function */
static exitfunc_t s_exit_func;

/* Cached time */
static struct timeval s_current_time;

/* Difference in timezone from GMT in seconds */
static long s_timezone;

static int and_sysutil_translate_openmode(
  const enum EANDSysUtilOpenMode mode);

static int lock_internal(int fd, int lock_type);

void*
and_sysutil_malloc(unsigned int size)
{
  void* p_ret;
  /* Paranoia - what if we got an integer overflow/underflow? */
  if (size == 0 || size > INT_MAX)
  {
    bug("zero or big size in and_sysutil_malloc");
  }  
  p_ret = malloc(size);
  if (p_ret == NULL)
  {
    die("malloc");
  }
  return p_ret;
}

void*
and_sysutil_realloc(void* p_ptr, unsigned int size)
{
  void* p_ret;
  if (size == 0 || size > INT_MAX)
  {
    bug("zero or big size in and_sysutil_realloc");
  }
  p_ret = realloc(p_ptr, size);
  if (p_ret == NULL)
  {
    die("realloc");
  }
  return p_ret;
}

void
and_sysutil_free(void* p_ptr)
{
  if (p_ptr == NULL)
  {
    bug("and_sysutil_free got a null pointer");
  }
  free(p_ptr);
}

unsigned int
and_sysutil_strlen(const char* p_text)
{
  unsigned int ret = strlen(p_text);
  /* A defense in depth measure. */
  if (ret > INT_MAX / 8)
  {
    die("string suspiciously long");
  }
  return ret;
}

char*
and_sysutil_strdup(const char* p_str)
{
  return strdup(p_str);
}

void
and_sysutil_memset(void* p_dest, int val, unsigned int size)
{
	/* Safety */
	if (size == 0)
	{
		return;
	}
	memset(p_dest, val, size);
}


void
and_sysutil_memclr(void* p_dest, unsigned int size)
{
  /* Safety */
  if (size == 0)
  {
    return;
  }
  memset(p_dest, '\0', size);
}

void
and_sysutil_memcpy(void* p_dest, const void* p_src, const unsigned int size)
{
  /* Safety */
  if (size == 0)
  {
    return;
  }
  /* Defense in depth */
  if (size > INT_MAX)
  {
    die("possible negative value to memcpy?");
  }
  memcpy(p_dest, p_src, size);
}

void
and_sysutil_strcpy(char* p_dest, const char* p_src, unsigned int maxsize)
{
  if (maxsize == 0)
  {
    return;
  }
  strncpy(p_dest, p_src, maxsize);
  p_dest[maxsize - 1] = '\0';
}

int
and_sysutil_memcmp(const void* p_src1, const void* p_src2, unsigned int size)
{
  /* Safety */
  if (size == 0)
  {
    return 0;
  }
  return memcmp(p_src1, p_src2, size);
}

int
and_sysutil_strcmp(const char* p_src1, const char* p_src2)
{
  return strcmp(p_src1, p_src2);
}

int
and_sysutil_atoi(const char* p_str)
{
  return atoi(p_str);
}

filesize_t
and_sysutil_a_to_filesize_t(const char* p_str)
{
  /* atoll() is C99 standard - but even modern FreeBSD, OpenBSD don't have
   * it, so we'll supply our own
   */
  filesize_t result = 0;
  filesize_t mult = 1;
  unsigned int len = and_sysutil_strlen(p_str);
  unsigned int i;
  /* Bail if the number is excessively big (petabytes!) */
  if (len > 15)
  {
    return 0;
  }
  for (i=0; i<len; ++i)
  {
    char the_char = p_str[len-(i+1)];
    filesize_t val;
    if (the_char < '0' || the_char > '9')
    {
      return 0;
    }
    val = the_char - '0';
    val *= mult;
    result += val;
    mult *= 10;
  }
  return result;
}

const char*
and_sysutil_ulong_to_str(unsigned long the_ulong)
{
  static char ulong_buf[32];
  (void) snprintf(ulong_buf, sizeof(ulong_buf), "%lu", the_ulong);
  return ulong_buf;
}

const char*
and_sysutil_filesize_t_to_str(filesize_t the_filesize)
{
  static char filesize_buf[32];
  if (sizeof(long) == 8)
  {
    /* Avoid using non-standard %ll if we can */
    (void) snprintf(filesize_buf, sizeof(filesize_buf), "%ld",
                    (long) the_filesize);
  }
  else
  {
    (void) snprintf(filesize_buf, sizeof(filesize_buf), "%lld", the_filesize);
  }
  return filesize_buf;
}

const char*
and_sysutil_double_to_str(double the_double)
{
  static char double_buf[32];
  (void) snprintf(double_buf, sizeof(double_buf), "%.2f", the_double);
  return double_buf;
}

const char*
and_sysutil_uint_to_octal(unsigned int the_uint)
{
  static char octal_buf[32];
  if (the_uint == 0)
  {
    octal_buf[0] = '0';
    octal_buf[1] = '\0';
  }
  else
  {
    (void) snprintf(octal_buf, sizeof(octal_buf), "0%o", the_uint);
  }
  return octal_buf;
}

unsigned int
and_sysutil_octal_to_uint(const char* p_str)
{
  /* NOTE - avoiding using sscanf() parser */
  unsigned int result = 0;
  int seen_non_zero_digit = 0;
  while (*p_str != '\0')
  {
    int digit = *p_str;
    if (!isdigit(digit) || digit > '7')
    {
      break;
    }
    if (digit != '0')
    {
      seen_non_zero_digit = 1;
    }
    if (seen_non_zero_digit)
    {
      result <<= 3;
      result += (digit - '0');
    }
    p_str++;
  }
  return result;
}

int
and_sysutil_toupper(int the_char)
{
  return toupper(the_char);
}

//for case_sensitive cfg
int
and_sysutil_tolower(int the_char)
{
  return tolower(the_char);
}

int
and_sysutil_isspace(int the_char)
{
  return isspace(the_char);
}

int
and_sysutil_isprint(int the_char)
{
  /* From Solar - we know better than some libc's! Don't let any potential
   * control chars through
   */
  unsigned char uc = (unsigned char) the_char;
  if (uc <= 31)
  {
    return 0;
  }
  if (uc == 177)
  {
    return 0;
  }
  if (uc >= 128 && uc <= 159)
  {
    return 0;
  }
  return isprint(the_char);
}

int
and_sysutil_isalnum(int the_char)
{
  return isalnum(the_char);
}

int
and_sysutil_isdigit(int the_char)
{
  return isdigit(the_char);
}

void
and_sysutil_exit(int exit_code)
{
  if (s_exit_func)
  {
    exitfunc_t curr_func = s_exit_func;
    /* Prevent recursion */
    s_exit_func = 0;
    (*curr_func)();
  }
  _exit(exit_code);
}

int
and_sysutil_open_file(const char* p_filename,
                      const enum EANDSysUtilOpenMode mode)
{
  return open(p_filename, and_sysutil_translate_openmode(mode)/* | O_NONBLOCK*/);
}

int
and_sysutil_create_or_open_file(const char* p_filename, unsigned int mode)
{
  return open(p_filename, O_CREAT | O_WRONLY/* | O_NONBLOCK*/, mode);
}

void
and_sysutil_ftruncate(int fd)
{
	int ret = ftruncate(fd, 0);
	if (ret != 0)
	{
		die("ftruncate");
	}
}

void
and_sysutil_dupfd2(int old_fd, int new_fd)
{
  int retval;
  if (old_fd == new_fd)
  {
    return;
  }
  retval = dup2(old_fd, new_fd);
  if (retval != new_fd)
  {
    die("dup2");
  }
}

void
and_sysutil_close(int fd)
{
  while (1)
  {
    int retval = close(fd);
    if (retval != 0)
    {
      if (errno == EINTR)
      {
        //and_sysutil_check_pending_actions(kVSFSysUtilUnknown, 0, 0);
        continue;
      }
      die("close");
    }
    return;
  }
}

int
and_sysutil_close_failok(int fd)
{
  return close(fd);
}

int
and_sysutil_unlink(const char* p_dead)
{
  return unlink(p_dead);
}

int
and_sysutil_write_access(const char* p_filename)
{
	int retval = access(p_filename, W_OK);
	return (retval == 0);
}

int
and_sysutil_read(const int fd, void* p_buf, const unsigned int size)
{
  while (1)
  {
    int retval = read(fd, p_buf, size);
    int saved_errno = errno;

    //and_sysutil_check_pending_actions(kVSFSysUtilIO, retval, fd);
    if (retval < 0 && saved_errno == EINTR)
    {
      continue;
    }
    return retval;
  }
}

int
and_sysutil_write(const int fd, const void* p_buf, const unsigned int size)
{
  while (1)
  {
    int retval = write(fd, p_buf, size);
    int saved_errno = errno;
    //and_sysutil_check_pending_actions(kVSFSysUtilIO, retval, fd);
    if (retval < 0 && saved_errno == EINTR)
    {
      continue;
    }
    return retval;
  }
}

int
and_sysutil_read_loop(const int fd, void* p_buf, unsigned int size)
{
  int retval;
  int num_read = 0;
  if (size > INT_MAX)
  {
    die("size too big in and_sysutil_read_loop");
  }
  while (1)
  {
    retval = and_sysutil_read(fd, (char*)p_buf + num_read, size);
    if (retval < 0)
    {
      return retval;
    }
    else if (retval == 0)
    {
      /* Read all we're going to read.. */
      return num_read; 
    }
    if ((unsigned int) retval > size)
    {
      die("retval too big in and_sysutil_read_loop");
    }
    num_read += retval;
    size -= (unsigned int) retval;
    if (size == 0)
    {
      /* Hit the read target, cool. */
      return num_read;
    }
  }
}

int
and_sysutil_write_loop(const int fd, const void* p_buf, unsigned int size)
{
  int retval;
  int num_written = 0;
  if (size > INT_MAX)
  {
    die("size too big in and_sysutil_write_loop");
  }
  while (1)
  {
    retval = and_sysutil_write(fd, (const char*)p_buf + num_written, size);
    if (retval < 0)
    {
      /* Error */
      return retval;
    }
    else if (retval == 0)
    {
      /* Written all we're going to write.. */
      return num_written;
    }
    if ((unsigned int) retval > size)
    {
      die("retval too big in and_sysutil_write_loop");
    }
    num_written += retval;
    size -= (unsigned int) retval;
    if (size == 0)
    {
      /* Hit the write target, cool. */
      return num_written;
    }
  }
}

filesize_t
and_sysutil_get_file_offset(const int file_fd)
{
  filesize_t retval = lseek(file_fd, 0, SEEK_CUR);
  if (retval < 0)
  {
    die("lseek");
  }
  return retval;
}

filesize_t 
and_sysutil_lseek(const int fd, filesize_t offset, int whence)
{
	filesize_t retval;
	if (offset < 0)
	{
		die("negative seek_pos in and_sysutil_lseek");
	}
	retval = lseek(fd, offset, whence);
	if (retval < 0)
	{
		die("lseek");
	}

	return retval;
}

void
and_sysutil_lseek_to(const int fd, filesize_t seek_pos)
{
  filesize_t retval;
  if (seek_pos < 0)
  {
    die("negative seek_pos in and_sysutil_lseek_to");
  }
  retval = lseek(fd, seek_pos, SEEK_SET);
  if (retval < 0)
  {
    die("lseek");
  }
}

void
and_sysutil_lseek_end(const int fd)
{
  filesize_t retval;
  retval = lseek(fd, 0, SEEK_END);
  if (retval < 0)
  {
    die("lseek");
  }
}

const char*
and_sysutil_get_current_date(void)
{
  static char datebuf[64];
  time_t curr_time;
  const struct tm* p_tm;
  int i = 0;
  curr_time = and_sysutil_get_time_sec();
  p_tm = localtime(&curr_time);
  if (strftime(datebuf, sizeof(datebuf), "%a %b!%d %H:%M:%S %Y", p_tm) == 0)
  {
    die("strftime");
  }
  datebuf[sizeof(datebuf) - 1] = '\0';
  /* This hack is because %e in strftime() isn't so portable */
  while (datebuf[i] != '!' && datebuf[i] != '\0')
  {
    ++i;
  }
  if (datebuf[i] == '!')
  {
    datebuf[i] = ' ';
    if (datebuf[i+1] == '0')
    {
      datebuf[i+1] = ' ';
    }
  }
  return datebuf;
}

long
and_sysutil_get_time_sec(void)
{
  if (gettimeofday(&s_current_time, NULL) != 0)
  {
    die("gettimeofday");
  }
  return s_current_time.tv_sec;
}

long
and_sysutil_get_time_usec(void)
{
  return s_current_time.tv_usec;
}

long
and_sysutil_parse_time(const char* p_text)
{
  struct tm the_time;
  unsigned int len = and_sysutil_strlen(p_text);
  and_sysutil_memclr(&the_time, sizeof(the_time));
  if (len >= 8)
  {
    char yr[5];
    char mon[3];
    char day[3];
    and_sysutil_strcpy(yr, p_text, 5);
    and_sysutil_strcpy(mon, p_text + 4, 3);
    and_sysutil_strcpy(day, p_text + 6, 3);
    the_time.tm_year = and_sysutil_atoi(yr) - 1900;
    the_time.tm_mon = and_sysutil_atoi(mon) - 1;
    the_time.tm_mday = and_sysutil_atoi(day);
  }
  if (len >= 14)
  {
    char hr[3];
    char mins[3];
    char sec[3];
    and_sysutil_strcpy(hr, p_text + 8, 3);
    and_sysutil_strcpy(mins, p_text + 10, 3);
    and_sysutil_strcpy(sec, p_text + 12, 3);
    the_time.tm_hour = and_sysutil_atoi(hr);
    the_time.tm_min = and_sysutil_atoi(mins);
    the_time.tm_sec = and_sysutil_atoi(sec);
  }
  return mktime(&the_time);
}

int
and_sysutil_setmodtime(const char* p_file, long the_time, int is_localtime)
{
  struct utimbuf new_times;
  if (!is_localtime)
  {
    the_time -= s_timezone;
  }
  and_sysutil_memclr(&new_times, sizeof(new_times));
  new_times.actime = the_time;
  new_times.modtime = the_time;
  return utime(p_file, &new_times);
}

void
and_sysutil_sleep(double seconds)
{
  int retval;
  int saved_errno;
  double fractional;
  time_t secs;
  struct timespec ts;
  secs = (time_t) seconds;
  fractional = seconds - (double) secs;
  ts.tv_sec = secs;
  ts.tv_nsec = (long) (fractional * (double) 1000000000);
  do
  {
    retval = nanosleep(&ts, &ts);
    saved_errno = errno;
    //and_sysutil_check_pending_actions(kVSFSysUtilUnknown, 0, 0);
  } while (retval == -1 && saved_errno == EINTR);
}

static int
and_sysutil_translate_openmode(const enum EANDSysUtilOpenMode mode)
{
  int retval = 0;
  switch (mode)
  {
    case kANDSysUtilOpenReadOnly:
      retval = O_RDONLY;
      break;
    case kANDSysUtilOpenWriteOnly:
      retval = O_WRONLY;
      break;
    case kANDSysUtilOpenReadWrite:
      retval = O_RDWR;
      break;
    default:
      bug("bad mode in and_sysutil_translate_openmode");
      break;
  }
  return retval;
}

static void
and_sysutil_alloc_statbuf(struct and_sysutil_statbuf** p_ptr)
{
	if (*p_ptr == NULL)
	{
		*p_ptr = and_sysutil_malloc(sizeof(struct stat));
	}
}

void
and_sysutil_fstat(int fd, struct and_sysutil_statbuf** p_ptr)
{
	int retval;
	and_sysutil_alloc_statbuf(p_ptr);
	retval = fstat(fd, (struct stat*) (*p_ptr));
	if (retval != 0)
	{
		die("fstat");
	}
}

int
and_sysutil_stat(const char* p_name, struct and_sysutil_statbuf** p_ptr)
{
	and_sysutil_alloc_statbuf(p_ptr);
	return stat(p_name, (struct stat*) (*p_ptr));
}

int
and_sysutil_lstat(const char* p_name, struct and_sysutil_statbuf** p_ptr)
{
	and_sysutil_alloc_statbuf(p_ptr);
	return lstat(p_name, (struct stat*) (*p_ptr));
}

filesize_t
and_sysutil_statbuf_get_size(const struct and_sysutil_statbuf* p_statbuf)
{
	const struct stat* p_stat = (const struct stat*) p_statbuf;
	if (p_stat->st_size < 0)
	{
		die("invalid inode size in and_sysutil_statbuf_get_size");
	}
	return p_stat->st_size;
}

int
and_sysutil_statbuf_is_regfile(const struct and_sysutil_statbuf* p_stat)
{
	const struct stat* p_realstat = (const struct stat*) p_stat;
	return S_ISREG(p_realstat->st_mode);
}

int
and_sysutil_statbuf_is_symlink(const struct and_sysutil_statbuf* p_stat)
{
	const struct stat* p_realstat = (const struct stat*) p_stat;
	return S_ISLNK(p_realstat->st_mode);
}

int
and_sysutil_statbuf_is_socket(const struct and_sysutil_statbuf* p_stat)
{
	const struct stat* p_realstat = (const struct stat*) p_stat;
	return S_ISSOCK(p_realstat->st_mode);
}

int
and_sysutil_statbuf_is_dir(const struct and_sysutil_statbuf* p_stat)
{
	const struct stat* p_realstat = (const struct stat*) p_stat;
	return S_ISDIR(p_realstat->st_mode);
}

void
and_sysutil_fchown(const int fd, const int uid, const int gid)
{
	if (fchown(fd, uid, gid) != 0)
	{
		die("fchown");
	}
}

void
and_sysutil_fchmod(const int fd, unsigned int mode)
{
	mode = mode & 0777;
	if (fchmod(fd, mode))
	{
		die("fchmod");
	}
}

int
and_sysutil_chmod(const char* p_filename, unsigned int mode)
{
	/* Safety: mask "mode" to just access permissions, e.g. no suid setting! */
	mode = mode & 0777;
	return chmod(p_filename, mode);
}

int
and_sysutil_readlink(const char* p_filename, char* p_dest, unsigned int bufsiz)
{
	int retval;
	if (bufsiz == 0) {
		return -1;
	}
	retval = readlink(p_filename, p_dest, bufsiz - 1);
	if (retval < 0)
	{
		return retval;
	}
	/* Ensure buffer is NULL terminated; readlink(2) doesn't do that */
	p_dest[retval] = '\0';
	return retval;
}

int
and_sysutil_lock_file_write(int fd)
{
	return lock_internal(fd, F_WRLCK);
}

int
and_sysutil_lock_file_read(int fd)
{
	return lock_internal(fd, F_RDLCK);
}

static int
lock_internal(int fd, int lock_type)
{
	struct flock the_lock;
	int retval;
	int saved_errno;
	and_sysutil_memclr(&the_lock, sizeof(the_lock));
	the_lock.l_type = lock_type;
	the_lock.l_whence = SEEK_SET;
	the_lock.l_start = 0;
	the_lock.l_len = 0;
	do
	{
		retval = fcntl(fd, F_SETLKW, &the_lock);
		saved_errno = errno;
		//and_sysutil_check_pending_actions(kVSFSysUtilUnknown, 0, 0);
	}
	while (retval < 0 && saved_errno == EINTR);
	return retval;
}

void
and_sysutil_unlock_file(int fd)
{
	int retval;
	struct flock the_lock;
	and_sysutil_memclr(&the_lock, sizeof(the_lock));
	the_lock.l_type = F_UNLCK;
	the_lock.l_whence = SEEK_SET;
	the_lock.l_start = 0;
	the_lock.l_len = 0;
	retval = fcntl(fd, F_SETLK, &the_lock);
	if (retval != 0)
	{
		die("fcntl");
	}
}

char*
and_sysutil_getcwd(char* p_dest, const unsigned int buf_size)
{
	char* p_retval;
	if (buf_size == 0) {
		return p_dest;
	}
	p_retval = getcwd(p_dest, buf_size);
	p_dest[buf_size - 1] = '\0';
	return p_retval;
}

int
and_sysutil_mkdir(const char* p_dirname, const unsigned int mode)
{
	return mkdir(p_dirname, mode);
}

int
and_sysutil_rmdir(const char* p_dirname)
{
	return rmdir(p_dirname);
}

int
and_sysutil_chdir(const char* p_dirname)
{
	return chdir(p_dirname);
}

int
and_sysutil_rename(const char* p_from, const char* p_to)
{
	return rename(p_from, p_to);
}

struct and_sysutil_dir*
	and_sysutil_opendir(const char* p_dirname)
{
	return (struct and_sysutil_dir*) opendir(p_dirname);
}

void
and_sysutil_closedir(struct and_sysutil_dir* p_dir)
{
	DIR* p_real_dir = (DIR*) p_dir;
	int retval = closedir(p_real_dir);
	if (retval != 0)
	{
		die("closedir");
	}
}

const char*
and_sysutil_next_dirent(struct and_sysutil_dir* p_dir)
{
	DIR* p_real_dir = (DIR*) p_dir;
	struct dirent* p_dirent = readdir(p_real_dir);
	if (p_dirent == NULL)
	{
		return NULL;
	}
	return p_dirent->d_name;
}

