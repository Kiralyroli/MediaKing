package com.kiroland.mediacenter.data.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * What reading the drives takes on each Android version. Up to 10 (the target TV) the legacy storage
 * permissions give plain file access to everything; from 11 the same paths only show folders and media
 * files, and from 13 reading videos takes READ_MEDIA_VIDEO instead.
 */
object StoragePermissions {

    /** Plain java.io access to whole drives, sidecar subtitles and writes included (Android 10 and older). */
    val hasLegacyAccess: Boolean get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q

    val required: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

    /** The permission that decides whether the library can be read at all. */
    private val reading: String get() = required.first()

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, reading) == PackageManager.PERMISSION_GRANTED

    fun isGranted(result: Map<String, Boolean>): Boolean = result[reading] == true
}
