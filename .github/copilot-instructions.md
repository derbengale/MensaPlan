# Workspace instructions

## Deploy changes to the Wear OS watch

- After every workspace change, build the debug app, install it on the connected watch, and start it. Do not stop after compiling.
- On Windows, run `gradlew.bat :app:installDebug --console=plain` from the workspace root. If `JAVA_HOME` is unset, use Android Studio's bundled JBR at `C:\Program Files\Android\Android Studio\jbr`.
- Confirm the watch is listed as `device` by `adb devices -l` before deployment. Use the Android SDK's `platform-tools\adb.exe` if `adb` is not on `PATH`.
- After installation, launch the app with `adb shell monkey -p com.watchyourself.mensaplan 1`.
- Confirm the install and launch succeeded before reporting completion. If the build fails or no watch is connected, do not claim deployment; report the blocker explicitly.