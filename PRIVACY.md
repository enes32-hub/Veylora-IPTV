# Veylora IPTV Privacy Policy

Last updated: October 7, 2026

Veylora IPTV (`app.veylora.tv`) is provided by Alquariz Core. It plays media from sources you add; it does not provide IPTV subscriptions or a built-in playable content catalogue. Contact us about privacy at **alquariz@yandex.com**.

## Information stored on your device

The app stores profiles, settings, playlist addresses, provider sign-in details, cached programme and title information, favourites, viewing progress, and diagnostic logs on your device as needed for its features. Downloads and recordings are saved to the storage location you choose. Local profile names are not registrations for an Alquariz Core online account.

We do not operate a central Veylora account service or automatically upload your viewing history or diagnostic logs to Alquariz Core. The app does not contain an Alquariz advertising or analytics service. This does not mean that the app makes no network requests: the services below receive information when the corresponding features run.

## Network services and information sent

- **Your media providers:** When you add, refresh, or play a source, the app contacts the server you specify. That server receives your IP address, requested playlist or media addresses, and any provider credentials or portal identifiers needed to fulfil the request. Playback requests can reveal what you are watching to that provider.
- **TMDB:** Metadata features request film or series titles, years, identifiers, language-specific information, and artwork from TMDB. These requests reveal the requested titles and language as well as your network IP address to the contacted service. Metadata results and image links are cached locally. Selecting provider-only metadata avoids using TMDB for title enrichment. If you configure another metadata server, that server also receives the requests sent to it.
- **Weather:** Weather is enabled by default and can be disabled in the weather settings. Automatic location uses **ipapi.co** to estimate your location from your public IP address. The resulting coordinates are sent to **Open-Meteo** for a forecast. A manually entered city or coordinates can be used instead. This feature does not request Android GPS location permission; manually entered coordinates may nevertheless identify a precise location.
- **Subtitles:** If you use online subtitle search or sign in to OpenSubtitles, title or episode information, selected language, subtitle requests, and any account credentials or session tokens required for that operation are sent to the configured subtitle service. The default route uses an OwnTV-operated intermediary (`my-owntv-opensub.xiannero.workers.dev`) before OpenSubtitles. A configured personal API key or custom server may change that route. These services are not operated by Alquariz Core.
- **Trailers:** Opening a trailer loads a YouTube player or opens YouTube externally. Google/YouTube may process IP addresses, device/browser information, cookies or identifiers, and playback interactions under its own policies. Do not use this optional feature if you do not want to contact YouTube.
- **Optional network settings:** If you select a proxy or encrypted DNS provider, that service processes the connections or DNS requests routed through it. Non-HTTPS traffic may be visible to the network or proxy provider.
- **Local remote entry:** When you enable the local web page for entering source details from another device, information is transferred over your local network to the TV. Use a trusted network; the local page uses HTTP.

