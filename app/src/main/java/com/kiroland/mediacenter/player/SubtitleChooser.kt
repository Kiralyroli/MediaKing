package com.kiroland.mediacenter.player

/** A text track as seen by [SubtitleChooser]: its position in Tracks plus what matters for choosing. */
data class TextTrackOption(val groupIndex: Int, val trackIndex: Int, val language: String?, val forced: Boolean)

sealed interface SubtitleChoice {
    data class Select(val option: TextTrackOption) : SubtitleChoice
    data object Off : SubtitleChoice
    /** Nothing better to suggest: leave ExoPlayer's own choice alone. */
    data object Keep : SubtitleChoice
}

/**
 * Hungarian-first automatic subtitle choice:
 * - Hungarian audio (dubbed releases): only the Hungarian *forced* track (signs, foreign dialogue), else none.
 * - Any other audio: the full Hungarian track, falling back to a forced one.
 */
object SubtitleChooser {

    fun choose(audioLanguage: String?, options: List<TextTrackOption>, preferred: String = "hu"): SubtitleChoice {
        val inPreferred = options.filter { it.language.normalized() == preferred }
        return if (audioLanguage.normalized() == preferred) {
            inPreferred.firstOrNull { it.forced }?.let { SubtitleChoice.Select(it) } ?: SubtitleChoice.Off
        } else {
            (inPreferred.firstOrNull { !it.forced } ?: inPreferred.firstOrNull())
                ?.let { SubtitleChoice.Select(it) }
                ?: SubtitleChoice.Keep
        }
    }

    /** "hun"/"hu-HU" → "hu"; Media3 usually normalises already, sidecar and odd files may not. */
    private fun String?.normalized(): String? = when (val code = this?.lowercase()?.substringBefore('-')) {
        null, "", "und" -> null
        "hun" -> "hu"
        "eng" -> "en"
        else -> code
    }
}
