plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.salatitime.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.salatitime.tv"
        minSdk = 26          // Android TV 8.0 et plus
        targetSdk = 35
        versionCode = 4
        versionName = "1.3"
    }

    signingConfigs {
        // Clé fixe : les nouvelles versions s'installent par-dessus l'ancienne sans désinstaller.
        // Projet personnel : pour une publication sur le Play Store, utilisez une clé privée hors du dépôt.
        create("salati") {
            storeFile = file("salati-release.jks")
            storePassword = "salatitime"
            keyAlias = "salati"
            keyPassword = "salatitime"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("salati")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Requis par NewPipeExtractor
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Extraction de l'audio des liens YouTube.
    // YouTube change souvent : mettez cette version à jour vers la dernière release
    // (https://github.com/TeamNewPipe/NewPipeExtractor/releases) si l'import YouTube échoue.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.6")
}
