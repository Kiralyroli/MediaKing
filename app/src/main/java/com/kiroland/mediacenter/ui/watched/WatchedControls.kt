package com.kiroland.mediacenter.ui.watched

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kiroland.mediacenter.ui.theme.OnAccent
import com.kiroland.mediacenter.ui.theme.TextMuted
import com.kiroland.mediacenter.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StarColor = Color(0xFFFFC857)

/** "Értékelésed" and five stars; pressing the current rating again clears it. */
@Composable
fun RatingRow(rating: Int?, onRate: (Int?) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Értékelésed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 6.dp))
        (1..5).forEach { star ->
            val filled = rating != null && star <= rating
            Surface(
                onClick = { onRate(if (rating == star) null else star) },
                shape = ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = if (filled) StarColor else TextMuted,
                    focusedContainerColor = TextPrimary,
                    focusedContentColor = if (filled) Color(0xFFB7791F) else OnAccent,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
            ) {
                Icon(
                    if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "$star csillag",
                    modifier = Modifier.padding(6.dp).size(28.dp),
                )
            }
        }
        if (rating == null) Text("nincs értékelve", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

/** "★★★★☆" for a list. */
fun starsText(rating: Int?): String? = rating?.let { "★".repeat(it) + "☆".repeat(5 - it) }

/** "2026. 10. 06." / "10/6/26" / "06.10.26" (short enough for a poster caption) */
fun watchedDate(millis: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, com.kiroland.mediacenter.util.AppLocale.current).format(Date(millis))
