//! Termux Rust Engine — PTY I/O puro (read/write loop only)
//!
//! Arquitectura:
//! - C nativo (termux.c) hace fork/exec y devuelve el FD del PTY master
//! - Rust lee bytes del master PTY y los envia a Kotlin via onPtyData()
//! - Rust escribe bytes del usuario al master PTY

use jni::objects::{GlobalRef, JByteArray, JClass, JObject, JString, JValue};
use jni::sys::{jint, jobject, jvalue, jmethodID};
use jni::JNIEnv;
use jni::JavaVM;
use std::ffi::c_char;
use std::os::raw::c_int;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;

// ============================================================================
// Constantes
// ============================================================================

pub const DEFAULT_COLS: u16 = 80;
pub const DEFAULT_ROWS: u16 = 24;

// ============================================================================
// Estado Global
// ============================================================================

static mut G_JVM: Option<Arc<JavaVM>> = None;
static mut G_MASTER_FD: c_int = -1;
static mut G_CHILD_PID: c_int = -1;
static mut G_IS_RUNNING: AtomicBool = AtomicBool::new(false);

// JNI callback cacheados (desde el main thread)
static mut G_TERMINAL_MANAGER_CLASS: Option<GlobalRef> = None;
static mut G_ON_PTY_DATA_METHOD: Option<jmethodID> = None;

// ============================================================================
// C bindings (libc en Android) — SOLO read/write/close
// ============================================================================

extern "C" {
    fn read(fd: c_int, buf: *mut u8, count: usize) -> isize;
    fn write(fd: c_int, buf: *const u8, count: usize) -> isize;
    fn close(fd: c_int) -> c_int;
    fn kill(pid: c_int, sig: c_int) -> c_int;
    fn fcntl(fd: c_int, cmd: c_int, ...) -> c_int;
}

fn get_errno() -> c_int {
    extern "C" { fn __errno() -> *mut c_int; }
    unsafe { *__errno() }
}

const F_GETFD: c_int = 1;
const F_SETFD: c_int = 2;
const FD_CLOEXEC: c_int = 1;
const SIGTERM: c_int = 15;
const EINTR: c_int = 4;
const EIO: c_int = 5;
const EAGAIN: c_int = 11;
const EWOULDBLOCK: c_int = 11;

// ============================================================================
// JniResult helper
// ============================================================================

#[derive(Debug)]
struct JniResultData {
    retval: jint,
    errno: jint,
    errmsg: String,
    int_data: jint,
}

impl JniResultData {
    fn success_with_int(v: jint) -> Self {
        Self { retval: 0, errno: 0, errmsg: String::new(), int_data: v }
    }
    fn error(retval: jint, errno: jint, errmsg: impl Into<String>) -> Self {
        Self { retval, errno, errmsg: errmsg.into(), int_data: 0 }
    }
    fn to_java(&self, env: &mut JNIEnv<'_>) -> jobject {
        let class = match env.find_class("com/termux/shared/jni/models/JniResult") {
            Ok(c) => c,
            Err(e) => {
                log_error(&format!("FindClass JniResult failed: {:?}", e));
                return JObject::null().into_raw();
            }
        };
        let errmsg_obj = env.new_string(&self.errmsg).unwrap_or_else(|_| {
            env.new_string("error").unwrap()
        });
        let obj = env.new_object(
            class,
            "(IILjava/lang/String;I)V",
            &[
                JValue::Int(self.retval),
                JValue::Int(self.errno),
                JValue::Object(&JObject::from(errmsg_obj)),
                JValue::Int(self.int_data),
            ],
        );
        match obj {
            Ok(o) => o.into_raw(),
            Err(_) => {
                log_error("NewObject JniResult failed");
                JObject::null().into_raw()
            }
        }
    }
}

// ============================================================================
// Logging helpers (android_log_print)
// ============================================================================

#[link(name = "log")]
extern "C" {
    fn __android_log_print(prio: c_int, tag: *const c_char, fmt: *const c_char, ...) -> c_int;
}

const ANDROID_LOG_INFO: c_int = 4;
const ANDROID_LOG_ERROR: c_int = 6;

