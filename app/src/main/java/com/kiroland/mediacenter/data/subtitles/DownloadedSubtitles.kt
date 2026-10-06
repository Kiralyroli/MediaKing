package com.kiroland.mediacenter.data.subtitles

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.kiroland.mediacenter.player.DetailedTrackNameProvider
import com.kiroland.mediacenter.player.SubtitleLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloaded subtitles, kept in the app's own storage per video (never written next to the video:
 * the drive is the user's and stays read-only), and offered to the player like sidecar files.
 */
@Singleton
class DownloadedSubtitles @Inject constructor(@param:ApplicationContext context: Context) {

    @Serializable
    data class Entry(val file: String, val language: String, val release: String, val forced: Boolean, val sdh: Boolean)

    private val root = File(context.filesDir, "downloaded_subtitles")
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(Entry.serializer())

    fun list(videoPath: String): List<Entry> {
        val index = File(dirFor(videoPath), INDEX)
        if (!index.isFile) return emptyList()
        return runCatching { json.decodeFromString(listSerializer, index.readText()) }.getOrDefault(emptyList())
            .filter { File(dirFor(videoPath), it.file).isFile }
    }

    /** Stores the file (as UTF-8, whatever it came in) and returns the player's id for it. */
    @Synchronized
    fun save(videoPath: String, offer: SubtitleOffer, bytes: ByteArray): String {
        val dir = dirFor(videoPath).apply { mkdirs() }
        val name = "${offer.language}.${offer.fileId}.srt"
        File(dir, name).writeText(SubtitleLoader.decode(bytes), Charsets.UTF_8)
        val entry = Entry(name, offer.language, offer.release, offer.forced, offer.hearingImpaired)
        val entries = list(videoPath).filterNot { it.file == name } + entry
        File(dir, INDEX).writeText(json.encodeToString(listSerializer, entries))
        return idOf(entry)
    }

    fun configurations(videoPath: String): List<MediaItem.SubtitleConfiguration> = list(videoPath).map { entry ->
        MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(File(dirFor(videoPath), entry.file)))
            .setId(idOf(entry))
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage(entry.language)
            .setLabel("OpenSubtitles: ${entry.release}")
            .setSelectionFlags(if (entry.forced) C.SELECTION_FLAG_FORCED else 0)
            .setRoleFlags(if (entry.sdh) C.ROLE_FLAG_CAPTION else 0)
            .build()
    }

    private fun idOf(entry: Entry) = DetailedTrackNameProvider.EXTERNAL_ID_PREFIX + "OpenSubtitles: ${entry.release} (${entry.file})"

    private fun dirFor(videoPath: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(videoPath.toByteArray())
        return File(root, digest.joinToString("") { "%02x".format(it) })
    }

    private companion object {
        const val INDEX = "index.json"
    }
}

/** Puts the releases that match the video's file name first ("Show.S01E01.1080p.WEB-DL.x264-GROUP"). */
object SubtitleRanking {
    private val SPLIT = Regex("[^a-z0-9]+")

    fun rank(offers: List<SubtitleOffer>, videoFileName: String): List<SubtitleOffer> {
        val wanted = tokens(videoFileName.substringBeforeLast('.'))
        return offers.sortedWith(
            compareByDescending<SubtitleOffer> { offer -> tokens(offer.release).count { it in wanted } }
                .thenBy { it.machineTranslated }
                .thenByDescending { it.downloads },
        )
    }

    fun tokens(name: String): Set<String> = name.lowercase().split(SPLIT).filter { it.length >= 2 }.toSet()
}
