plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.retrofilm.core.export"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