fn log_info(msg: &str) {
    unsafe {
        let tag = std::ffi::CString::new("termux-rust-engine").unwrap();
        let fmt = std::ffi::CString::new("%s").unwrap();
        let c_msg = std::ffi::CString::new(msg).unwrap();
        __android_log_print(ANDROID_LOG_INFO, tag.as_ptr(), fmt.as_ptr(), c_msg.as_ptr());
    }
}

fn log_error(msg: &str) {
    unsafe {
        let tag = std::ffi::CString::new("termux-rust-engine").unwrap();
        let fmt = std::ffi::CString::new("%s").unwrap();
        let c_msg = std::ffi::CString::new(msg).unwrap();
        __android_log_print(ANDROID_LOG_ERROR, tag.as_ptr(), fmt.as_ptr(), c_msg.as_ptr());
    }
}

// ============================================================================
// Cache JNI callback (llamado desde el main thread)
// ============================================================================

fn cache_pty_data_callback(env: &mut JNIEnv<'_>) -> bool {
    let class = match env.find_class("com/termux/shared/jni/TerminalManager") {
        Ok(c) => c,
        Err(e) => {
            log_error(&format!("FindClass TerminalManager failed: {:?}", e));
            return false;
        }
    };

    let global = match env.new_global_ref(&class) {
        Ok(g) => g,
        Err(e) => {
            log_error(&format!("NewGlobalRef TerminalManager failed: {:?}", e));
            return false;
        }
    };

    unsafe {
        G_TERMINAL_MANAGER_CLASS = Some(global);
    }

    let class_raw = env.find_class("com/termux/shared/jni/TerminalManager").unwrap();
    let name = std::ffi::CString::new("onPtyData").unwrap();
    let sig = std::ffi::CString::new("(I[B)V").unwrap();

    let method_raw = unsafe {
        let jni_ptr = env.get_native_interface();
        let get_method_id = (*(*jni_ptr)).GetStaticMethodID.unwrap();
        get_method_id(jni_ptr, class_raw.as_raw(), name.as_ptr(), sig.as_ptr())
    };

    if method_raw.is_null() {
        log_error("❌ GetStaticMethodID returned null for onPtyData");
        return false;
    }

    unsafe {
        G_ON_PTY_DATA_METHOD = Some(method_raw);
    }

    log_info("✅ JNI callback cacheado (class + method)");
    true
}

// ============================================================================
// Send bytes to Kotlin via cached JNI callback
// ============================================================================

fn send_bytes_to_kotlin(data: &[u8]) {
    let jvm = unsafe {
        match G_JVM.as_ref() {
            Some(vm) => Arc::clone(vm),
            None => {
                log_error("G_JVM is None - cannot send bytes to Kotlin");
                return;
            }
        }
    };

    let mut env = match jvm.attach_current_thread() {
        Ok(e) => e,
        Err(e) => {
            log_error(&format!("AttachCurrentThread failed: {:?}", e));
            return;
        }
    };

    let class_global = unsafe {
        match G_TERMINAL_MANAGER_CLASS.as_ref() {
            Some(c) => c,
            None => {
                log_error("G_TERMINAL_MANAGER_CLASS is None");
                return;
            }
        }
    };

    let method = unsafe {
        match G_ON_PTY_DATA_METHOD {
            Some(m) => m,
            None => {
                log_error("G_ON_PTY_DATA_METHOD is None");
                return;
            }
        }
    };

    let java_bytes = match env.new_byte_array(data.len() as i32) {
        Ok(b) => b,
        Err(e) => {
            log_error(&format!("new_byte_array failed: {:?}", e));
            return;
        }
    };

    let bytes_as_i8: &[i8] = unsafe {
        std::slice::from_raw_parts(data.as_ptr() as *const i8, data.len())
    };
    if let Err(e) = env.set_byte_array_region(&java_bytes, 0, bytes_as_i8) {
        log_error(&format!("set_byte_array_region failed: {:?}", e));
        return;
    }

    let class_raw = class_global.as_raw();
    let args: [jvalue; 2] = [
        jvalue { i: 0i32 },
        jvalue { l: java_bytes.as_raw() },
    ];

    unsafe {
        let jni_ptr = env.get_native_interface();
        let call_static_void = (*(*jni_ptr)).CallStaticVoidMethodA.unwrap();
        call_static_void(jni_ptr, class_raw, method, args.as_ptr());
    }

    if env.exception_check().unwrap_or(false) {
        log_error("⚠️ Pending exception after onPtyData call");
        let _ = env.exception_describe();
        let _ = env.exception_clear();
    }
}

