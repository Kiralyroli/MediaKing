# MediaKing – Adatvédelmi nyilatkozat / Privacy policy

*Hatályos / Effective: 2026-10-08*

[Magyar](#magyar) · [English](#english)

---

## Magyar

A MediaKing egy Android TV-s médialejátszó, amely a TV-hez csatlakoztatott meghajtókon és a helyi hálózaton lévő
filmeket és sorozatokat rendezi médiatárba és játssza le. Nyílt forráskódú: <https://github.com/Kiralyroli/MediaKing>.

### Mit gyűjtünk?

**Semmit.** A MediaKing fejlesztője semmilyen adatot nem kap a felhasználóktól. Az appban nincs fiókregisztráció,
reklám, analitika, hibajelentés-küldés vagy más követés.

### Mit tárol az app a TV-n?

Minden adat kizárólag a készüléken marad, és az app törlésével törlődik:

- a médiatár (fájlnevek, elérési utak), a lejátszási pozíciók, a megnézett lista és a saját értékeléseid;
- a beállítások (nyelv, ország, előfizetéseid listája, feliratbeállítások);
- ha megadod: a hálózati (SMB) mappák felhasználóneve és jelszava, a TMDB-token, az OpenSubtitles API-kulcs,
  felhasználónév és jelszó;
- a letöltött feliratok.

A meghajtókon lévő fájlokat az app csak olvassa, kivéve ha a feltöltő szerveren keresztül te töltesz fel vagy törölsz.

### Kivel kommunikál az app?

Az app a működéséhez az alábbi szolgáltatásokhoz fordul. Ezek a kéréssel együtt megkapják a TV IP-címét, és saját
adatvédelmi szabályaik vonatkoznak rájuk.

| Szolgáltatás | Mire | Mit küld |
|---|---|---|
| [The Movie Database (TMDB)](https://www.themoviedb.org/privacy-policy) | filmadatok, poszterek, hol nézhető (JustWatch-adatok) | a fájlnévből kiolvasott címet és évet, a TMDB-azonosítókat, a nyelvet és a beállított országot |
| [OpenSubtitles](https://www.opensubtitles.com/en/privacy) | feliratletöltés (csak ha kéred) | a film vagy epizód TMDB-azonosítóját, a nyelvet, és ha megadtad, a fiókod adatait |
| [TheIntroDB](https://theintrodb.org) | főcím/stáblista átugrása | a film vagy epizód TMDB-azonosítóját |
| Kiegészítők | élő csatornák, műsorújság | amit a kiegészítő leír; kiegészítőt csak te telepíthetsz |

A „Hol nézheted?” gombok a megfelelő streaming appot vagy weboldalt nyitják meg; onnantól annak a szolgáltatónak a
szabályai érvényesek.

### Helyi feltöltő szerver

Ha bekapcsolod, a TV a helyi hálózaton egy weboldalt szolgál ki (feltöltés, távirányító, beállítások). Csak az éri el,
aki ismeri a TV-n megjelenő párosítókódot. Az adatok nem hagyják el a helyi hálózatot.

### Engedélyek

- **Tárhely / videók**: a meghajtókon lévő filmek és feliratok olvasása.
- **Internet, hálózat állapota**: a fenti szolgáltatások és a helyi feltöltő szerver.
- **Előtérben futó szolgáltatás**: a feltöltő szerver, amíg be van kapcsolva.
- **Android TV „Következő” sor**: a félbehagyott filmek megjelenítése a TV kezdőképernyőjén.

### Gyerekek

Az app nem gyermekeknek szól, és senkiről nem gyűjt adatot.

### Változások és kapcsolat

A nyilatkozat változásai ezen az oldalon jelennek meg. Kérdés esetén:
<https://github.com/Kiralyroli/MediaKing/issues>.

---

## English

MediaKing is an Android TV media player that organises and plays films and shows from drives attached to the TV and
from the local network. It is open source: <https://github.com/Kiralyroli/MediaKing>.

### What we collect

**Nothing.** The developer of MediaKing receives no data from its users. The app has no accounts, ads, analytics,
crash reporting or any other tracking.

### What the app stores on the TV

All data stays on the device only and is removed when the app is uninstalled:

- the library (file names, paths), playback positions, the watched list and your own ratings;
- settings (language, country, your subscriptions, subtitle preferences);
- if you enter them: user names and passwords of network (SMB) folders, a TMDB token, an OpenSubtitles API key,
  user name and password;
- downloaded subtitles.

The app only reads files on the drives, except when you upload or delete files through the upload server.

### Services the app talks to

The app contacts the services below to work. They receive the TV's IP address with each request, and their own privacy
policies apply.

| Service | Purpose | What is sent |
|---|---|---|
| [The Movie Database (TMDB)](https://www.themoviedb.org/privacy-policy) | film data, posters, where to watch (JustWatch data) | the title and year read from the file name, TMDB ids, the language and the chosen country |
| [OpenSubtitles](https://www.opensubtitles.com/en/privacy) | subtitle download (only when you ask for it) | the TMDB id of the film or episode, the language, and your account if you entered one |
| [TheIntroDB](https://theintrodb.org) | skipping intros and credits | the TMDB id of the film or episode |
| Add-ons | live channels, programme guide | whatever the add-on describes; only you can install add-ons |

The "Where to watch" buttons open the streaming app or website; from there that provider's terms apply.

### Local upload server

When you switch it on, the TV serves a web page on the local network (uploads, remote control, settings). Only someone
who knows the pairing code shown on the TV can use it. Nothing leaves the local network.

### Permissions

- **Storage / videos**: reading films and subtitles on the drives.
- **Internet, network state**: the services above and the local upload server.
- **Foreground service**: the upload server while it is switched on.
- **Android TV "Watch Next" row**: showing unfinished films on the TV's home screen.

### Children

The app is not directed at children and collects no data from anyone.

### Changes and contact

Changes to this policy are published on this page. Questions:
<https://github.com/Kiralyroli/MediaKing/issues>.
