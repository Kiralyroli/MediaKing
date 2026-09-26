# Kiegészítők (add-onok)

A kiegészítő egy **JSON leíró fájl**, ami élő csatornákat ad az **Élő TV** menühöz, és a beépített lejátszóban
játssza le őket. A kiegészítő **adat, nem kód**: az app csak a benne felsorolt HTTP GET kéréseket és
szövegműveleteket hajtja végre.

Telepítés: TV → **Feltöltés → Bekapcsolás**, a böngészőben a **Kiegészítők** résznél fájlból vagy URL-ről.
Azonos `id`-jű kiegészítő telepítése frissíti a régit. Eltávolítás a böngészőből vagy a TV **Kiegészítők** menüjéből.

> Csak olyan forrást adj hozzá, amelynek a használatára jogod van, és tartsd be a szolgáltató feltételeit.

## Egyszerű példa: fix címek

```json
{
  "id": "example.test-streams",
  "name": "Teszt-streamek",
  "version": 1,
  "channels": [
    { "id": "mux", "name": "Mux teszt", "color": "#3E5C76", "url": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" }
  ]
}
```

## Mezők

| Mező | Leírás |
|---|---|
| `id` | Egyedi azonosító: kisbetű, szám, `.`, `-`, `_` (pl. `example.test-streams`) |
| `name`, `version`, `description` | Megjelenített név, verziószám, rövid leírás |
| `headers` | Alapértelmezett fejlécek minden `get` lépéshez (pl. `User-Agent`) |
| `playlist` | Egy M3U lejátszási lista címe: a benne lévő csatornák is megjelennek (a `channels` mellett) |
| `epg` | Egy XMLTV műsorújság címe (lehet `.gz`); ha hiányzik, a lista fejlécében lévő `x-tvg-url`-t használja |
| `channels[]` | `id`, `name`, opcionálisan `color` (`#RRGGBB`), `logo` (kép URL), `group` (sor címe az Élő TV-ben), `url` (fix stream-cím), `epgId` (a csatorna azonosítója a műsorújságban), `vars` (sablonértékek), `builtin` |
| `channels[].builtin` | Egy beépített csempe azonosítója (`m1`, `m2`, `m4`, `m4plus`, `m5`, `duna`, `dunaworld`): a csempe ezzel a csatornával indul a beépített lejátszóban, ahelyett hogy külön sorban jelenne meg |
| `resolve[]` | Lépések a fix `url` nélküli csatornákhoz (lásd lent) |
| `stream.mimeType` | pl. `application/x-mpegURL`, ha a cím nem árulkodó |
| `stream.headers` | Fejlécek, amelyeket a lejátszó a stream letöltésekor küld |

## Lejátszási lista és műsorújság

```json
{
  "id": "example.iptv",
  "name": "Saját IPTV",
  "playlist": "https://example.org/lista.m3u",
  "epg": "https://example.org/musor.xml.gz"
}
```

A lista `#EXTINF` soraiból a `tvg-id` (műsorújság-azonosító), `tvg-logo` (logó) és `group-title` (sor az Élő TV-ben)
kerül át. A műsorújságból az Élő TV csempéi a „Most” és a következő műsort mutatják; 6 óránként frissül, és csak
egy napnyi ablakot tart meg.

## Feloldási lépések

Minden lépés pontosan egy műveletet tartalmaz; az érték lépésről lépésre halad tovább, a végeredménynek
`http(s)://` címnek kell lennie.

| Lépés | Mit csinál |
|---|---|
| `{ "get": "https://…?v={video}", "headers": {…} }` | Letölti a címet (legfeljebb 5 MB); az érték a válasz szövege lesz |
| `{ "regex": "minta", "group": 1 }` | Az első egyezés adott csoportja (a `.` sortörésre is illeszkedik) |
| `{ "json": "playlist[type=hls].file" }` | Egy mező kiválasztása JSON-ból |
| `{ "replace": "^//", "with": "https://" }` | Regex szerinti csere |

Sablonok a `get` címben és a `with` értékben: `{id}`, `{name}` és a csatorna `vars` értékei (a címben URL-kódolva).

### A `json` út nyelve

| Út | Jelentés |
|---|---|
| `a.b.c` | objektummezők |
| `lista[0]` | tömbelem index szerint |
| `lista[type=hls]` / `lista[type!=mp4]` | első elem, amelynek a mezője egyenlő / nem egyenlő |
| `lista[file~live]` / `lista[file!~bumper]` | első elem, amelynek a mezője tartalmazza / nem tartalmazza |
| `lista.file` | a tömb első elemének mezője |

## Hibák

Telepítéskor az app ellenőrzi a fájlt (a regexeket is lefordítja), és megmondja, mi a hiba. Lejátszáskor egy
hibás lépés a lépés sorszámával jelenik meg (pl. „2. lépés: a regex nem talált egyezést”).
