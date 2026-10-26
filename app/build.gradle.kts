import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

/**
 * İmza materyali kaynak kodda tutulmaz. Değerler sırayla `local.properties`
 * (git dışı) ve ortam değişkenlerinden okunur:
 *   NEXGUARD_STORE_FILE, NEXGUARD_STORE_PASSWORD, NEXGUARD_KEY_ALIAS, NEXGUARD_KEY_PASSWORD
 */
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String): String? =
    (localProps.getProperty(key) ?: System.getenv(key))?.trim()?.takeIf { it.isNotEmpty() }

val releaseStoreFile = signingValue("NEXGUARD_STORE_FILE")
val releaseStorePassword = signingValue("NEXGUARD_STORE_PASSWORD")
val releaseKeyAlias = signingValue("NEXGUARD_KEY_ALIAS")
val releaseKeyPassword = signingValue("NEXGUARD_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { it != null }

val releaseTaskRequested = gradle.startParameter.taskNames.any {
    val t = it.lowercase()
    t.contains("assemblerelease") || t.contains("bundlerelease") ||
        t.contains("installrelease") || t.contains("packagerelease")
}
if (releaseTaskRequested && !hasReleaseSigning) {
    throw GradleException(
        "Release imzası için NEXGUARD_STORE_FILE, NEXGUARD_STORE_PASSWORD, " +
            "NEXGUARD_KEY_ALIAS ve NEXGUARD_KEY_PASSWORD tanımlanmalı " +
            "(local.properties veya ortam değişkeni). Debug derlemesi bu ayarlardan etkilenmez."
    )
}

android {
    namespace = "com.nexguard.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nexguard.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storeType = "pkcs12"
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // Debug, kişisel release anahtarını asla kullanmaz; AGP'nin
            // otomatik oluşturduğu debug keystore'u ile imzalanır.
            isMinifyEnabled = false
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE}"
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:eventbus"))
    implementation(project(":core:security"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:home"))
    implementation(project(":feature:scan"))
    implementation(project(":feature:monitor"))
    implementation(project(":feature:quarantine"))
    implementation(project(":feature:alerts"))
    implementation(project(":feature:settings"))
    implementation(project(":engine:signatures"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.foundation:foundation:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}