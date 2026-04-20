#include <dirent.h>
#include <fcntl.h>
#include <jni.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>
#include <android/log.h>

#define TERMUX_UNUSED(x) x __attribute__((__unused__))
#ifdef __APPLE__
# define LACKS_PTSNAME_R
#endif

#define LOG_TAG "termux-native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static int throw_runtime_exception(JNIEnv* env, char const* message)
{
    jclass exClass = (*env)->FindClass(env, "java/lang/RuntimeException");
    (*env)->ThrowNew(env, exClass, message);
    return -1;
}

static int create_subprocess(JNIEnv* env,
        char const* cmd,
        char const* cwd,
        char* const argv[],
        char** envp,
        int* pProcessId,
        jint rows,
        jint columns,
        jint cell_width,
        jint cell_height)
{
    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (ptm < 0) return throw_runtime_exception(env, "Cannot open /dev/ptmx");

#ifdef LACKS_PTSNAME_R
    char* devname;
#else
    char devname[64];
#endif
    if (grantpt(ptm) || unlockpt(ptm) ||
#ifdef LACKS_PTSNAME_R
            (devname = ptsname(ptm)) == NULL
#else
            ptsname_r(ptm, devname, sizeof(devname))
#endif
       ) {
        return throw_runtime_exception(env, "Cannot grantpt()/unlockpt()/ptsname_r() on /dev/ptmx");
    }

    // Enable UTF-8 mode and disable flow control to prevent Ctrl+S from locking up the display.
    struct termios tios;
    tcgetattr(ptm, &tios);
    tios.c_iflag |= IUTF8;
    tios.c_iflag &= ~(IXON | IXOFF);
    tcsetattr(ptm, TCSANOW, &tios);

    /** Set initial winsize. */
    struct winsize sz = { .ws_row = (unsigned short) rows, .ws_col = (unsigned short) columns, .ws_xpixel = (unsigned short) (columns * cell_width), .ws_ypixel = (unsigned short) (rows * cell_height)};
    ioctl(ptm, TIOCSWINSZ, &sz);

    pid_t pid = fork();
    if (pid < 0) {
        return throw_runtime_exception(env, "Fork failed");
    } else if (pid > 0) {
        *pProcessId = (int) pid;
        return ptm;
    } else {
        // Clear signals which the Android java process may have blocked:
        sigset_t signals_to_unblock;
        sigfillset(&signals_to_unblock);
        sigprocmask(SIG_UNBLOCK, &signals_to_unblock, 0);

        close(ptm);
        setsid();

        int pts = open(devname, O_RDWR);
        if (pts < 0) exit(-1);

        dup2(pts, 0);
        dup2(pts, 1);
        dup2(pts, 2);

        DIR* self_dir = opendir("/proc/self/fd");
        if (self_dir != NULL) {
            int self_dir_fd = dirfd(self_dir);
            struct dirent* entry;
            while ((entry = readdir(self_dir)) != NULL) {
                int fd = atoi(entry->d_name);
                if (fd > 2 && fd != self_dir_fd) close(fd);
            }
            closedir(self_dir);
        }

        // Guardar TERMUX_NATIVE_LIB_DIR ANTES de clearenv (para termux-exec)
        char* saved_native_lib_dir = getenv("TERMUX_NATIVE_LIB_DIR");
        char saved_native_lib_dir_buf[512] = {0};
        if (saved_native_lib_dir) {
            strncpy(saved_native_lib_dir_buf, saved_native_lib_dir, sizeof(saved_native_lib_dir_buf) - 1);
        }

        // Debug: log envp BEFORE clearenv
        {
            int env_count = 0;
            char** tmp = envp;
            while (tmp && *tmp) { env_count++; tmp++; }
            char msg[256];
            snprintf(msg, sizeof(msg), "[termux.c] envp received: %d vars", env_count);
            __android_log_print(ANDROID_LOG_INFO, "termux-native", "%s", msg);
            // Log first 3 env vars
            for (int i = 0; i < 3 && envp && envp[i]; i++) {
                __android_log_print(ANDROID_LOG_INFO, "termux-native", "[termux.c] env[%d] = %s", i, envp[i]);
            }
        }

        clearenv();
        if (envp) for (; *envp; ++envp) putenv(*envp);

        // Force set environment variables
        setenv("PREFIX", "/data/data/com.termux.alpa_termuxkit/files/usr", 1);
        setenv("HOME", "/data/data/com.termux.alpa_termuxkit/files/home", 1);
        setenv("PATH", "/data/data/com.termux.alpa_termuxkit/files/usr/bin:/system/bin", 1);
        setenv("LD_LIBRARY_PATH", "/data/data/com.termux.alpa_termuxkit/files/usr/lib", 1);
        setenv("TMPDIR", "/data/data/com.termux.alpa_termuxkit/files/usr/tmp", 1);
        setenv("TERM", "xterm-256color", 1);
        setenv("LANG", "en_US.UTF-8", 1);

        // Debug: log the PATH
        {
            char* path = getenv("PATH");
            if (path) {
                char msg[1024];
                snprintf(msg, sizeof(msg), "[termux.c] PATH=%s", path);
                __android_log_print(ANDROID_LOG_INFO, "termux-native", "%s", msg);
            } else {
                __android_log_print(ANDROID_LOG_ERROR, "termux-native", "PATH not set after clearenv+putenv!");
            }
        }

        // Construir un envp explícito para execve — no usar execvp que depende del estado global
        // Recopilar todas las variables actuales del environment después de putenv()
        extern char** environ;
        int final_env_count = 0;
        for (char** e = environ; *e; e++) final_env_count++;

        // Agregar LD_PRELOAD al count si aplica
        if (saved_native_lib_dir_buf[0] != '\0') final_env_count++;

        char** final_envp = (char**)malloc((final_env_count + 1) * sizeof(char*));
        int idx = 0;
        for (char** e = environ; *e; e++) {
            final_envp[idx++] = *e;
        }

        // Agregar LD_PRELOAD si no estaba ya
        if (saved_native_lib_dir_buf[0] != '\0') {
            char ld_preload_buf[512];
            snprintf(ld_preload_buf, sizeof(ld_preload_buf),
                     "LD_PRELOAD=%s/libtermux-exec.so", saved_native_lib_dir_buf);
            final_envp[idx++] = strdup(ld_preload_buf);
        }
        final_envp[idx] = NULL;

        // Debug: verificar que PATH está en el env final
        char* final_path = NULL;
        for (char** e = final_envp; *e; e++) {
            if (strncmp(*e, "PATH=", 5) == 0) {
                final_path = *e + 5;
                break;
            }
        }
        if (final_path) {
            char msg[512];
            snprintf(msg, sizeof(msg), "[termux.c] execve con PATH=%s", final_path);
            __android_log_print(ANDROID_LOG_INFO, "termux-native", "%s", msg);
        } else {
            __android_log_print(ANDROID_LOG_ERROR, "termux-native", "[termux.c] PATH NO ENCONTRADO en env final!");
        }

        // Fix SELinux: si cmd es un script (shebang #!), ejecutarlo via /system/bin/sh
        // porque SELinux bloquea execute_no_trans para archivos en app_data_file.
        {
            FILE *check_f = fopen(cmd, "r");
            if (check_f) {
                char hdr[2];
                if (fread(hdr, 1, 2, check_f) == 2 && hdr[0] == '#' && hdr[1] == '!') {
                    __android_log_print(ANDROID_LOG_INFO, "termux-native",
                        "[termux.c] Script detectado: %s, ejecutando via /system/bin/sh", cmd);
                    // Construir nuevo argv: [sh, script, argv[1]..., NULL]
                    int arg_count = 0;
                    while (argv && argv[arg_count]) arg_count++;
                    char **sh_argv = (char**)malloc((arg_count + 2) * sizeof(char*));
                    sh_argv[0] = "/system/bin/sh";
                    sh_argv[1] = (char*)cmd;
                    for (int i = 1; i < arg_count; i++) {
                        sh_argv[i + 1] = argv[i];
                    }
                    sh_argv[arg_count + 1] = NULL;
                    execve("/system/bin/sh", sh_argv, final_envp);
                    perror("execve(/system/bin/sh) for script failed");
                    free(sh_argv);
                }
                fclose(check_f);
            }
        }

        // Usar execve con envp explícito en lugar de execvp
        execve(cmd, (char* const*)argv, final_envp);

        // Si falla (SELinux bloquea app_data_file), intentar via el linker de Android
        {
            struct stat st;
            if (stat(cmd, &st) == 0) {
                const char* linker = "/system/bin/linker64";

                // Construir nuevo argv con el linker
                int arg_count = 0;
                while (argv && argv[arg_count]) arg_count++;

                char** linker_argv = (char**)malloc((arg_count + 3) * sizeof(char*));
                linker_argv[0] = (char*)linker;
                linker_argv[1] = (char*)cmd;
                for (int i = 0; i < arg_count; i++) {
                    linker_argv[i + 2] = argv[i];
                }
                linker_argv[arg_count + 2] = NULL;

                perror("exec() failed, trying via Android linker64");
                fflush(stderr);

                execve(linker, linker_argv, final_envp);
                perror("execve(linker64) also failed");
                fflush(stderr);
                free(linker_argv);
            }
        }

        // Liberar final_envp array. 
        // We do NOT free the elements because most are pointers to the system's environ.
        free(final_envp);

        // Show terminal output about failing exec() call:
        char* error_message;
        if (asprintf(&error_message, "exec(\"%s\")", cmd) == -1) error_message = "exec()";
        perror(error_message);
        _exit(1);
    }
}

