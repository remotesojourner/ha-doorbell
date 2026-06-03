# Build Instructions

These instructions are intended for reproducible builds.

## Prerequisites
- **Java Development Kit (JDK)**: Version 17
- **Android SDK**: Build Tools and SDK platforms as defined in `app/build.gradle.kts`
- **Gradle**: Will be automatically downloaded via the Gradle wrapper (`gradlew`).

## Clone the Repository
```bash
git clone https://github.com/remotesojourner/ha-doorbell.git
cd ha-doorbell
```

## Build the Application
This project does not require any proprietary services (like Google Mobile Services) or specific API keys baked in.
To build a release APK, run the following command:

```bash
./gradlew assembleRelease
```

The resulting unsigned (or debug-signed if not specifying keys) APK will be located at:
`app/build/outputs/apk/release/app-release-unsigned.apk`

## Versioning
This project utilizes [GitVersion](https://gitversion.net/) for semantic versioning within GitHub Actions.
To manually set the version during a local build, you can pass the version properties directly to Gradle:

```bash
./gradlew assembleRelease -PversionCode=123 -PversionName=1.2.3
```

## Signing the APK
To sign the application with your own keystore:
1. Generate a keystore if you don't have one:
```bash
keytool -genkey -v -keystore my-release-key.keystore -alias my-key-alias -keyalg RSA -keysize 2048 -validity 10000
```
2. Sign the APK using `apksigner` (from Android SDK Build-Tools):
```bash
apksigner sign --ks my-release-key.keystore --out app-release.apk app/build/outputs/apk/release/app-release-unsigned.apk
```
