package com.kiroland.mediacenter.data.segments

/**
 * Reads the chapter list of a Matroska file (Media3 does not expose it). Only the header and the
 * Chapters element are read: [read] returns up to `length` bytes at `offset` (fewer at the end).
 */
object MkvChapters {

    fun read(read: (offset: Long, length: Int) -> ByteArray): List<Chapter>? = runCatching {
        val head = read(0, HEADER_BYTES)
        val r = Reader(head)
        if (r.id(0) != EBML) return null
        var p = r.skip(0)
        if (r.id(p) != SEGMENT) return null
        val segmentData = r.dataStart(p)
        p = segmentData
        var chaptersAt: Long? = null
        while (p < head.size - 12) {
            val id = r.id(p)
            when (id) {
                CHAPTERS -> return parseChapters(head, p)
                SEEK_HEAD -> chaptersAt = seekPosition(r, p)?.let { segmentData + it }
                CLUSTER -> break
            }
            p = r.skip(p)
        }
        val at = chaptersAt ?: return null
        if (at < head.size - 12) return parseChapters(head, at.toInt())
        // Chapters written after the media data: fetch just that element.
        val sizeProbe = read(at, 12)
        val probe = Reader(sizeProbe)
        if (probe.id(0) != CHAPTERS) return null
        val total = probe.dataStart(0) + probe.size(0)
        if (total > MAX_CHAPTERS_BYTES) return null
        parseChapters(read(at, total.toInt()), 0)
    }.getOrNull()

    private fun seekPosition(r: Reader, seekHead: Int): Long? {
        var result: Long? = null
        r.children(seekHead) { id, data, size ->
            if (id != SEEK) return@children
            var target = 0L
            var position: Long? = null
            r.childrenOf(data, data + size) { cid, cdata, csize ->
                if (cid == SEEK_ID) target = r.uint(cdata, csize)
                if (cid == SEEK_POSITION) position = r.uint(cdata, csize)
            }
            if (target == CHAPTERS) result = position
        }
        return result
    }

    private fun parseChapters(bytes: ByteArray, at: Int): List<Chapter> {
        val r = Reader(bytes)
        val chapters = mutableListOf<Chapter>()
        var editionDone = false
        r.children(at) { id, data, size ->
            // The first edition is the default one.
            if (id != EDITION_ENTRY || editionDone) return@children
            editionDone = true
            r.childrenOf(data, data + size) { aid, adata, asize ->
                if (aid != CHAPTER_ATOM) return@childrenOf
                var start = -1L
                var name: String? = null
                var hidden = false
                r.childrenOf(adata, adata + asize) { cid, cdata, csize ->
                    when (cid) {
                        CHAPTER_TIME_START -> start = r.uint(cdata, csize)
                        CHAPTER_FLAG_HIDDEN -> hidden = r.uint(cdata, csize) != 0L
                        CHAPTER_DISPLAY -> r.childrenOf(cdata, cdata + csize) { did, ddata, dsize ->
                            if (did == CHAP_STRING && name == null) name = String(bytes, ddata, dsize, Charsets.UTF_8)
                        }
                    }
                }
                if (start >= 0 && !hidden) chapters += Chapter(start / 1_000_000, name)
            }
        }
        return chapters.sortedBy { it.startMs }
    }

    /** EBML primitives over a byte array; element IDs keep their length marker, sizes do not. */
    private class Reader(val b: ByteArray) {
        private fun length(first: Int): Int {
            var mask = 0x80
            var len = 1
            while (len <= 8 && first and mask == 0) {
                mask = mask shr 1
                len++
            }
            require(len <= 8) { "bad vint" }
            return len
        }

        fun id(p: Int): Long {
            val first = b[p].toInt() and 0xFF
            val len = length(first)
            return uint(p, len)
        }

        fun size(p: Int): Long {
            val idLen = length(b[p].toInt() and 0xFF)
            val first = b[p + idLen].toInt() and 0xFF
            val len = length(first)
            var v = (first and ((0x80 shr (len - 1)) - 1)).toLong()
            for (i in 1 until len) v = (v shl 8) or (b[p + idLen + i].toLong() and 0xFF)
            return v
        }

        fun dataStart(p: Int): Int {
            val idLen = length(b[p].toInt() and 0xFF)
            return p + idLen + length(b[p + idLen].toInt() and 0xFF)
        }

        fun skip(p: Int): Int = (dataStart(p) + size(p)).toInt()

        fun uint(p: Int, n: Int): Long {
            var v = 0L
            for (i in 0 until n) v = (v shl 8) or (b[p + i].toLong() and 0xFF)
            return v
        }

        fun children(element: Int, block: (id: Long, data: Int, size: Int) -> Unit) {
            val start = dataStart(element)
            childrenOf(start, (start + size(element)).toInt(), block)
        }

        fun childrenOf(from: Int, to: Int, block: (id: Long, data: Int, size: Int) -> Unit) {
            var p = from
            val end = minOf(to, b.size)
            while (p < end) {
                val data = dataStart(p)
                val size = size(p).toInt()
                if (size < 0 || data + size > b.size) return
                block(id(p), data, size)
                p = data + size
            }
        }
    }

    private const val HEADER_BYTES = 256 * 1024
    private const val MAX_CHAPTERS_BYTES = 1024 * 1024
    private const val EBML = 0x1A45DFA3L
    private const val SEGMENT = 0x18538067L
    private const val SEEK_HEAD = 0x114D9B74L
    private const val SEEK = 0x4DBBL
    private const val SEEK_ID = 0x53ABL
    private const val SEEK_POSITION = 0x53ACL
    private const val CLUSTER = 0x1F43B675L
    private const val CHAPTERS = 0x1043A770L
    private const val EDITION_ENTRY = 0x45B9L
    private const val CHAPTER_ATOM = 0xB6L
    private const val CHAPTER_TIME_START = 0x91L
    private const val CHAPTER_FLAG_HIDDEN = 0x98L
    private const val CHAPTER_DISPLAY = 0x80L
    private const val CHAP_STRING = 0x85L
}
