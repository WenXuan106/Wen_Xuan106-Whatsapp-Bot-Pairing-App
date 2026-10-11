/*
 * Tiny JNI bridge that starts Node.js (libnode.so from nodejs-mobile) inside the app.
 * Written without any libc headers on purpose: the functions below are resolved by
 * Android's own libc/libdl when the library is loaded on the phone.
 */
#include <jni.h>

typedef __SIZE_TYPE__ size_t_;

extern void *dlopen(const char *file, int mode);
extern void *dlsym(void *handle, const char *name);
extern int setenv(const char *name, const char *value, int overwrite);
extern int open(const char *path, int flags, ...);
extern int dup2(int oldfd, int newfd);
extern int close(int fd);
extern void *malloc(size_t_ n);
extern size_t_ strlen(const char *s);
extern void *memcpy(void *dst, const void *src, size_t_ n);

#define RTLD_NOW_ 2
#define RTLD_GLOBAL_ 0x100
#define O_WRONLY_ 1
#define O_CREAT_ 0100
#define O_APPEND_ 02000

typedef int (*node_start_fn)(int, char **);

/* int NodeRunner.start(String[] args, String[] env, String logPath): blocks until Node exits. */
JNIEXPORT jint JNICALL Java_com_wenxuan106_botpair_NodeRunner_start(JNIEnv *env, jclass clazz,
                                                                    jobjectArray jargs, jobjectArray jenv,
                                                                    jstring jlog) {
    /* Send stdout / stderr of Node into the log file. */
    const char *log = (*env)->GetStringUTFChars(env, jlog, 0);
    int fd = open(log, O_WRONLY_ | O_CREAT_ | O_APPEND_, 0644);
    (*env)->ReleaseStringUTFChars(env, jlog, log);
    if (fd >= 0) {
        dup2(fd, 1);
        dup2(fd, 2);
        if (fd > 2) close(fd);
    }

    /* Environment variables, given as "NAME=value". */
    jsize envCount = (*env)->GetArrayLength(env, jenv);
    for (jsize i = 0; i < envCount; i++) {
        jstring item = (jstring)(*env)->GetObjectArrayElement(env, jenv, i);
        const char *kv = (*env)->GetStringUTFChars(env, item, 0);
        size_t_ n = strlen(kv);
        char *copy = (char *)malloc(n + 1);
        memcpy(copy, kv, n + 1);
        for (size_t_ k = 0; k < n; k++) {
            if (copy[k] == '=') {
                copy[k] = 0;
                setenv(copy, copy + k + 1, 1);
                break;
            }
        }
        (*env)->ReleaseStringUTFChars(env, item, kv);
        (*env)->DeleteLocalRef(env, item);
    }

    /* Node (libuv) wants all argv strings in one contiguous block. */
    jsize argc = (*env)->GetArrayLength(env, jargs);
    size_t_ total = 0;
    for (jsize i = 0; i < argc; i++) {
        jstring item = (jstring)(*env)->GetObjectArrayElement(env, jargs, i);
        const char *a = (*env)->GetStringUTFChars(env, item, 0);
        total += strlen(a) + 1;
        (*env)->ReleaseStringUTFChars(env, item, a);
        (*env)->DeleteLocalRef(env, item);
    }
    char *block = (char *)malloc(total + 1);
    char **argv = (char **)malloc(sizeof(char *) * (size_t_)(argc + 1));
    size_t_ off = 0;
    for (jsize i = 0; i < argc; i++) {
        jstring item = (jstring)(*env)->GetObjectArrayElement(env, jargs, i);
        const char *a = (*env)->GetStringUTFChars(env, item, 0);
        size_t_ n = strlen(a) + 1;
        memcpy(block + off, a, n);
        argv[i] = block + off;
        off += n;
        (*env)->ReleaseStringUTFChars(env, item, a);
        (*env)->DeleteLocalRef(env, item);
    }
    argv[argc] = 0;

    void *lib = dlopen("libnode.so", RTLD_NOW_ | RTLD_GLOBAL_);
    if (!lib) return -1001;
    node_start_fn start = (node_start_fn)dlsym(lib, "_ZN4node5StartEiPPc");
    if (!start) return -1002;
    return start((int)argc, argv);
}
