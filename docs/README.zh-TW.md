# OwnBridge

[English](../README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Español](README.es.md) · [Русский](README.ru.md) · [Türkçe](README.tr.md) · [فارسی](README.fa.md)

**有一台 VPS，就能架起自己的私人 VPN。**

OwnBridge 是一款在 Android 手機上使用的本機部署工具。準備一台有公用 IPv4 位址的 Ubuntu VPS，填入登入資訊、選好協定，點一下「開始部署」；伺服器安裝、節點檢查和設定產生，交給 OwnBridge。

完成後，你可以索取 **Clash/Mihomo 訂閱網址**，也能拿到 **v2rayN/v2rayNG 可匯入的節點連結**。不用敲伺服器指令，不用手寫設定檔。

**你的伺服器，你的節點。手機上幾步，從部署到交付。**

**[⬇ 下載 Android APK（arm64-v8a）](https://github.com/cupp-debug/OwnBridge/releases/download/v0.1.0/OwnBridge-v0.1.0-arm64.apk)**

## 目前狀態

v0.1.0 是首個非預覽版 Android 發行套件。OwnBridge 不是 VPN 服務供應商，也不提供伺服器或帳號。不可偵錯的 APK 支援 SS、VLESS Reality 與 Hysteria 2 的組合部署。不同 VPS、網路與客戶端組合尚未全部驗證；如遇問題，歡迎透過 GitHub Issues 回報。APK 以 GitHub Release 附件提供，不提交到原始碼儲存庫。

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

本機開發時，Windows 執行 `./gradlew.bat :app:assembleDebug`；macOS 或 Linux 執行 `./gradlew :app:assembleDebug`。公開發行套件須另外建置為不可偵錯的 APK 並以發行金鑰簽署，詳見[建置與簽署說明](BUILDING.md)。建置時會下載固定版本的 Mihomo Android 橋接程式並驗證 SHA-256；VPS 部署會從上游下載固定版本的 sing-box 並驗證摘要。

## 安全說明

僅輸入你擁有或獲授權管理的 VPS 憑證。首次連線採用「首次使用即信任」方式記錄 SSH 主機金鑰；金鑰變更時會停止連線，等待你明確核對。設定檔與訂閱連結包含代理憑證。目前部署於 VPS 的訂閱連結使用 HTTP 而非 HTTPS：取得連結的人，或能觀察傳輸流量的人，可能讀取這些憑證。請將連結視為秘密；若已外洩，應更換相關憑證。

## 授權條款與上游專案

OwnBridge 原始碼採用 [GPL-3.0-only](../LICENSE)。Mihomo、sing-box、圖示及其授權條款與建置時原始碼來源，請參閱[第三方聲明](../THIRD_PARTY_NOTICES.md)。OwnBridge 與這些專案互相獨立。
