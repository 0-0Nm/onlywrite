plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // 与 app 共用同一份源码：namespace 必须一致，否则 R 类对不上
    namespace = "com.greed.onlywrite"
    // 只有 applicationId 不同 —— 这样两者可以共存
    compileSdk = 34

    signingConfigs {
        create("fixed") {
            storeFile = file("../keystore/ow.jks")
            storePassword = "ow123456"
            keyAlias = "ow"
            keyPassword = "ow123456"
        }
    }

    defaultConfig {
        applicationId = "com.greed.onlywrite.delta"
        minSdk = 24
        targetSdk = 34
        versionCode = 3 + ((System.getenv("GITHUB_RUN_NUMBER") ?: "0").toIntOrNull() ?: 0)
        versionName = "0.1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        buildConfigField("boolean", "DELTA", "true")
    }

    buildFeatures { buildConfig = true }

    sourceSets {
        getByName("main") {
            java.srcDirs("../app/src/main/java")
            res.srcDirs("src/main/res", "../app/src/main/res")
            manifest.srcFile("src/main/AndroidManifest.xml")
            assets.srcDirs("../app/src/main/assets")
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("fixed") }
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("fixed") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
