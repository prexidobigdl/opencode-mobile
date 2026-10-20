CI injects the OpenCode engine here (from the `engine-linux-arm64-musl` GitHub Actions
artifact, renamed to libopencode.so during the android.yml build):

jniLibs/arm64-v8a/libopencode.so

Because the file is named lib*.so and app/build.gradle.kts sets useLegacyPackaging=true,
Android extracts it to nativeLibraryDir with the exec bit, and EngineRunner exec()s it.
Until a real artifact is dropped in, the APK builds fine but the engine start fails with
a clear "Missing engine binary" error.
