package com.kiroland.mediacenter.media

import android.media.MediaCodecList
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import javax.inject.Inject
import javax.inject.Singleton

data class CodecSupport(
    val label: String,
    val mimeType: String,
    /** Platform decoder names; empty when the device has none. */
    val platformDecoders: List<String>,
    val hardwareAccelerated: Boolean,
    /** Whether the bundled FFmpeg extension can decode it (audio only). */
    val ffmpeg: Boolean,
) {
    val playable: Boolean get() = platformDecoders.isNotEmpty() || ffmpeg
}

data class CodecReport(
    val video: List<CodecSupport>,
    val audio: List<CodecSupport>,
    val ffmpegVersion: String?,
)

@Singleton
class CodecInspector @Inject constructor() {

    @OptIn(UnstableApi::class)
    fun inspect(): CodecReport {
        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
        val ffmpegAvailable = FfmpegLibrary.isAvailable()

        fun support(label: String, mime: String, isAudio: Boolean): CodecSupport {
            val matching = decoders.filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
            return CodecSupport(
                label = label,
                mimeType = mime,
                platformDecoders = matching.map { it.name },
                hardwareAccelerated = matching.any { it.isHardwareAccelerated },
                ffmpeg = isAudio && ffmpegAvailable && FfmpegLibrary.supportsFormat(mime),
            )
        }

        return CodecReport(
            video = VIDEO_FORMATS.map { (label, mime) -> support(label, mime, isAudio = false) },
            audio = AUDIO_FORMATS.map { (label, mime) -> support(label, mime, isAudio = true) },
            ffmpegVersion = if (ffmpegAvailable) FfmpegLibrary.getVersion() else null,
        )
    }

    private companion object {
        val VIDEO_FORMATS = listOf(
            "H.264 / AVC" to MimeTypes.VIDEO_H264,
            "H.265 / HEVC" to MimeTypes.VIDEO_H265,
            "AV1" to MimeTypes.VIDEO_AV1,
            "VP9" to MimeTypes.VIDEO_VP9,
            "Dolby Vision" to MimeTypes.VIDEO_DOLBY_VISION,
            "MPEG-2" to MimeTypes.VIDEO_MPEG2,
            "VC-1" to MimeTypes.VIDEO_VC1,
        )
        val AUDIO_FORMATS = listOf(
            "AAC" to MimeTypes.AUDIO_AAC,
            "MP3" to MimeTypes.AUDIO_MPEG,
            "Dolby Digital (AC3)" to MimeTypes.AUDIO_AC3,
            "Dolby Digital Plus (E-AC3)" to MimeTypes.AUDIO_E_AC3,
            "Dolby TrueHD" to MimeTypes.AUDIO_TRUEHD,
            "DTS" to MimeTypes.AUDIO_DTS,
            "DTS-HD" to MimeTypes.AUDIO_DTS_HD,
            "FLAC" to MimeTypes.AUDIO_FLAC,
            "Opus" to MimeTypes.AUDIO_OPUS,
            "Vorbis" to MimeTypes.AUDIO_VORBIS,
        )
    }
}
