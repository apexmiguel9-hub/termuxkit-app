/*
 * LD_PRELOAD shim for termux-exec — SELinux workaround.
 *
 * Problem: SELinux on Android denies execute_no_trans for files in app_data_file.
 * When bash tries to execve() a script (e.g., pkg), the kernel reads the shebang
 * (#!/path/to/bash) and tries to execute the interpreter directly from app_data_file.
 * SELinux blocks this.
 *
 * Solution: Intercept execve() via LD_PRELOAD. If the target file is a script
 * (starts with #!), redirect execution to /system/bin/sh which CAN execute
 * files from app_data_file (it's a system binary with proper SELinux context).
 *
 * For ELF binaries, inject LD_PRELOAD so child processes also get this shim.
 */

#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <sys/stat.h>
#include <android/log.h>

#define LOG_TAG "termux-exec"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* Termux paths */
#define TERMUX_PREFIX_DATA "/data/data/com.termux.alpa_termuxkit/files/usr"
#define TERMUX_PREFIX_USER0 "/data/user/0/com.termux.alpa_termuxkit/files/usr"
#define SYSTEM_SH "/system/bin/sh"

/* Original execve */
typedef int (*execve_func_t)(const char *filename, char *const argv[], char *const envp[]);
static execve_func_t real_execve = NULL;

static void ensure_real_execve(void) {
    if (!real_execve) {
        real_execve = (execve_func_t)dlsym(RTLD_NEXT, "execve");
        if (!real_execve) {
            LOGE("Failed to find real execve: %s", dlerror());
        }
    }
}

/*
 * Check if filename is under our Termux prefix.
 */
static int is_termux_path(const char *filename) {
    if (!filename || filename[0] != '/') return 0;

    size_t data_len = strlen(TERMUX_PREFIX_DATA);
    size_t user0_len = strlen(TERMUX_PREFIX_USER0);

    return (strncmp(filename, TERMUX_PREFIX_DATA, data_len) == 0) ||
           (strncmp(filename, TERMUX_PREFIX_USER0, user0_len) == 0);
}

/*
 * Check if a file is a shell script (starts with #!).
 * Reads only the first 2 bytes — very fast.
 */
static int is_script_file(const char *filename) {
    FILE *f = fopen(filename, "r");
    if (!f) return 0;

    char header[2];
    size_t n = fread(header, 1, 2, f);
    fclose(f);

    return (n == 2 && header[0] == '#' && header[1] == '!');
}

/*
 * Count envp entries.
 */
static int count_envp(char *const envp[]) {
    int count = 0;
    if (!envp) return 0;
    while (envp[count]) count++;
    return count;
}

/*
 * Build new envp with LD_PRELOAD, LD_LIBRARY_PATH, and TERMUX_NATIVE_LIB_DIR injected.
 */
static char **build_enhanced_envp(char *const envp[], int *out_count) {
    int env_count = count_envp(envp);

    /* We add up to 3 extra vars + NULL terminator */
    char **new_envp = (char **)malloc((env_count + 4) * sizeof(char *));
    if (!new_envp) {
        LOGE("malloc failed for new envp");
        return NULL;
    }

    /* Copy existing env vars */
    for (int i = 0; i < env_count; i++) {
        new_envp[i] = envp[i];
    }

    int extra = 0;

    /* Get native lib dir */
    const char *native_lib_dir = getenv("TERMUX_NATIVE_LIB_DIR");
    static char shim_path_buf[512];
    static char ld_preload_buf[512];
    static char ld_lib_path_buf[512];
    static char native_lib_dir_buf[512];

    if (native_lib_dir && native_lib_dir[0] != '\0') {
        snprintf(shim_path_buf, sizeof(shim_path_buf), "%s/libtermux-exec.so", native_lib_dir);
        snprintf(native_lib_dir_buf, sizeof(native_lib_dir_buf), "TERMUX_NATIVE_LIB_DIR=%s", native_lib_dir);
        new_envp[env_count + extra++] = native_lib_dir_buf;
    }

    /* Inject LD_PRELOAD */
    const char *shim_path = (native_lib_dir && native_lib_dir[0] != '\0') ? shim_path_buf : "/system/lib64/libtermux-exec.so";
    snprintf(ld_preload_buf, sizeof(ld_preload_buf), "LD_PRELOAD=%s", shim_path);
    new_envp[env_count + extra++] = ld_preload_buf;

    /* Inject LD_LIBRARY_PATH */
    snprintf(ld_lib_path_buf, sizeof(ld_lib_path_buf),
             "LD_LIBRARY_PATH=/data/data/com.termux.alpa_termuxkit/files/usr/lib:"
             "/data/user/0/com.termux.alpa_termuxkit/files/usr/lib");
    new_envp[env_count + extra++] = ld_lib_path_buf;

    new_envp[env_count + extra] = NULL;
    if (out_count) *out_count = env_count + extra;

    return new_envp;
}

