# Retrobit — Geliştirici El Kitabı

Retrobit, kullanıcının kendi cihazında tuttuğu ROM klasörünü tarayıp tek bir kütüphanede toplayan,
kapak görsellerini çözen ve oyunları gömülü Libretro çekirdekleriyle (LibretroDroid) çalıştıran bir
Android / Android TV uygulamasıdır.

- Paket adı: `com.retrobit.app`
- Sürüm: **1.19.0** (`versionCode = 33`)
- minSdk 26 · target/compileSdk 36 · Kotlin 2.0.21 · Compose Material3 (BOM 2024.10.01)
- Diller: Türkçe + İngilizce (`values/`, `values-tr/`)
- Gelir modeli: tek seferlik **Destekçi paketi** (Google Play Billing, ürün kimliği `supporter_pack`); reklam yok.
- **Dağıtım kararı (11.11.2026):** tüm geliştirme ve çıktılar **yalnızca `play` flavor** içindir;
  `sideload` askıya alındı (kaynakta durur, derlenmez/yayınlanmaz).

> **Altın kural:** Bu proje hiçbir ticari ROM'u paketlemez, barındırmaz veya indirmez. Yalnızca
> kullanıcının kendi depolamasındaki dosyalar içe aktarılır. Reklam ve izleme SDK'sı yoktur.

---

## 1. Hızlı başlangıç

```bash
cd /path/to/Retrobit
# Yalnızca play flavor (sideload askıda — derlemeyin):
set -o pipefail && ./gradlew assemblePlayRelease bundlePlayRelease 2>&1 | tr -d '\000' | tail -6
# çıktılar:
#   app/build/outputs/apk/play/release/app-play-release.apk           (Play Store, SAF)
#   app/build/outputs/bundle/playRelease/app-play-release.aab         (Play Store yüklemesi)
```

`set -o pipefail` şart: Gradle çıktısı pipe'tan geçerken hata kodu kaybolmasın. `tr -d '\000'`
Windows'taki NUL baytlarını temizler.

Test cihazına kurulum (MuMu):

```bash
export MSYS_NO_PATHCONV=1
ADB="$ANDROID_HOME/platform-tools/adb.exe"
# DİKKAT: MSYS_NO_PATHCONV=1 ile adb install Windows yolu ister (C:/...), /c/... DEĞİL:
"$ADB" -s 127.0.0.1:16384 install -r C:/path/to/Retrobit/app/build/outputs/apk/play/release/app-play-release.apk
"$ADB" -s 127.0.0.1:16384 shell monkey -p com.retrobit.app -c android.intent.category.LAUNCHER 1
```

---

## 2. Çalışma alanı hiyerarşisi

```
C:\Users\Admin\Desktop\RetroPro\
├─ Retrobit\                         Android uygulamasının tamamı (kaynak)
│  ├─ app\
│  │  ├─ build.gradle.kts            Uygulama modülü, bağımlılıklar, imza, sürüm
│  │  ├─ proguard-rules.pro          Release'te minify kapalı; kural dosyası yedekte
│  │  ├─ build\                      Üretilen çıktılar (APK, ara dosyalar) — SÜRÜM KONTROLÜNE GİRMEZ
│  │  └─ src\main\
│  │     ├─ AndroidManifest.xml      Activity'ler, izinler, TV banner, intent filtreleri
│  │     ├─ assets\games.json        121 kayıtlık yerel metadata kataloğu (başlık/konsol/kapak)
│  │     ├─ java\com\retrobit\app\   Kotlin kaynakları (aşağıda dosya dosya)
│  │     └─ res\
│  │        ├─ values\strings.xml    Varsayılan (İngilizce) metinler
│  │        ├─ values-tr\            Türkçe metinler
│  │        ├─ values\               renkler, tema, stiller
│  │        ├─ drawable*\            glif ikonları, TV banner, splash logo
│  │        ├─ font\                 Poppins
│  │        ├─ mipmap-*\             launcher ikonları
│  │        └─ xml\                  file_paths (FileProvider), locales_config
│  ├─ art\                           Kaynak sanat dosyaları (ikon, TV banner)
│  ├─ docs\
│  │  ├─ DEVELOPER.md                BU DOSYA
│  │  └─ screenshots\                tv-home.png, tv-library.png, in-game-menu.png
│  ├─ gradle\wrapper\                Gradle 8.9 wrapper
│  ├─ build.gradle.kts               Kök build (plugin sürümleri)
│  ├─ settings.gradle.kts            Modül listesi + JitPack deposu
│  ├─ gradle.properties              JVM/AndroidX ayarları
│  ├─ local.properties               SDK yolu — makineye özgü, paylaşılmaz
│  ├─ gradlew / gradlew.bat
│  ├─ native-sources\                Exact commitlere sabitlenmiş çekirdek submodule'leri
│  └─ icon-playstore-512.png         Mağaza ikonu
├─ reference\
│  └─ Android64-v1.1.5.apk           Tersine mühendislik referansı (orijinal hedef APK)
├─ ROMS\                             Kullanıcıya ait test ROM'ları (kaynak kod değil)
│  ├─ GBA\  NES\  PSX\  SEGA\  SNES\
└─ ROMS.zip                          Kullanıcının kendi ROM arşivi (~1.2 GB, bilinçli olarak korundu)
```

Notlar:

- `app/build/`, `.gradle/`, `.kotlin/` üretilen klasörlerdir; silinmeleri güvenlidir, kaynak değildir.
- `ROMS/` ve `ROMS.zip` **uygulama kaynağı değildir**; kullanıcı verisidir. Temizlik sırasında
  silinmedi. Depoyu paylaşacaksanız bunları hariç tutun.
- `reference/` yalnızca karşılaştırma/analiz içindir; uygulama çalışma zamanında bu dosyayı kullanmaz.
- Proje şu anda bir git deposu değil. `*.keystore` ve `local.properties` `.gitignore` içindedir;
  yine de keystore'u asla halka açık bir depoya koymayın.

---

## 3. Mimari

Tek Activity + Compose Navigation; durum `AppViewModel` içinde toplanır, veri erişimi
`AppContainer` üzerinden enjekte edilen repository'lere gider.

```
MainActivity / RetroPlayerActivity
        │
        ▼
AppViewModel  ──────────────►  AppContainer
   │  (StateFlow)                 ├─ SettingsRepository   (DataStore)
   │                              ├─ LibraryRepository    (Room SQLite + In-Memory StateFlow)
   ▼                              ├─ GameRepository       (assets/games.json)
Compose UI                        ├─ CoverRepository      (libretro-thumbnails + Wikipedia)
 (RetrobitApp → screens)          ├─ RomImporter          (dosya/SAF tarama)
                                  ├─ RomArchives          (zip/7z okuma)
                                  ├─ CoreManager          (paketlenmiş native çekirdek çözümleme)
                                  └─ EmulatorManager      (harici emülatör)
```

### 3.1 Kotlin dosyaları

| Dosya | Görevi |
|---|---|
| `RetrobitApplication.kt` | `AppContainer`'ı oluşturur; süreç ömrü boyunca tek örnek. |
| `MainActivity.kt` | Compose kabuğu; splash, tema, `ACTION_VIEW` ile gelen ROM'u içe aktarma, `onResume`'da çekirdek yenileme + klasör otomatik eşitleme. |
| `AppViewModel.kt` | Tüm UI durumu: ayarlar, kütüphane, favoriler, kapak URL'leri, tarama/ilerleme, çekirdekler, depolama, emülatör listesi, mesajlar. İçe aktarma, klasör eşitleme, oynatma, silme ve ayar setter'ları burada. |
| `data/AppContainer.kt` | Elle yapılan DI: OkHttp istemcisi ve tüm repository'ler. |
| `data/Models.kt` | `CatalogGame`, `LibraryGame`, `ConsoleInfo`, `SettingsData` (+sabitler), `ThemeMode`, `EmulatorInfo`. |
| `data/ConsoleCatalog.kt` | 16 konsol tanımı: kimlik, ad, renk, glif, uzantılar, libretro sistem adı, önerilen çekirdekler. Uzantı→konsol eşlemesi. |
| `data/SettingsRepository.kt` | DataStore (`retrobit_settings`): dil, tema, oynatıcı modu, varsayılan emülatör, ROMS yolu/URI, onboarding, arayüz modu, destekçi durumu/isim, raf düzeni, kapak boyutu, özel kapaklar, attract mode. |
| `data/SupporterKeys.kt` | Çevrimdışı destekçi CD anahtarı üretimi/doğrulaması (PBKDF2-HMAC-SHA256, `RB7-XXXX-XXXX-XXXX-CCCCCC`). |
| `data/StorageVolumes.kt` | Play build'de çıkarılabilir USB/SD birimlerini listeler ve başlangıç URI'si üretir. |
| `billing/SupporterPurchase.kt` + `src/play/.../SupporterPurchaseFactory.kt` | Play Billing 9.1.0 köprüsü: `supporter_pack` ürünü, satın alma, acknowledge, `queryPurchasesAsync` ile geri yükleme. Sideload flavor'da stub'dır (`BuildConfig.PLAY_BILLING = false`). |
| `data/LibraryRepository.kt` | Room veritabanı (`retrobit.db`); `StateFlow` yayını; oynama sayacı + `play_seconds`, lazy unpack yolu, favori anahtarları. |
| `data/GameRepository.kt` | `assets/games.json` yükler; başlık normalizasyonu ve konsol kontrollü eşleştirme. |
| `data/NetworkApis.kt` | `WikiApi`: Wikipedia REST özeti/görseli (son çare kapak). |
| `data/CoverRepository.kt` | Kapak çözümleme hattı (aşağıda §6). |
| `data/RomImporter.kt` | Dosya/SAF taraması, konsol tahmini, arşiv açma, yerinde referanslama, `prepareRom` ile ilk açılışta disk arşivi çıkarma. |
| `data/RomArchives.kt` | Zip ve 7z okuma; **uzantıya değil sihirli baytlara** göre konteyner seçimi. |
| `data/CoreManager.kt` | AAB'ye paketlenen ABI uyumlu `.so` çekirdeklerini `nativeLibraryDir` içinden çözer; `system/` ve `saves/` klasörlerini yönetir. Çalışma zamanında çekirdek indirmez. |
| `data/EmulatorManager.kt` | Kurulu harici emülatörleri keşfeder; `ACTION_VIEW` + FileProvider ile ROM başlatır. |
| `data/StorageFolders.kt` | Birincil/ikincil depolama kökleri, `ROMS` klasörünü bulma (bir seviye derin dahil), erişim kontrolü. |
| `ui/RetrobitApp.kt` | Tema/TV sağlayıcısı, `LocalSafeArea` + `LocalContentColor`, tam ekran Navigation grafiği (alt bar / yan ray yok), menü rotalarının birbirinin yerine geçmesi, `RetroSheet` (Ayarlar/İstatistik/Destekçiler/Vitrin), snackbar; TV **attract mode** (90 sn boşta slayt gösterisi). |
| `ui/components/RetroKit.kt` | 1.9.0 tema seti: `FocusConsoleIds`, `consoleStyle`, `consoleBackdropRes` (`bg_*.webp`), `ConsoleWordmark`, `RetroBackdrop` + `crtOverlay` (scanline), `GamepadHintBar`/`ButtonCap`, `RetroTopBar`, `RetroNavButton`, `RetroChip`, `RetroClock`, `retroShortcuts` (X/Y/Start/Select/L1/R1), `snapUrlFor`. |
| `ui/components/RetroGames.kt` | Konsol temalı oyun öğeleri: `GameListRow`, `RetroGameCard`, `CrtSnap` (4:3 CRT çerçeveli ekran görüntüsü), `MetaCell`, `playTimeLabel`, `formatLabel`. |
| `ui/components/Common.kt` | `LocalIsTv`, `rememberIsTv`, `tvFocus`, `gamepadClickable`, `RequestFocusOnWindowFocus`, `GameCover`, `ConsoleGlyph`, `FolderBrowserDialog`, `RomZipImportCard`, depolama izni yardımcıları. |
| `ui/screens/SetupScreen.kt` | İlk kurulum: klasör seçimi / varsayılanı bul / SAF / ZIP. |
| `ui/screens/SystemsScreen.kt` | Açılış ekranı: tam ekran konsol arka planı + yatay panel kaydırıcı (System Carousel), oyun/sistem sayaçları, üretici·yıl, tarama ilerlemesi. |
| `ui/screens/GameListScreen.kt` | Konsolun oyun listesi (Gamelist): solda CRT snap + kapak + üst veri, sağda konsol temalı liste; sıralama, harf atlama (L1/R1). |
| `ui/screens/LibraryDetailScreen.kt` | Oyun detayı: snap + kapak, oyna, favori, dosya durumu/boyutu, istatistik hücreleri, kaldır; destekçi için **kapak değiştir / sıfırla** (SAF `GetContent`). |
| `ui/screens/FavoritesScreen.kt` | Favoriler: konsol filtre çipleri + sıralama (A-Z, son oynanan, en çok oynanan), kart ızgarası. |
| `ui/screens/SearchScreen.kt` | TV dostu arama: metin alanı + harf şeridi + konsol çipleri, kart ızgarası. |
| `ui/screens/StatsScreen.kt` | Yerel oyun istatistikleri: toplam süre, en çok oynananlar, konsol dağılımı (tamamen cihazda). |
| `ui/screens/SupportersScreen.kt` | Opt-in "Destekçiler" duvarı (yalnızca cihazda; isim + rozet). |
| `ui/screens/ShowcaseScreen.kt` | Koleksiyon vitrini: kapak kolajı üretir, `cacheDir/showcase` üzerinden paylaşır. |
| `ui/screens/SettingsScreen.kt` | Kütüphane (klasör, tarama), Görünüm (dil, tema, arayüz), destekçi paketi (satın alma/CD anahtarı, isim, görünüm, vitrin modu, istatistik/vitrin/destekçiler), oynatıcı, üst veri, depolama, çekirdekler, emülatörler, hakkında. |
| `ui/player/RetroPlayerActivity.kt` | Oynatıcı Activity: oyun çözümleme, tuş/gamepad yönlendirme, L2+R2 / SELECT+START kombinasyonu, sistem çubuklarını gizleme, yaşam döngüsünde SRAM kaydı. |
| `ui/player/PlayerScreen.kt` | Compose oynatıcı: `GLRetroView`, çekirdek hazırlık ekranı, dokunmatik overlay, oyun menüsü (kaydet/yükle/reset/harici/çık). |
| `ui/theme/*` | Renkler, tipografi, `RetrobitTheme`, konsol gradyanları. |

### 3.2 Veri akışı (klasör taraması)

1. `SetupScreen` / `SettingsScreen` bir klasör seçer (`FolderBrowserDialog` veya SAF).
2. `AppViewModel.useFolderPath()` yolu DataStore'a yazar, `syncDirectFolders()` çağırır.
   Bu metod yapılandırılmış yolları ve her zaman okunabilir **Download** klasörünü birleştirir
   (`directSources`); Download zaten bir kökün altındaysa yinelenmez.
