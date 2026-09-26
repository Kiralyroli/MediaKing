# My Media – Android TV Media Center

Saját médiaközpont Android TV-re: a TV-re kötött USB-s HDD filmjeit és sorozatait rendezett,
posztereket és magyar leírásokat mutató médiatárrá alakítja, és saját lejátszóval játssza le őket.
Portfólióprojekt, egy valódi Xiaomi Mi TV-n (Android 10) fejlesztve és tesztelve.

## Funkciók

- **Médiatár** – a kijelölt mappák átfésülése, filmek és sorozatok felismerése a fájl- és mappanevekből
  (`Charmed.1998.S01.1080p…/Charmed.S01E07….mkv` → Bűbájos boszorkák, 1998, 1. évad 7. rész)
- **TMDB-metaadatok** magyarul – cím, leírás, poszter, háttérkép, műfaj, szereplők, epizódcímek;
  saját pontozó illesztővel, mert a TMDB első találata gyakran rossz (a „Life” keresésre „No Game No Life”)
- **Kezdőlap** – Folytatás (félbehagyott filmek, sorozatoknál a következő rész), legutóbb hozzáadott filmek, sorozatok
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
  a TV beépített tunere (ha van antenna); kiegészítőkkel a beépített lejátszóban, csatornaváltással
- **Kiegészítők** – deklaratív JSON-bővítmények élő csatornákhoz (nem futtatnak kódot), telepítés a feltöltő oldalról;
  formátum: [docs/addons.md](docs/addons.md)
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
Coil 3 · Ktor (CIO) szerver előtérszolgáltatásban · ZXing · JUnit (egység- és valódi HTTP-s integrációs tesztek)

```
app/src/main/java/com/kiroland/mediacenter/          (webes felület: app/src/main/assets/web)
├── data/storage     tárhelyek, fájllistázás, írásteszt
├── data/library     Room-adatbázis, átfésülő, médiatár-repository
├── data/metadata    TMDB API, illesztő, metaadat-kiegészítő
├── data/transfer    feltöltő szerver, folytatható feltöltés, párosítás, előtérszolgáltatás
├── data/live        Élő TV: csatornák, hivatalos appok és tuner indítása
├── data/addons      kiegészítők: formátum, ellenőrzés, feloldó motor, tárolás
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

Token nélkül is működik, csak poszterek és leírások nélkül.

### Android 10-es TV-ken

- A médiatár sima fájlútvonalakkal olvas (`requestLegacyExternalStorage`), mert sok TV-n nincs rendszer-fájlválasztó.
- Egyes TV-ken az egyik USB-port OTG-s: bekapcsolt **USB-hibakeresésnél** az a port eszköz módba vált, és a rá
  dugott meghajtót nem látja a rendszer. Fejlesztés közben a HDD a másik portba kerüljön.

## Tervek

- Android TV „Watch Next” integráció
- M3U / XMLTV lejátszási listák mint kiegészítő-típus, `.strm` fájlok

## Köszönet

- Film- és sorozatadatok: [TMDB](https://www.themoviedb.org).
  *This product uses the TMDB API but is not endorsed or certified by TMDB.*
- FFmpeg-dekóder: [Jellyfin media3-ffmpeg-decoder](https://github.com/jellyfin/jellyfin-androidx-media)
