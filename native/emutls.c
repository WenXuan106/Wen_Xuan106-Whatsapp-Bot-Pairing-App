/*
 * Provides __emutls_get_address for libnode.so (nodejs-mobile is built with emulated thread-local storage,
 * and the libc++_shared.so we ship doesn't export this helper). Same ABI as LLVM compiler-rt's emutls.c.
 * Written without libc headers, like nodeglue.c: pthread/malloc come from Android's libc at runtime.
 */
typedef __SIZE_TYPE__ size_t_;
typedef __UINTPTR_TYPE__ uintptr_t_;

extern int pthread_key_create(int *key, void (*destructor)(void *));
extern void *pthread_getspecific(int key);
extern int pthread_setspecific(int key, const void *value);
extern void *calloc(size_t_ n, size_t_ size);
extern int posix_memalign(void **out, size_t_ align, size_t_ size);
extern void free(void *p);
extern void *memcpy(void *dst, const void *src, size_t_ n);
extern void *memset(void *dst, int c, size_t_ n);

typedef struct {
    size_t_ size;
    size_t_ align;
    union { uintptr_t_ index; void *address; } object;
    void *value; /* initial contents, or 0 for zeros */
} emutls_control;

typedef struct {
    size_t_ count;
    void *data[1]; /* really `count` entries */
} emutls_array;

static int g_lock;
static int g_key_ready;
static int g_key;
static uintptr_t_ g_next_index = 1;

static void lock(void) { while (__atomic_exchange_n(&g_lock, 1, __ATOMIC_ACQUIRE)) { } }
static void unlock(void) { __atomic_store_n(&g_lock, 0, __ATOMIC_RELEASE); }

static void destroy_array(void *p) {
    emutls_array *arr = (emutls_array *)p;
    if (!arr) return;
    for (size_t_ i = 0; i < arr->count; i++) free(arr->data[i]);
    free(arr);
}

void *__emutls_get_address(emutls_control *ctl) {
    uintptr_t_ idx = __atomic_load_n(&ctl->object.index, __ATOMIC_ACQUIRE);
    if (!idx) {
        lock();
        idx = ctl->object.index;
        if (!idx) {
            idx = g_next_index++;
            __atomic_store_n(&ctl->object.index, idx, __ATOMIC_RELEASE);
        }
        unlock();
    }
    if (!g_key_ready) {
        lock();
        if (!g_key_ready) {
            pthread_key_create(&g_key, destroy_array);
            __atomic_store_n(&g_key_ready, 1, __ATOMIC_RELEASE);
        }
        unlock();
    }

    emutls_array *arr = (emutls_array *)pthread_getspecific(g_key);
    if (!arr || arr->count < idx) {
        size_t_ want = idx + 16;
        emutls_array *bigger = (emutls_array *)calloc(1, sizeof(emutls_array) + want * sizeof(void *));
        if (!bigger) return 0;
        bigger->count = want;
        if (arr) {
            memcpy(bigger->data, arr->data, arr->count * sizeof(void *));
            free(arr);
        }
        pthread_setspecific(g_key, bigger);
        arr = bigger;
    }

    void **slot = &arr->data[idx - 1];
    if (!*slot) {
        size_t_ align = ctl->align < sizeof(void *) ? sizeof(void *) : ctl->align;
        void *mem = 0;
        if (posix_memalign(&mem, align, ctl->size ? ctl->size : 1) != 0 || !mem) return 0;
        if (ctl->value) memcpy(mem, ctl->value, ctl->size);
        else memset(mem, 0, ctl->size);
        *slot = mem;
    }
    return *slot;
}
