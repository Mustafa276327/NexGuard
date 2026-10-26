package com.nexguard.feature.scan.detection

/**
 * Dosyanın gerçek içerik türü. Uzantıya güvenilmez; ilk baytlardan (magic)
 * tespit edilir. Yanlış pozitifleri azaltmak için kullanılır: gerçek bir JPEG,
 * uzantısı ne olursa olsun çalıştırılabilir sayılmaz.
 */
enum class ContentType {
    JPEG, PNG, GIF, WEBP, BMP, TIFF, HEIC, MP4, AUDIO, PDF, ZIP, OLE, RTF,
    PE, ELF, RAR, SEVEN_ZIP, TEXT, UNKNOWN;

    val isImage: Boolean
        get() = this == JPEG || this == PNG || this == GIF || this == WEBP ||
            this == BMP || this == TIFF || this == HEIC

    val isMedia: Boolean
        get() = isImage || this == MP4 || this == AUDIO

    val isExecutable: Boolean
        get() = this == PE || this == ELF

    val isArchive: Boolean
        get() = this == ZIP || this == RAR || this == SEVEN_ZIP
}

object FileTypeSniffer {

    /** Uzantının vaat ettiği içerik türü; bilinmeyen uzantılarda `null`. */
    fun expectedForExtension(extension: String): ContentType? = when (extension.lowercase()) {
        "jpg", "jpeg", "jpe" -> ContentType.JPEG
        "png" -> ContentType.PNG
        "gif" -> ContentType.GIF
        "webp" -> ContentType.WEBP
        "bmp" -> ContentType.BMP
        "tif", "tiff" -> ContentType.TIFF
        "heic", "heif", "avif" -> ContentType.HEIC
        "mp4", "m4v", "mov", "3gp", "3g2" -> ContentType.MP4
        "pdf" -> ContentType.PDF
        "zip", "apk", "jar", "docx", "xlsx", "pptx", "docm", "xlsm", "pptm", "odt" -> ContentType.ZIP
        "rar" -> ContentType.RAR
        "7z" -> ContentType.SEVEN_ZIP
        "exe", "dll", "sys", "scr", "ocx", "cpl", "drv" -> ContentType.PE
        "elf", "so" -> ContentType.ELF
        "rtf" -> ContentType.RTF
        "doc", "xls", "ppt", "msi" -> ContentType.OLE
        else -> null
    }

    /**
     * Baytların gerçek türünü döndürür. En az 16 bayt önerilir; daha kısa
     * örneklerde yalnızca kesin imzalar tanınır.
     */
    fun sniff(bytes: ByteArray): ContentType {
        if (bytes.isEmpty()) return ContentType.UNKNOWN
        val n = bytes.size

        fun at(i: Int): Int = bytes[i].toInt() and 0xFF
        fun ascii(off: Int, text: String): Boolean {
            if (off < 0 || off + text.length > n) return false
            for (i in text.indices) {
                if (bytes[off + i].toInt() and 0xFF != text[i].code) return false
            }
            return true
        }

        if (n >= 3 && at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF) return ContentType.JPEG
        if (n >= 8 && at(0) == 0x89 && ascii(1, "PNG")) return ContentType.PNG
        if (n >= 6 && (ascii(0, "GIF87a") || ascii(0, "GIF89a"))) return ContentType.GIF
        if (n >= 12 && ascii(0, "RIFF") && ascii(8, "WEBP")) return ContentType.WEBP
        if (n >= 2 && at(0) == 0x42 && at(1) == 0x4D) return ContentType.BMP
        if (n >= 4 && ((at(0) == 0x49 && at(1) == 0x49 && at(2) == 0x2A && at(3) == 0x00) ||
                (at(0) == 0x4D && at(1) == 0x4D && at(2) == 0x00 && at(3) == 0x2A))
        ) return ContentType.TIFF
        if (n >= 12 && ascii(4, "ftyp")) {
            val brand = String(bytes, 8, 4, Charsets.ISO_8859_1).lowercase()
            return when (brand) {
                "heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1", "avif", "avis" -> ContentType.HEIC
                else -> ContentType.MP4
            }
        }
        if (n >= 4 && at(0) == 0x25 && ascii(1, "PDF")) return ContentType.PDF
        if (n >= 4 && at(0) == 0x50 && at(1) == 0x4B &&
            (at(2) == 0x03 || at(2) == 0x05 || at(2) == 0x07)
        ) return ContentType.ZIP
        if (n >= 4 && at(0) == 0xD0 && at(1) == 0xCF && at(2) == 0x11 && at(3) == 0xE0) return ContentType.OLE
        if (n >= 5 && ascii(0, "{\\rtf")) return ContentType.RTF
        if (n >= 2 && at(0) == 0x4D && at(1) == 0x5A) return ContentType.PE
        if (n >= 4 && at(0) == 0x7F && ascii(1, "ELF")) return ContentType.ELF
        if (n >= 4 && ascii(0, "Rar!")) return ContentType.RAR
        if (n >= 6 && at(0) == 0x37 && at(1) == 0x7A && at(2) == 0xBC && at(3) == 0xAF &&
            at(4) == 0x27 && at(5) == 0x1C
        ) return ContentType.SEVEN_ZIP
        if (n >= 4 && ascii(0, "fLaC")) return ContentType.AUDIO
        if (n >= 3 && ascii(0, "ID3")) return ContentType.AUDIO
        if (n >= 4 && at(0) == 0x4F && ascii(1, "ggS")) return ContentType.AUDIO

        return if (looksLikeText(bytes)) ContentType.TEXT else ContentType.UNKNOWN
    }

    /** Örnek çoğunlukla yazdırılabilir metinse true. İkili dosyalarda false. */
    fun looksLikeText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val sample = if (bytes.size > 4096) bytes.copyOf(4096) else bytes
        var printable = 0
        for (b in sample) {
            val c = b.toInt() and 0xFF
            when {
                c == 0 -> return false
                c == 9 || c == 10 || c == 13 -> printable++
                c in 32..126 -> printable++
                c >= 0xC0 -> printable++ // UTF-8 çok baytlı dizilerin başlangıcı
            }
        }
        return printable.toDouble() / sample.size >= 0.90
    }
}
