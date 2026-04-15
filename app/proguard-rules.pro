# Alpa TermuxKit - Proguard Rules

# Keep Rust JNI native methods
-keep class com.termux.shared.net.socket.local.LocalSocketManager {
    public native *;
}

# Keep JniResult model
-keep class com.termux.shared.jni.models.JniResult {
    *;
}

# Keep PeerCred model
-keep class com.termux.shared.net.socket.local.PeerCred {
    *;
}

# Keep native method signatures
-keepclasseswithmembernames class * {
    native <methods>;
}
