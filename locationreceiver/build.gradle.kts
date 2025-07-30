plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.locationreceiver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.locationreceiver"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            buildConfigField("String", "API_EMAIL", "\"${System.getenv("API_EMAIL") ?: project.properties["API_EMAIL"]}\"")
            buildConfigField("String", "API_PASSWORD", "\"${System.getenv("API_PASSWORD") ?: project.properties["API_PASSWORD"]}\"")
            buildConfigField("String", "API_PLATE", "\"${System.getenv("API_PLATE") ?: project.properties["API_PLATE"]}\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            buildConfigField("String", "API_EMAIL", "\"${System.getenv("API_EMAIL") ?: project.properties["API_EMAIL"]}\"")
            buildConfigField("String", "API_PASSWORD", "\"${System.getenv("API_PASSWORD") ?: project.properties["API_PASSWORD"]}\"")
            buildConfigField("String", "API_PLATE", "\"${System.getenv("API_PLATE") ?: project.properties["API_PLATE"]}\"")
        }
    }
    buildFeatures {
        aidl = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("io.ktor:ktor-client-core:2.3.9") // Or the latest version
    implementation("io.ktor:ktor-client-cio:2.3.9")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.9")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}