/*
 * Check if LD_PRELOAD already has our shim.
 */
static int ld_preload_has_shim(char *const envp[]) {
    for (char *const *env = envp; env && *env; env++) {
        if (strncmp(*env, "LD_PRELOAD=", 11) == 0) {
            if (strstr(*env + 11, "libtermux-exec.so") != NULL) {
                return 1;
            }
        }
    }
    return 0;
}

int execve(const char *filename, char *const argv[], char *const envp[]) {
    ensure_real_execve();

    if (!real_execve) {
        LOGE("real_execve is NULL");
        errno = ENOENT;
        return -1;
    }

    /* Not a Termux path — pass through unchanged */
    if (!is_termux_path(filename)) {
        return real_execve(filename, argv, envp);
    }

    LOGD("Intercepting execve: %s", filename);

    /* Check if it's a script file */
    if (is_script_file(filename)) {
        /*
         * SELinux blocks execute_no_trans for scripts in app_data_file.
         * Redirect to /system/bin/sh which has proper SELinux context.
         *
         * Build new argv: ["/system/bin/sh", script_path, orig_argv[2]..., NULL]
         */
        LOGD("Script detected, redirecting to %s", SYSTEM_SH);

        int argc = 0;
        while (argv && argv[argc]) argc++;

        char **new_argv = (char **)malloc((argc + 1) * sizeof(char *));
        if (!new_argv) {
            LOGE("malloc failed for script redirect");
            errno = ENOMEM;
            return -1;
        }

        new_argv[0] = SYSTEM_SH;
        new_argv[1] = (char *)filename;
        for (int i = 2; i < argc; i++) {
            new_argv[i] = argv[i];
        }
        new_argv[argc] = NULL;

        int ret = real_execve(SYSTEM_SH, new_argv, envp);
        LOGE("execve(%s) for script failed: %s", SYSTEM_SH, strerror(errno));
        free(new_argv);
        return ret;
    }

    /*
     * ELF binary — inject LD_PRELOAD so child processes also get this shim.
     * This ensures nested execve() calls (bash → script → etc) are all intercepted.
     */
    if (ld_preload_has_shim(envp)) {
        LOGD("LD_PRELOAD already set, passing through");
        return real_execve(filename, argv, envp);
    }

    char **new_envp = build_enhanced_envp(envp, NULL);
    if (!new_envp) {
        errno = ENOMEM;
        return -1;
    }

    LOGD("Injecting LD_PRELOAD for ELF: %s", filename);

    int ret = real_execve(filename, argv, new_envp);
    LOGE("execve(%s) failed: %s", filename, strerror(errno));
    free(new_envp);
    return ret;
}

/* Intercept execvp too — same logic but filename comes from PATH search */
int execvp(const char *file, char *const argv[]) {
    ensure_real_execve();

    if (!real_execve) {
        typedef int (*execvp_func_t)(const char *, char *const *);
        execvp_func_t real_execvp = (execvp_func_t)dlsym(RTLD_NEXT, "execvp");
        if (real_execvp) return real_execvp(file, argv);
        errno = ENOENT;
        return -1;
    }

    /* execvp searches PATH — we can't easily intercept without knowing the resolved path */
    typedef int (*execvp_func_t)(const char *, char *const *);
    execvp_func_t real_execvp = (execvp_func_t)dlsym(RTLD_NEXT, "execvp");
    if (real_execvp) return real_execvp(file, argv);
    errno = ENOENT;
    return -1;
}
