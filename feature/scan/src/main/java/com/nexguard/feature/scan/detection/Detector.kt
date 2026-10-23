package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding

data class ScanTarget(
    val path: String,
    val file: java.io.File,
    val sha256: String,
    val size: Long,
    val mtime: Long,
    val magic: ByteArray = byteArrayOf(),
    val head: ByteArray = byteArrayOf()
)

interface Detector {
    val name: String
    /** Higher = runs earlier. */
    val priority: Int get() = 0
    suspend fun inspect(target: ScanTarget): List<Finding>
}