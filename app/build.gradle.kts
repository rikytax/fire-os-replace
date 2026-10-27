plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.firehome"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.firehome"
        minSdk = 22 // Fire OS 5 (Fire TV Stick 2a gen) in su
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
    }

    // Chiave di firma FISSA, inclusa nel progetto. Prima si usava la chiave di debug, che GitHub genera nuova a ogni
    // build: due build avevano firme diverse e Android rifiutava gli aggiornamenti. Non è un segreto (come la chiave
    // di debug di Android): serve solo a far riconoscere come "la stessa app" le versioni successive, per il sideload.
    signingConfigs {
        create("firehome") {
            storeFile = file("firehome.keystore")
            storePassword = "firehome"
            keyAlias = "firehome"
            keyPassword = "firehome"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("firehome")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("firehome")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")

    val media3 = "1.6.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")
}
