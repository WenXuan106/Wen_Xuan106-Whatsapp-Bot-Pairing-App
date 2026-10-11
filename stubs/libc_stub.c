/* Link-time stand-ins so nodeglue records "libc.so" / "libdl.so" as dependencies. Never shipped. */
void *malloc(__SIZE_TYPE__ n) { return 0; }
__SIZE_TYPE__ strlen(const char *s) { return 0; }
void *memcpy(void *d, const void *s, __SIZE_TYPE__ n) { return d; }
int setenv(const char *a, const char *b, int c) { return 0; }
int open(const char *p, int f, ...) { return -1; }
int dup2(int a, int b) { return -1; }
int close(int a) { return 0; }
int pthread_key_create(int *k, void (*d)(void *)) { return 0; }
void *pthread_getspecific(int k) { return 0; }
int pthread_setspecific(int k, const void *v) { return 0; }
void *calloc(__SIZE_TYPE__ n, __SIZE_TYPE__ s) { return 0; }
int posix_memalign(void **o, __SIZE_TYPE__ a, __SIZE_TYPE__ s) { return 0; }
void free(void *p) { }
void *memset(void *d, int c, __SIZE_TYPE__ n) { return d; }
