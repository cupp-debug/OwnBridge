# OwnBridge

[English](../README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Español](README.es.md) · [Русский](README.ru.md) · [Türkçe](README.tr.md) · [فارسی](README.fa.md)

**[⬇ Android önizleme APK'sını indir (arm64-v8a)](https://github.com/cupp-debug/OwnBridge/releases/download/v0.1.0-preview.1/OwnBridge-v0.1.0-preview.1-arm64.apk)**

OwnBridge, kontrol ettiğiniz bir Ubuntu VPS üzerinde kendi proxy düğümlerinizi kuran deneysel bir Android uygulamasıdır. Bir VPN sağlayıcısı değildir; sunucu veya hesap sağlamaz.

## Mevcut durum

Bu, üretim kullanımına hazır bir hizmet değil, erken aşama bir önizleme sürümüdür. Hata ayıklama özelliği kapalı önizleme APK'sı SS, VLESS Reality ve Hysteria 2 protokollerinin birlikte kurulmasına izin verir; kararlı sürümün kurulum yeteneği şimdilik daha sınırlıdır. Her VPS, ağ ve istemci birleşimi doğrulanmamıştır. APK, depoya eklenmek yerine GitHub Release eki olarak sunulur.

## Özellikler

- VPS'nin işletim sistemini, adreslerini, dinleyen portlarını, hizmetlerini ve güvenlik duvarını SSH üzerinden salt okunur biçimde inceler.
- SSH sunucu anahtarını sabitler, sürümlenmiş kurulum paketini SHA-256 ile doğrular ve güvenli yeniden denemeler için uzak ilerlemeyi kaydeder.
- Doğrulanmış kurulum sonuçlarından Clash/Mihomo YAML ve v2rayN/v2rayNG paylaşım bağlantıları üretir. Clash abonelik bağlantısı ayrıca elle istenebilir.
- Her düğümü Android cihazından Mihomo aracılığıyla yapılan bir HTTP isteğiyle sınar. Bu, ICMP ping değildir ve Android VPN'i başlatmaz.
- Doğrulanmış sonuçları ve günlükleri şifreli yerel geçmişte saklar. VPS root parolasını saklamaz.

## Gereksinimler ve derleme

- `arm64-v8a` destekli Android 8.0 veya üzeri bir cihaz.
- Yönetme yetkiniz olan, root SSH erişimli Ubuntu 22.04, 24.04 veya 26.04 x86_64 VPS. Desteklenen birleşimler mevcut sürümün özellik sınırlarına bağlıdır.
- Derleme için JDK 21 ve Android SDK API 36.

Yerel geliştirme için Windows'ta `./gradlew.bat :app:assembleDebug`, macOS veya Linux'ta `./gradlew :app:assembleDebug` çalıştırın. Genel önizleme APK'sı ayrıca, hata ayıklama kapalı olarak derlenir ve yayın anahtarıyla imzalanır; [derleme ve imzalama kılavuzuna](BUILDING.md) bakın. Derleme, sürümü sabitlenmiş Mihomo Android köprüsünü indirir ve SHA-256 özetini doğrular. VPS kurulumu, sing-box'ın sabitlenmiş sürümünü kaynak projeden indirip özetini doğrular.

## Güvenlik

Yalnızca size ait veya yönetme yetkiniz bulunan VPS kimlik bilgilerini girin. İlk SSH bağlantısında sunucu anahtarı ilk kullanımda güven ilkesiyle kaydedilir; anahtar değişirse açıkça inceleyene kadar bağlantı durur. Yapılandırma dosyaları ve abonelik bağlantıları proxy kimlik bilgilerini içerir. VPS'de sunulan abonelik bağlantısı şu anda HTTPS değil HTTP kullanır: bağlantıyı ele geçiren veya trafiği izleyebilen biri bu bilgileri okuyabilir. Bağlantıyı gizli tutun; sızarsa ilgili kimlik bilgilerini değiştirin.

## Lisans ve üçüncü taraf projeler

OwnBridge kaynak kodu [GPL-3.0-only](../LICENSE) ile yayımlanır. Mihomo, sing-box, simgeler, lisansları ve kaynak bilgileri için [üçüncü taraf bildirimlerine](../THIRD_PARTY_NOTICES.md) bakın. OwnBridge bu projelerden bağımsızdır.
