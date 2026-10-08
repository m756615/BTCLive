plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hermeslux.btclive"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hermeslux.btclive"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.6.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Live BTC price
    implementation(libs.okhttp)

    testImplementation(libs.junit)
}