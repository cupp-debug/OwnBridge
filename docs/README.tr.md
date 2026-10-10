# OwnBridge

[English](../README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Español](README.es.md) · [Русский](README.ru.md) · [Türkçe](README.tr.md) · [فارسی](README.fa.md)

**Bir VPS'iniz mi var? Kendi özel VPN'inizi kurun.**

OwnBridge, Android için yerel bir kurulum aracıdır. Genel IPv4 adresi olan bir Ubuntu VPS hazırlayın, giriş bilgilerini girin, protokolü seçin ve «Kurulumu başlat»a dokunun. Sunucu kurulumu, düğüm kontrolleri ve yapılandırma oluşturma işlerini OwnBridge halleder.

İşlem tamamlandığında **Clash/Mihomo** abonelik bağlantısı isteyebilir, **v2rayN/v2rayNG**'ye aktarılabilir düğüm bağlantılarını alabilirsiniz. Sunucuda komut çalıştırmanız veya yapılandırma dosyalarını elle yazmanız gerekmez.

**Kendi sunucunuz. Kendi düğümleriniz. Birkaç dokunuşla kurulumdan teslimata.**

**[⬇ Android APK'sını indir (arm64-v8a)](https://github.com/cupp-debug/OwnBridge/releases/download/v0.1.0/OwnBridge-v0.1.0-arm64.apk)**

## Mevcut durum

v0.1.0, Android uygulamasının ilk önizleme dışı sürümüdür. OwnBridge bir VPN sağlayıcısı değildir; sunucu veya hesap sağlamaz. Hata ayıklama özelliği kapalı APK, SS, VLESS Reality ve Hysteria 2 protokollerinin birlikte kurulmasını destekler. Her VPS, ağ ve istemci birleşimi doğrulanmamıştır; sorunları GitHub Issues üzerinden bildirebilirsiniz. APK, depoya eklenmek yerine GitHub Release eki olarak sunulur.

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

Yerel geliştirme için Windows'ta `./gradlew.bat :app:assembleDebug`, macOS veya Linux'ta `./gradlew :app:assembleDebug` çalıştırın. Genel APK ayrıca hata ayıklama kapalı olarak derlenir ve yayın anahtarıyla imzalanır; [derleme ve imzalama kılavuzuna](BUILDING.md) bakın. Derleme, sürümü sabitlenmiş Mihomo Android köprüsünü indirir ve SHA-256 özetini doğrular. VPS kurulumu, sing-box'ın sabitlenmiş sürümünü kaynak projeden indirip özetini doğrular.

## Güvenlik

Yalnızca size ait veya yönetme yetkiniz bulunan VPS kimlik bilgilerini girin. İlk SSH bağlantısında sunucu anahtarı ilk kullanımda güven ilkesiyle kaydedilir; anahtar değişirse açıkça inceleyene kadar bağlantı durur. Yapılandırma dosyaları ve abonelik bağlantıları proxy kimlik bilgilerini içerir. VPS'de sunulan abonelik bağlantısı şu anda HTTPS değil HTTP kullanır: bağlantıyı ele geçiren veya trafiği izleyebilen biri bu bilgileri okuyabilir. Bağlantıyı gizli tutun; sızarsa ilgili kimlik bilgilerini değiştirin.

## Lisans ve üçüncü taraf projeler

OwnBridge kaynak kodu [GPL-3.0-only](../LICENSE) ile yayımlanır. Mihomo, sing-box, simgeler, lisansları ve kaynak bilgileri için [üçüncü taraf bildirimlerine](../THIRD_PARTY_NOTICES.md) bakın. OwnBridge bu projelerden bağımsızdır.
