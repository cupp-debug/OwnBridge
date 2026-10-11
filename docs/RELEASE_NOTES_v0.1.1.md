# OwnBridge v0.1.1

Maintenance release for more reliable deployment on Ubuntu VPS images.

## Fixes

- Keep remote deployment and subscription scripts in LF format on every build, including Windows builds. A build-time check prevents CRLF scripts from being packaged again.
- Treat UFW as optional: servers without UFW can proceed, while an existing active UFW still receives the selected protocols' required port rules. OwnBridge does not install, enable, or disable UFW.
- Allow recognized tasks blocked before protocol deployment by the earlier bundle to retry with the corrected package. Original package files are preserved, and tasks with protocol work already started are not rewritten by this recovery.
- Include the remote command's failure details when an early deployment step fails.

## Download and compatibility

- APK: `OwnBridge-v0.1.1-arm64.apk`
- Android 8.0 or newer; `arm64-v8a` only
- Application ID: `app.ownbridge`; version code: `4`
- Non-debuggable release build; SS, VLESS Reality, and Hysteria 2 deployment remains enabled
- Uses the same release signing certificate as v0.1.0; supports installation over that version without clearing local app data
- Source: [v0.1.1 tag](https://github.com/cupp-debug/OwnBridge/tree/v0.1.1); [build instructions](https://github.com/cupp-debug/OwnBridge/blob/v0.1.1/docs/BUILDING.md)
- License: GPL-3.0-only; third-party notices and license texts remain included

The deployment fixes passed three reported end-to-end manual tests before publication. Core and Android unit tests, deployment regression tests, release compilation, vital lint, APK signature checks, and installation/startup checks were also performed. This does not imply that every VPS, network, or client combination has been validated; report reproducible problems through GitHub Issues.

**Security:** The VPS-hosted subscription URL currently uses HTTP and contains proxy credentials. Treat it as a secret. The Android app does not save the VPS root password.

APK SHA-256: `48694A28A6AFC551BADD46799349A826436C3C9B1A005185A567C7D6F3B4BABE`

Signing certificate SHA-256: `B63E792CFEA385BED335B208C704BCE793D12C133D9822235BFED78E825867B6`
