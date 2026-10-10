# OwnBridge v0.1.0-preview.1

First public Android preview. This is experimental software, not a production-ready VPN service. OwnBridge does not provide a VPS or an account.

- APK: `OwnBridge-v0.1.0-preview.1-arm64.apk`
- Android 8.0 or newer; `arm64-v8a` only
- Application ID: `app.ownbridge`; version code: `1`
- Non-debuggable preview build with the composite SS, VLESS Reality, and Hysteria 2 deployment path enabled
- Source: this release tag; build instructions in [`docs/BUILDING.md`](https://github.com/cupp-debug/OwnBridge/blob/v0.1.0-preview.1/docs/BUILDING.md)
- License: GPL-3.0-only; third-party notices and source references are in the repository and license texts are included in the APK

The Android debug build and deployment scripts have automated tests, and earlier test devices exercised the composite path. This newly signed preview package should still be treated as a preview; use a VPS you control and do not assume every VPS, network, or client combination has been validated.

**Security:** The VPS-hosted subscription URL currently uses HTTP and contains proxy credentials. Treat it as a secret. The Android app does not store the VPS root password.

APK SHA-256: `CBEB894603937496EC38D02B60FFF3C1B0BC708F194B13BB55191646D0BF42ED`

Signing certificate SHA-256: `B63E792CFEA385BED335B208C704BCE793D12C133D9822235BFED78E825867B6`
