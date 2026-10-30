plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.greed.onlywrite"

    signingConfigs {
        create("fixed") {
            storeFile = file("../keystore/ow.jks")
            storePassword = "ow123456"
            keyAlias = "ow"
            keyPassword = "ow123456"
        }
    }
    compileSdk = 34

    defaultConfig {
        applicationId = "com.greed.onlywrite"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
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
    testImplementation("junit:junit:4.13.2")
}