Third-party services determine their own retention and processing practices. Their relevant policies include [TMDB](https://www.themoviedb.org/privacy-policy), [Google and YouTube](https://policies.google.com/privacy), [ipapi](https://ipapi.co/privacy/), and [Open-Meteo](https://open-meteo.com/en/terms). Consult your media and subtitle providers' policies before supplying credentials or content to them. Requests may be processed in countries other than your own.

## Storage access and security

The app does not request access to all files. It uses the Android system picker for files and folders you select. Selected files are copied into app-private storage so they remain available after restart. On TVs without a system picker, external file selection is unavailable and downloads or recordings use app-owned storage. Uninstalling removes files in app-owned storage, including on removable drives. It does not upload the contents of your storage to Alquariz Core for advertising or analytics. Android automatic app backup is disabled in this build.

We use HTTPS where the selected service supports it. User-supplied sources can use unencrypted HTTP, and we cannot guarantee encryption for every configured connection. Do not share playlist URLs containing passwords, access tokens, or private diagnostic information. No method of storage or transmission is completely secure.

## Retention and deletion

Local app data is kept while needed for the app's functions and until removed or cleared. You can remove sources and profiles in the app or clear Veylora IPTV's storage through Android settings to remove its private local data. Uninstalling removes app-private data; files saved to shared or USB storage may remain and must be deleted separately. Removing local data does not delete data retained by external providers; contact those providers for their deletion procedures.

If you email support, we receive your email address and the information you choose to send, and use them to respond to your request. Do not send passwords or API keys. To request deletion of support correspondence, email **alquariz@yandex.com**; we will handle the request subject to applicable legal obligations.

## Children and content choices

Parents and guardians should supervise children's use, select age-appropriate sources, and review or disable optional third-party features. A player's age rating does not guarantee that media supplied by an outside provider is suitable for children. This policy is not a claim that every third-party service is approved for children. Do not provide a child's personal information in profiles, provider accounts, or support messages unless you have the necessary authority and the relevant service permits it.

## Changes

We will update this page when the app's data practices change and revise the date above. Contact **alquariz@yandex.com** with questions about this policy.

# Veylora IPTV Gizlilik Politikası

Son güncelleme: 7 Ekim 2026

Veylora IPTV (`app.veylora.tv`), Alquariz Core tarafından sunulur. Eklediğiniz kaynaklardaki medyayı oynatır; IPTV aboneliği veya hazır oynatılabilir içerik kataloğu sağlamaz. Gizlilik iletişim adresimiz **alquariz@yandex.com** adresidir.

## Cihazınızda saklanan bilgiler

Profiller, ayarlar, kaynak adresleri ve sağlayıcı giriş bilgileri, önbelleğe alınmış yayın ve yapım bilgileri, favoriler, izleme ilerlemesi ve hata günlükleri ilgili işlevler için cihazınızda tutulur. İndirmeler ve kayıtlar seçtiğiniz konuma kaydedilir. Yerel profil oluşturmak, Alquariz Core üzerinde çevrimiçi hesap açmak değildir.

Merkezi bir Veylora hesap hizmetimiz yoktur. İzleme geçmişiniz veya hata günlükleriniz otomatik olarak Alquariz Core'a yüklenmez. Uygulamada Alquariz'e ait reklam veya analiz hizmeti bulunmaz. Bununla birlikte aşağıdaki özellikler çalışırken üçüncü taraflarla ağ üzerinden bilgi alışverişi yapılır.

## Ağ bağlantıları

- **Medya sağlayıcınız:** Kaynak ekleme, yenileme ve oynatma sırasında belirttiğiniz sunucuya bağlanılır. Sunucu IP adresinizi, istenen kaynak veya medya adreslerini ve gerekiyorsa hesap bilgileri ya da portal tanımlayıcılarını alır. Oynatma istekleri izlediğiniz içeriği sağlayıcınıza gösterebilir.
- **TMDB:** Yapım adı, yıl, kimlik, dil ve görsel istekleri ilgili bilgi hizmetine gönderilir; hizmet IP adresinizi ve istenen yapım/dil bilgilerini görebilir. Sonuçlar yerelde önbelleğe alınır. Yalnızca sağlayıcı metaverisi seçimi, yapım bilgilerini zenginleştirmek için TMDB kullanımını önler. Özel bir metaveri sunucusu seçerseniz ilgili istekleri o sunucu alır.
- **Hava durumu:** Varsayılan olarak açıktır; hava durumu ayarlarından kapatılabilir. Otomatik konum için ipapi.co, IP adresinizden yaklaşık konum çıkarır. Koordinatlar tahmin için Open-Meteo'ya gönderilir. Elle şehir veya koordinat girebilirsiniz. Android GPS konum izni istenmez; elle girilen koordinatlar yine de hassas bir konumu gösterebilir.
- **Altyazılar:** Çevrimiçi altyazı özelliklerinde yapım/bölüm, dil ve altyazı talepleri; giriş yaparsanız gerekli hesap bilgileri veya oturum belirteçleri ilgili hizmete gönderilir. Varsayılan bağlantı OpenSubtitles öncesinde OwnTV'nin `my-owntv-opensub.xiannero.workers.dev` aracı hizmetini kullanır. Kişisel anahtar veya özel sunucu bu yolu değiştirebilir. Bu hizmetler Alquariz Core tarafından işletilmez.
- **Fragmanlar:** Fragman açmak YouTube oynatıcısını yükler veya YouTube'u açar. Google/YouTube kendi politikaları kapsamında IP, cihaz/tarayıcı bilgileri, çerezler veya tanımlayıcılar ve oynatma etkileşimlerini işleyebilir. YouTube'a bağlanmak istemiyorsanız bu isteğe bağlı özelliği kullanmayın.
- **Diğer bağlantılar:** Seçtiğiniz proxy veya şifreli DNS hizmeti kendisine yönlendirilen trafiği işler. Yerel uzaktan kaynak girişi, aynı ağdaki cihaz ile TV arasında HTTP kullanır; yalnız güvenilir ağlarda kullanın.

Üçüncü tarafların saklama süreleri ve veri uygulamaları kendi politikalarına tabidir. Yukarıdaki İngilizce bölümünde TMDB, Google/YouTube, ipapi ve Open-Meteo politikalarına bağlantılar bulunur. Medya ve altyazı sağlayıcınızın politikasını da inceleyin. Veriler bulunduğunuz ülke dışında işlenebilir.

## Depolama ve güvenlik

Uygulama tüm dosyalara erişim izni istemez. Seçtiğiniz dosya ve klasörler için Android sistem seçicisi kullanılır. Seçilen dosyalar yeniden başlatma sonrasında da kullanılabilmeleri için uygulamanın özel alanına kopyalanır. Sistem seçicisi bulunmayan televizyonlarda dışarıdan dosya seçimi desteklenmez; indirme ve kayıtlar uygulamaya ait alana yapılır. Uygulamayı kaldırmak, çıkarılabilir sürücülerdekiler dahil uygulamaya ait alandaki dosyaları siler. Dosyalarınız reklam veya analiz amacıyla Alquariz Core'a yüklenmez. Bu sürümde Android'in otomatik uygulama yedeklemesi kapalıdır.

Hizmet desteklediğinde HTTPS kullanılır. Eklediğiniz kaynaklar şifrelenmemiş HTTP kullanabilir; bütün bağlantıların şifreli olduğunu garanti edemeyiz. Parola veya erişim belirteci içeren kaynak adreslerini ve özel hata bilgilerini paylaşmayın. Hiçbir saklama veya aktarım yöntemi tamamen güvenli değildir.

## Saklama ve silme

Yerel veriler işlevler için gerektiği sürece ve kaldırılana ya da temizlenene kadar tutulur. Kaynakları ve profilleri uygulamadan kaldırabilir; özel yerel verileri silmek için Android ayarlarından uygulama depolamasını temizleyebilirsiniz. Uygulamayı kaldırmak özel verileri kaldırır; ortak veya USB depolamaya kaydedilen dosyalar kalabilir ve ayrıca silinmelidir. Yerel silme, dış hizmetlerde tutulan verileri silmez; bunun için ilgili sağlayıcıyla iletişime geçin.

Destek için yazarsanız e-posta adresiniz ve gönderdiğiniz bilgiler talebinize yanıt vermek için işlenir. Parola veya API anahtarı göndermeyin. Destek yazışmalarının silinmesi için **alquariz@yandex.com** adresine başvurabilirsiniz; talepler geçerli yasal yükümlülükler gözetilerek ele alınır.

## Çocuklar ve içerik seçimi

Ebeveynler ve vasiler çocukların kullanımını gözetmeli, yaşlarına uygun kaynakları seçmeli ve isteğe bağlı üçüncü taraf özelliklerini değerlendirmeli veya kapatmalıdır. Oynatıcının yaş derecelendirmesi, dış sağlayıcının içeriğinin çocuklara uygun olduğunu garanti etmez. Bu politika tüm dış hizmetlerin çocuklar için onaylandığı anlamına gelmez. Gerekli yetki ve ilgili hizmetin izni olmadan çocukların kişisel bilgilerini profil, sağlayıcı hesabı veya destek mesajlarına eklemeyin.

## Değişiklikler

Veri uygulamaları değiştiğinde bu sayfa ve yukarıdaki tarih güncellenir. Sorularınızı **alquariz@yandex.com** adresine iletebilirsiniz.
