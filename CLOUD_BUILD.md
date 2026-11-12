# Browser-based build via GitHub Actions

Repository target: `Thygoo/Wii-Remote-Emulator`.

1. Unzip `WiiRemoteAndroidPrototype_v0.3_FullSource.zip` on your computer.
2. Open https://github.com/Thygoo/Wii-Remote-Emulator (Code tab).
3. Choose **Add file → Upload files** and drag in the `app` folder from the unzipped project (and any other changed files you want updated). Keep the `app/src/main/java/org/example/wiiremote/...` paths intact. GitHub will usually show changes to existing files. Commit to `main`.
4. Important minimum updates: `app/src/main/java/org/example/wiiremote/MainActivity.kt`, `WiiHidSession.kt`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`.
5. The repo already has a working `.github/workflows/build-apk.yml`. You can **leave the existing workflow unchanged**; the new Kotlin project uses the same SDK and Gradle settings. (The archive also contains an equivalent current workflow for new repositories.)
6. Open Actions, view the new **Build Android APK** run, download `WiiRemote-Android-APK` from Artifacts. Extract `app-debug.apk` and install it.
7. If GitHub's browser upload refuses to replace existing files, use the GitHub web editor https://github.dev/Thygoo/Wii-Remote-Emulator to edit those four files, using the files in the unzipped project as the content, then Source Control → Commit & Push.

If build fails, inspect the Build debug APK step for actual Kotlin compiler diagnostics. This package has not yet been built with the Android SDK in the packaging environment.