3. `RomImporter.importDirectory()` ağacı gezer (derinlik ≤ 12), `bin`/`cue` çiftlerini ayıklar,
   arşivleri `RomArchives` ile inceler. Download kökü `strictDownloads = true` ile taranır (§22.6).
4. Her dosya için konsol tahmini: klasör adı ipucu (`PSX`, `N64`, `MAME`, `NDS`…) → uzantı.
5. Kütüphane kaydı `LibraryRepository`'ye 6'lık partiler hâlinde yazılır; ilerleme
   `ImportProgress` ile Home/Library ekranına akar.
6. Kapaklar kart göründükçe `CoverRepository`'den asenkron istenir.
7. `onResume`'da `autoSyncFolder()` en fazla 60 saniyede bir sessiz eşitleme yapar.

---

## 4. Desteklenen konsollar ve çekirdekler

| Konsol | id | Uzantılar | Paketlenmiş çekirdek | Not |
|---|---|---|---|---|
| NES | `nes` | nes, unf, unif, fds | fceumm | |
| SNES | `snes` | sfc, smc, fig, swc | bsnes_mercury_performance | DSP/Cx4/ST010 için HLE |
| Nintendo 64 | `n64` | n64, z64, v64 | mupen64plus_next_gles3 | GLES3/Vulkan önerilir |
| Game Boy | `gb` | gb | gambatte | |
| Game Boy Color | `gbc` | gbc | gambatte | |
| Game Boy Advance | `gba` | gba | mgba | |
| Nintendo DS | `nds` | nds | melonds | BIOS isteğe bağlı |
| SEGA | `genesis` | md, gen, smd, 32x, sms, gg | picodrive + gearsystem | SMS/GG Gearsystem'a yönlenir |
| PlayStation | `ps1` | cue, bin, chd, pbp, iso | pcsx_rearmed | HLE ile BIOS'suz çalışır |
| PSP | `psp` | iso, cso, pbp | ppsspp | Pinned yardımcı assets otomatik hazırlanır; BIOS gerekmez |
| Atari 2600 | `atari2600` | a26, bin | stella | |

Çekirdekler dört ABI için AAB/APK içine `jniLibs` olarak paketlenir ve Android'in
`nativeLibraryDir` klasöründen yüklenir. Uygulama çalışma zamanında çekirdek veya başka
çalıştırılabilir kod indirmez. `files/cores/` yalnız eski sürümlerin artık yüklenmeyen kalıntılarıdır.

BIOS dosyaları `files/system/` altına kullanıcı tarafından konur. Uygulama BIOS indirmez veya dağıtmaz.
PPSSPP'nin pinned açık kaynak yardımcı dosyaları ilk PSP açılışında `files/system/PPSSPP/`
altına hazırlanır; mevcut kullanıcı dosyalarının üzerine yazılmaz. Çekirdek commitleri, dört ABI
hash'i ve lisansları `app/src/main/assets/licenses/EMULATOR_COMPONENTS.txt` içindedir.
Exact source tree'leri NDK 28.0.13004108 ile yeniden üretme tarifi `tools/build-android-cores.sh`
dosyasında tutulur.

---

## 5. ROM içe aktarma kuralları

- **Klasör taraması (önerilen):** dosyalar yerinde referanslanır; kopyalanmaz. Disk görüntüsü
  içeren arşivler (`.zip`/`.7z` içinde cue/chd/iso) açılmaz, ilk oynatmada `prepareRom()` açar.
- **Tek tek içe aktarma (SAF):** dosya kopyalanır; arşivde kartuş ROM'u varsa hemen çıkarılır,
  disk görüntüsü varsa arşiv kopyalanır ve ilk açılışta çıkarılır.
- Atlanan klasörler: `bios, saves, save, savestates, states, system, cheats, screenshots, covers,
  artwork, media`.
- `.cue` yanındaki `.bin` yinelenen kayıt olmasın diye atlanır.
- Arşiv türü **sihirli baytlarla** belirlenir: `PK` → zip, `7z` → 7z. Sahne dökümlerinde 7z
  dosyalarının `.zip` adıyla gelmesi bu yüzden sorun çıkarmaz.
- Aynı konsol + aynı dosya adı ikinci kez eklenmez (`claim()`).
- Tehlikeli yol çıkışına karşı arşiv üyeleri hedef klasörün dışına yazılamaz (`insideFolder`).
- `bin` uzantısı belirsizdir: klasör ipucu `ps1` ise oraya, aksi hâlde **genesis**
  sayılır. (`Knuckles Chaotix 32X (A) [!].bin` bu yüzden Genesis olarak içe aktarılır — bkz. §10.)
- **Download klasörü otomatik taraması katıdır:** doğrudan `.exe`/`.com`/`.bin`/`.m3u` yok sayılır,
  `.md` yalnızca ≥ 64 KB ise Genesis sayılır (README/LICENSE elenir), `.iso` ≥ 1 MB olmalıdır.
  Arşivlerde yalnızca gerçek kartuş ya da disk girdisi kabul edilir; "arcade/DOS" yedeği kapalıdır.
  Kullanıcının açıkça seçtiği bir kökte bu filtre uygulanmaz.

---

## 6. Kapak (box art) hattı

Kaynak: `https://raw.githubusercontent.com/libretro-thumbnails/<system>/master/Named_Boxarts|Named_Titles|Named_Snaps/<No-Intro adı>.png`

Sıra:

1. `LibraryGame.coverUrl` (katalogdan geldiyse) → 2. `covers.json` önbelleği →
3. Dosya adından üretilen libretro adayları (bölge kodu açılımı, parantez temizliği) →
4. Katalog eşleşmesi (`catalogId` veya normalize başlık, **konsol eşitse**) →
5. Wikipedia özet görseli (yalnızca `/wikipedia/en/` medyası ve başlık eşleşmesi doğrulanırsa).

Kurallar:

- Aday URL'ler `HEAD` ile 4 sn zaman aşımıyla yoklanır; eşzamanlılık `Semaphore(4)`.
- **Yalnızca bulunan URL'ler diske yazılır.** Kaçırmalar oturum içi `probed` kümesinde tutulur;
  böylece geçici ağ hatası kalıcı "kapaksız" duruma dönüşmez.
- `covers.json` anahtarları: `l:<libraryId>`, `c:<catalogId>`.
- Wikipedia son çare, franchise logosu dönmemesi için sıkı kontrol edilir.

---

## 7. Oynatıcı ve TV kontrolleri

- Gömülü oynatıcı: `RetroPlayerActivity` → `GLRetroView` (LibretroDroid 0.14.0).
- **Menü açma:** L2+R2 **veya** SELECT+START 650 ms basılı tutulur. Tuş tekrarları yüzünden
  zamanlayıcı kombinasyon başına yalnızca bir kez kurulur.
- Menü içeriği: Devam, Durum kaydet, Durum yükle, Sıfırla, (dokunmatikte) kontrolleri göster/gizle,
  Harici emülatörde aç, **Oyundan çık**.
- Geri tuşu: menü açıksa kapatır, oyun sürüyorsa menüyü açar, aksi hâlde çıkar.
- Gamepad yüz tuşları RetroPad'e eşlenirken A↔B, X↔Y takas edilir (Xbox dizilimiyle konsol
  dizilimi uyuşsun diye).
- Analog çubuklar: sol X/Y, sağ RX/RY; pad Z/RZ bildiriyorsa ona düşülür. Ölü bölge 0.12.
- L2/R2 hem tuş hem analog eksen (`AXIS_LTRIGGER`/`AXIS_BRAKE`, `AXIS_RTRIGGER`/`AXIS_GAS`) olarak
  yakalanır, eşik 0.5.
- **TV'de dokunmatik overlay hiç gösterilmez** (`overlayRequested && !isTv`); TV kullanıcısı her şeyi
  oyun menüsünden yapar.
- Çıkışta/pause'da SRAM yazılır; `releaseInput()` basılı kalan tuşları bırakır.

---

## 8. Arayüz ve Android TV

- `LocalIsTv`, `rememberIsTv(layoutMode)` ile belirlenir:
  - `auto` → `PackageManager.FEATURE_LEANBACK`
  - `phone` → her zaman false
  - `tv` → her zaman true
- MuMu gibi Leanback bayrağı olmayan TV kutularında **Ayarlar → Görünüm → Arayüz → Android TV**
  seçilmelidir. Bu, overscan boşluğuna, büyük odak halkalarına ve dokunmatik kontrollerin
  gizlenmesine yol açar.
- 1.9.0'dan itibaren alt bar / yan ray yoktur; tüm ekranlar tam ekrandır ve gezinme
  gamepad kısayolları + üst çubuk düğmeleriyle yapılır (§23).
- Odak: `tvFocus()` 7 dp halka + %3.5 büyüme; listelerde tek büyük hedef; `focusGroup()` menülerde.
- Güvenli alan `LocalSafeArea` ile verilir: TV'de 40 dp yatay / 22 dp dikey overscan payı,
  diğer cihazlarda sistem çubukları + çentik insetleri + küçük bir pay.

---

## 9. Depolama şeması

| Yol | İçerik |
|---|---|
| DataStore `retrobit_settings` | Dil, tema, oynatıcı modu, varsayılan emülatör, ROMS yolu/URI, onboarding, arayüz modu, destekçi durumu (`supporter_unlocked`, `supporter_source`, `supporter_name`, `supporter_listed`), `library_shelf`, `cover_size`, `custom_covers`, `attract_mode` |
| `databases/retrobit.db` | Room kütüphanesi (`library_games`, `play_seconds` dahil) + favoriler |
| `files/data/covers.json` | Kapak URL önbelleği (yalnızca isabetler) |
| `files/covers/custom_<gameId>.jpg` | Kullanıcının yüklediği özel kapak (destekçi) |
| `cacheDir/showcase/showcase.png` | Üretilen koleksiyon vitrini görseli (FileProvider: `<applicationId>.files`) |
| `applicationInfo.nativeLibraryDir/lib*_libretro_android.so` | AAB/APK'ye gömülü libretro çekirdekleri |
| `files/cores/*.so` | Eski sürümlerden kalan, artık yüklenmeyen çekirdekler |
| `files/system/` | BIOS / sistem dosyaları (kullanıcı sağlar) |
| `files/saves/` | `<gameId>.srm`, `<gameId>.state` |
| `files/roms/<consoleId>/` | SAF ile içe aktarılan dosyalar ve açılan arşivler |
| `files/roms/.import/` | Geçici arşiv hazırlama alanı |

`library_games` şeması (Room, v2): `id`, `title`, `console_id`, `file_path` (UNIQUE), `file_name`,
`added_at`, `last_played_at`, `play_count`, `play_seconds`, `catalog_id`, `cover_url`,
`pending_entry`, `unpacked_path`.

`pending_entry`, disk arşivi içindeki ana üye adıdır; `unpacked_path` ilk açılışta çıkarılan
dosyanın yoludur. `play_seconds`, oyuncu yüzeyi hazır olduğundan itibaren `SystemClock.elapsedRealtime()`
ile ölçülür ve oyundan çıkışta (veya `onPause`) yazılır; istatistikler tamamen cihazda kalır.

---

## 10. Derleme, imzalama, kurulum

```bash
# Debug
./gradlew assembleDebug

# Release (imzalı, lintVital dahil)
./gradlew assembleRelease --console=plain

# Tek modül temizliği
./gradlew clean assembleRelease
```

- Release imzası yalnız `RETROBIT_STORE_FILE`, `RETROBIT_STORE_PASSWORD`,
  `RETROBIT_KEY_ALIAS` ve `RETROBIT_KEY_PASSWORD` ortam değişkenlerinin tamamı verildiğinde
  etkinleşir. Anahtar ve parolalar depoya eklenmez.
- `isMinifyEnabled = false`, `isShrinkResources = false`. R8 açılacaksa LibretroDroid/JNI için
  keep kuralları eklenmelidir.
- `resourceConfigurations += listOf("en", "tr")`.
- Sürüm yükseltirken `versionCode` ve `versionName` birlikte artırılmalı.
- JitPack gerekli: `settings.gradle.kts` içinde `maven("https://jitpack.io")`.

Kurulum / çalıştırma:

```bash
ADB="$ANDROID_HOME/platform-tools/adb.exe"
"$ADB" -s 127.0.0.1:16384 install -r app/build/outputs/apk/play/release/app-play-release.apk
"$ADB" -s 127.0.0.1:16384 shell monkey -p com.retrobit.app -c android.intent.category.LAUNCHER 1
```

---

## 11. Test cihazı iş akışı (MuMu / Android TV)

- Cihaz: `adb -s 127.0.0.1:16384` (yerel MuMu örneği).
- `adb` PATH'te değilse Android SDK yolunu kullanın:
  `$ANDROID_HOME/platform-tools/adb.exe`
- Git Bash'te `/sdcard/...` yolları bozulmasın diye `export MSYS_NO_PATHCONV=1`.
  Bu ayarla `adb install` **Windows yolu** ister (`C:/...`); `/c/...` "failed to stat" verir.
- Ekranı TV düzenine zorla: `wm size 1920x1080; wm density 280` → ~1097 dp genişlik.
- MuMu'da `FEATURE_LEANBACK` yoktur; TV yerleşimi Ayarlar → Arayüz → "Android TV" ile zorlanır.
- Test ZIP'i: `/storage/emulated/0/Download/ROMS.zip` (düz Download yolu kullanın;
  `$MuMu12Shared` kabuk kaçışları gerektirir).

Faydalı komutlar:

```bash
export MSYS_NO_PATHCONV=1
ADB="$ANDROID_HOME/platform-tools/adb.exe -s 127.0.0.1:16384"

# Ön plandaki uygulamayı doğrula (tuş enjeksiyonu yanlış uygulamaya gitmesin)
$ADB shell dumpsys window | grep mCurrentFocus

# D-pad / OK / geri
$ADB shell input keyevent 19   # yukarı
$ADB shell input keyevent 20   # aşağı
$ADB shell input keyevent 21   # sol
$ADB shell input keyevent 22   # sağ
$ADB shell input keyevent 23   # OK
$ADB shell input keyevent 4    # geri
$ADB shell input keyevent 82   # MENU (ana ekranda Ayarlar'ı açar; oynatıcıda ÇIKIŞ!)

# Ekran görüntüsü + hiyerarşi
$ADB exec-out screencap -p > "$TEMP/screen.png"
$ADB shell uiautomator dump /sdcard/ui.xml
$ADB pull /sdcard/ui.xml "$TEMP/ui.xml"

# Uygulama veritabanı (root gerekir; Room, snake_case kolonlar)
printf 'SELECT id,title,console_id,play_count FROM library_games;\n' | \
  $ADB shell su -c 'sqlite3 /data/data/com.retrobit.app/databases/retrobit.db'
# printf'te % kaçışı: %% ; SQL metinlerinde çift tırnak kullanın.

# Oynatıcı dışa aktarılmadığı için (exported=false) kabuktan am start REDDEDİLİR;
# oyunu arayüzden başlatın (gamelist'te kart odakta → OK / keyevent 23).

# Gerçek gamepad olayları (MuMu /dev/input/event5): her tuş için bas + sync, bırak + sync
# 304=A 305=B 307=X 308=Y 314=SELECT 315=START 312=L2 313=R2
$ADB shell su -c "sendevent /dev/input/event5 1 304 1; sendevent /dev/input/event5 0 0 0"  # A bas
$ADB shell su -c "sendevent /dev/input/event5 1 304 0; sendevent /dev/input/event5 0 0 0"  # A bırak

# Uzun A (detay, ≈550 ms): bas, sleep 0.85, bırak
# Oyun içi menü: SELECT+START birlikte ≈1.1 s basılı (MENU_HOLD_MS=650); L2+R2 de olur
$ADB shell su -c "sendevent /dev/input/event5 1 314 1; sendevent /dev/input/event5 1 315 1; sendevent /dev/input/event5 0 0 0"
sleep 1.1
$ADB shell su -c "sendevent /dev/input/event5 1 314 0; sendevent /dev/input/event5 1 315 0; sendevent /dev/input/event5 0 0 0"
```