// ============================================================================
// Read loop thread — SOLO lee del FD que C le pasa
// ============================================================================

fn read_pty_loop(master_fd: c_int) {
    log_info("🧵 PTY read loop started");
    let mut buffer = [0u8; 8192];
    let mut eio_retry_count = 0;
    const EIO_MAX_RETRIES: i32 = 50;

    loop {
        if !unsafe { G_IS_RUNNING.load(Ordering::Relaxed) } {
            log_info("🛑 G_IS_RUNNING is false, exiting read loop");
            break;
        }

        let n = unsafe { read(master_fd, buffer.as_mut_ptr(), buffer.len()) };

        if n > 0 {
            eio_retry_count = 0;
            let n = n as usize;
            log_info(&format!("📥 Read {} bytes from PTY, sending to Kotlin", n));
            send_bytes_to_kotlin(&buffer[..n]);
        } else if n == 0 {
            log_info("🛑 EOF on PTY — shell cerro");
            break;
        } else {
            let errno = get_errno();
            if errno == EINTR {
                continue;
            } else if errno == EAGAIN || errno == EWOULDBLOCK {
                continue;
            } else if errno == EIO && eio_retry_count < EIO_MAX_RETRIES {
                eio_retry_count += 1;
                log_info(&format!("⏳ EIO en PTY (retry {}/{})", eio_retry_count, EIO_MAX_RETRIES));
                std::thread::sleep(std::time::Duration::from_millis(100));
                continue;
            } else if errno == EIO {
                log_error(&format!("❌ read() EIO persistente despues de {} reintentos", EIO_MAX_RETRIES));
                break;
            } else {
                log_error(&format!("❌ read() returned error on PTY, errno={}", errno));
                break;
            }
        }
    }

    log_info("🛑 PTY read loop ended");
}

// ============================================================================
// JNI Functions — Rust SOLO maneja read/write del FD que C le pasa
// ============================================================================

#[no_mangle]
pub extern "C" fn Java_com_termux_shared_jni_TerminalManager_startPtySessionNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    _log_title: JString<'_>,
    _shell_path: JString<'_>,
    cols: jint,
    rows: jint,
) -> jobject {
    log_info("========================================");
    log_info("🚀 RUST: startPtySessionNative llamado (modo C fork/exec)");
    log_info(&format!("📐 Size: {}x{}", cols, rows));

    // Save JVM reference
    if let Ok(vm) = env.get_java_vm() {
        unsafe {
            G_JVM = Some(Arc::new(vm));
        }
        log_info("✅ JVM guardada");
    }

    // Cache JNI callback
    if !cache_pty_data_callback(&mut env) {
        log_error("❌ No se pudo cachear el JNI callback");
        return JniResultData::error(-1, 0, "Failed to cache JNI callback").to_java(&mut env);
    }

    // NOTA: El fork/exec lo hace el C nativo (JNI.createSubprocess).
    // Rust solo recibe el FD via JNI.attachPtyFd y arranca el read loop.
    // Esta funcion se mantiene como placeholder para compatibilidad.
    log_info("⚠️ Rust: fork/exec delegado a C nativo (JNI.createSubprocess)");
    log_info("📌 Rust espera a que Kotlin llame a attachPtyFdNative con el FD del C");

    log_info("========================================");

    // Retorna 0 (success) — el FD real se pasa por attachPtyFdNative
    JniResultData::success_with_int(0).to_java(&mut env)
}

