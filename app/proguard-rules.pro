# NexGuard R8 kuralları
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Room varlıkları ve DAO'lar yansıma/üretilen kod ile kullanılır.
-keep class com.nexguard.core.database.** { *; }

# SQLCipher JNI katmanı korunmalı.
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.** { *; }
-dontwarn net.sqlcipher.**
-dontwarn net.zetetic.**

# Hilt/Room üretilen sınıfları.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.lifecycle.ViewModel

-dontwarn org.jetbrains.annotations.**

# Tink/EncryptedSharedPreferences yalnızca derleme zamanı anotasyonlarına
# başvurur; bu sınıflar APK'ya dahil edilmez.
-dontwarn com.google.errorprone.annotations.**
