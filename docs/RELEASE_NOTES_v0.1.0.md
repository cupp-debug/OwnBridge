# OwnBridge v0.1.0

First non-preview Android release. OwnBridge helps deploy self-hosted connection nodes on an Ubuntu VPS that you control; it does not supply a VPS or account.

- APK: `OwnBridge-v0.1.0-arm64.apk`
- Android 8.0 or newer; `arm64-v8a` only
- Application ID: `app.ownbridge`; version code: `2`
- Non-debuggable release build with SS, VLESS Reality, and Hysteria 2 deployment enabled
- Clash/Mihomo configuration and subscription URL, plus v2rayN/v2rayNG importable node links
- Source: this release tag; build instructions in [`docs/BUILDING.md`](https://github.com/cupp-debug/OwnBridge/blob/v0.1.0/docs/BUILDING.md)
- License: GPL-3.0-only; third-party notices and source references are in the repository, and license texts are bundled in the APK

The About page now links directly to the public source repository and states the license. The obsolete server-purchase recommendation has been removed from the introduction. This release uses the same signing certificate as `v0.1.0-preview.1`; the signed APK was installed over that preview on an OPPO test device without removing local app data. Automated core and Android unit tests, release compilation, vital lint, APK signature verification, and a launch/About-page smoke check passed. Earlier test devices exercised the deployment flow against Ubuntu VPS instances. Not every VPS, network, or client combination has been validated; report reproducible problems through GitHub Issues.

**Security:** The VPS-hosted subscription URL currently uses HTTP and contains proxy credentials. Treat it as a secret. The Android app does not save the VPS root password.

APK SHA-256: `106FF45209DCAB8B7B77757F06372928DC4673D24BDB028B6E5D709A257D6195`

Signing certificate SHA-256: `B63E792CFEA385BED335B208C704BCE793D12C133D9822235BFED78E825867B6`