#[no_mangle]
pub extern "C" fn Java_com_termux_shared_jni_TerminalManager_attachPtyFdNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    _log_title: JString<'_>,
    master_fd: jint,
    child_pid: jint,
) -> jobject {
    log_info("========================================");
    log_info(&format!("🔗 RUST: attachPtyFdNative llamado, fd={}, pid={}", master_fd, child_pid));

    if master_fd < 0 {
        log_error("❌ FD invalido");
        return JniResultData::error(-1, 0, "Invalid FD").to_java(&mut env);
    }

    // Save JVM reference (necesario para send_bytes_to_kotlin)
    if let Ok(vm) = env.get_java_vm() {
        unsafe {
            G_JVM = Some(Arc::new(vm));
        }
        log_info("✅ JVM guardada");
    }

    // Cache JNI callback
    if !cache_pty_data_callback(&mut env) {
        log_error("❌ No se pudo cachear el JNI callback");
        return JniResultData::error(-1, 0, "Failed to cache JNI callback").to_java(&mut env);
    }

    // Set FD_CLOEXEC para que no se herede en futuros forks
    unsafe {
        let flags = fcntl(master_fd, F_GETFD);
        if flags >= 0 {
            fcntl(master_fd, F_SETFD, flags | FD_CLOEXEC);
        }
    }

    unsafe {
        G_MASTER_FD = master_fd;
        G_CHILD_PID = child_pid;
        G_IS_RUNNING.store(true, Ordering::Relaxed);
    }

    log_info(&format!("💾 Master FD {} guardado, PID {}", master_fd, child_pid));

    // Start read thread
    let read_fd = master_fd;
    thread::spawn(move || {
        read_pty_loop(read_fd);
    });

    log_info("✅ attachPtyFdNative completado");
    log_info("========================================");

    JniResultData::success_with_int(0).to_java(&mut env)
}

#[no_mangle]
pub extern "C" fn Java_com_termux_shared_jni_TerminalManager_writeNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    _log_title: JString<'_>,
    data_array: JByteArray<'_>,
) -> jobject {
    let len = match env.get_array_length(&data_array) {
        Ok(l) => l as usize,
        Err(e) => {
            log_error(&format!("❌ get_array_length failed: {:?}", e));
            return JniResultData::error(-1, 0, "get_array_length failed").to_java(&mut env);
        }
    };

    let mut buf = vec![0u8; len];
    if let Err(e) = env.get_byte_array_region(&data_array, 0, unsafe {
        std::slice::from_raw_parts_mut(buf.as_mut_ptr() as *mut i8, len)
    }) {
        log_error(&format!("❌ get_byte_array_region failed: {:?}", e));
        return JniResultData::error(-1, 0, "get_byte_array_region failed").to_java(&mut env);
    }

    log_info(&format!("📝 writeNative: {} bytes", len));

    let master_fd = unsafe { G_MASTER_FD };
    if master_fd < 0 {
        return JniResultData::error(-1, 0, "No active PTY session").to_java(&mut env);
    }

    let written = unsafe { write(master_fd, buf.as_ptr(), buf.len()) };
    if written < 0 {
        log_error(&format!("❌ write() failed, returned {}", written));
        JniResultData::error(-1, 0, format!("write failed: {}", written)).to_java(&mut env)
    } else {
        log_info(&format!("✅ {} bytes escritos al PTY", written));
        JniResultData::success_with_int(written as jint).to_java(&mut env)
    }
}

#[no_mangle]
pub extern "C" fn Java_com_termux_shared_jni_TerminalManager_nativeFlush(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) {
    // Flush is automatic for PTY, nothing to do
}

#[no_mangle]
pub extern "C" fn Java_com_termux_shared_jni_TerminalManager_closePtySessionNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    _log_title: JString<'_>,
    _session_id: jint,
) -> jobject {
    log_info("🔴 Cerrando sesion PTY...");

    unsafe {
        G_IS_RUNNING.store(false, Ordering::Relaxed);
        if G_CHILD_PID > 0 {
            kill(G_CHILD_PID, SIGTERM);
            log_info(&format!("📤 SIGTERM sent to PID {}", G_CHILD_PID));
        }
        if G_MASTER_FD >= 0 {
            close(G_MASTER_FD);
            G_MASTER_FD = -1;
        }
        G_CHILD_PID = -1;
    }

    log_info("✅ Sesion PTY cerrada");
    JniResultData::success_with_int(0).to_java(&mut env)
}

// ============================================================================
// Init
// ============================================================================

#[no_mangle]
pub extern "C" fn JNI_OnLoad(_vm: jni::JavaVM, _reserved: *mut std::os::raw::c_void) -> jint {
    log_info("🚀 Termux Rust Engine — PTY I/O puro (JNI_OnLoad, C fork/exec mode)");
    jni::sys::JNI_VERSION_1_6
}
