# Build and install SpriteForge on Android

A debug APK is built automatically whenever code is pushed to this repository. You can also start a build manually from GitHub Actions.

## Download the APK

1. Open the repository on GitHub and tap **Actions**.
2. Open the latest successful **Build Android APK** run.
3. Under **Artifacts**, download **SpriteForge-debug-apk**. It downloads as a ZIP file; extract `app-debug.apk` from it.
4. Open the APK on your Android device and follow the install prompt. Android may ask you to allow installs from your browser or file manager; only allow this for the app you used to open the APK.

The downloadable build is a debug build for testing, not a Play Store release. GitHub keeps each APK artifact for 14 days.

## Firebase-powered features

The project does not currently include `app/google-services.json` or a Firebase project configuration. The build workflow can compile the app without those credentials, but sprite analysis through Firebase AI may not work until Firebase is set up and configured for this Android app. Do not put private credentials in public files or commit them to the repository.
