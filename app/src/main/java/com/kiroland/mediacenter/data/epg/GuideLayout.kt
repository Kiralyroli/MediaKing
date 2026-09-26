package com.kiroland.mediacenter.data.epg

/**
 * Places a channel's programmes on the guide's time axis. Programmes are clipped to the window;
 * holes in the schedule become [Block]s without a programme, so every row covers the whole window.
 */
object GuideLayout {
    data class Block(val start: Long, val end: Long, val programme: Programme?) {
        val minutes: Float get() = (end - start) / 60_000f
    }

    const val SLOT_MS = 30 * 60 * 1000L

    /** The guide opens half an hour before now, on a half-hour boundary (in local time). */
    fun windowStart(now: Long, offsetMs: Long = java.util.TimeZone.getDefault().getOffset(now).toLong()): Long {
        val local = now + offsetMs - SLOT_MS
        return local - Math.floorMod(local, SLOT_MS) - offsetMs
    }

    fun blocks(programmes: List<Programme>, from: Long, to: Long): List<Block> {
        val result = mutableListOf<Block>()
        var cursor = from
        for (p in programmes.sortedBy { it.start }) {
            if (p.stop <= cursor || p.start >= to) continue
            val start = maxOf(p.start, cursor)
            if (start > cursor) result += Block(cursor, start, null)
            val end = minOf(p.stop, to)
            result += Block(start, end, p)
            cursor = end
        }
        if (cursor < to) result += Block(cursor, to, null)
        return result
    }
}
