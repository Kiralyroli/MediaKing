package com.kiroland.mediacenter.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.TrackNameProvider
import java.util.Locale

/**
 * Track names with everything useful for choosing on a TV, in Hungarian, e.g.
 * "Magyar · SRT · Kényszerített · külső fájl" or "Angol · Dolby TrueHD · 7.1 · Kommentár".
 * Media3's default provider only shows language and label.
 */
@OptIn(UnstableApi::class)
object DetailedTrackNameProvider : TrackNameProvider {

    const val EXTERNAL_ID_PREFIX = "external:"

    private val HU = Locale.forLanguageTag("hu-HU")
    private val NON_LANGUAGES = setOf(C.LANGUAGE_UNDETERMINED, "mul", "mis", "zxx")

    override fun getTrackName(format: Format): String {
        val mime = effectiveMimeType(format)
        val parts = when (MimeTypes.getTrackType(mime)) {
            C.TRACK_TYPE_TEXT -> textParts(format, mime)
            C.TRACK_TYPE_AUDIO -> audioParts(format, mime)
            C.TRACK_TYPE_VIDEO -> videoParts(format, mime)
            else -> listOfNotNull(languageName(format.language), format.label)
        }
        return parts.filterNot { it.isNullOrBlank() }.distinct().joinToString(" · ").ifEmpty { "Ismeretlen sáv" }
    }

    /** Subtitles parsed during extraction are re-labelled as Media3 cues; the original type is in `codecs`. */
    private fun effectiveMimeType(format: Format): String? =
        if (format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES) format.codecs else format.sampleMimeType

    private fun textParts(format: Format, mime: String?): List<String?> {
        val external = format.id?.contains(EXTERNAL_ID_PREFIX) == true
        return buildList {
            add(languageName(format.language) ?: "Ismeretlen nyelv")
            // Embedded tracks often carry a useful title ("Forced", "SDH"); for files it is the file name, shown last.
            if (!external) add(format.label)
            add(subtitleFormatName(mime))
            // Many MKVs only say "forced" / "SDH" in the track title, without setting the flags.
            val label = format.label.orEmpty()
            if (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0 || label.contains("forced", ignoreCase = true)) {
                add("Kényszerített")
            }
            if (format.roleFlags and (C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0 ||
                label.contains("sdh", ignoreCase = true)
            ) {
                add("SDH")
            }
            if (format.roleFlags and C.ROLE_FLAG_COMMENTARY != 0) add("Kommentár")
            if (external) add("külső fájl: ${format.label}")
        }
    }

    private fun audioParts(format: Format, mime: String?): List<String?> = buildList {
        add(languageName(format.language) ?: if (format.label.isNullOrBlank()) "Ismeretlen nyelv" else null)
        add(format.label)
        add(audioCodecName(mime))
        add(channelLayout(format.channelCount))
        if (format.bitrate > 0) add("${format.bitrate / 1000} kbps")
        if (format.roleFlags and C.ROLE_FLAG_COMMENTARY != 0) add("Kommentár")
        if (format.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0) add("Narrált")
    }

    private fun videoParts(format: Format, mime: String?): List<String?> = buildList {
        if (format.width > 0 && format.height > 0) add("${format.width}×${format.height}")
        add(videoCodecName(mime))
        when (format.colorInfo?.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> add("HDR10")
            C.COLOR_TRANSFER_HLG -> add("HLG")
        }
        if (format.frameRate > 0) add(String.format(HU, "%.3f fps", format.frameRate).replace(Regex(",?0+ fps$"), " fps"))
        if (format.bitrate > 0) add(String.format(HU, "%.1f Mbps", format.bitrate / 1_000_000.0))
        add(format.label)
    }

    fun languageName(language: String?): String? {
        // "mul" (several languages) and friends say nothing useful; the track title, if any, does.
        if (language.isNullOrBlank() || language in NON_LANGUAGES) return null
        val name = Locale.forLanguageTag(language).getDisplayName(HU)
        if (name.isBlank() || name.equals(language, ignoreCase = true)) return language
        return name.replaceFirstChar { it.titlecase(HU) }
    }

    fun subtitleFormatName(mime: String?): String? = when (mime) {
        MimeTypes.APPLICATION_SUBRIP -> "SRT"
        MimeTypes.TEXT_SSA -> "ASS/SSA"
        MimeTypes.TEXT_VTT, MimeTypes.APPLICATION_MP4VTT -> "WebVTT"
        MimeTypes.APPLICATION_TTML -> "TTML"
        MimeTypes.APPLICATION_TX3G -> "MP4 szöveg"
        MimeTypes.APPLICATION_PGS -> "PGS (képalapú)"
        MimeTypes.APPLICATION_VOBSUB -> "VobSub (képalapú)"
        MimeTypes.APPLICATION_DVBSUBS -> "DVB (képalapú)"
        MimeTypes.APPLICATION_CEA608, MimeTypes.APPLICATION_CEA708, MimeTypes.APPLICATION_MP4CEA608 -> "CC"
        null -> null
        else -> mime.substringAfterLast('/').removePrefix("x-").uppercase()
    }

    private fun audioCodecName(mime: String?): String? = when (mime) {
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MP3"
        MimeTypes.AUDIO_AC3 -> "Dolby Digital"
        MimeTypes.AUDIO_E_AC3 -> "Dolby Digital Plus"
        MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Atmos (DD+)"
        MimeTypes.AUDIO_AC4 -> "Dolby AC-4"
        MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
        MimeTypes.AUDIO_DTS -> "DTS"
        MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
        MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_RAW -> "PCM"
        null -> null
        else -> mime.substringAfterLast('/').uppercase()
    }

    private fun videoCodecName(mime: String?): String? = when (mime) {
        MimeTypes.VIDEO_H264 -> "H.264"
        MimeTypes.VIDEO_H265 -> "HEVC"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
        MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
        MimeTypes.VIDEO_VC1 -> "VC-1"
        null -> null
        else -> mime.substringAfterLast('/').uppercase()
    }

    fun channelLayout(channels: Int): String? = when (channels) {
        Format.NO_VALUE, 0 -> null
        1 -> "Mono"
        2 -> "Sztereó"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$channels csatorna"
    }
}
