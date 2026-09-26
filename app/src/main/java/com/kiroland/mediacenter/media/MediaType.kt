package com.kiroland.mediacenter.media

enum class MediaType {
    VIDEO, AUDIO, IMAGE, SUBTITLE, STREAM, OTHER;

    val isPlayable: Boolean get() = this == VIDEO || this == AUDIO

    companion object {
        private val byExtension: Map<String, MediaType> = buildMap {
            listOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "mts", "mpg", "mpeg", "wmv", "flv", "3gp")
                .forEach { put(it, VIDEO) }
            listOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "ac3", "dts", "mka")
                .forEach { put(it, AUDIO) }
            listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp").forEach { put(it, IMAGE) }
            listOf("srt", "ass", "ssa", "vtt", "sub").forEach { put(it, SUBTITLE) }
            put("strm", STREAM)
        }

        fun fromFileName(name: String): MediaType =
            byExtension[name.substringAfterLast('.', "").lowercase()] ?: OTHER
    }
}