JNIEXPORT jint JNICALL Java_com_termux_terminal_JNI_createSubprocess(
        JNIEnv* env,
        jclass TERMUX_UNUSED(clazz),
        jstring cmd,
        jstring cwd,
        jobjectArray args,
        jobjectArray envVars,
        jintArray processIdArray,
        jint rows,
        jint columns,
        jint cell_width,
        jint cell_height)
{
    LOGI("createSubprocess llamado");

    jsize size = args ? (*env)->GetArrayLength(env, args) : 0;
    char** argv = NULL;
    if (size > 0) {
        argv = (char**) malloc((size + 1) * sizeof(char*));
        if (!argv) return throw_runtime_exception(env, "Couldn't allocate argv array");
        for (int i = 0; i < size; ++i) {
            jstring arg_java_string = (jstring) (*env)->GetObjectArrayElement(env, args, i);
            char const* arg_utf8 = (*env)->GetStringUTFChars(env, arg_java_string, NULL);
            if (!arg_utf8) return throw_runtime_exception(env, "GetStringUTFChars() failed for argv");
            argv[i] = strdup(arg_utf8);
            (*env)->ReleaseStringUTFChars(env, arg_java_string, arg_utf8);
        }
        argv[size] = NULL;
    }

    size = envVars ? (*env)->GetArrayLength(env, envVars) : 0;
    char** envp = NULL;
    if (size > 0) {
        envp = (char**) malloc((size + 1) * sizeof(char *));
        if (!envp) return throw_runtime_exception(env, "malloc() for envp array failed");
        for (int i = 0; i < size; ++i) {
            jstring env_java_string = (jstring) (*env)->GetObjectArrayElement(env, envVars, i);
            char const* env_utf8 = (*env)->GetStringUTFChars(env, env_java_string, 0);
            if (!env_utf8) return throw_runtime_exception(env, "GetStringUTFChars() failed for env");
            envp[i] = strdup(env_utf8);
            (*env)->ReleaseStringUTFChars(env, env_java_string, env_utf8);
        }
        envp[size] = NULL;
    }

    int procId = 0;
    char const* cmd_cwd = (*env)->GetStringUTFChars(env, cwd, NULL);
    char const* cmd_utf8 = (*env)->GetStringUTFChars(env, cmd, NULL);
    int ptm = create_subprocess(env, cmd_utf8, cmd_cwd, argv, envp, &procId, rows, columns, cell_width, cell_height);
    (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf8);
    (*env)->ReleaseStringUTFChars(env, cmd, cmd_cwd);

    if (argv) {
        for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
        free(argv);
    }
    if (envp) {
        for (char** tmp = envp; *tmp; ++tmp) free(*tmp);
        free(envp);
    }

    int* pProcId = (int*) (*env)->GetPrimitiveArrayCritical(env, processIdArray, NULL);
    if (!pProcId) return throw_runtime_exception(env, "JNI call GetPrimitiveArrayCritical(processIdArray, &isCopy) failed");

    *pProcId = procId;
    (*env)->ReleasePrimitiveArrayCritical(env, processIdArray, pProcId, 0);

    LOGI("createSubprocess: ptm=%d, pid=%d", ptm, procId);
    return ptm;
}