Notlar:

- `input keyevent 82` (MENU) oynatıcıda geri tuşu davranışını tetikler ve oyundan çıkarır; menüyü
  açmak için kombinasyonu kullanın. Ana ekranda/kütüphanede Ayarlar'ı açar.
- Enjeksiyondan önce `dumpsys window` ile Retrobit'in ön planda olduğundan emin olun; aksi hâlde
  tuşlar başka uygulamaya gider.
- `sendevent` cihaza özgüdür: doğru `eventN` ve tuş kodlarını `getevent -lp` ile doğrulayın.
- `pm clear com.retrobit.app` DB'yi VE çıkarılmış ROM kopyalarını siler; `adb install -r` veriyi korur.
- Windows'ta `/tmp` Python tarafından okunamayabilir; geçici dosyalar için `$TEMP` kullanın ve
  adb çıktısındaki `\r\n` karakterlerini JSON ayrıştırmadan önce temizleyin.

---

## 12. Sürüm geçmişi ve yapılan işler

| Sürüm | Öne çıkanlar |
|---|---|
| 1.0.0 | Referans APK analizi; Compose iskeleti; kütüphane, konsollar, favoriler, kapaklar; gömülü oynatıcı; klasör içe aktarma. |
| 1.1.0 | Toplu klasör içe aktarma; kütüphane ekranının yeniden tasarımı; ilk cihaz doğrulamaları. |
| 1.2.0 | Minimal ve TV'ye duyarlı arayüz; ilk kurulum ekranı; tüm ekranlardan "klasörden içe aktar" butonlarının kaldırılması; katalog/homebrew yüzeylerinin temizlenmesi. |
| 1.3.0 | Android TV uyumluluğu; SAF'sız doğrudan `ROMS` taraması; TV dostu uygulama içi klasör tarayıcı; USB gamepad tuş/analog yönlendirmesi. |
| 1.4.1 | Ana ekranda canlı tarama ilerlemesi; ROM'ları yerinde referanslama ve arşiv açmayı erteleme. |
| 1.4.3 | Kapak hattı düzeltmesi (kaçırmaların önbelleğe yazılmaması); TV odak navigasyonunun tamamen elden geçirilmesi; L2+R2 / SELECT+START oyun menüsü ve "Oyundan çık"; `PSX` klasörünün tanınması; ikincil depolama köklerinin taranması; 7z'in `.zip` olarak gelmesinin desteklenmesi; N64/MAME/NDS doğrulaması; ölü indirme/katalog/RAWG kodunun ve kullanılmayan metinlerin kaldırılması. |
| 1.5.0 | Room veritabanı; 5 slotlu durum kaydı; harici bellek yedekleme/geri yükleme; gamepad tuş eşleme; sekme/odak düzeltmeleri; fast-forward kaldırıldı. |
| **1.6.0** | Play Store dağıtımı için `play`/`sideload` flavor ayrımı (MANAGE_EXTERNAL_STORAGE yalnızca sideload'da); oyun bazlı shader + ekran oranı ve "Tüm oyunlara uygula"; varsayılan oranın açılışta uygulanması ve CORE oranının düzeltilmesi; slot zaman damgasının kayıttan sonra yenilenmesi; yedekleme/geri yükleme sağlamlaştırması (izin istemi, hata mesajları, geri yükleme onayı, ön yedek); TV'de H3 sekme geri dönüş hatası; okunamayan ROM için hata ekranı. |
| **1.6.1** | Mi Box saha geri bildirimi: gamepad varsayılanı Xbox; gamepad A/B → DPAD_CENTER/BACK (arayüzde A ile seçim); L2+R2 menüsünün TV yüksekliğine sığdırılması ve "Oyundan çık"ın erişilebilir olması; shader'ın oyun içinde anında değişmesi; **ekran oranı özelliğinin kaldırılması** (LibretroDroid viewport API'si asla esnetemiyor); ilk kurulum dışındaki klasör taramalarının sessizleştirilmesi; Play sürümü için "Yol gir" ile okunamayan klasörde net yönlendirme. |
| **1.7.0** | Destekçi modeli (tek seferlik `supporter_pack`): destekçi rozeti + opt-in "Destekçiler" ekranı ve Hakkında teşekkürü; OLED/amber/yeşil fosfor/konsol temaları; shader paketleri (aperture grille, eğimli scanline, LCD dot-matrix, handheld hayalet iz); raf/grid + kapak boyutu + özel kapak; TV attract mode (90 sn); yerel oyun istatistikleri; save slotu 2 ücretsiz / 5 destekçi; paylaşılabilir koleksiyon vitrini; Ayarlar'da CD anahtarı ile test/grant. |
| **1.8.0** | Çoklu ROM klasörü (SAF kök listesi), USB/SD algılama + tek dokunuşla izin, ön planda imza tabanlı anlık diff taraması (60 sn → 5 sn eşik); **Play kurulum akışı yenilendi** (ZIP veya tek tek ROM seçimi, "ROM eklemeden devam et", sade yönergeler); ZIP içe aktarmada `openFileDescriptor` + `/proc/self/fd` ile kopyalamasız okuma (878 MB → ~126 sn); kütüphanede kısa A = oyunu başlat, uzun basış = detay, B = geri; güvenlik: PBKDF2 CD anahtarı, Play'de CD girişi kapalı, Billing RSA imza doğrulaması, all-files izni yalnızca sideload'da, yedekleme çıkarma kuralları; Play yayın rehberi ve güvenlik değerlendirmesi. |
| **1.9.0** | Retro konsol arayüzü (Batocera/RetroBat tarzı): sistem karuseli ana ekran, konsol temalı gamelist (CRT snap + kapak + üst veri), yeniden yazılan Favoriler/Arama, `RetroSheet` menüleri; özgün konsol arka planları + metin wordmark'lar; PixelFont/TechFont; alt bar ve yan ray kaldırıldı; `uiScanlines` ayarı. |
| **1.9.1** | **Play-only dağıtım** (sideload askıda); retro tema kapısı (`supporterUnlocked \|\| retroThemeTest`) + Ayarlar'da test anahtarı; ücretsiz arayüz aynı düzenle düz Material yüzeyler (koyu/açık); MD sahte kart düzeltmesi (MAME `parentHint` + `claim()` tekilleştirme, `SEQUEL_START` başlık koruması); ZIP içe aktarma TV regresyonu için iki katmanlı fallback (fd → kopyala+aç); kurulum yönergeleri sadeleştirildi; Ayarlar anahtar satırları D-pad erişilebilir (`SettingSwitchRow`). İkinci tur: boş konsollar karuselden kaldırıldı (G3); ZIP içe aktarma tek toplam sayaç + "açılıyor" alt satırı (G4); favori onay toast'ı (G5); TV'de oyuncu üst çubuğu gizli (G6); MAME kısa ad → tam başlık sözlüğü + libretro/Wikipedia kapak çözümü (G7); ZIP kartında 2 satır özet + rehber bağlantısı (G9). |

1.4.3 temizliğinde kaldırılanlar:

- `RomDownloader.kt` ve kütüphane/ViewModel içindeki tüm indirme bölümü (ticari ROM indirme riski
  taşıyan, arayüzden erişilemeyen ölü kod).
- RAWG anahtarı ve RAWG istemcisi; kullanılmayan Wikipedia durum katmanı.
- Kullanılmayan katalog API'leri (`featured`, `byConsole`, `search`, `freeGamesForConsole`, …).
- 123 kullanılmayan metin × 2 dil; yinelenen `values-en/` klasörü (lint `ExtraTranslation`
  hatasının kaynağı).
- Çağrısı olmayan yardımcılar: `coverUrlFor`, `importFolder`, `ConsoleCatalog.require`,
  `CoreManager.totalBytes`, `EmulatorManager.recommendedEmulator`, `CatalogGame.localizedTitle`.

---

## 13. Dikkat edilmesi gerekenler (tuzaklar)

1. **`values-en/` oluşturmayın.** Varsayılan `values/` zaten İngilizcedir; ikinci bir İngilizce
   klasör `lintVitalRelease` içinde `ExtraTranslation` hatası verir.
2. **Kapak kaçırmalarını kalıcı yazmayın.** Yalnızca bulunan URL `covers.json`'a girer; aksi hâlde
   geçici ağ hatası oyunu kalıcı olarak kapaksız bırakır.
3. **Klasör taramasında disk arşivlerini açmayın.** Yüzlerce MB kopyalamak taramayı dondurur;
   `keepInPlace` + `pendingEntry` + `prepareRom()` kullanın.
4. **`.cue` + `.bin` çiftlerinde `bin`'i atlayın**, yoksa aynı oyun iki kez görünür.
5. **Arşiv türünü uzantıdan değil baytlardan okuyun.** Sahne 7z'leri `.zip` adıyla gelir.
6. **Arşiv çıkarma yolunu doğrulayın** (`insideFolder` / `canonicalFile`); zip-slip'e izin vermeyin.
7. **`bin` belirsizliği:** ipucu yoksa Genesis sayılır. Yeni bir konsol eklerseniz
   `consoleForFile()` ve `consoleHint()` eşlemelerini birlikte güncelleyin.
8. **İkincil depolama:** ROMS bazen `/storage/emulated/0/$MuMu12Shared/ROMS` gibi bir seviye
   derindedir; `StorageFolders.findDefaultRoms()` bunu bilinçli olarak tarar.
9. **Ticari ROM/BIOS indirmeyin veya barındırmayın.** Bu proje yalnızca kullanıcının seçtiği yerel içeriği içe aktarır.
10. **Play sürümünde çalıştırılabilir kod indirmeyin.** Libretro çekirdekleri AAB/APK içinde olmalı; Buildbot yalnız kontrollü geliştirme girdisi olabilir.
11. **Reklam/izleme SDK'sı eklemeyin.** İsteğe bağlı üst veri/kapak sorguları Wikipedia, Wikidata ve GitHub'a gider; Play Billing ayrıca Google Play'e bağlanır.
12. **Sürüm alanlarını birlikte artırın** (`versionCode` + `versionName`).
13. **Keystore'u paylaşmayın.** Mevcut anahtar test içindir; mağaza öncesi değiştirin.
14. **TV'de küçük dokunmatik hedefler koymayın.** TV'de overlay hiç gösterilmez; her işlem D-pad ile
    erişilebilir olmalı.
15. **Odak halkası görünür olmalı.** `tvFocus()` kullanmadan `clickable` eklemek 3 metreden
    görülemeyen bir arayüz üretir.
16. **Oynatıcıda `KEYCODE_MENU` (82) çıkışa gider.** Menü için L2+R2 / SELECT+START kombinasyonunu
    kullanın.
17. **Gamepad eşlemesi takaslıdır** (A↔B, X↔Y). Yeni tuş eklerseniz `gamepadKey()` ve
    `gamepadKeys` kümesini birlikte güncelleyin.
18. **Kombinasyon zamanlayıcısı tuş tekrarıyla yeniden kurulmamalı** (`comboArmed`), yoksa menü
    hiç açılmaz.
19. **`onPause` SRAM kaydeder.** Oynatıcıya yeni bir duraklatma yolu eklerseniz
    `pauseForSave()` çağırın.
20. **`library.json` şemasını bozmayın.** `ignoreUnknownKeys = true` eski kayıtları okur; alan
    kaldırırken varsayılan değer bırakın.
21. **`clearLibrary()` kullanıcı ROM'larını silmez**; yalnızca uygulamanın sahip olduğu kopyaları
    siler. Bu davranışı koruyun.
22. **`lintVitalRelease` release derlemesinde çalışır.** Metin silerken iki dili birlikte
    güncelleyin.
23. **`local.properties` makineye özgüdür**; commit etmeyin.
24. **MuMu'da tuş enjeksiyonu öncesi ön planı doğrulayın**; aksi hâlde tuşlar başka uygulamaya gider.
25. **Windows/Git Bash:** adb tam yolla çağrılmalı, `MSYS_NO_PATHCONV=1` kullanılmalı, `/tmp` yerine
    `$TEMP`.

---

## 14. Bilinen sınırlamalar

- **Kapaksız oyunlar:** Test kütüphanesindeki ~21 kayıt için upstream'de kapak yok (MAME kısa set
  adları `dino.zip`/`mvsc2.zip`, hack/homebrew, birleşik kartuşlar, BS kartuşları). Kod doğru
  çalışıyor; kaynak yok.
- **NDS:** Çift ekran tek ekrana ortalanır; geniş ekran TV'de yanlarda siyah bant oluşur.
- **MAME:** `mame2003_plus` yalnızca 0.78 setleriyle uyumludur; güncel setler açılmaz.
- **PS1/PSP:** BIOS/sistem dosyaları kullanıcı tarafından sağlanmalıdır; bazı oyunlar
  BIOS'suz açılmaz.
- **N64:** Düşük GPU'lu kutularda `mupen64plus_next_gles3` yavaş olabilir.
- **`bin` uzantısı:** 32X gibi kartuşlar ipucusuz Genesis olarak sınıflanabilir.
- **Leanback bayrağı:** Bazı TV kutularında yoktur; TV yerleşimi elle seçilmelidir.
- **Harici emülatör modu:** Kurulu paket yoksa hata mesajı verir; otomatik kurulum yapmaz.
- **Arşiv içi disk oyunları:** İlk açılışta çıkarılır; o an depolama alanı gerekir.

---

## 15. Geliştirici kontrol listesi

Bir değişiklikten sonra:

- [ ] `./gradlew assembleRelease --console=plain` hatasız.
- [ ] Yeni metinler hem `values/strings.xml` hem `values-tr/strings.xml` içinde.
- [ ] `values-en/` yok.
- [ ] TV'de (veya Ayarlar → Arayüz → Android TV zorlanmış telefonda) D-pad ile tüm ekranlar
      erişilebilir.
