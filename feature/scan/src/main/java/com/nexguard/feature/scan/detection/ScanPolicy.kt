package com.nexguard.feature.scan.detection

/**
 * Dosya türüne göre tarama bütçesi. Amaç: tüm depolamayı tararken büyük
 * medya dosyalarını gereksiz yere baştan sona okumamak, ama çalıştırılabilir
 * ve belge türlerini derinlemesine incelemek.
 */
object ScanPolicy {
    /** Dedektör kuralları değiştiğinde artırın; dosya önbelleği geçersiz olur. */
    const val ENGINE_VERSION = 7

    val mediaExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "tif", "tiff",
        "mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "wmv", "flv", "ts", "m2ts",
        "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "amr", "mid", "midi"
    )

    val documentExtensions = setOf(
        "pdf", "doc", "docx", "docm", "xls", "xlsx", "xlsm", "ppt", "pptx", "pptm",
        "rtf", "html", "htm", "xhtml", "svg", "txt", "csv", "xml", "json"
    )

    val officeZipExtensions = setOf("docx", "docm", "xlsx", "xlsm", "pptx", "pptm")

    val executableExtensions = setOf("apk", "exe", "dll", "sys", "scr", "ocx", "cpl", "drv", "elf", "so", "bin")

    val archiveExtensions = setOf("zip", "jar", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")

    val scriptExtensions = setOf("js", "jse", "vbs", "vbe", "wsf", "hta", "ps1", "bat", "cmd", "sh", "py", "php", "pl")

    /**
     * Geliştirici betikleri. Bunlar tehdit olarak değil, davranışları açıklanan
     * uyarılar olarak raporlanır; kesin kötü amaçlı imzalar (EICAR, kötü hash)
     * yine tehdit sayılır.
     */
    val pythonExtensions = setOf("py", "pyw", "ipynb")

    /** Sistem uygulamalarının kurulu olduğu salt okunur bölümler. */
    private val trustedSystemRoots = listOf(
        "/system/", "/system_ext/", "/product/", "/vendor/", "/apex/", "/odm/"
    )

    /**
     * Sistem bölümündeki APK'lar güvenilir kabul edilir: değiştirilemezler,
     * platform imzasıyla gelirler ve güçlü izinleri işlevlerinin parçasıdır.
     */
    fun isTrustedSystemApk(path: String): Boolean {
        val p = path.replace('\\', '/')
        if (!p.endsWith(".apk", ignoreCase = true)) return false
        return trustedSystemRoots.any { p.startsWith(it) }
    }

    fun sampleLimit(extension: String, size: Long): Int = when {
        extension in mediaExtensions -> 64 * 1024
        extension in documentExtensions || extension in executableExtensions ||
            extension in archiveExtensions || extension in scriptExtensions -> 4 * 1024 * 1024
        size <= 1024 * 1024 -> size.toInt().coerceAtLeast(64)
        else -> 1024 * 1024
    }

    fun hashLimit(extension: String, size: Long): Long = when {
        extension in mediaExtensions && size > 4L * 1024 * 1024 -> 4L * 1024 * 1024
        extension in executableExtensions || extension in archiveExtensions -> 64L * 1024 * 1024
        extension in documentExtensions -> 32L * 1024 * 1024
        else -> 16L * 1024 * 1024
    }

    private val skipDirNames = setOf(
        ".thumbnails", ".cache", "cache", "lost.dir", "\$recycle.bin", ".trash", ".trashes",
        "node_modules", ".git", ".gradle", ".kotlin", ".idea", "build"
    )

    fun shouldSkipDirectory(path: String, name: String): Boolean {
        val lower = name.lowercase()
        // Gizli dizinler (.gs_fs0, .thumbnails, .trash…) uygulama önbelleği/thumbnail
        // içerir. Buradaki ".jpg" dosyaları gerçek görsel olmadığı için taranmaz.
        if (lower.startsWith(".")) return true
        if (lower in skipDirNames) return true
        val p = path.replace('\\', '/')
        return p.contains("/Android/data") || p.contains("/Android/obb") ||
            p.endsWith("/Android/data") || p.endsWith("/Android/obb")
    }

    fun shouldSkipFile(name: String): Boolean {
        val lower = name.lowercase()
        if (lower.startsWith(".")) return true
        return lower == "thumbs.db" || lower == ".ds_store" || lower == "desktop.ini"
    }

    fun isMedia(extension: String): Boolean = extension in mediaExtensions
}
