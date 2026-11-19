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

## Releasing
You never need to push tags by hand. The **Build Release APK** workflow creates the tag and the GitHub release itself:

- **Every push to `main`** that changes the app is released automatically. The version follows the commit messages since the last release: `feat:` bumps the minor version, `fix:`, `perf:`, `refactor:` and `revert:` bump the patch version, and a `!` or `BREAKING CHANGE:` bumps the major version. Pushes that only change documentation, blueprints, screenshots, Fastlane metadata, assets or workflows are not built.
- **To release on demand**, open **Actions → Build Release APK → Run workflow**, choose `main`, tick **Publish a GitHub release**, and pick a **Version bump**:
  - `auto` uses the version worked out from the commit messages.
  - `patch`, `minor` or `major` bump the latest release tag, for example `v0.5.1` becomes `v0.5.2`, `v0.6.0` or `v1.0.0`.

  The run fails without releasing if the version has already been released, for example `auto` with no new commits; run it again with a bump. Later automatic releases continue from the new version.

Running the workflow without **Publish a GitHub release** only builds and tests, and uploads the APK as a workflow artifact.

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
