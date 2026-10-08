# MediaKing – Android TV Media Center

![MediaKing](docs/brand/mediaking-banner.png)

[![CI](https://github.com/Kiralyroli/MediaKing/actions/workflows/ci.yml/badge.svg)](https://github.com/Kiralyroli/MediaKing/actions/workflows/ci.yml)

Saját médiaközpont Android TV-re: a TV-re kötött USB-s HDD filmjeit és sorozatait rendezett,
posztereket és magyar leírásokat mutató médiatárrá alakítja, és saját lejátszóval játssza le őket.
Portfólióprojekt, egy valódi Xiaomi Mi TV-n (Android 10) fejlesztve és tesztelve.

## Funkciók

- **Médiatár** – a kijelölt mappák átfésülése, filmek és sorozatok felismerése a fájl- és mappanevekből
  (`Charmed.1998.S01.1080p…/Charmed.S01E07….mkv` → Bűbájos boszorkák, 1998, 1. évad 7. rész)
- **TMDB-metaadatok** magyarul – cím, leírás, poszter, háttérkép, műfaj, szereplők, epizódcímek;
  saját pontozó illesztővel, mert a TMDB első találata gyakran rossz (a „Life” keresésre „No Game No Life”);
  rossz találatnál kézi javítás („Nem ez a film?”)
- **Kezdőlap** – Folytatás (félbehagyott filmek, sorozatoknál a következő rész), legutóbb hozzáadott filmek, sorozatok
- **Android TV kezdőképernyő** – a „Következő” (Watch Next) sorban a félbehagyott film vagy a következő rész, onnan egy gombnyomással folytatható
- **Keresés** cím, eredeti cím, szereplő vagy rendező szerint, ékezetek nélkül is
- **Hol nézheted?** – a film- és sorozat-adatlapokon a magyarországi streaming-elérhetőség (előfizetés, kölcsönzés, vásárlás;
  forrás: JustWatch a TMDB-n át); egy gombnyomással megnyitja a szolgáltató appját: a Prime Video rögtön a címre keres,
  az HBO Max és a Disney+ a keresőoldalt nyitja, a Netflix az appot; a beírandó eredeti címet felirat mutatja
- **Streaming-keresés és előfizetések** – a keresés a médiatár mellett a TMDB filmjeit és sorozatait is mutatja, saját
  adatlappal és „Hol nézheted?” résszel; a Beállításokban megadott előfizetéseidet teszi előre („benne van az előfizetésedben”)
- **Megnézendők és „Népszerű nálad”** – streaming-címek félretehetők a Megnézendők közé (kezdőlapi sor); a kezdőlapon
  előfizetésenként egy „Népszerű · Szolgáltató” sor mutatja, mi megy most a te szolgáltatásaidon
- **Keresési előzmények és műfajok** – üres keresőmezőnél az utolsó 10 keresés és műfaj szerinti böngészés
  (pl. „Vígjáték a szolgáltatásaidon”)
- **Megnézettek és értékelés** – filmek és sorozatok „Megnéztem” jelöléssel és 1–5 csillaggal, saját menüpontban
  (szűrés filmre, sorozatra, legjobbra értékeltre); a médiatár végignézett filmjei maguktól bekerülnek
- **Hálózati mappák (SMB)** – számítógép vagy NAS megosztott mappája a médiatárban, másolás nélkül; lejátszás közvetlenül
  a hálózatról (előreolvasó pufferrel), feliratokkal együtt; beállítás a feltöltő oldalon, kapcsolatpróbával
- **Lejátszó** (Media3 / ExoPlayer)
  - pozíció mentése és folytatás, automatikus következő epizód
  - AC3 / E-AC3 / DTS / TrueHD: passthrough a TV felé, ahol nincs, FFmpeg szoftveres dekóder
  - feliratok a videó mellett és a kiadások almappáiban (`hun/`, `hundub.hunsub/`), Windows-1250 → UTF-8 átalakítással
  - magyar szinkronnál automatikusan a kényszerített (forced) felirat, eredeti nyelvnél a teljes magyar
  - részletes sávnevek: nyelv, formátum (SRT/ASS/PGS…), kényszerített, SDH, kodek, csatornák
- **Wi-Fi-s feltöltés** telefonról vagy PC-ről, böngészőből, közvetlenül a TV-re kötött meghajtóra
  - QR-kódos vagy begépelhető párosítás, hibás kódoknál letiltás
  - darabolt, **folytatható** feltöltés: megszakadás után onnan folytatja, ahol abbamaradt; félkész fájl nem kerül a médiatárba
  - fájlok, teljes mappák, húzd-és-ejtsd; haladás, sebesség, hátralévő idő, szünet/folytatás
  - törlés előnézettel és megerősítéssel; meghajtók és médiatár-mappák védettek, meglévő fájl sosem íródik felül
  - a beérkezett fájlok után automatikus médiatár-frissítés
- **Élő TV** – közmédia-csatornák csempéi: a hivatalos Médiaklikk-appban vagy a mediaklikk.hu élő oldalán nyílnak meg;
  a TV beépített tunere (ha van antenna); kiegészítőkkel a beépített lejátszóban, csatornaváltással;
  műsorújság (XMLTV): a csempéken és csatornaváltáskor a „Most” és a következő műsor, teljes idősávos műsorújság-nézet
- **Kiegészítők** – deklaratív JSON-bővítmények élő csatornákhoz (nem futtatnak kódot), M3U-lejátszási listák és XMLTV-műsorújság, telepítés a feltöltő oldalról;
  formátum: [docs/addons.md](docs/addons.md)
- **Beállítások** – automatikus következő rész és feliratválasztás, antennás adás, feltöltő automatikus indítása, metaadatok újratöltése
- **Fájlböngésző** távirányítóra optimalizálva, **diagnosztika** (kodekek, tárhelyek írástesztje)

## Wi-Fi-s feltöltés használata

1. A TV-n: **Feltöltés → Bekapcsolás**. Megjelenik a cím (pl. `http://192.168.1.3:8080`), a párosítási kód és egy QR-kód.
2. Telefonon olvasd be a QR-kódot, PC-n nyisd meg a címet és add meg a kódot (a böngésző megjegyzi).
3. Válaszd ki a célmappát, és húzd rá a fájlokat vagy mappákat.

Ha a böngésző szerint a TV „visszautasította a csatlakozást”, a szerver nincs bekapcsolva a TV-n
(biztonsági okból nem indul magától), vagy a TV-nek új IP-címe lett – mindig a TV-n látható címet használd.

## Technológia

Kotlin · Jetpack Compose for TV · Hilt · Room (automatikus migrációval) · Coroutines/Flow ·
Navigation (típusos útvonalak) · Media3 ExoPlayer + FFmpeg extension · Retrofit + kotlinx.serialization ·
Coil 3 · Ktor (CIO) szerver előtérszolgáltatásban · smbj (SMB2/3) · ZXing · JUnit (egység- és valódi HTTP-s integrációs tesztek)

```
app/src/main/java/com/kiroland/mediacenter/          (webes felület: app/src/main/assets/web)
├── data/storage     tárhelyek, fájllistázás, írásteszt
├── data/network     SMB-megosztások: kliens, útvonalak, mentett belépések
├── data/library     Room-adatbázis, átfésülő, médiatár-repository
├── data/metadata    TMDB API, illesztő, metaadat-kiegészítő
├── data/transfer    feltöltő szerver, folytatható feltöltés, párosítás, előtérszolgáltatás
├── data/live        Élő TV: csatornák, hivatalos appok és tuner indítása
├── data/addons      kiegészítők: formátum, ellenőrzés, feloldó motor, tárolás
├── data/epg         műsorújság: XMLTV-olvasó, frissítés, idősáv-elrendezés
├── media            fájlnév-felismerő, kodekvizsgáló
├── player           lejátszó, feliratkezelés, sávnevek
└── ui               képernyők (Compose for TV)
```

## Fordítás

Android Studio (JDK 21) vagy parancssorból:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

A TMDB-adatokhoz egy saját **API Read Access Token** kell ([themoviedb.org](https://www.themoviedb.org/settings/api)),
a verziókezelésből kizárt `local.properties` fájlba:

```properties
tmdb.token=eyJ...
```

Token nélkül is működik, csak poszterek és leírások nélkül. A token a TV-n is megadható: **Feltöltés → Bekapcsolás**,
majd a böngészőben a **Filmadatok (TMDB)** résznél; ez felülírja a beépítettet.

A feliratletöltéshez ugyanide egy OpenSubtitles API-kulcs is beírható (`opensubtitles.key=...`); ez is megadható a TV-n.

### Kiadás

- `./gradlew :app:assembleSideload` – a TV-re telepíthető APK. A debug kulccsal van aláírva, így a meglévő telepítést
  frissíti, és megmarad a médiatár és a megnézett lista.
- `./gradlew :app:bundleRelease` – a Play Áruházba feltölthető AAB, a saját feltöltési kulccsal aláírva. A kulcs adatai a
  verziókezelésből kizárt `keystore.properties` fájlba kerülnek:

  ```properties
  storeFile=upload-key.jks
  storePassword=...
  keyAlias=upload
  keyPassword=...
  ```

### CI

Minden push a `main` ágra lefordítja az appot és lefuttatja a teszteket (GitHub Actions, `.github/workflows/ci.yml`).

### Android 10-es TV-ken

- A médiatár sima fájlútvonalakkal olvas (`requestLegacyExternalStorage`), mert sok TV-n nincs rendszer-fájlválasztó.
- Egyes TV-ken az egyik USB-port OTG-s: bekapcsolt **USB-hibakeresésnél** az a port eszköz módba vált, és a rá
  dugott meghajtót nem látja a rendszer. Fejlesztés közben a HDD a másik portba kerüljön.

## Tervek

- `.strm` fájlok

## Köszönet

- Film- és sorozatadatok: [TMDB](https://www.themoviedb.org).
- Streaming-elérhetőség: [JustWatch](https://www.justwatch.com) (a TMDB-n keresztül).
  *This product uses the TMDB API but is not endorsed or certified by TMDB.*
- FFmpeg-dekóder: [Jellyfin media3-ffmpeg-decoder](https://github.com/jellyfin/jellyfin-androidx-media)
- Betűtípusok: [Space Grotesk](https://github.com/floriankarsten/space-grotesk) és [Manrope](https://github.com/googlefonts/manrope),
  SIL Open Font License 1.1 ([docs/licenses](docs/licenses)).

## Licenc

A MediaKing szabad szoftver: [GNU General Public License v3.0](LICENSE).
A felhasznált könyvtárak licencei az appban is megtekinthetők (Diagnosztika → Nyílt forrású licencek).
