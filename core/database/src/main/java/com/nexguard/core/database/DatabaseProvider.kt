package com.nexguard.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

object DatabaseProvider {
    @Volatile private var instance: AppDatabase? = null

    /**
     * v1 -> v2: oturumlar, dosya önbelleği, indeksler ve zenginleştirilmiş
     * sonuç/karantina alanları. Mevcut veriler korunur.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `scan_results` ADD COLUMN `sizeBytes` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `scan_results` ADD COLUMN `threatType` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `scan_results` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'manual'")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_results_startedAt` ON `scan_results` (`startedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_results_verdict` ON `scan_results` (`verdict`)")

            db.execSQL("ALTER TABLE `quarantine` ADD COLUMN `sizeBytes` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `quarantine` ADD COLUMN `threatType` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `quarantine` ADD COLUMN `encrypted` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quarantine_createdAt` ON `quarantine` (`createdAt`)")

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_findings_scanId` ON `findings` (`scanId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_timeline_ts` ON `timeline` (`ts`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `file_cache` (" +
                    "`targetPath` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `mtime` INTEGER NOT NULL, " +
                    "`sha256` TEXT NOT NULL, `verdict` TEXT NOT NULL, `score` INTEGER NOT NULL, " +
                    "`threatType` TEXT NOT NULL, `engineVersion` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`targetPath`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_cache_updatedAt` ON `file_cache` (`updatedAt`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `scan_sessions` (" +
                    "`id` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER, " +
                    "`mode` TEXT NOT NULL, `label` TEXT NOT NULL, `filesScanned` INTEGER NOT NULL, " +
                    "`threats` INTEGER NOT NULL, `cachedFiles` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                    "`score` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_sessions_startedAt` ON `scan_sessions` (`startedAt`)")
        }
    }

    /**
     * v2 -> v3: oturum özetine teknik hata sayısı. Eski oturumlar 0 ile korunur.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `scan_sessions` ADD COLUMN `errors` INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun get(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }
    }

    private fun build(context: Context): AppDatabase {
        val passphrase = getOrCreatePassphrase(context)
        val factory = SupportFactory(passphrase)
        return Room.databaseBuilder(context, AppDatabase::class.java, "nexguard.db")
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    private fun getOrCreatePassphrase(context: Context): ByteArray {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
            context, "ng_db", masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val existing = prefs.getString("pp", null)
        if (existing != null) return android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        // Anahtar kaybı veritabanını kalıcı olarak açılamaz yapar; ilk yazımı
        // senkron tamamla (yalnızca ilk kurulumda bir kez çalışır).
        prefs.edit()
            .putString("pp", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
            .commit()
        return bytes
    }
}
