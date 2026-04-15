# Alpa TermuxKit

Terminal emulador para Android con backend nativo en Rust.

## 🏗️ Arquitectura

```
Alpa TermuxKit/
├── app/                          # Módulo principal de la aplicación
│   └── src/main/
│       ├── java/                 # Código Java/Kotlin
│       │   └── com/termux/alpa_termuxkit/
│       │       ├── data/         # Capa de datos
│       │       ├── domain/       # Lógica de negocio
│       │       └── di/           # Inyección de dependencias
│       └── kotlin/               # Código Kotlin (Jetpack Compose)
│           └── com/termux/alpa_termuxkit/
│               ├── ui/           # Componentes UI
│               └── presentation/ # ViewModels y estados
├── core/                         # Módulo de funcionalidades compartidas
├── ui/                           # Módulo de componentes UI reutilizables
└── rust_engine/                  # Motor nativo en Rust
    ├── src/
    │   └── lib.rs                # Implementación de Unix Domain Sockets
    ├── .cargo/
    │   └── config.toml           # Configuración cross-compilation
    └── Cargo.toml                # Dependencias Rust
```

## 🔧 Requisitos

- Android Studio Hedgehog (2023.1.1) o superior
- JDK 17
- Android SDK 29+ (Android 10+)
- Rust toolchain con targets Android:
  ```bash
  rustup target add aarch64-linux-android
  rustup target add x86_64-linux-android
  ```

## 🚀 Build

### Compilar Rust
```bash
cd rust_engine
cargo build --release --target aarch64-linux-android
```

### Compilar Android
```bash
./gradlew assembleDebug
# o para release
./gradlew assembleRelease
```

## 📋 Características

- **Backend Rust**: Reemplazo del C++ legacy (local-socket.cpp)
- **Jetpack Compose**: UI moderna y declarativa
- **Hilt**: Inyección de dependencias
- **Coroutines**: I/O asíncrono
- **Android 10+**: Sin soporte legacy

## 📄 Licencia

MIT License
