package com.kiroland.mediacenter.data.segments

import kotlinx.serialization.Serializable

enum class SegmentType { INTRO, RECAP, CREDITS, PREVIEW }

/** A skippable stretch of a video; [endMs] null means "until the end". */
@Serializable
data class Segment(val type: SegmentType, val startMs: Long, val endMs: Long? = null) {
    fun contains(positionMs: Long): Boolean = positionMs >= startMs && (endMs == null || positionMs < endMs)
}

/** A chapter of the file itself: where it starts and what it is called. */
data class Chapter(val startMs: Long, val name: String?)

object ChapterSegments {

    /**
     * Segments from chapter names, as streaming releases often have them ("Intro", "Opening Credits",
     * "Recap", "Credits"). Numbered or timecode-named chapters say nothing and give no segments.
     */
    fun fromChapters(chapters: List<Chapter>): List<Segment> {
        val sorted = chapters.sortedBy { it.startMs }
        return sorted.mapIndexedNotNull { index, chapter ->
            val type = typeOf(chapter.name) ?: return@mapIndexedNotNull null
            Segment(type, chapter.startMs, sorted.getOrNull(index + 1)?.startMs)
        }
    }

    fun typeOf(name: String?): SegmentType? {
        val n = name?.trim()?.lowercase() ?: return null
        return when {
            RECAP.containsMatchIn(n) -> SegmentType.RECAP
            CREDITS.containsMatchIn(n) && !OPENING.containsMatchIn(n) -> SegmentType.CREDITS
            INTRO.containsMatchIn(n) -> SegmentType.INTRO
            PREVIEW.containsMatchIn(n) -> SegmentType.PREVIEW
            else -> null
        }
    }

    private val INTRO = Regex("""\b(intro|opening|main title|title sequence|theme song)\b|főcím""")
    private val OPENING = Regex("""\bopening\b""")
    private val RECAP = Regex("""\b(recap|previously)\b|előzmények|korábban""")
    private val CREDITS = Regex("""\b(credits|end credits|ending|outro|closing)\b|stáblista""")
    private val PREVIEW = Regex("""\b(preview|next time|next episode|coming up)\b|előzetes""")
}
