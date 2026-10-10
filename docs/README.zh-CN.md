# OwnBridge

[English](../README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Español](README.es.md) · [Русский](README.ru.md) · [Türkçe](README.tr.md) · [فارسی](README.fa.md)

**[⬇ 下载 Android 预览版 APK（arm64-v8a）](https://github.com/cupp-debug/OwnBridge/releases/download/v0.1.0-preview.1/OwnBridge-v0.1.0-preview.1-arm64.apk)**

OwnBridge 是一款实验性的 Android 应用，可在你自己管理的 Ubuntu VPS 上部署代理节点。它不是 VPN 服务商，不提供服务器或账户。

## 当前状态

这是早期预览版，不是可直接用于生产环境的服务。非调试 APK 开放了 SS、VLESS Reality 和 Hysteria 2 的组合部署流程；稳定正式版的可写部署范围仍较窄。不同 VPS、网络和客户端组合尚未全部验证。APK 作为 GitHub Release 附件提供，不提交到源码仓库。

## 功能

- 通过 SSH 只读检查 VPS 的系统、地址、监听端口、服务和防火墙状态。
- 固定 SSH 主机密钥，使用 SHA-256 校验版本化部署包，并记录远端进度以支持安全重试。
- 根据已验证的部署结果生成 Clash/Mihomo YAML 和 v2rayN/v2rayNG 分享链接；也可手动索取 Clash 订阅链接。
- 在 Android 设备上使用 Mihomo 发出 HTTP 请求，逐个测试节点。这不是 ICMP Ping，也不会启动 Android VPN。
- 将验证成功的结果和日志保存在本机加密历史中；不会保存 VPS root 密码。

## 环境与构建

- Android 8.0 或更高版本，支持 `arm64-v8a`。
- 你有权管理的 Ubuntu 22.04、24.04 或 26.04 x86_64 VPS，并可通过 root SSH 登录；具体组合仍受当前构建版本的功能限制。
- 构建时需要 JDK 21 和 Android SDK API 36。

本地开发时，Windows 执行 `./gradlew.bat :app:assembleDebug`；macOS 或 Linux 执行 `./gradlew :app:assembleDebug`。公开预览版需单独构建为不可调试的 APK 并使用发行密钥签名，详见[构建与签名说明](BUILDING.md)。构建会下载固定版本的 Mihomo Android 桥接库并校验 SHA-256；VPS 部署会从上游下载固定版本的 sing-box 并校验摘要。

## 安全说明

只输入你拥有或获授权管理的 VPS 凭据。首次连接采用“首次使用即信任”方式记录 SSH 主机密钥；密钥变化时会停止连接，等待你明确核对。配置文件和订阅链接含有代理凭据。目前部署在 VPS 上的订阅链接使用 HTTP 而非 HTTPS：拿到链接的人，或能观察传输流量的人，可能读到这些凭据。请将链接视为秘密；如已泄露，应轮换相关凭据。

## 许可证与上游项目

OwnBridge 源码采用 [GPL-3.0-only](../LICENSE) 许可证。Mihomo、sing-box、图标及其许可证和构建时源码出处，见[第三方声明](../THIRD_PARTY_NOTICES.md)。OwnBridge 与这些项目相互独立。
