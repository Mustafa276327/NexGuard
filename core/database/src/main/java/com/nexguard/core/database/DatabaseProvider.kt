package com.nexguard.core.database

import android.content.Context
import androidx.room.Room
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

object DatabaseProvider {
    @Volatile private var instance: AppDatabase? = null

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
            .fallbackToDestructiveMigration()
            .build()
    }

    private fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences("ng_db", Context.MODE_PRIVATE)
        val existing = prefs.getString("pp", null)
        if (existing != null) return android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString("pp", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)).apply()
        // NOTE: in production, wrap this passphrase with Keystore; MVP keeps it in encrypted prefs (TODO).
        return bytes
    }
}