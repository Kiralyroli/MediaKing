package com.kiroland.mediacenter.media.parse

sealed interface ParsedMedia {
    data class Movie(val title: String, val year: Int?) : ParsedMedia

    data class Episode(
        val seriesTitle: String,
        val season: Int,
        val episode: Int,
        /** Last episode of a multi-episode file ("S01E01E02"), else null. */
        val episodeEnd: Int?,
        val year: Int?,
    ) : ParsedMedia
}

/**
 * Recognises movies and episodes from scene/release-style file names and folder layouts:
 *
 * - `Interstellar.2014.1080p.BluRay.x264-GROUP.mkv` → Movie("Interstellar", 2014)
 * - `Breaking.Bad.S02E05.1080p.mkv`, `Show 2x05.mkv` → Episode("Breaking Bad", 2, 5)
 * - `Breaking Bad/Season 2/05 - Title.mkv`, `Barátok közt/1. évad/E05.mkv` → Episode from folders
 * - `Interstellar (2014)/movie.mkv` → the folder name is used when the file name says less
 */
object MediaNameParser {

    /**
     * @param fileName file name with extension
     * @param parentDirs folder names between the library root and the file, outermost first
     */
    fun parse(fileName: String, parentDirs: List<String> = emptyList()): ParsedMedia {
        val base = fileName.substringBeforeLast('.', fileName)

        parseEpisodeFromName(base, parentDirs)?.let { return it }
        parseEpisodeFromFolders(base, parentDirs)?.let { return it }

        val fromFile = parseMovie(base)
        val parent = parentDirs.lastOrNull()?.takeUnless { SEASON_DIR.matches(it.trim()) }
        if (parent != null && fromFile.year == null) {
            val fromFolder = parseMovie(parent)
            if (fromFolder.year != null || fromFile.title.isBlank()) return fromFolder
        }
        return fromFile.takeIf { it.title.isNotBlank() } ?: ParsedMedia.Movie(cleanSeparators(base), null)
    }

