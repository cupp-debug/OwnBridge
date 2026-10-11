# Building OwnBridge

This repository contains the Android app, the plain Kotlin/JVM `core` module, the deployment scripts packaged into the app, tests, and third-party notices. Public APKs are built from tagged source commits, published as GitHub Release assets, and not committed to Git.

## Prerequisites

- JDK 21 for Gradle (the app's Java bytecode target is 17).
- Android SDK Platform 36 and matching build tools.
- An internet connection for pinned Gradle dependencies and the Mihomo Android bridge.

Set `JAVA_HOME` to JDK 21 and `ANDROID_HOME` to the Android SDK. Use the included Gradle wrapper:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

On Windows, use `gradlew.bat` in place of `./gradlew`. The debug APK is signed with a local Android debug key, has application ID `app.ownbridge.dev`, and must not be redistributed as the public release. The `release` build has application ID `app.ownbridge`, enables the tested composite deployment path, and is **not debuggable**. It is unsigned by default. Its output is under `app/build/outputs/apk/release/` and must be signed with a private release key before installation or distribution. Do not commit a keystore or its password.

Remote deployment and subscription assets must use LF line endings, including Windows checkouts. `.gitattributes` enforces this, and every APK build runs `:app:verifyRemoteScriptLineEndings` before packaging. That check rejects CRLF assets instead of producing an APK with scripts that cannot run on Linux. Do not disable it when preparing a release.

For a self-signed release, first align the unsigned APK with Android SDK `zipalign`, then sign it with `apksigner` and verify it with `apksigner verify --verbose --print-certs`. Keep the signing key and password securely: future updates of the same Android application ID must use the same key and a higher `versionCode`.

## Pinned components and corresponding source

- `app/build.gradle.kts` downloads `libmihomo-android` v0.3.5 from its [upstream release](https://github.com/oviron/libmihomo-android/releases/tag/v0.3.5), checks the AAR SHA-256, and records the wrapper source revision in [third-party notices](../THIRD_PARTY_NOTICES.md). The bridge includes Mihomo v1.19.31; its source is available from [MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo).
- The APK includes versioned deployment scripts from `app/src/main/assets/deployment/` and subscription scripts from `app/src/main/assets/subscription/`.
- VPS deployment fetches an unmodified, version-pinned sing-box executable from [SagerNet/sing-box](https://github.com/SagerNet/sing-box) and checks its published digest. It is not included in the APK.
- License text for OwnBridge and the GPL components is in [LICENSE](../LICENSE) and bundled as an app asset. Other icon license texts are in `third_party_licenses/` and also bundled as assets.

Refer to [third-party notices](../THIRD_PARTY_NOTICES.md) for exact component versions and licenses. This document describes source and build inputs; it does not assert that an APK built on another machine will have an identical byte-for-byte hash.
