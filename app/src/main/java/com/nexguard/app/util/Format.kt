package com.nexguard.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun humanBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return if (i == 0) "${bytes} B" else String.format(Locale.getDefault(), "%.1f %s", value, units[i])
}

fun formatDateTime(ts: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ts))

fun formatShortDate(ts: Long): String =
    SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(ts))

fun relativeTime(ts: Long): String {
    if (ts <= 0) return "—"
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "az önce"
        diff < 3_600_000 -> "${diff / 60_000} dk önce"
        diff < 86_400_000 -> "${diff / 3_600_000} sa önce"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000} gün önce"
        else -> formatShortDate(ts)
    }
}

fun fileTypeIcon(extension: String): ImageVector = when (extension.lowercase()) {
    "apk" -> Icons.Outlined.Android
    "zip", "rar", "7z", "tar", "gz", "tgz", "jar" -> Icons.Outlined.Archive
    "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "svg" -> Icons.Outlined.Image
    "mp4", "mkv", "avi", "mov", "webm", "3gp" -> Icons.Outlined.VideoFile
    "mp3", "m4a", "wav", "ogg", "flac", "aac" -> Icons.Outlined.AudioFile
    "pdf" -> Icons.Outlined.PictureAsPdf
    "doc", "docx", "docm", "txt", "rtf", "xls", "xlsx", "ppt", "pptx", "csv" -> Icons.Outlined.Description
    "js", "vbs", "ps1", "bat", "cmd", "sh", "py", "html", "htm", "xml", "json" -> Icons.Outlined.Code
    "exe", "dll", "msi", "scr" -> Icons.Outlined.Android
    "" -> Icons.Outlined.Folder
    else -> Icons.Outlined.InsertDriveFile
}

fun Drawable.toBitmapSafe(width: Int, height: Int): Bitmap? = try {
    if (this is BitmapDrawable && bitmap != null) {
        bitmap
    } else {
        val bmp = Bitmap.createBitmap(
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp
    }
} catch (e: Exception) {
    null
}
