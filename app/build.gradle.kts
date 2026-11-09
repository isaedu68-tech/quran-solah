plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Số bản dựng do GitHub Actions truyền vào để bản mới cài đè lên bản cũ
val buildNo = (System.getenv("BUILD_NO") ?: "1").toInt()

android {
    namespace = "com.isaedu.quran"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.isaedu.quran"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNo
        versionName = "0.1.$buildNo"
    }
    signingConfigs {
        create("personal") {
            storeFile = rootProject.file("keystore/quran.jks")
            storePassword = "quransolah"
            keyAlias = "quran"
            keyPassword = "quransolah"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("personal") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    androidResources { noCompress += listOf("txt", "tsv") }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
}
