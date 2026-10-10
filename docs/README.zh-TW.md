# OwnBridge

[English](../README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Español](README.es.md) · [Русский](README.ru.md) · [Türkçe](README.tr.md) · [فارسی](README.fa.md)

**[⬇ 下載 Android 預覽版 APK（arm64-v8a）](https://github.com/cupp-debug/OwnBridge/releases/download/v0.1.0-preview.1/OwnBridge-v0.1.0-preview.1-arm64.apk)**

OwnBridge 是一款實驗性的 Android 應用程式，可在你自行管理的 Ubuntu VPS 上部署代理節點。它不是 VPN 服務供應商，也不提供伺服器或帳號。

## 目前狀態

這是早期預覽版，尚非可直接用於正式環境的服務。非偵錯 APK 開放了 SS、VLESS Reality 與 Hysteria 2 的組合部署流程；穩定正式版的可寫入部署範圍仍較窄。不同 VPS、網路與客戶端組合尚未全部驗證。APK 以 GitHub Release 附件提供，不提交到原始碼儲存庫。

## 功能

- 透過 SSH 以唯讀方式檢查 VPS 的系統、位址、監聽連接埠、服務及防火牆狀態。
- 固定 SSH 主機金鑰，使用 SHA-256 驗證版本化部署套件，並記錄遠端進度以支援安全重試。
- 根據已驗證的部署結果產生 Clash/Mihomo YAML 與 v2rayN/v2rayNG 分享連結；也可手動索取 Clash 訂閱連結。
- 在 Android 裝置上透過 Mihomo 發出 HTTP 請求，逐一測試節點。這不是 ICMP Ping，也不會啟動 Android VPN。
- 將已驗證的結果與日誌儲存在本機加密歷史紀錄中；不會儲存 VPS root 密碼。

## 環境與建置

- Android 8.0 或更新版本，支援 `arm64-v8a`。
- 你有權管理、可用 root SSH 登入的 Ubuntu 22.04、24.04 或 26.04 x86_64 VPS；實際組合仍受目前版本功能限制。
- 建置需要 JDK 21 與 Android SDK API 36。

本機開發時，Windows 執行 `./gradlew.bat :app:assembleDebug`；macOS 或 Linux 執行 `./gradlew :app:assembleDebug`。公開預覽版須另外建置為不可偵錯的 APK 並以發行金鑰簽署，詳見[建置與簽署說明](BUILDING.md)。建置時會下載固定版本的 Mihomo Android 橋接程式並驗證 SHA-256；VPS 部署會從上游下載固定版本的 sing-box 並驗證摘要。

## 安全說明

僅輸入你擁有或獲授權管理的 VPS 憑證。首次連線採用「首次使用即信任」方式記錄 SSH 主機金鑰；金鑰變更時會停止連線，等待你明確核對。設定檔與訂閱連結包含代理憑證。目前部署於 VPS 的訂閱連結使用 HTTP 而非 HTTPS：取得連結的人，或能觀察傳輸流量的人，可能讀取這些憑證。請將連結視為秘密；若已外洩，應更換相關憑證。

## 授權條款與上游專案

OwnBridge 原始碼採用 [GPL-3.0-only](../LICENSE)。Mihomo、sing-box、圖示及其授權條款與建置時原始碼來源，請參閱[第三方聲明](../THIRD_PARTY_NOTICES.md)。OwnBridge 與這些專案互相獨立。
