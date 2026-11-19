# The decoder engine boundary is a Kotlin interface; the one JNI class is kept by name below.

# The Rust core's JNI entry points: the library binds Java_app_scanner_engine_NativeCore_version and
# Java_app_scanner_engine_NativeCore_analyzeFrame, so R8 must keep the class name and the native method names.
# AGP's optimizing defaults keep every class with native methods; this states it for the class the core needs, and
# ReleaseConfigurationTest pins the rule against the names in the prebuilt libraries (the security pass, 2026-09-06).
-keepclasseswithmembernames class app.scanner.engine.NativeCore { native <methods>; }

# EdDSA (net.i2p.crypto) references a JDK-only key class in a branch that never runs on Android.
-dontwarn sun.security.x509.X509Key
