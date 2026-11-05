# PDF Oluştur 📄📷

Kamera ile taradığınız fotoğrafları ve galeriden seçtiğiniz resimleri **PDF**'e dönüştüren Android uygulaması.

## Özellikler
- 📷 Kamera ile tek tek fotoğraf çekme
- 🖼️ Galeri'den **çoklu** fotoğraf seçme (aynı anda 20'ye kadar)
- ▲▼ Fotoğraf sırasını değiştirme (PDF sayfa sırası bu sıraya göre oluşur)
- 🗑️ Listedeki fotoğrafı silme
- 📄 A4 boyutunda PDF oluşturma (her fotoğraf bir sayfa, beyaz sayfa, ortalanmış)
- 🔄 PDF'i WhatsApp, e-posta vb. ile doğrudan paylaşma
- Android 10+ cihazlarda PDF'in kopyası **İndirilenler** klasörüne de kaydedilir
- Ek bağımlılık yok: yerleşik `PdfDocument` API kullanılır

## APK nasıl derlenir?
Kod `main` dalına push edildiğinde **GitHub Actions** otomatik derler.
- Repo → **Actions** → son "Derle (APK)" işi → **Artifacts → PdfOlustur-APK**
- `app-debug.apk` cihaza yüklenip doğrudan kullanılabilir.

## Yerelde derleme
```bash
gradle app:assembleDebug
```

## Teknoloji
- Java + Android SDK (minSdk 24, targetSdk 34)
- AndroidX AppCompat, RecyclerView, ActivityResult API