- [ ] Kütüphane taraması ilerleme gösteriyor ve çökme yok.
- [ ] Kapak önbelleği yalnızca isabetleri yazıyor.
- [ ] Oyun menüsü L2+R2 ve SELECT+START ile açılıyor; "Oyundan çık" çalışıyor.
- [ ] Kullanıcı ROM'ları silinmiyor.
- [ ] Ticari ROM indirme kodu eklenmedi.
- [ ] Sürüm numarası gerekiyorsa artırıldı.

---

## 16. Temizlik kaydı (bu çalışma alanında yapılanlar)

Kaldırılanlar:

- `analysis/`, `tools/`, `vibe_images/` klasörleri.
- Eski APK'lar: `Retrobit-1.0.0`, `1.1.0`, `1.2.0`, `1.3.0`, `1.4.1`.
- `combo.sh`, `combo2.sh`, `getevent.log`, `scratch_*.json` ve kök dizindeki tüm geçici ekran
  görüntüleri.
- Ölü indirme/katalog/RAWG kodu, 123×2 kullanılmayan metin, `values-en/`.
- Çağrısız altı yardımcı fonksiyon.

Korunanlar:

- `ROMS/` — kullanıcının test ROM'ları.
- `ROMS.zip` — kullanıcının kendi arşivi (uygulama tarafından kullanılmıyor; yedek olarak duruyor).
- `reference/Android64-v1.1.5.apk` — analiz referansı.
- `Retrobit/art/` ve `Retrobit/docs/screenshots/` — projeye taşındı.

Çalışma alanı boyutu bu temizlikte yaklaşık 3723 MB → 3170 MB'a indi; kalan büyüklüğün neredeyse
tamamı `ROMS/` + `ROMS.zip` (+ 391 MB üretilen `app/build/`).

---

## 17. Lisans ve dağıtım notları

- Uygulama kodu GPL-3.0 olarak yayımlanmalıdır; public kaynak deposunda kök lisans ve complete corresponding source bulunmalıdır.
- Paket içi lisans metinleri ve bileşen listeleri `app/src/main/assets/licenses/` altındadır.
- On bir çekirdeğin tamamı kayıtlı exact commitlerden Android NDK 28.0.13004108 ile dört Play ABI'si için üretildi; hashler bileşen listesinde, tekrar üretim komutları `tools/build-android-cores.sh` içindedir.
- Native source yayınında recursive submodule içerikleri ile melonDS, PPSSPP ve Stella build patch'leri korunmalıdır.
- libretro-thumbnails görselleri ilgili hak sahiplerine aittir; ticari oyun kapakları mağaza görsellerinde kullanılmaz.
- İsteğe bağlı oyun/dosya adı sorguları Wikipedia, Wikidata ve GitHub hizmetlerine gider; veri güvenliği beyanı ve gizlilik politikası bunu açıkça belirtir. Reklam/izleme SDK'sı yoktur.

---

## 18. v1.4.3 Sonrası Geliştirmeler ve Proje Devir Notları (v1.5.0)

Bu bölüm, v1.4.3 sürümünden sonra gerçekleştirilen Faz 1 - Faz 5 mimari geliştirmelerini, giderilen kritik çalışma zamanı hatalarını ve sonraki geliştiricinin dikkat etmesi gereken teknik ayrıntıları içermektedir.

### 18.1 Veritabanı ve Kütüphane Mimarisi (Room Database Geçişi)
- **Eski Durum:** Kütüphane oyun listesi `library.json` adında düz bir JSON dosyasına seri hale getirilerek yazılıyordu. Yüzlerce ROM tarandığında dosya I/O darboğazı oluşuyor ve silinen dosyalar senkronize edilemiyordu.
- **Yapılan İyileştirme:**
  - Android Jetpack Room (`RoomDatabase`, SQLite) mimarisine geçildi (`RetrobitDatabase`, `LibraryGameDao`, `LibraryGameEntity`).
  - Taramalar arka planda Room veritabanına asenkron yazılırken `LibraryRepository.library` `StateFlow` ile UI'a anlık ve reaktif beslenir; böylece offline önbellekleme sağlanır ve UI donması önlenir.
  - **Orphan (Yetim Kayıt) Temizliği:** Kütüphanede "Şimdi Eşitle" dendiğinde cihaz depolamasında silinmiş olan ROM dosyaları (ör. kullanıcının sildiği BS Mario kartuşları) tespit edilerek Room veritabanından otomatik olarak temizlenir.

### 18.2 Kayıt ve Yedekleme Sistemi (Multi-Slot & External Storage)
- **Slotlar:** `${gameId}_slot1.state` .. `${gameId}_slot5.state`; 1.7.0'dan itibaren **ücretsiz 2, destekçide 5** slot açıktır (`FREE_SLOTS` / `SUPPORTER_SLOTS`).
- **Slot Seçici ve Zaman Damgası:** `SaveSlotSelector` bileşeni ile oyun içi menüden slotlar arasında geçiş yapılabilir; her slotun en son kaydedildiği tarih/saat gösterilir.
- **Harici Depolamaya Yedekleme / Geri Yükleme:** Ayarlar ekranına Android SAF tabanlı "Kayıtları Dışa Aktar" ve "Kayıtları İçe Aktar" özelliği eklenmiştir. Tüm `.state` ve `.srm` dosyaları bir `.zip` arşivi olarak seçilen harici depolamaya aktarılabilir veya oradan geri yüklenebilir.

### 18.3 Çekirdek (Core) Yönetimi
- **Gömülü ikililer:** Tüm çekirdekler AAB/APK'nin `jniLibs` ağacındadır; Android kurulumda cihaz ABI'sine uygun ikiliyi `nativeLibraryDir` altına çıkarır.
- **Çevrimdışı çalışma:** `CoreManager` yalnız paketlenmiş ikilileri çözer. Ağdan çekirdek veya çalıştırılabilir kod indirmez; eski `files/cores/` içeriği yalnız ölçülebilir ve kullanıcı isterse temizlenebilir.

### 18.4 TV ve Gamepad Arayüz Gezinti Düzeltmeleri
- **Konsollar → Ayarlar Atlama Hatası:** NavigationRail üzerinde Konsollar sekmesi seçildiğinde klavye/dpad odağının doğrudan Ayarlar sekmesine atlamasına yol açan odaklanma karmaşası `focusGroup` ve açık `firstFocus` yönlendirmeleriyle düzeltildi.
- **Favoriler Sekmesi Onarımı:** Boş/hatalı çalışan Favoriler sekmesi, favori oyunların filtrelendiği, grid yerleşimli ve doğrudan oyunu başlatan stabil bir ekrana dönüştürüldü.
- **D-Pad İçerik / Menü Geçişleri:** TV'de menüden sağa (`DPAD_RIGHT`) basıldığında ilgili sekmenin ilk öğesine odaklanması, sola (`DPAD_LEFT`) basıldığında menü çubuğuna geri dönmesi güvenceye alındı.

### 18.5 Gamepad Özelleştirme & Tuş Eşleme
- Ayarlar ekranına `GamepadRemapDialog` eklenerek Button A, B, X, Y, L1, R1, L2, R2, Select, Start tuşlarının kullanıcı tarafından özelleştirilebilmesi sağlandı. Eşlemeler `DataStore` üzerinde kalıcı olarak saklanır.

### 18.6 Emülatör Kararlılığı ve Hızlı İleri Sarma (Fast-Forward) Kaldırılması
- **LibretroDroid observable Hatası:** `com.swordfish.libretrodroid:LibretroDroid:0.14.0` bağımlılığı içerisindeki `GLRetroView.frameSpeed` ve `viewport` Kotlin delegate kodunda `afterChange` metodunda `newValue` yerine yanlışlıkla `oldValue` C native katmanına (`setFrameSpeed(0)`) iletilmektedir.
- Bu durum hız kilidini sınırsıza açmakta, Oboe ses kuyruklarında tampon taşmasına (NDS MelonDS `FifoBuffer::readNow` SIGSEGV) ve N64 dinamik yeniden derleyicisinde bellek yığını bozulmasına (Mupen64plus `Scudo ERROR: corrupted chunk header`) sebep oluyordu.
- Kullanıcı talimatı doğrultusunda **hızlı ileri sarma özelliği ve tüm bağlantılı UI/controller kodları tamamen iptal edilip kaldırıldı.**
- Oyunlar %100 orijinal 1x hızında, 60 FPS akıcı ve hatasız çalışır hâle getirildi.
- **Yüzey Senkronizasyonu (`surfaceReady`):** Emülasyon yüzeyi oluşurken eşzamanlı native çağrılar kaldırıldı; render thread kilitlenmeleri önlendi.

### 18.7 Oyun İçi Menü & Geri Tuşu Entegrasyonu
- `RetroPlayerActivity.dispatchKeyEvent` metoduna açık `KeyEvent.KEYCODE_BACK` yakalama eklendi; TV kumandası veya Gamepad geri tuşuna basıldığında doğrudan `onBackPressedDispatcher.onBackPressed()` tetiklenerek `PlayerMenu` açılması sağlandı.
- Menü açıkken B tuşuna (`KEYCODE_BUTTON_B`) basıldığında menü kapanıp oyuna geri dönülmektedir.
- `onNewIntent` eklenerek arka arkaya farklı oyunlar başlatıldığında `singleTop` activity kilitlenmesi engellendi.

### 18.8 8 Konsol Doğrulama Matrisi
Aşağıdaki 8 konsolun tamamı oyun içi çalışma, durum kaydetme, durum yükleme, sürdürme ve çıkış adımlarıyla tam test edilmiş ve onaylanmıştır:
- GBA (`mgba`): Golden Sun — Başarılı (452 KB state)
- Genesis (`genesis_plus_gx`): Alex Kidd — Başarılı (900 KB state)
- MAME (`mame2003_plus`): Cadillacs & Dinosaurs — Başarılı (400 KB state)
- N64 (`mupen64plus_next_gles3`): Super Mario 64 — Başarılı (16 MB state, 60fps)
- NDS (`melonds`): New Super Mario Bros. — Başarılı (6.2 MB state, senkron ses)
- NES (`fceumm`): 1200-in-1 — Başarılı (13 KB state)
- PS1 (`pcsx_rearmed`): Metal Slug X — Başarılı (4.2 MB state)
- SNES (`snes9x`): Aladdin — Başarılı (804 KB state)

### 18.9 Yeni Geliştiriciye Önemli Uyarılar (DİKKAT!)
1. **ASLA `values-en/` KLASÖRÜ OLUŞTURMAYIN:** Varsayılan dil `res/values/strings.xml`, Türkçe `res/values-tr/strings.xml` dosyasındadır. Android kaynak çözücüsü `values-en/` olduğunda yerel ayarları karıştırabilir.
2. **`LibretroDroid.frameSpeed` ve `viewport`'a DOKUNMAYIN:** LibretroDroid AAR delegate hatası nedeniyle bu özelliklere runtime müdahalesi native SIGSEGV ve bellek bozulmasına yol açar.
3. **ROM İÇE AKTARMA & ÇEKİRDEK GÜVENLİĞİ:** Bu proje hiçbir ticari ROM barındırmaz. Kullanıcının cihazındaki dizinleri (`/ROMS/` vb.) tarar. Kullanıcı dosyalarını silen ya da değiştiren kod yazmayın.
4. **ANDROID TV / D-PAD UYUMLULUĞU:** Eklenen her yeni ekranda TV kumandası (`DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`, `DPAD_CENTER`) ile gezinilebildiğini ve odak halkasının (focus border) görünür olduğunu test edin.

---

## 19. v1.6.0 — Play Store Dağıtımı, TV-First ve Oyun Bazlı Görüntü Ayarları

### 19.1 Dağıtım flavor'ları

| Flavor | BuildConfig | Depolama erişimi | Çıktı |
|---|---|---|---|
| `play` | `ALL_FILES_ACCESS = false` | Yalnızca SAF (`ACTION_OPEN_DOCUMENT_TREE`); içe aktarılan ROM'lar `files/roms/<console>/` altına kopyalanır | `app-play-release.apk` / `.aab` |
| `sideload` | `ALL_FILES_ACCESS = true` | `MANAGE_EXTERNAL_STORAGE` + doğrudan dosya taraması, uygulama içi klasör tarayıcı | `app-sideload-release.apk` |

- Manifest izinleri flavor'a göre `src/play/AndroidManifest.xml` ve `src/sideload/AndroidManifest.xml` içinde ayrılır; birleşik manifestte play tarafında `MANAGE_EXTERNAL_STORAGE` **bulunmamalıdır**.
- `directFileAccessAvailable()` (`ui/components/Common.kt`): SDK ≤ P veya (`ALL_FILES_ACCESS` && SDK ≥ R). Kurulum/ayarlar ekranları bu fonksiyonla SAF ve direct access arasında dallanır.
- Play flavor'da klasör seçimi `rememberTreePicker` ile SAF açar; DocumentsUI bulunmayan TV kutularında `file_picker_missing` toast'u gösterilir. **TV'lerde tam işlevsellik için sideload APK dağıtılmalıdır.**

### 19.2 Yedekleme (davranış korunmuştur, konum aynı)

- Konum her iki flavor'da da `Download/Retrobit_Backups/`.
- API 29+ play flavor'ı `MediaStore.Downloads` (`RELATIVE_PATH`, `IS_PENDING`) ile yazar; bu nedenle all-files izni gerekmez. Sideload ve API < 29 doğrudan `File` API kullanır.
- MediaStore yalnızca uygulamanın kendi oluşturduğu yedekleri listeler; sideload'ın dosya sistemine yazdığı eski yedekler play flavor'ın listesinde görünmez (beklenen davranış).
- Geri yükleme artık onay ister ve öncesinde mevcut kayıtların güvenlik yedeğini alır.

### 19.3 Oyun bazlı görüntü ayarları

- `GameDisplayPrefs(shader)` JSON haritası `game_display_overrides` anahtarında DataStore'da tutulur (`SettingsRepository.setGameDisplay`). `Json { ignoreUnknownKeys = true }` sayesinde 1.6.0'da yazılmış `aspect` alanlı eski kayıtlar sorunsuz okunur.
- Oyun içi menüde seçilen değer yalnızca o oyunu etkiler; "Tüm oyunlara uygula" (`applyDisplayToAll`) global varsayılanı yazar ve override haritasını temizler.
- Shader değişimi `PlayerController.setShader` ile çalışma anında (`GLRetroView.shader`) uygulanır; açılışta `GLRetroViewData.shader` üzerinden gelir.

### 19.4 Zaman damgası ve hata görünürlüğü

- Slot zaman damgası kaydetme/yükleme sonrası `slotVersion` anahtarıyla yeniden okunur.
- `RomImporter.prepareRom` artık `File.canRead()` denetler; erişilemeyen bir ROM çekirdeğe verilmez, oyuncu siyah ekran yerine `player_error_game` ekranını görür.

---

## 20. v1.6.1 — Mi Box Saha Düzeltmeleri ve Ekran Oranının Kaldırılması

### 20.1 Gamepad varsayılanı ve arayüz onayı