JNIEXPORT void JNICALL Java_com_termux_terminal_JNI_setPtyWindowSize(JNIEnv* TERMUX_UNUSED(env), jclass TERMUX_UNUSED(clazz), jint fd, jint rows, jint cols, jint cell_width, jint cell_height)
{
    struct winsize sz = { .ws_row = (unsigned short) rows, .ws_col = (unsigned short) cols, .ws_xpixel = (unsigned short) (cols * cell_width), .ws_ypixel = (unsigned short) (rows * cell_height) };
    ioctl(fd, TIOCSWINSZ, &sz);
}

JNIEXPORT void JNICALL Java_com_termux_terminal_JNI_setPtyUTF8Mode(JNIEnv* TERMUX_UNUSED(env), jclass TERMUX_UNUSED(clazz), jint fd)
{
    struct termios tios;
    tcgetattr(fd, &tios);
    if ((tios.c_iflag & IUTF8) == 0) {
        tios.c_iflag |= IUTF8;
        tcsetattr(fd, TCSANOW, &tios);
    }
}

JNIEXPORT jint JNICALL Java_com_termux_terminal_JNI_waitFor(JNIEnv* TERMUX_UNUSED(env), jclass TERMUX_UNUSED(clazz), jint pid)
{
    int status;
    waitpid(pid, &status, 0);
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        return -WTERMSIG(status);
    } else {
        return 0;
    }
}

JNIEXPORT void JNICALL Java_com_termux_terminal_JNI_close(JNIEnv* TERMUX_UNUSED(env), jclass TERMUX_UNUSED(clazz), jint fileDescriptor)
{
    close(fileDescriptor);
}
