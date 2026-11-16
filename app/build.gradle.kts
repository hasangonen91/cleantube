plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.cleantube.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.cleantube.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_PATH") ?: (project.findProperty("KEYSTORE_PATH") as String?)
            val ksp = System.getenv("KEYSTORE_PASSWORD") ?: (project.findProperty("KEYSTORE_PASSWORD") as String?)
            val ka = System.getenv("KEY_ALIAS") ?: (project.findProperty("KEY_ALIAS") as String?)
            val kp = System.getenv("KEY_PASSWORD") ?: (project.findProperty("KEY_PASSWORD") as String?)
            if (!ks.isNullOrBlank() && !ksp.isNullOrBlank() && !ka.isNullOrBlank() && !kp.isNullOrBlank()) {
                storeFile = file(ks)
                storePassword = ksp
                keyAlias = ka
                keyPassword = kp
            }
        }
    }
    buildTypes {
        // Debug anahtariyla imzalanir ama debuggable KAPALI (adb ile veri okunamasin).
        debug {
            isDebuggable = false
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val sc = signingConfigs.getByName("release")
            if (sc.storeFile != null) signingConfig = sc
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST") } }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-cast:1.3.1")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