- `SettingsData.gamepadLayout` varsayılanı `GAMEPAD_XBOX`; Settings'te "Xbox / Standart (A altta, B sağda)" seçili gelir.
- `MainActivity.dispatchKeyEvent` gamepad `KEYCODE_BUTTON_A/B` (96/97) ve `BUTTON_1/BUTTON_2` (188/189) olaylarını `DPAD_CENTER/BACK`'e çevirir; Compose tarafında ek bir işlem gerekmez. Kumandanın `ENTER`/`DPAD_CENTER` akışı değişmedi.

### 20.2 Oyun içi menü (L2+R2 / SELECT+START)

- Menü `heightIn(max = screenHeight - 48.dp)` + `verticalScroll` ile TV ekranına sığar; tüm satırlara D-pad ile ulaşılır.
- Üst satırda **"Sürdür" ve "Oyundan çık" yan yana** durur (exit `danger = true`); D-pad sağ ile çıkışa geçilir, merkez tuşu `pauseForSave()` sonrası `onExit()` çağırır. 1.6.1'in ilk derlemesindeki alttaki çıkış satırı kaldırıldı.
- **"Başka emülatör" kaldırıldı** (menü, çekirdek hata ekranı ve GL hata katmanı): TV kutularında harici emülatör bulunmadığı için sessizce başarısız oluyordu. Ayarlar'daki "Harici oynatıcı" modu (`player_mode`) etkilenmedi.
- Shader satırı `cycleShader()` ile çalışma anında değiştirir (kod: `PlayerController.setShader`); "Tüm oyunlara uygula" yalnızca shader'ı global varsayılana yazar.

### 20.3 Ekran oranı neden kaldırıldı

- LibretroDroid 0.14.0'da `GLRetroView.viewport` → `VideoLayout::updateViewportSize` → `updateForegroundVertices` zinciri içeriği **viewport kutusu içine çekirdeğin en-boy oranıyla sığdırır** (`contentAspect = aspectRatio`). API esnetme (stretch) yapamaz; 4:3 çekirdeklerde 4:3/16:9/orijinal seçenekleri aynı görüntüyü üretir.
- Genel API çekirdek oranını dışa açmadığı için "gerçekten uygulanan" bir oran etiketi de üretilemez.
- Bu nedenle ekran oranı ayarı **tamamen kaldırıldı**: `SettingsData.defaultAspectRatio`, `ASPECT_RATIO_*` sabitleri, `GameDisplayPrefs.aspect`, Settings bölümü, oyuncu menüsü satırı ve `player_aspect*` metinleri silindi. Eski DataStore JSON'ları `ignoreUnknownKeys` ile okunur, veri kaybı yoktur.
- `LibretroDroid.setViewport` görüntüyü kırpma/letterbox için hâlâ kullanılabilir; ancak kullanıcıya sunulan bir seçenek olarak anlamlı olmadığı için kod da tutulmadı.

### 20.4 Arka plan taraması

- İlk kurulumda (onboarding içi `syncFolderPath/syncFolderUri`, `silent = false`) ilerleme çubuğu korunur.
- Uygulama açılışındaki otomatik tarama `silent = true` ile çalışır; Home ekranında ilerleme kartı gösterilmez.

### 20.5 Play sürümünde klasör seçimi (TV kutuları)