    /** Normalised key for grouping episodes of the same show: "The.Office (US)" and "the office us" match. */
    fun seriesKey(seriesTitle: String): String =
        seriesTitle.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), "")

    private fun parseEpisodeFromName(base: String, parentDirs: List<String>): ParsedMedia.Episode? {
        val match = SXXEYY.find(base) ?: NXNN.find(base) ?: return null
        val season = match.groupValues[1].toInt()
        val episode = match.groupValues[2].toInt()
        val episodeEnd = match.groupValues.getOrNull(3)?.toIntOrNull()?.takeIf { it > episode }

        val before = base.substring(0, match.range.first)
        var (title, year) = titleAndYear(before)
        if (title.isBlank()) {
            // "S02E05.mkv" inside "Breaking Bad/Season 2": take the show from the folders.
            val showDir = parentDirs.lastOrNull { !SEASON_DIR.matches(it.trim()) } ?: return null
            titleAndYear(showDir).let { (t, y) -> title = t; year = y }
        }
        if (title.isBlank()) return null
        if (year == null) {
            // "Charmed.1998.S01.1080p/Charmed.S01E07.mkv": the release folder often carries the show's year,
            // which tells reboots apart when looking the show up.
            val key = seriesKey(title)
            year = parentDirs.asReversed().firstNotNullOfOrNull { dir ->
                val folderTitle = SXXEYY_OR_SEASON.split(dir).first()
                titleAndYear(folderTitle).takeIf { (t, _) -> seriesKey(t) == key }?.second
            }
        }
        return ParsedMedia.Episode(title, season, episode, episodeEnd, year)
    }

    private fun parseEpisodeFromFolders(base: String, parentDirs: List<String>): ParsedMedia.Episode? {
        if (parentDirs.size < 2) return null
        val seasonMatch = SEASON_DIR.matchEntire(parentDirs.last().trim()) ?: return null
        val season = seasonMatch.groupValues.drop(1).first { it.isNotEmpty() }.toInt()
        val episode = (EPISODE_ONLY.find(base) ?: LEADING_NUMBER.find(base))?.groupValues?.get(1)?.toInt() ?: return null
        val (title, year) = titleAndYear(parentDirs[parentDirs.size - 2])
        if (title.isBlank()) return null
        return ParsedMedia.Episode(title, season, episode, null, year)
    }

    private fun parseMovie(text: String): ParsedMedia.Movie {
        val (title, year) = titleAndYear(text)
        return ParsedMedia.Movie(title, year)
    }

    /**
     * Title is everything before the release year or the first quality/codec token.
     * The last year that is not at the very start wins, so "1917.2019" → ("1917", 2019) and
     * "2001 A Space Odyssey 1968" → ("2001 A Space Odyssey", 1968).
     */
    private fun titleAndYear(raw: String): Pair<String, Int?> {
        val text = cleanSeparators(raw.replace(BRACKETED, " "))
        val yearMatch = YEAR.findAll(text).lastOrNull { it.range.first > 0 }
        val beforeYear = yearMatch?.let { text.substring(0, it.range.first) } ?: text
        val tokens = beforeYear.split(' ').filter { it.isNotBlank() }
        val titleTokens = tokens.takeWhile { !isJunk(it) }
        val title = titleTokens.joinToString(" ").trim(' ', '-', '(', '[', ',')
        return title to yearMatch?.groupValues?.get(1)?.toInt()
    }

    private fun cleanSeparators(text: String): String =
        text.replace('.', ' ').replace('_', ' ')
            .replace(Regex("""[()]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun isJunk(token: String): Boolean {
        val t = token.lowercase().trim('-', '[', ']')
        return t in JUNK || t.substringBefore('-') in JUNK || RESOLUTION.matches(t)
    }

    // Android compiles these with ICU, not java.util.regex: no inline (?u) flag and no bare "]" in classes.
    // RegexOption.IGNORE_CASE makes Kotlin add UNICODE_CASE on the JVM, so "Évad" matches "évad" in both.
    private val IC = RegexOption.IGNORE_CASE
    private val SXXEYY = Regex("""(?<![a-z0-9])s(\d{1,2})[ ._-]?e(\d{1,3})(?:[ ._-]?-?e(\d{1,3}))?(?![0-9])""", IC)
    /** Splits "Charmed.1998.S01.1080p" before the season code, leaving the show part. */
    private val SXXEYY_OR_SEASON = Regex("""(?<![a-z0-9])s\d{1,2}(?:e\d{1,3})?(?![0-9])""", IC)
    private val NXNN = Regex("""(?<![a-z0-9])(\d{1,2})x(\d{2,3})(?![0-9])""", IC)
    private val SEASON_DIR = Regex("""^(?:season|évad|evad|series|s)\s*(\d{1,2})$|^(\d{1,2})\s*\.?\s*(?:évad|evad|season)$""", IC)
    private val EPISODE_ONLY = Regex("""(?<![a-z])(?:e|ep|episode|rész|resz)\s*\.?\s*(\d{1,3})(?![0-9])""", IC)
    private val LEADING_NUMBER = Regex("""^\s*(\d{1,3})(?![0-9])""")
    private val YEAR = Regex("""(?<![0-9])(19[2-9]\d|20[0-4]\d)(?![0-9])""")
    private val BRACKETED = Regex("""\[[^\]]*\]|\{[^\}]*\}""")
    private val RESOLUTION = Regex("""\d{3,4}[pi]""")

    private val JUNK = setOf(
        "4k", "uhd", "hdr", "hdr10", "hdr10+", "dv", "dovi", "sdr", "remux", "bluray", "blu-ray", "bdrip", "brrip",
        "bdremux", "webrip", "web-dl", "webdl", "web", "hdtv", "dvdrip", "dvd", "hdrip", "x264", "x265", "h264",
        "h265", "h 264", "h 265", "hevc", "avc", "av1", "xvid", "divx", "10bit", "8bit", "aac", "ac3", "eac3", "ddp",
        "ddp5", "dd5", "dts", "dts-hd", "truehd", "atmos", "hun", "eng", "multi", "dual", "extended", "unrated",
        "proper", "repack", "internal", "limited", "imax", "hybrid", "hc", "sub", "subs", "dubbed", "magyar",
        "custom", "retail", "amzn", "atvp", "nf", "dsnp", "hmax", "hulu", "dd2", "hunsub", "hundub",
    )
}
