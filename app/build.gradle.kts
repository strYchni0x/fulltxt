import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val keystoreProps = Properties().also { props ->
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
}

android {
    namespace = "me.fulltxt.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "me.fulltxt.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "1.2.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Two editions that differ in whether Google Drive is offered.
    // - playstore: public build WITHOUT Google Drive. drive.readonly is a restricted scope
    //   that would require OAuth verification + a yearly CASA assessment, and drive.file only
    //   exposes individually picked files (no folder indexing), so Drive is left out entirely.
    // - dev: private build WITH Google Drive (drive.readonly, full indexing), used under the
    //   OAuth consent screen's "Testing" mode with manually added test users (no verification).
    flavorDimensions += "edition"
    productFlavors {
        create("playstore") {
            dimension = "edition"
            // applicationId stays "me.fulltxt.app"
            buildConfigField("boolean", "DRIVE_ENABLED", "false")
        }
        create("dev") {
            dimension = "edition"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("boolean", "DRIVE_ENABLED", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true   // needed for BuildConfig.DEBUG checks at runtime
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/ASL2.0"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.navigation.compose)

    // Room + FTS5
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.workmanager.ktx)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    // Network
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)

    // Security (EncryptedSharedPreferences)
    implementation(libs.security.crypto)

    // Google Drive
    implementation(libs.google.api.drive)
    implementation(libs.google.api.client.android)
    implementation(libs.play.services.auth)

    // Microsoft OneDrive (MSAL)
    implementation(libs.msal)

    // Document parsing
    implementation(libs.pdfbox.android)
    implementation(libs.poi) {
        exclude(group = "org.apache.xmlbeans")
        exclude(group = "com.github.virtuald")
    }
    implementation(libs.poi.ooxml) {
        exclude(group = "org.apache.xmlbeans")
        exclude(group = "com.github.virtuald")
    }

    // OCR for scanned PDFs (on-device, bundled model — fully offline)
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
