# OwnBridge

[English](README.md) · [简体中文](docs/README.zh-CN.md) · [繁體中文](docs/README.zh-TW.md) · [Español](docs/README.es.md) · [Русский](docs/README.ru.md) · [Türkçe](docs/README.tr.md) · [فارسی](docs/README.fa.md)

**Have a VPS? Build your own private VPN.**

OwnBridge is a local deployment tool for Android. Bring an Ubuntu VPS with a public IPv4 address, enter its login details, choose a protocol, and tap to start deployment. OwnBridge handles server setup, node checks, and configuration generation.

Once complete, you can request a Clash/Mihomo subscription URL and get node links ready to import into v2rayN/v2rayNG. No server commands to type. No configuration files to write by hand.

**Your server. Your nodes. From setup to delivery in just a few taps.**

**[⬇ Download Android APK (arm64-v8a)](https://github.com/cupp-debug/OwnBridge/releases/download/v0.1.0/OwnBridge-v0.1.0-arm64.apk)**

## Current status

v0.1.0 is the first non-preview Android release. OwnBridge is not a VPN provider and does not supply servers or accounts. The non-debuggable APK includes the SS, VLESS Reality, and Hysteria 2 deployment path. Not every VPS, network, or client combination has been validated; please report problems through GitHub Issues. The APK is a GitHub Release asset, not a file committed to this repository.

## What it does

- Discovers the VPS over SSH and checks its OS, addresses, listening ports, services, and firewall state before deployment.
- Pins the SSH host key, transfers a versioned deployment bundle with SHA-256 verification, and records remote progress for safe retries.
- Generates Clash/Mihomo YAML and v2rayN/v2rayNG share links from verified deployment results. A manually requested Clash subscription URL is also available.
- Tests each node with a Mihomo HTTP request from the Android device. This is not ICMP ping and does not start an Android VPN.
- Saves verified results and logs in encrypted local history. It does not store the VPS root password.

## Requirements and build

- An Android device running Android 8.0 or newer with `arm64-v8a` support.
- A VPS you are authorized to administer, running Ubuntu 22.04, 24.04, or 26.04 x86_64 with root SSH access. The supported combinations remain subject to the build's current feature gates.
- JDK 21 and an Android SDK with API 36 to build the app.

For local development, run `./gradlew.bat :app:assembleDebug` on Windows or `./gradlew :app:assembleDebug` on macOS/Linux. The public APK is built separately without Android debugging and signed for release. See the [build and signing guide](docs/BUILDING.md). The build downloads a pinned Mihomo Android bridge and checks its SHA-256 digest. VPS deployment downloads a pinned sing-box release from upstream and verifies its digest.

## Security notes

Only enter credentials for a VPS you own or administer. The first SSH host key is trusted on first use; a changed key stops the connection until you explicitly review it. Generated configuration files and subscription URLs contain proxy credentials. The current VPS-hosted subscription URL uses HTTP, not HTTPS, so anyone who obtains the URL—or can observe its traffic—may read those credentials. Treat the URL as a secret and rotate the affected credentials if exposed.

## License and upstream projects

OwnBridge source code is released under [GPL-3.0-only](LICENSE). See [third-party notices](THIRD_PARTY_NOTICES.md) for Mihomo, sing-box, icons, their licenses, and build-time source references. OwnBridge is independent of those projects.
