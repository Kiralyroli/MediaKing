package com.kiroland.mediacenter.data.network

/**
 * A location on an SMB share, written in the library as "smb://host/share/dir/file.mkv"
 * (raw, not percent-encoded; '/' separated). [path] is relative to the share, "" for its root.
 */
data class SmbPath(val host: String, val share: String, val path: String = "") {
    val uri: String get() = "$SCHEME$host/$share" + if (path.isEmpty()) "" else "/$path"

    /** The share-relative path as SMB wants it. */
    val smbPath: String get() = path.replace('/', '\\')

    val name: String get() = if (path.isEmpty()) share else path.substringAfterLast('/')

    fun child(name: String): SmbPath = copy(path = if (path.isEmpty()) name else "$path/$name")

    override fun toString(): String = uri

    companion object {
        const val SCHEME = "smb://"

        fun isSmb(path: String): Boolean = path.startsWith(SCHEME, ignoreCase = true)

        fun parse(path: String): SmbPath? {
            if (!isSmb(path)) return null
            val parts = path.substring(SCHEME.length).replace('\\', '/').split('/').filter { it.isNotEmpty() }
            if (parts.size < 2 || !isHost(parts[0])) return null
            if (parts.any { it == "." || it == ".." }) return null
            return SmbPath(parts[0], parts[1], parts.drop(2).joinToString("/"))
        }

        /** Host names and IPv4 addresses; the share is found by name, so nothing fancier is needed. */
        fun isHost(host: String): Boolean = HOST.matches(host)

        private val HOST = Regex("""[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?""")
    }
}
