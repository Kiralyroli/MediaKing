package com.kiroland.mediacenter.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.kiroland.mediacenter.media.MediaType
import com.kiroland.mediacenter.data.storage.FsEntry
import com.kiroland.mediacenter.data.storage.MediaFiles
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class SubtitleInfo(val language: String?, val forced: Boolean, val sdh: Boolean)

/**
 * Finds subtitle files for a video and converts them to UTF-8: Hungarian subtitles are often
 * Windows-1250, which ExoPlayer would garble.
 *
 * - Files next to the video whose name starts with the video's name ("Film.hu.srt").
 * - If the video is the only one in its folder (typical release folder), also every subtitle in that
 *   folder and its subfolders, whatever the name ("hun/life.2160p.forced.hunsub-trinity.srt").
 */
object SubtitleLoader {

    fun findSidecars(videoPath: String, files: MediaFiles, cacheDir: File): List<MediaItem.SubtitleConfiguration> {
        val children = files.list(MediaFiles.parentOf(videoPath)).orEmpty()
        val baseName = MediaFiles.nameOf(videoPath).substringBeforeLast('.')
        val soleVideo = children.count { !it.isDirectory && MediaType.fromFileName(it.name) == MediaType.VIDEO && !isSample(it.name) } <= 1

        val candidates = buildList {
            children.filter { !it.isDirectory && isSubtitle(it) }.forEach { add(it to null) }
            if (soleVideo) {
                children.filter { it.isDirectory && !isSample(it.name) }.forEach { sub ->
                    files.list(sub.path).orEmpty().filter { !it.isDirectory && isSubtitle(it) }.forEach { add(it to sub.name) }
                }
            }
        }.filter { (file, _) -> soleVideo || file.name.startsWith(baseName, ignoreCase = true) }
            .sortedBy { (file, folder) -> "${folder.orEmpty()}/${file.name}" }

        val outDir = File(cacheDir, "subtitles").apply { mkdirs() }
        return candidates.mapNotNull { (file, folder) ->
            val extension = file.extension.lowercase()
            val converted = runCatching {
                File(outDir, "${file.path.hashCode().toUInt()}.$extension").apply {
                    writeText(decode(files.readBytes(file.path, MAX_SUBTITLE_BYTES)), Charsets.UTF_8)
                }
            }.getOrNull() ?: return@mapNotNull null
            val info = describe(file.nameWithoutExtension, baseName, folder)
            val label = listOfNotNull(folder, file.name).joinToString("/")
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(converted))
                .setId(DetailedTrackNameProvider.EXTERNAL_ID_PREFIX + label)
                .setMimeType(MIME_BY_EXTENSION.getValue(extension))
                .setLanguage(info.language)
                .setLabel(label)
                .setSelectionFlags(if (info.forced) C.SELECTION_FLAG_FORCED else 0)
                .setRoleFlags(if (info.sdh) C.ROLE_FLAG_CAPTION else 0)
                .build()
        }
    }

    /**
     * Language and flags from the file and folder names. Two-letter codes only count in the part after
     * the video's name ("Film.hu"), so words in a title ("It.Follows") are not taken for a language.
     */
    fun describe(subtitleNameWithoutExtension: String, videoBaseName: String, folder: String?): SubtitleInfo {
        val matchesVideo = subtitleNameWithoutExtension.startsWith(videoBaseName, ignoreCase = true)
        val suffixTokens = if (matchesVideo) tokens(subtitleNameWithoutExtension.drop(videoBaseName.length)) else emptyList()
        val allTokens = tokens(subtitleNameWithoutExtension) + tokens(folder.orEmpty())

        val language = allTokens.firstNotNullOfOrNull { LANGUAGE_ALIASES[it] }
            ?: suffixTokens.firstOrNull { it.length == 2 && it in ISO_LANGUAGES }
        return SubtitleInfo(
            language = language,
            forced = allTokens.any { it == "forced" || it == "forc" },
            sdh = allTokens.any { it == "sdh" || it == "cc" || it == "hi" },
        )
    }

    /** Kept for callers that only need the language of "Film.hu.srt"-style names. */
    fun languageOf(subtitleNameWithoutExtension: String, videoBaseName: String): String? =
        describe(subtitleNameWithoutExtension, videoBaseName, folder = null).language

    /** UTF-8 (with or without BOM) and UTF-16 with BOM are honoured, everything else is Windows-1250. */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, WINDOWS_1250)
        }
    }

    private fun tokens(text: String): List<String> =
        text.lowercase().split('.', '_', '-', ' ', '[', ']', '(', ')').filter { it.isNotEmpty() }

    private fun isSubtitle(file: FsEntry) = file.extension.lowercase() in MIME_BY_EXTENSION

    private const val MAX_SUBTITLE_BYTES = 10L * 1024 * 1024

    private fun isSample(name: String) = Regex("""(?i)(^|[ ._-])sample([ ._-]|$)""").containsMatchIn(name.substringBeforeLast('.'))

    private val WINDOWS_1250: Charset = Charset.forName("windows-1250")

    private val ISO_LANGUAGES: Set<String> = java.util.Locale.getISOLanguages().toSet()

    private val MIME_BY_EXTENSION = mapOf(
        "srt" to MimeTypes.APPLICATION_SUBRIP,
        "ass" to MimeTypes.TEXT_SSA,
        "ssa" to MimeTypes.TEXT_SSA,
        "vtt" to MimeTypes.TEXT_VTT,
    )

    private val LANGUAGE_ALIASES = mapOf(
        "hun" to "hu", "hungarian" to "hu", "magyar" to "hu", "hunsub" to "hu", "hundub" to "hu",
        "eng" to "en", "english" to "en", "angol" to "en", "engsub" to "en",
        "ger" to "de", "deu" to "de", "german" to "de",
    )
}
