# CleanTube doğrulama kontrol listesi

Bu belge, uçtan uca doğrulamanın cihazda manuel olarak yapılması için hazırlanmıştır. Bu ortamda fiziksel cihaz/emülatör üzerinde otomatik doğrulama yapılamaz.

## Medya oturumu ve bildirim
- [ ] Video oynatılırken sistem medya bildirimi görünüyor ve başlık/sanatçı bilgisi doğru.
- [ ] Bildirimden oynat/duraklat/sonraki kontrolleri çalışıyor.
- [ ] Uygulama arka plana alındığında oynatma (bekleniyorsa) ve bildirim davranışı doğru.
- [ ] Ses odağı (arama, başka uygulama sesi) kazanma/kaybetme ve `becoming noisy` (kulaklık çıkınca duraklama) davranışı.

## Arka plan ve PiP
- [ ] Oynatma sırasında ana ekrana çıkıldığında Picture-in-Picture tetikleniyor (destekli cihazlarda).
- [ ] PiP penceresinde video görünümü korunuyor, geri dönünce player durumu tutarlı.
- [ ] Farklı Android sürümlerinde (özellikle 8.0+, 12+, 13+) PiP ve arka plan geçişleri.

## İndirme yöneticisi ve çevrimdışı
- [ ] Player’dan indirme kalitesi seçilip indirme başlatılıyor (DownloadManager kuyruğu).
- [ ] Bildirim/İndirmeler ekranında kayıt görünüyor; dosya tamamlandığında “Çevrimdışı hazır” durumu.
- [ ] İndirmeler ekranından çevrimdışı oynatma çalışıyor (yerel dosya kaynağı).
- [ ] Kayıt/dosya silme çalışıyor.
- [ ] Depolama izinleri/erişim (özellikle eski Android sürümleri ve OEM kısıtları).

## Cast
- [ ] Aynı ağda Cast uyumlu alıcı keşfi (Google Play Hizmetleri kurulu cihaz).
- [ ] Cast oturumu başlatma ve medya yönlendirme/oturum davranışı (entegre Cast framework + seçenek sağlayıcı mevcut; cihazda doğrulama gerekir).

## InnerTube / kişisel akışlar
- [ ] Girişli hesapla ana akış, abonelikler, geçmiş, oynatma listeleri ve “Daha fazla yükle” continuation.
- [ ] Beğen/abone/yorum gibi mutasyonların (best-effort) hesapta yansıması ve hata durumları.

## Erişilebilirlik
- [ ] TalkBack ile ana navigasyon, player kontrolleri, listeler ve diyaloglar.
- [ ] İçerik açıklamaları, odak sırası, dokunma hedef boyutları ve ekran okuyucu duyuruları.
- [ ] Kontrast ve metin ölçekleme.

## Genel
- [ ] Deep link/share-in, arama/Mixler/Shorts continuation, çevrim içi oynatma ve hata senaryoları.
