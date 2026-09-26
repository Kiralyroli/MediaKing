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
- **Fájlböngésző** távirányítóra optimalizálva, **diagnosztika** (kodekek, tárhelyek írástesztje)

## Technológia

Kotlin · Jetpack Compose for TV · Hilt · Room (automatikus migrációval) · Coroutines/Flow ·
Navigation (típusos útvonalak) · Media3 ExoPlayer + FFmpeg extension · Retrofit + kotlinx.serialization ·
Coil 3 · JUnit

```
app/src/main/java/com/kiroland/mediacenter/
├── data/storage     tárhelyek, fájllistázás, írásteszt
├── data/library     Room-adatbázis, átfésülő, médiatár-repository
├── data/metadata    TMDB API, illesztő, metaadat-kiegészítő
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

- Wi-Fi-s feltöltés a TV-re (helyi szerver, webes felület, QR-kódos párosítás)
- Android TV „Watch Next” integráció
- M3U / `.strm` online források

## Köszönet

- Film- és sorozatadatok: [TMDB](https://www.themoviedb.org).
  *This product uses the TMDB API but is not endorsed or certified by TMDB.*
- FFmpeg-dekóder: [Jellyfin media3-ffmpeg-decoder](https://github.com/jellyfin/jellyfin-androidx-media)
