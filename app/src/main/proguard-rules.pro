# NexGuard — R8 rules
-dontwarn androidx.security.**
-dontwarn net.sqlcipher.**
-keep class com.nexguard.** { *; }
-keep class com.nexguard.core.** { *; }
-keep class com.nexguard.feature.** { *; }
-keep class com.nexguard.engine.** { *; }
-keepnames class * implements android.os.Parcelable { *; }
-keepclassmembers class ** {
    @dagger.hilt.android.AndroidEntryPoint *;
}