- DocumentsUI bulunmayan Android 11+ TV kutularında SAF seçici açılamaz (`file_picker_missing`). "Yol gir" diyalogu yazılan yolun yalnızca **doğrudan depolama erişimi** (Android 10 ve altı ya da sideload) ile çalıştığını açıkça yazar.
- Okunamayan yol denendiğinde `folder_path_unreadable` mesajı gösterilir: "sistem seçiciyle izin verin veya TV kutularında sideload sürümünü kullanın".
- **Önerilen dağıtım:** TV kutuları için sideload APK; Play kullanıcıları için tek dosya "Birlikte aç / Paylaş" ile içe aktarma (manifest'te `SEND`/`SEND_MULTIPLE` ve `application/octet-stream` `VIEW` filtreleri vardır).




---

## 21. v1.7.0 — Destekçi Paketi, TV Vitrin Modu ve Yerel İstatistikler

Gelir modeli reklam değil, **tek seferlik destekçi paketidir**; hiçbir özellik ROM dağıtımı içermez.
Destekçi olmayan kullanıcı uygulamayı tam işlevli kullanmaya devam eder; yalnızca aşağıdaki
kişiselleştirme/ek içerik katmanı kilitlidir.

### 21.1 Destekçi durumu ve satın alma

| Yol | Nasıl çalışır |
|---|---|
| Google Play | `play` flavor + `PLAY_BILLING = true`; Google Play Billing 9.1.0 (`playImplementation`). Tek seferlik ürün kimliği **`supporter_pack`**; satın alma `acknowledgePurchase` ile onaylanır, açılışta `queryPurchasesAsync` ile geri yüklenir. |
| CD anahtarı | `sideload` ve test için. Ayarlar → Destekçi paketi → "CD anahtarı" diyaloğu `AppViewModel.redeemSupporterKey()` çağırır. |
| Sideload | Play butonu yerine "bu sürümde kullanılamaz" metni gösterilir; CD anahtarı yolu açıktır. |

DataStore anahtarları: `supporter_unlocked`, `supporter_source` (`play` / `cdkey`), `supporter_name`,
`supporter_listed`. Kilit açıldığında rozet, Destekçiler ekranı girdisi, Hakkında teşekkürü ve tüm
destekçi ayarları görünür olur.

**Play Console kurulumu:** Monetize → Products → In-app products → yeni ürün `supporter_pack`
(tek seferlik, fiyatlandır → Activate) → License testing'e test hesaplarını ekleyin. AAB yüklenirken
billing izni `com.android.vending.BILLING` play manifest'indedir.

### 21.2 CD anahtarı biçimi

- Görünüm: `RB7-XXXX-XXXX-XXXX-CCCCCC` (tireler isteğe bağlı; doğrulayıcı alfanümerik olmayanları atar).
- Gövde 12 karakter, alfabe `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (I, O, 0, 1 yok).
- Son 6 karakter, gövdenin **PBKDF2-HMAC-SHA256 (100.000 iterasyon, 256 bit)** çıktısından üretilir;
  uygulamada yalnızca sağlama algoritması bulunur, anahtar listesi yoktur.
- Test anahtarı: **`RB7-GAME-RKEY-ABCD-8V3TB9`** (tiresiz de kabul edilir).
- Play flavor'da CD anahtarı girişi varsayılan olarak **kapalıdır** (`CD_KEY_ENTRY=false`);
  açmak için `-PallowCdKey=true` gerekir. Bu, mağaza sürümünde kırılma yüzeyini daraltır.
- Gizli anahtar `SupporterKeys.secret` içindedir. Üretimde değiştirmek isterseniz eski anahtarlar
  geçersiz olur; yeni anahtarları aynı algoritmayla üretin.

### 21.3 Destekçiye özel özellikler

| Özellik | Uygulama |
|---|---|
| Temalar | `THEME_OLED`, `THEME_AMBER`, `THEME_GREEN`, `THEME_NINTENDO`, `THEME_SEGA`, `THEME_PSX`. Kilitliyken `SettingsData.effectiveTheme` koyu temaya düşer; seçim yine de saklanır. |
| Shader paketleri | `aperture`, `scanline`, `dotmatrix`, `ghost`. Ücretsizler: `default`, `crt`, `lcd`, `sharp`. |
| Kütüphane düzeni | `library_shelf` (raf) ve `cover_size` (`s`/`m`/`l`); kilitliyken `effective*` yardımcıları varsayılana döner. |
| Özel kapak | Detay ekranında `GetContent("image/*")`; görsel `files/covers/custom_<gameId>.jpg` olarak kopyalanır, harita `custom_covers` DataStore anahtarındadır. "Kapağı sıfırla" dosyayı ve kaydı siler. |
| Attract mode | TV'de 90 sn girdisiz kalınca tam ekran kapak slaytı; 6 sn'de bir `Crossfade`. Herhangi bir tuş/dokunma kapatır ve zamanlayıcıyı sıfırlar. |
| İstatistikler | `StatsScreen`: toplam süre, en çok oynanan 8 oyun, konsol dağılımı. Veri `library_games.play_seconds`; bulut yok. |
| Save slotları | Ücretsiz **2**, destekçi **5** (`FREE_SLOTS` / `SUPPORTER_SLOTS`). Oyuncu menüsündeki slot seçici limiti uygular. |
| Koleksiyon vitrini | İlk 12 kapaktan 1080×1350 kolaj; `cacheDir/showcase/showcase.png`, `FileProvider` ile `ACTION_SEND`. ROM dosyası paylaşılmaz. |
| Destekçiler ekranı | Opt-in isim + rozet; veri yalnızca cihazda, sunucu yok. |

### 21.4 Shader paketlerinin doğası (önemli)

LibretroDroid'e üçüncü taraf `.glslp` yüklenmez. Paketler, oynatıcı yüzeyinin üzerine çizilen
**Compose/Bitmap desen katmanlarıdır**:

- `aperture` ve `scanline` → CRT taban shader'ı + desen katmanı.
- `dotmatrix` ve `ghost` → LCD taban shader'ı + desen katmanı.
- `ghost` (handheld hayalet iz) gerçek gecikmeli çift görüntü değil, statik bir izlenim
  (offset/blur deseni) olarak yaklaşıklanır.

Bu nedenle "shader paketi" performans açısından hafiftir; cihaz GPU'sunda ek shader derlemesi yapmaz.

### 21.5 Attract mode ayrıntıları

- Yalnızca `isTv && supporterUnlocked && attractMode` iken etkindir. `isTv`, `rememberIsTv(layoutMode)`
  sonucudur; Leanback bayrağı olmayan kutularda Ayarlar → Arayüz → **Android TV** seçilmelidir.
- Zamanlayıcı `LaunchedEffect(attractEnabled, interactionTick, currentRoute)` içinde 90 sn bekler;
  kök `Row` üzerindeki `onPreviewKeyEvent` ve `pointerInput` her etkileşimde `interactionTick`'i
  artırır. Ekran değişimi de zamanlayıcıyı sıfırlar.
- Slayt `Dialog(usePlatformDefaultWidth = false)` içindedir; odak ister ve A/OK/geri ile kapanır.
  Kapanınca alttaki ekranın odağı korunur.

### 21.6 Cihaz doğrulaması (MuMu, 30.10.2026)

- CD anahtarı `RB7-GAME-RKEY-ABCD-8V3TB9` ile kilit açıldı; rozet ve bölümler göründü, durum yeniden
  kurulumda korundu.
- Attract mode: Android TV düzeninde 90 sn sonra tam ekran slayt açıldı; geri tuşu kapattı ve D-pad
  odağı ana ekrana döndü.
- İstatistik: Alex Kidd ~28 sn oynandı; `play_seconds = 28`, Stats ekranı toplam/en çok oynanan/
  konsol dağılımı satırlarını doldurdu.
- Özel kapak: galeriden seçildi → `files/covers/custom_<id>.jpg` yazıldı, "Kapak güncellendi";
  sıfırlama dosyayı sildi, "Kapak sıfırlandı".
- Kilitli durum (veri dosyasındaki bayrak geçici olarak kapatılarak): tema koyuya döndü, raf/kapak
  boyutu/shader çipleri kilitli, Stats/Vitrin/Destekçiler satırları kilitli, oyun menüsünde yalnızca
  slot 1–2 açık (3–5 kilit ikonu + "destekçi paketine dahildir" notu). Sonra bayrak geri alındı.

### 21.7 1.7.0 için yeni tuzaklar

1. **Billing yalnızca `play` flavor'da.** `BuildConfig.PLAY_BILLING` false iken `BillingClient`
   oluşturmayın; sideload'da stub fabrika kullanılır.
2. **Ürün kimliği `supporter_pack` sabittir.** Play Console'da farklı kimlik oluşturursanız kodu
   güncelleyin; satın alma acknowledge edilmezse 3 gün sonra iade edilir.
3. **Destekçi kapıları `effective*` yardımcılarından geçmeli.** Doğrudan `settings.theme` /
   `libraryShelf` okumak kilidi atlar.
4. **Özel kapak dosyaları `filesDir` altındadır**, `cacheDir` değil; temizlenmemeli. Paylaşım
   görseli ise `cacheDir/showcase` altındadır ve `file_paths.xml`'e kayıtlıdır.
5. **Attract mode için `isTv` şart.** Leanback'siz kutuda "Otomatik" yeterli değildir.
6. **`play_seconds` yazımı oyuncu kapanışına bağlıdır.** Yeni bir çıkış yolu eklerseniz süre
   biriktiricisini de kapatın, yoksa süre kaybolur.

---

## 22. v1.8.0 — Çoklu ROM Kaynağı, USB Algılama ve Anlık Tarama

### 22.1 Çoklu SAF kökü

- `SettingsData.romsTreeUris: List<String>` asıl kaynaktır; `val treeUris` getter'ı eski tek
  `romsTreeUri` alanını tek elemanlı listeye çevirir. Yeni kodda **her zaman `treeUris`** kullanın.
- DataStore: `roms_tree_uris` (JSON liste), `scan_signatures` (kök URI → SHA-256 imza).
  `roms_tree_uri` yalnızca ilk kökle uyumluluk için senkron tutulur.
- `AppViewModel`:
  - `useFolderTree(uri)` kökü listeye ekler; aynı URI varsa `folder_duplicate` yayınlar.
  - `removeFolderTree(uri)` `releasePersistableUriPermission` çağırır, imzayı siler,
    birim kimliğini susturur ve `folder_removed` yayınlar.
  - `syncFolder()` tüm kökleri sırayla tarar; sonuçlar tek `ImportResult`'a toplanır.
- Ayarlar: `treeRoots` listesi, kök sayısı başlığı, her kök için kaldırma düğmesi ve
  `rom_folder_add`.

### 22.2 USB/SD algılama

- `StorageVolumes.removable(context)`: `isRemovable && state == MEDIA_MOUNTED && uuid != null`.
  Başlangıç URI'si `DocumentsContract.buildDocumentUri("com.android.externalstorage.documents",
  "<UUID>:")`.
- `MainActivity` dinamik alıcısı `ACTION_MEDIA_MOUNTED/UNMOUNTED/EJECT` dinler ve
  `RECEIVER_NOT_EXPORTED` ile kaydedilir.
- `RetrobitApp` kökünde `pendingVolume` diyaloğu; "Seç" `rememberTreePicker(initialUri = ...)`
  açar, "Şimdi değil" `dismissPendingVolume()` çağırır.
- Yalnızca Play build'de çalışır (`BuildConfig.ALL_FILES_ACCESS` false). Sideload doğrudan yol
  taraması kullandığı için diyalog gösterilmez.

### 22.3 Hızlı imza taraması

- `RomImporter.treeSignature`/`fileSignature`: göreli yol + dosya boyutu listesini sıralayıp
  SHA-256 üretir; içerik okumaz. Sınırlar: `SIGNATURE_LIMIT = 5000`, derinlik 12, `skippedFolders`.
- `syncTreeRoots(..., quick = true)` ve `syncFolderPath(..., quick = true)` önce imzayı
  karşılaştırır; aynıysa tarama yapılmaz. İmza her taramadan sonra `setScanSignature` ile yazılır.
- `autoSyncFolder` eşiği **5 sn**; `onResume`, medya alıcısı ve açılış bunu tetikler.
  `folderSyncActive` eşzamanlı taramayı engeller. Manuel eşitleme `quick = false` ile tam tarar.
- İlk 1.8 açılışında imza kaydı olmadığı için bir kez normal tarama yapılır; bu beklenen davranıştır.

### 22.4 Yayın yapılandırması

- `versionCode = 13`, `versionName = "1.8.0"`.
- `-PbillingPublicKey` / `RETROBIT_BILLING_KEY` → `BuildConfig.BILLING_PUBLIC_KEY`; boşsa yerel
  imza kontrolü atlanır.
- `-PallowCdKey=true` yalnızca test içindir; Play varsayılanı `CD_KEY_ENTRY=false`.
- Play manifest'inde `MANAGE_EXTERNAL_STORAGE` yok; sideload manifest'inde var.
- `allowBackup=false`, `dataExtractionRules`, `usesCleartextTraffic=false`.
- Yayın: `./gradlew bundlePlayRelease` → `app/build/outputs/bundle/playRelease/app-play-release.aab`.
- Ayrıntılı rehber: `docs/PLAY_YAYIN_REHBERI.md`; güvenlik: `docs/GUVENLIK_DEGERLENDIRMESI.md`.

### 22.5 1.8.0 tuzakları

1. **`treeUris` kullanın.** `romsTreeUri` yalnızca ilk kökü temsil eder; çoklu kökte veri kaybı
   görünümlü hatalar üretir.
2. **İmza karşılaştırması yalnızca hızlı yoldur.** Şüpheli durumda `quick = false` ile tam tarama
   yapın; imza 5000 dosyada kesilir.
3. **USB diyaloğu Play'e özgüdür.** Sideload'da `checkRemovableVolumes` erken döner.
4. **Yeni kök eklerken izin alın.** `DocumentFile` taraması `takePersistableUriPermission`
   olmadan yeniden başlatmada çalışmaz.
5. **CD anahtarı biçimi değişti.** Eski `...-YQSQ` anahtarları artık geçersizdir; yeni biçim
   `RB7-GAME-RKEY-ABCD-8V3TB9`.

### 22.6 Download otomatik taraması ve düşük donanım

#### Kaynak birleştirme

- `AppViewModel.directSources()` kullanıcının kayıtlı yollarına her zaman
  `StorageFolders.downloadsFolder()` sonucunu ekler. Download zaten kayıtlı bir kökün
  altındaysa (ör. `/storage/emulated/0` kökü seçilmişse) tekrar eklenmez; aynı dosya iki kez
  içe aktarılmaz.
- İlk açılışta `StorageFolders.findDefaultRoms()` indirilenler klasöründe ROM varsa onu seçer.
  Sayaç muhafazakârdır: 400 girdi ve bilinen ROM uzantılarıyla sınırlıdır; yalnızca kurulum
  dosyası/PDF bulunan bir Download klasörü ilk kurulumu atlatmaz.

#### Katı filtre (yalnızca Download kökü)

`strictDownloads = true` yalnızca indirilenler köküne uygulanır; kullanıcının açıkça seçtiği
kökler filtresiz taranır. Kurallar (`RomImporter.downloadsDirectPlausible`):

- `.exe`, `.com`, `.bin`, `.m3u` doğrudan yok sayılır (DOS/arcade yedeği kapalı).
- `.md` yalnızca ≥ 64 KB ise Genesis sayılır; README/LICENSE elenir.
- `.iso` ≥ 1 MB olmalıdır.
- Arşivlerde (`zip`, `7z`, `rar`) yalnızca gerçek kartuş ya da disk girdisi kabul edilir;
  disket imajı boyut eşiğinin altındaysa elenir. Arşiv içinde uygun girdi yoksa dosya atlanır.

#### Play kısıtı

Android 11+ `ACTION_OPEN_DOCUMENT_TREE` çağrısında indirilenler kökünü reddeder
("Bu klasör kullanılamıyor"). Play build'de otomatik tarama bu yüzden Download kökünü kapsamaz.
Kullanıcıya iki yol sunulur:

1. ROM'ları bir alt klasöre koyup o klasörü eklemek (SAF kökü).
2. Kurulum ve Ayarlar ekranlarındaki **ROM ZIP'inden oyun yükle** kartı ile tek bir
   `ROMS.zip` seçmek (`ACTION_OPEN_DOCUMENT`, `OpenMultipleDocuments`, §22.7). Kart, iki
   ekranda da 5 adımlı yönergeyi gösterir.

Akış `AppViewModel.importRomZip` ile başlar; `rememberImportLauncher` yalnızca sideload
build'lerde tek tek dosya seçimi için kalır. İlgili metin: `zip_guide_step1..5`,
`folder_downloads_play_hint`.

#### Düşük donanım önlemleri

- Coil: düşük RAM algılandığında küçük bellek önbelleği ve crossfade kapalı.
- Kütüphane veritabanı arka planda açılır; UI `libraryLoaded` kapısıyla boş liste gösterir.
- Ana ekran türetmeleri `remember` ile tutulur; her kompozisyonda yeniden hesaplanmaz.
- Ön plan eşitlemesi 5 sn eşikli ve `folderSyncActive` kilidiyle; hızlı yol SHA-256 imzasıyla
  5000 girdiye kadar taramayı atlar.

#### Ölçümler (MuMu vm1: 2 GB RAM, 4 çekirdek)

- `pm clear` sonrası soğuk açılış ≈ 350–500 ms.
- Ana ekran PSS ≈ 99 MB.
- NDS (melonds) oynatma ≈ 59.9 fps; çekirdek otomatik indirme akışı çalışıyor.
- 2 ROM / 2 konsol ile TV düzeni D-pad gezinmesi, konsol açma, manuel eşitleme ve
  dosya silinince otomatik budama doğrulandı.
- vm0 (4 GB, çok köklü): 134 ROM / 10 konsol korunuyor; regresyon yok.

### 22.7 ROM ZIP koleksiyon içe aktarma

Play build'de paylaşılan depolama taranamadığı için kullanıcı tek bir `ROMS.zip` verir;
Retrobit tüm oynanabilir üyeleri uygulama depolamasına açar.

#### Seçim ve kopyalama

- `rememberRomZipLauncher` (`Common.kt`) `OpenMultipleDocuments` kullanır; MIME listesi
  `application/zip`, `application/x-zip-compressed`, `application/x-7z-compressed`,
  `application/octet-stream`. Aynı `ACTION_OPEN_DOCUMENT` sözleşmesi sayesinde File Manager
  Plus (`com.alphainventor.filemanager`) cihazın kendi seçicisinin yerine açılabilir.
- Çoklu seçim sonucu yalnızca ilk URI işlenir. Seçilen dosya `looksLikeArchive` ile sihirli
  baytlardan (PK / 7z) doğrulanır; değilse `zip_import_invalid` gösterilir.
- `RomImporter.openArchive` önce `contentResolver.openFileDescriptor(uri, "r")` dener ve
  `/proc/self/fd/<fd>` üzerinden arşivi **yerinde** okur. File Manager Plus ve İndirilenler
  sağlayıcısı gerçek dosya tanımlayıcısı verdiği için 800 MB'lık bir ZIP'i uygulama
  depolamasına kopyalamak gerekmez; eski akış 878 MB'ı hem okuma hem yazma olarak işliyordu.
  Sağlayıcı yalnızca akış sunuyorsa `stageFile` ile `files/roms/.import/` altına kopyalama
  yedeği devreye girer. `ArchiveSource.close()` tanımlayıcıyı kapatır, yalnızca geçici
  kopyayı siler. `clearStaleStaging` açılışta 6 saatten eski artıkları temizler.

#### Üye çözümleme

- `RomArchives.entries` adları `/` ile normalize eder, ham adı `rawName` içinde tutar. 7z
  paketleri `\` ayırıcısı kullanır ve `.zip` etiketiyle gelebilir; kap türü uzantıdan değil
  sihirli baytlardan belirlenir.
- `importArchiveCollection` gizli/`__MACOSX` ve `bios`, `saves`, `states` gibi klasörleri atlar.
- Kartuşlar doğrudan konsol klasörüne açılır. `cue`/`gdi` kümeleri metin üyesinden
  (`RomArchives.readEntryText`) parça referanslarını okur; eksik parça varsa oyun başarısız
  sayılır ve yarım klasör silinir.
- İç içe `zip`/`7z` üyeleri en fazla 2 seviye açılır (`MAX_NESTED_ARCHIVE_DEPTH`); PSX gibi
  arşiv içinde arşiv taşıyan koleksiyonlar bu yolla çözülür.
- Çıkarma `extractEntries` ile 6 üyelik parçalar hâlinde ve arşiv başına tek geçişte yapılır
  (`EXTRACT_CHUNK`). Bu, düşük donanımlı TV kutularında ilerleme çubuğunun donmasını önler.
- Aynı konsol + dosya adı daha önce eklenmişse `claim` kaydı atlar; yeniden içe aktarmada
  kopya üretilmez ve `zip_import_already` mesajı gösterilir.
- İlerleme `ImportProgress` ile `RomZipImportCard` içinde “n / m oyun çıkarıldı” olarak
  gösterilir; işlem sırasında düğme kilitlenir (`AppViewModel.zipImporting`).

#### Sınırlar

- ZIP'teki her oyun uygulama depolamasına kopyalanır; kullanıcıya ZIP boyutu kadar boş alan
  gerekir. Kaynak ZIP silinmez, uygulama verisi temizlenince çıkarılan oyunlar da silinir.
- SD kart/USB taraması değişmedi: `Klasör ekle` hâlâ SAF kökü ekler ve dosyaları yerinde
  referanslar.

### 22.8 Play kurulum akışı ve gamepad öncelikleri

Play flavor'da paylaşılan depolama taranamadığı için kurulum ekranı klasör seçimini hiç
önermez; ekranda yalnızca üç yol vardır:

1. **ROM ZIP'i seç** — `OpenMultipleDocuments`, PK/7z sihirli bayt doğrulaması.
2. **ROM dosyalarını seç** — ZIP'e gerek kalmadan tek tek kartuş seçimi (`OpenMultipleDocuments`
   + `importRomFiles`); seçilen her oyun konsol klasörüne kopyalanır.
3. **ROM eklemeden devam et** (`setup_skip_roms`) — `onboarding_done` yazılır, kütüphane boş
   kalabilir; kullanıcı sonradan Ayarlar → kaynak ekleme ile devam eder.

`setup_play_steps` / `setup_play_step1..3` metinleri "ROM klasörünü seç" ifadesi yerine ZIP ya
da dosya seçmeyi anlatır. ZIP kartının altında `zip_import_slow` uyarısı durur ("birkaç dakika
sürebilir, uygulamayı açık tutun"). Kurulum içeriği dikey kaydırılabilir; kısa ekranlı TV
kutularında düğmeler erişilebilir kalır.

Gamepad ile kütüphane kullanımı (1.8.0 sonrası):

- **Kısa A / OK** kartı doğrudan oynatır (`LibraryGridCard`/`LibraryShelfCard` →
  `gamepadClickable(onClick = onPlay, onLongClick = onGame)`).
- **Uzun basış (550 ms)** detay/istatistik ekranını açar. Basış bırakıldığında oluşan
  DPAD_CENTER key-up yeni ekranın ilk düğmesini tetiklemesin diye `GamepadKeyGuard` o key-up'ı
  yutar; bunun için `MainActivity.dispatchKeyEvent` içinde BUTTON_A → DPAD_CENTER eşlemesinden
  sonra `GamepadKeyGuard.consumeCenterUp()` kontrol edilir.
- **B** (`KEYCODE_BUTTON_B` → BACK) detaydan kütüphaneye döner; oyun içinde tuşlar çekirdeğe
  gider, oyundan çıkış menüden yapılır.
- Detay ekranı açılırken `playFocus` ~220 ms sonra "Oyna" düğmesine odaklanır; böylece ilk A
  basışı oyunu başlatır (TV olmayan cihazlarda da çalışır).
- Kütüphane arama alanı D-pad'i yuttuğu için `onPreviewKeyEvent` ile DPAD_DOWN, "Tümü"
  çipine (`filtersFocus`) odak aktarır.

#### Ölçüm (MuMu vm0, 11.11.2026)

`/storage/emulated/0/$MuMu12Shared/Download/ROMS.zip` (878 MB) uçtan uca **~126 saniyede**
içe aktarıldı: 147 ROM, 7 konsol, 1.34 GB açılmış veri. Dosya tanımlayıcısı akışı sayesinde
kopyalama aşaması yoktur; süre çıkarma + kapak/veritabanı yazımıdır. Aynı arşiv yeniden
seçilirse `claim` kayıtları nedeniyle kopya üretilmez.

## 23. v1.9.0 — Retro Konsol Arayüzü (Batocera/RetroBat tarzı)

`versionCode 14`, `versionName 1.9.0`. Arayüz tamamen yeniden yazıldı; veri katmanı, oynatıcı,
içe aktarma ve destekçi mantığı değişmedi.

### 23.1 Ekranlar ve rotalar

| Rota | Ekran | Not |
|---|---|---|
| `systems` (başlangıç) | `SystemsScreen` | Tam ekran konsol arka planı, büyük wordmark, üretici · yıl, oyun sayısı, alt kısımda yatay panel kaydırıcı. |
| `system/{consoleId}` | `GameListScreen` | Sol: `CrtSnap` (libretro Named_Snaps) + kapak + üst veri; sağ: konsol temalı liste. |
| `libgame/{libraryId}` | `LibraryDetailScreen` | Detay; X favori. |
| `favorites`, `search` | `FavoritesScreen`, `SearchScreen` | Kart ızgarası, `RetroChip` filtreleri. |
| `settings`, `stats`, `supporters`, `showcase` | mevcut ekranlar | `RetroSheet` içinde: bulanık arka plan + 880 dp genişlikte yarı saydam `Surface`. |

`openMenu(route)` menü rotalarını (`favorites`/`search`/`settings`) birbirinin yerine koyar;
böylece Start → Select → X zinciri geri yığınını şişirmez. Eski `HomeScreen`, `ConsolesScreen`,
`LibraryScreen`, `RetrobitBottomBar`, `RetrobitSideRail` ve kullanılmayan kart bileşenleri silindi.

### 23.2 Odak konsolları ve görseller

`FocusConsoleIds`: NES, SNES, GB, GBC, GBA, N64, PS1, Mega Drive, NDS, PSP, Arcade
(FBNeo), MAME. Her biri için `res/drawable-nodpi/bg_*.webp` **özgün** (yapay zekâ ile üretilmiş,
marka/logo içermeyen) bir arka plan ve `consoleStyle` ile font/renk/şekil tanımı vardır.
Logolar resmî logo değildir; `ConsoleWordmark` metinle çizilir. Ticari marka logosu eklemeyin.

Fontlar (`res/font`): `press_start_2p.ttf` (PixelFont, Türkçe glifleri var), `orbitron.ttf`
(TechFont, **yalnızca ASCII** — Türkçe metinde kullanmayın), `poppins_*.ttf`. Büyük harfe
çevirme `uppercase(Locale)` ile yapılır (Türkçe İ).

### 23.3 Gamepad kısayolları

| Tuş | Sistemler | Oyun listesi | Detay |
|---|---|---|---|
| A (kısa) | konsolu aç | oyunu başlat | odaktaki düğme |
| A (550 ms) | — | detay | — |
| B | — | sistemlere dön | geri |
| X | Favoriler | favori aç/kapa | favori aç/kapa |
| Y | rastgele oyun | rastgele oyun | — |
| Start / Select | Ayarlar / Arama | Ayarlar / Arama | — |
| L1 / R1 | önceki/sonraki konsol | önceki/sonraki harf | — |

Kısayollar `Modifier.retroShortcuts` ile ekranın kök kutusuna bağlanır; `MainActivity`
BUTTON_X/Y/START/SELECT/L1/R1'i değiştirmeden Compose'a iletir.

### 23.4 Dokunma

TV öncelikli ama dokunma çalışır: kaydırıcıda yan panele dokunmak onu seçer, ortadaki panele
dokunmak konsolu açar; listede seçili olmayan satıra dokunmak seçer, seçili satıra dokunmak
oynatır; Favoriler/Arama ızgarasında karta dokunmak oynatır, uzun basmak detayı açar.

### 23.5 Ayarlar

`uiScanlines` (DataStore, varsayılan açık) arayüz üzerindeki CRT scanline katmanını
(`crtOverlay`) açar/kapatır; Ayarlar → Görünüm'de anahtardır. Oyun içi shader ayarlarından
bağımsızdır.

### 23.6 1.9.0 tuzakları

- Scaffold/Surface olmayan ekranlarda `LocalContentColor` varsayılanı siyahtır; kökte
  `onBackground` sağlanıyor. Yeni sayfa eklerken `RetroSheet` ya da `Surface` kullanın.
- Tuş değiştiricileri `.focusable()`'dan **önce** gelmeli; onay KeyUp'ta ve yalnızca kendi
  KeyDown'u görüldüyse çalışır (uzun basıştan sızan key-up'ı önler).
- Kapak önbellek anahtarı `"lib:${game.id}"`; snap URL'si `snapUrlFor` ile kapaktan türetilir.
- MuMu (non-TV düzen) durum çubuğunu gösterir; `LocalSafeArea` bunu hesaba katar.

### 23.7 Doğrulama (MuMu vm0, 11.11.2026)

Kaydırıcı, arka planlar ve ipucu çubuğu; D-pad ile seçim, OK ile liste + snap; uzun A ile detay
(sızan başlatma yok); B ile odağı koruyarak dönüş; Start → Ayarlar, X → Favoriler, Select → Arama;
dokunarak panel seçme/açma ve arama kartından oyunu başlatma doğrulandı.

---

## 24. v1.9.1 — Play-Only Dağıtım, Retro Tema Kapısı ve TV Düzeltmeleri

`versionCode 15`, `versionName 1.9.1`. **Karar (11.11.2026):** tüm geliştirme `play` flavor
üzerinden; sideload askıda (kaynak durur, derlenmez). Çıktılar her zaman
`assemblePlayRelease bundlePlayRelease`.

### 24.1 Retro tema kapısı

- `SettingsData.retroThemeTest` (DataStore `retro_theme_test`, varsayılan kapalı) +
  `val retroTheme get() = supporterUnlocked || retroThemeTest`.
- `LocalRetroTheme = compositionLocalOf { false }` (RetroKit); sağlayıcı `RetrobitApp`'te
  `LocalRetroTheme provides settings.retroTheme`. Ekranlar `retroOnSurface(alpha)` ile
  retro/flat yüzey seçer.
- **Ücretsiz mod aynı düzeni korur** (karusel + gamelist + snap/kapak), yalnızca görsel katman
  düzleşir: arka plan sanatı, piksel wordmark, CRT/scanline yok; Material yüzeyler koyu/açık
  temaya uyar. Destekçi olmayan kullanıcı Ayarlar → Görünüm → "Yeni retro temayı test et"
  anahtarıyla önizleyebilir (test evresi; anahtar yalnızca `!supporterUnlocked` iken görünür).
- Scanline anahtarı yalnızca `settings.retroTheme` açıkken görünür.

### 24.2 Ayarlar anahtar satırları (D-pad odak hatası)

Sağa hizalı `Switch`, D-pad odak ışını yatay örtüşme aradığı için üst/alttaki çiplerden
**erişilemiyordu**. Çözüm: `SettingSwitchRow` — tüm satır `tvFocus` +
`androidx.compose.foundation.selection.toggleable` (role = Switch), iç `Switch` yalnızca
gösterim (`onCheckedChange = null`). Retro test, scanline ve attract mode satırları bunu
kullanır. **Yeni anahtar eklerken aynı kalıbı kullanın; çıplak Switch koymayın.**

### 24.3 Mega Drive sahte kart düzeltmesi

- `RomImporter`: MAME üyeleri için `parentHint` koruması (bios/parent/device olmayan clone
  zip'leri ayrı oyun sayılmaz) + `claim()` ile aynı dosyanın iki kez sahiplenilmesi engellendi.
- `GameRepository.matchByTitle`: `SEQUEL_START` guard'ı — başlığı rakam/roma rakamıyla başlayan
  adaylar (ör. "2", "II") eşleşmeden önce elenir; "Golden Axe II" doğru başlığını korur.
- `consoleForFile`: ipuçsuz `.bin` artık doğru konsola gider (MD listesi 20 gerçek oyun).

### 24.4 ZIP içe aktarma TV regresyonu (iki katmanlı fallback)

1.9.0'daki hız optimizasyonu (`openFileDescriptor` + `/proc/self/fd`) bazı TV sağlayıcılarında
(File Manager Plus) çalışmıyordu. 1.9.1: fd yolu başarısızsa **eski kopyala+aç** yöntemine
düşülür (`roms/.import/` altına yedek kopya). 920 MB ROMS.zip → 120/120 oyun, 8 konsol.
İç içe zip/7z 2 seviye; CUE kümeleri track referanslarından çözülür.

### 24.5 Kurulum yönergeleri

`setup_play_steps` uzun listesi kaldırıldı; kurulum ekranı 3 yol sunar (ZIP / tek dosya / atla) +
gizlilik notu. ZIP kartı 2 satırlık özet (`zip_guide_summary`) + "ROM ZIP'i seç" + "Adım adım
rehber" bağlantısı gösterir; 5 adım yalnızca diyalogda (`RomZipSteps`). 1080p TV'de tamamı
görünür, D-pad ile tüm düğmeler erişilebilir (odak halkaları belirginleştirildi).

### 24.6 1.9.1 tuzakları

1. **`toggleable` import'u** `androidx.compose.foundation.selection.toggleable` —
   `androidx.compose.foundation.toggleable` DEĞİL (derleme hatası).
2. **Retro kapısını atlama:** ekranlarda ham `settings.theme` yerine `LocalRetroTheme` /
   `retroOnSurface` kullanın; yeni ekran free modda da test edilmeli.
3. **Kombinasyon tuşları çekirdeğe sızıyor** (bilinen hata, G1): `RetroPlayerActivity`
   `dispatchKeyEvent` SELECT/START/L2/R2'yi hem kombinasyon izler hem çekirdeğe iletir.
4. **PS1 boyutu 0 KB** (G2): üst veri `.cue` boyutunu gösteriyor; `.bin` track toplamı gerekir.

### 24.6.1 İkinci tur düzeltmeleri (G3–G7, G9)

- **G3 — boş konsollar:** `SystemsScreen` karusel listesi `counts[id] > 0` ile süzülüyor; tüm
  kütüphane boşsa kurulum için tam liste kalıyor. Başlık artık kart sayısıyla tutarlı.
- **G4 — ZIP sayacı:** `RomImporter.CollectionProgress` tek `current/total` çifti tutar; iç içe
  arşiv `expect()` ile toplamı büyütür, `opening()` `ImportProgress.note` alt satırını yazar.
  Yeniden deneme toplamı `maxOf` ile korunur (çift sayım yok). `ScanBanner` ve ZIP kartı
  `import_opening` ("%1$s açılıyor…") satırını gösterir. **Yeni sayaç eklemeyin; `CollectionProgress`
  kullanın.**
- **G5 — favori toast'ı:** `favorite_added` / `favorite_removed` onay metinleri; `add_favorite` /
  `remove_favorite` yalnızca eylem etiketi olarak kalır.
- **G6 — oyuncu üst çubuğu:** TV'de üst çubuk hiç çizilmiyor (`if (!isTv)`); menü SELECT+START,
  çıkış MENU / menüdeki "Oyundan çık".
- **G7 — MAME başlık/görsel:** `data/ArcadeAliases.kt` kısa ad → tam başlık (~65 giriş) ve
  doğrulanmış libretro MAME sanat adları (20 set, ör. `Cadillacs and Dinosaurs (World 930201)`).
  `RomImporter.buildGame` başlığı sözlükten çözer; `LibraryRepository` açılışta eski satırları
  `RetrobitDatabase.updateTitles` ile düzeltir. `CoverRepository` sırası: doğrulanmış libretro
  adı → tam başlık → ham ad → Wikipedia (`title` ve `title (video game)` denemeleri, `/wikipedia/en/`
  görseli şartı). mvsc2 libretro'da yok; Wikipedia arcade flyer'ı kullanılıyor.
- **G9 — ZIP kartı:** yukarıdaki 24.5 maddesi; `RomZipImportCard.showSteps` kaldırıldı.

**Doğrulama:** "120 oyun · 8 sistem", 8 dolu karusel kartı; sentetik 30+10 arşivde sayaç
12/30 → 25/30 → 32/40 → 40/40; toast onay metinleri; dino tam ekran (üst çubuk yok); DB başlıkları
düzeltilmiş + kapak/snap; Ayarlar ZIP kartı özet + bağlantı. Test verisi (44 geçici satır) silindi.

### 24.6.2 Üçüncü tur (G10, G11, G13, G14 + rehber boşlukları)

- **G10 — slot görselleri:** Kare, `PixelCopy.request(SurfaceView, …)` ile `GLRetroView`den alınır
  (`captureGameFrame`); Compose menü ayrı katman olduğu için görüntüye karışmaz. PNG 480 px genişliğe
  ölçeklenir, `${gameId}_slot$n.png` olarak state'in yanına yazılır ve `Bitmap.recycle()` edilir.
  Seçicide görsel `produceState` + `Dispatchers.IO` ile çözülür; anahtar `path + lastModified`
  olduğundan yeni kayıt kendiliğinden tazelenir. **Yeni slot eklerken `slotThumbnail` lambdasını ve
  silme yolunu birlikte güncelleyin.**
- **G11 — otomatik kurtarma:** Kullanıcı slotlarından ayrı `${gameId}_auto.state`; oyuncu
  `pauseForSave()` önce `releaseInput()`, sonra `saveAutoState()` çağırır ve `coreLoaded = false`
  yapar (çift yazım yok). Periyodik döngü `surfaceReady && autoSaveState` iken 120 sn'de bir çalışır;
  `autoSaveStamp` menüdeki tarihli satırı besler. **`autoStateFile()` adını değiştirirseniz menü
  satırı ve slot 1 göçü (`${gameId}.state`) ile birlikte güncelleyin.**
- **G13 — depolama:** `AppViewModel.refreshStorage()` yalnızca uygulamanın sahip olduğu dizinleri
  tarar (çekirdekler, `files/saves`, `cacheDir/image_cache`, içe/dışa aktarılan ROM kopyaları).
  `clearCoverCache()` Coil bellek + disk önbelleğini temizler (`@OptIn(ExperimentalCoilApi::class)`)
  ve `refreshStorage()` ile boyutları yeniler; ROM ve kayıt silme yolu yoktur, bu ilke korunmalı.
- **G14 — sıralama:** `SettingsData.librarySort` (varsayılan `SORT_TITLE`) DataStore'da kalıcı.
  Çipler için `RetroChip` kullanın; imzası `(label, selected, accent, onClick, modifier)` olduğundan
  sondaki lambda `modifier`a gider — `onClick = { … }` adlandırılmış argümanı şart. Seçili çip
  metnini sabit `Color.White` yapmayın; açık aksanlarda okunmaz, `RetroChip` odak renklerini kullanır.
  Sıralama değişince `LazyListState`/`LazyGridState` 0'a döner ve seçim ilk oyuna çekilir.

**Doğrulama:** Slot 1'de 52 KB PNG + görsel; çıkışta `_auto.state` (16:27) ve 2 dakikalık döngü
(16:29:29); Depolama kırılımı 57.7 MB / 270 KB / 23.4 MB / 1.42 GB (toplam 1.49 GB); kapak temizleme
onayı iptali; sıralama çipleri D-pad ile retro ve free temada seçilebilir, seçim kalıcı.

### 24.7 Doğrulama (MuMu vm0, 11.11.2026, play release)

TV modu + gamepad/D-pad ile: retro AÇIK tüm ekranlar (karusel, MD/MAME/PS1 gamelist, oyuncu
menüsü, detay, Favoriler, Arama, Ayarlar); retro KAPAK düz free düzen (koyu + açık tema);
anahtar satırları odak + OK; Alex Kidd (Genesis Plus GX) oynanış, dino (MAME) başlangıç,
Metal Slug X (PCSX, iç içe 7z+CUE) "NOW LOADING!"; SELECT+START menü + çıkışta istatistik
güncellemesi; ZIP 120/120. İkinci turda G3/G4/G5/G6/G7/G9 tekrar doğrulandı (ayrıntı 24.6.1).
Üçüncü turda G10/G11/G13/G14 retro ve free temada, ZIP rehberinin üç boşluğu 1080p diyalogda
doğrulandı (ayrıntı 24.6.2). Ayrıntılı geliştirme listesi: `docs/GELISTIRME_LISTESI.md`.

## 25. v1.9.2 — Hata Turu ve Akıcı Arayüz

`versionCode 16`, `versionName 1.9.2`. Hata tablosu, frontend araştırması ve planlar `docs/GELISTIRME_LISTESI.md` içinde.

### 25.1 Animasyon yardımcıları (`ui/components`)
- `RetroKit.kt`:
  - `RetroBackdrop` 26 sn'lik ters döngüyle yavaş kayan ışık çizer.
  - `Modifier.focusLift(focused, accent, shape, lift, spread)` ölçek uygular; `LocalRetroTheme` açıksa `focusGlow` da eklenir.
  - `Modifier.focusGlow(progress, color, shape, spread)` `drawBehind` ile parıltı çizer. Lambda ile okunduğu için recomposition tetiklemez.
- `RetroGames.kt`:
  - `Modifier.entryMotion(delayMs)` ve `rememberEntryDelay(openedAt, index, firstVisible)`: kademeli giriş yalnızca ekran açılışında ilk görünen kartlar için oynar. Kaydırmayla gelen kartlar gecikmesiz çıkar.
  - CrtSnap'te 14 sn'lik Ken Burns efekti var.
- Parıltı yalnızca retro temadadır. Ücretsiz temada `LocalRetroTheme` kapalıdır ve düz kenarlık kullanılır.
- Liste `contentPadding` değeri, parıltı `spread` değerinden küçük olmamalıdır; yoksa parıltı kenarda kesilir.

### 25.2 Yerel snap yedeği
`rememberLocalSnap(gameId, refreshKey)` şu dosyalar arasından en yenisini IO'da seçer: `filesDir/saves/${gameId}_slot*.png`.
- `refreshKey = game.lastPlayedAt` olduğundan oturum sonrası yeniden okunur.
- `CrtSnap(snapUrl: Any?)` hem URL hem `File` kabul eder. Coil'de File anahtarı lastModified içerdiği için önbellek tazelenir.
- Bu yardımcı yalnızca okuma yapar; kullanıcı dosyalarına yazmaz.

### 25.3 Oynama süresi ve duraklatma
`PlayerController.setEmulationPaused()` menü görünürken çekirdeği durdurur. `pausedMillis()` toplam duraklatma süresini verir. `PlayerScreen` dispose anında `geçen − duraklatılan` değerini kaydeder (≥5 sn). `frameSpeed`/`viewport` çalışma anında değiştirilmez.

### 25.4 Kombinasyon tuşu ertelemesi (G1)
`RetroPlayerActivity.deferKey`, SELECT/START'ın ilkini `COMBO_GRACE_MS = 140` ms bekletir:
- İkinci tuş gelirse ikisi de yutulur ve çekirdeğe gitmez.
- Tek basışta tuş gecikmeyle iletilir. Bırakma `TAP_RELEASE_MS` sonra gönderilir, çünkü çekirdek kare başına yoklar.

### 25.5 Yerel ayar kuralı
Marka/konsol adları ("SEGA MEGA DRIVE") `uppercase(Locale.ROOT)` ile büyütülür. Türkçe arayüz metinleri ("KAYIT YUVASI") yerel ayarla büyütülür. Press Start 2P Türkçe glifleri içerir; Orbitron'da İ/Ş/Ğ yok, Türkçe metinde kullanmayın.

### 25.6 Doğrulama (MuMu vm0, 2 GB RAM / 2 CPU)
Retro açık ve kapalı tüm ekranlar D-pad/gamepad ile gezildi; kayıt/yükleme ve slot görseli çalışıyor. Menüde 30 sn bekletilen ~55 sn'lik oturum +27 sn kaydedildi. Logcat crash tamponu boş. `RetroPlayerActivity` dışa aktarılmadığı için oyunu kabuktan `am start` ile değil, arayüzden başlatın.

## 26. v1.9.3 — Test Destekçi Paketi ve Konsol Kimlikleri

`versionCode 17`, `versionName 1.9.3`; yalnızca play. P-UI1–7 ve P-UI9 uygulandı; P-UI8 güvenli renderer bilgisi bekliyor.

### 26.1 Test/yayın ayrımı
- Yerel test çıktılarında tüm destekçi özellikleri `-PsupporterTestUnlock=true` ile açılabilir. Gerçek satın alma/anahtar kaydı yazılmaz.
- Test çıktısı: `./gradlew assemblePlayRelease bundlePlayRelease -PsupporterTestUnlock=true`.
- Varsayılan `false` değeridir. Play Console'a yükleme: `./gradlew bundlePlayRelease -PsupporterTestUnlock=false`; masaüstüne teslim edilen test AAB'sini doğrudan yayınlamayın.
- Retro test anahtarı korunur; destekçi özellikleri açıkken de düz arayüzün test edilmesine izin verir.

### 26.2 Özgün görsel kimlik ve lisans
- `ConsoleMarks.kt`: 13 konsola özgü kodla çizilmiş wordmark; resmî marka logo dosyası kullanılmaz. NES ile SNES farklı font, şekil ve palet kullanır.
- `res/drawable-nodpi/bg_*.webp`: sisteme özgü yeni özgün manzaralar; ticari oyun karakteri veya frontend tema dosyası eklenmez.
- Font aileleri SIL OFL; lisans metinleri `assets/licenses/` içinde (Poppins dahil). Fontları bağımsız ürün olarak satmayın, lisans metinlerini paketten çıkarmayın.
- Yapay zekâ ile özgün üretim hukuki garanti değildir; yayın öncesi marka benzerliği kontrolü gerekir. Oyun kapakları/kareleri uygulama sanatı değildir ve mağaza görseli olarak kullanılmamalıdır.
- `YearMark`: sağ alanın ölçüsüne göre outline yıl; taşmayan güvenli padding. Eğik etiketlerde açık `lineHeight` ile yüksekliğe bağlı standart padding kullanılır; özel ölçümle sınır aşılmaz.

### 26.3 Arayüz yardımcıları
- P-UI1: `HeroBlur` snap'i 64×48 çözer, API 31+ blur uygular; eski Android'de düşük çözünürlüklü görüntü. `shownSnap` seçimi 160 ms bekletir ve iptal edilebilir; hızlı D-pad hareketi eski kareyi sonradan yayınlamaz.
- P-UI2: `SharedTransitionLayout` / `LocalSharedScope` / `LocalNavAnimScope`; `sharedCover(game.id)` gamelist ve detay kapağını eşler. Deneysel Compose API için opt-in korunmalı.
- P-UI3: `carouselDirection` sarılan karuselde kısa yönü seçer; arka plan, bilgi, yıl katmanlarının kayma mesafesi/süresi farklıdır.
- P-UI4: `crtOverlay` tek 1×3 bitmap tile ve tekrar eden ImageShader kullanır. Gerçek TV'de GPU tasarrufu henüz ölçülmedi.
- P-UI5: `GameCover` yükleme durumunda `PixelShimmer`; yalnızca `LocalRetroTheme` açıkken. Hata/success durumunda efekt kalkar.
- P-UI6: Ayarlar bölüm başlığı piksel yazı ve üç kare; son oynanan konsol aksanı. Düz modda eski başlık ve kenarlık.
- P-UI7: kilitli `SettingsActionRow` yine tıklanabilir/odaklanabilir; açıklama toast'ı ve Play satın alma düğmesine `FocusRequester`. Satın alma düğmesi ile kilitli satırlar aynı lazy item'da kalmalı.
- P-UI9: detay `SystemPanel` açıklama, üretici/yıl, oyun sayısı, konsol toplam süresi ve seçili oyunun ortalama oturum süresini gösterir. Bir dakikadan kısa ortalamayı göstermez.

### 26.4 Son TV regresyon düzeltmeleri
- `StatsScreen`: en çok oynanan oyunlar ve konsol dağılımı satırları TV'de odaklanabilir; `tvFocus` ile görünür çerçeve. İlk oyundan son konsola D-pad erişimi MuMu'da doğrulandı; telefon modunda gereksiz bilgi odağı yok.
- `ShowcaseScreen`: oluştur/paylaş düğmelerinde aynı görünür odak çerçevesi; kolaj oluşturma yerel kalır, dışarıya paylaşım ayrı kullanıcı eylemidir.
- Compose runtime lint 1.7.5 dört `produceState` içindeki gerçek değer atamasını çözemedi. `GameListScreen`, `LibraryDetailScreen`, `rememberLocalSnap` ve slot bitmap yüklemesi aynı anahtarlar/başlangıç değerleriyle `remember` + `LaunchedEffect` kullanır; IO ve iptal davranışı korunur. Kontrol kapatılmadı, baseline/suppression yok. Tam `lintPlayRelease` başarılı: 0 hata, 110 uyarı, 2 bilgi.
- Son APK'de boş slot 4'e tam kare kayıt/yükleme doğrulandı (480×270, siyah yan şeritler korunur); önceki slot 1/3 dosyaları SHA-256 ile aynı kaldı. Bu P-UI8 otomatik kırpmasının tamamlandığı anlamına gelmez.
- Son kapsam ve sınırlar `GELISTIRME_LISTESI.md` §7.2'de; gerçek TV GPU ve Play satın alma testleri yapılmadı.

### 26.5 P-UI8 neden açık kaldı
Siyah piksel taraması karanlık sahneyi şerit sanıp içeriği kesiyor; bu algoritma kaldırıldı. LibretroDroid 0.14.0 gerçek viewport/çekirdek oranını genel API'de sağlamıyor (§20.3). `captureGameFrame` tam SurfaceView karesini 480 px genişliğe ölçeklemeye devam eder. Güvenli kırpma için salt okunur renderer dikdörtgeni gerekir; `frameSpeed`/`viewport` runtime setterları kullanılmaz. ROM, çekirdek ve kullanıcı kayıt dosyaları bu arayüz işi için silinmez.

## 27. v1.9.4 — Veri Güvenliği, Gamepad ve Özgün Marka

`versionCode 18`; yalnız destekçi açık Play test paketleri. Ayrıntılı değişiklikler ve test kanıtları: `GELISTIRME_LISTESI.md` §7.3/§8.

- **Güncel DB:** `RetrobitDatabase` doğrudan `SQLiteOpenHelper` kullanır; eski bölümlerdeki Room/DAO ifadeleri güncel uygulama değildir. DB mutasyonu ve StateFlow snapshot yayını ortak kilitte; ilk yükleme IO'da. Otomatik prune kaldırıldı: USB ayrılması/izin kaybı oyun UUID'sini yok etmez. `clearLibrary` yalnız DB satırlarını temizler, ROM silmez.
- **Import:** beş public import/prepare girişi aynı Mutex'te; library.loaded beklenir. Lazy disc extraction `unpacked-<game.id>` ve UUID attempt klasörü kullanır; eski unpackedPath korunur. Yalnız kendi başarısız staging'i temizlenir.
- **Kayıt:** `AtomicFiles.write` aynı dosya sistemindeki sibling temp'i flush/sync eder, `ATOMIC_MOVE + REPLACE_EXISTING` ile yayınlar; fallback truncate yok. Slot/auto/SRAM/PNG ve cover cache kullanır. Restore aynı monitor'ü paylaşır; controller yazımı ile restore eşzamanlı yayınlanmaz.
- **Yedek/restore:** UUID isim; MediaStore null stream cleanup. Restore tüm ZIP'i staging'de CRC/size/path/duplicate denetler; state/SRAM/PNG ve yerel çekirdek kayıt biçimleri (`mcd`, `hi`, `nv`, `cfg`, vb.) seçilir. Eski ağacın backup'ta olmayan dosyaları korunur; commit hatasında rollback. Mevcut kayıt varsa güvenlik yedeği oluşturulmadan UI restore başlamaz.
- **Restore sınırı:** iki ayrı atomik rename, tek-syscall exchange değildir. Process/power loss için manifest/recovery henüz yoktur. Başarısız rollback'in `.saves-restore-*/previous` ağacını otomatik silmeyin; G25 açık.
- **Core:** runtime indirme kaldırıldı. On bir çekirdeğin dört ABI ikilisi AAB/APK içinde, exact commit ve SHA-256 kayıtları bileşen listesinde; `files/cores/` yalnız eski sürüm kalıntısıdır. PPSSPP yardımcı assets yalnız eksik hedeflere kopyalanır ve kullanıcı sistem dosyalarının üzerine yazılmaz.
- **Cover:** library fallback permit içinde ikinci permit almaz; hit cache önceliklidir, session miss tekrarlanmaz. Signature taramaları IO; başarısız import signature kaydetmez. GL hata paneli artık null şartının dışında.
- **Gamepad:** `GamepadInput` canonical/mapped tuş, gerçek menü çiftleri, deadzone/yön; `GamepadNavigation` HAT/solstick UI D-pad ve kontrollü tekrar. Sağ stick cihaz range'iyle RX/RY veya Z/RZ. Metadata korunur; dijital/analog trigger birleşir; pause/focus/disconnect/menu input reset. `GamepadTestActivity` exported=false, yalnız yerel teşhis; marka adına bakarak özel HID tahmini yok.
- **Logo:** `art/generate-brand.py` özgün vektörle aynı piksel R geometrisinden density/round/512px/TV banner üretir. Adaptive/monochrome/splash ve in-app marka güncellendi; OFL font lisansları korunur. Ana ekran ve Hakkında cihazda, 512px ikon ve banner kaynakları görsel olarak doğrulandı; Setup/splash/TV launcher bu turda yeniden test edilmedi.
- **TV odağı:** Hakkında/yasal bilgiler bölümü `SettingsSection` modifier'ı üzerinden yalnız TV'de focusable; D-pad alt bölüme kaydırır ve görünür çerçeve gösterir. Gamepad teşhisi yardım/boş durum metninde açık foreground rengiyle kontrast düzeltildi. Retro test/vitrin tercihleri ve aktif slot test sonunda geri alındı.
- **JVM/teslim:** son kaynakta 93 test, 0 failure/error/skip; lint 0 hata, 110 uyarı, 2 bilgi. Play APK/AAB exit 0; build/teslim kopyası ve imzalar doğrulandı. Kurulu APK teslimle aynı SHA-256. AAB self-signed/timestamp/entry-order uyarıları beklenir. G16 kısmi: zamanlayıcılı deferKey state machine ve SEQUEL_START eşleme hâlâ açık; JVM sonuçları UI doğrulamasının yerine geçmez.
- **Veri kanıtı:** son kurulumdan sonra 124 ROM, 5 core, arşiv ve önceki tüm manuel/native/SRAM kayıtları aynı SHA-256. Yalnız oynanan oyunun auto.state dosyası değişti, önceden boş slot 2'ye state/PNG eklendi. SELECT+START/L2+R2 menü, geçersiz SELECT+R2, kayıt/yükleme ve normal çıkış doğrulandı; crash tamponu boş. Canlı restore/açılış-crash recovery ve yeni core indirme bu turda test edilmedi.
- **MuMu sınırı:** `dumpsys input` Xiaomi Joystick'i listelese de teşhis Activity'sine GAMEPAD/JOYSTICK ulaşmadı; Linux X/scan 307, DEL/source 0x701 olarak geldi. MuMu host logunda paket joystick maskesi 0x8 (`joystick=0`), eski gerçek XInput bağlantısında 0xc (`joystick=4`). ADB gamepad BUTTON_X 99 da DPAD_CENTER 23/source 0x401 oldu. Bu paket düzeyindeki giriş kapısıdır; kesin DEL dönüşüm noktası veya 8bitDo arızasının aynı nedeni kanıtlanmadı. Emulator yapılandırması değiştirilmedi.
- **Teyit sınırı:** fiziksel Xbox/PlayStation/8bitDo USB/BT/mod matrisi G24'te açık; marka profili ham olay kanıtı olmadan eklenmez. DB retry ve AndroidView lifecycle doğrulaması açık planlarda kalır. G12/G15/G19/G20 askıda; G17/G18 körlemesine uygulanmaz. Ayrıntılı güncel kapsam ve paket hash'leri `GELISTIRME_LISTESI.md` §7.3'te.
