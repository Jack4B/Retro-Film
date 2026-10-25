plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val versionNameValue: String = rootProject.file("VERSION").readText().trim()
val versionParts = versionNameValue.split(".").map { it.toInt() }
val runNumber: Int = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0) % 1000
val versionCodeValue: Int =
    (versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]) * 1000 + runNumber

val keystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")?.takeIf { file(it).exists() }

android {
    namespace = "app.retrofilm"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.retrofilm"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeValue
        versionName = versionNameValue
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

tasks.register("printVersionCode") {
    doLast { println("versionCode=$versionCodeValue") }
}

dependencies {
    implementation(project(":core:engine"))
    implementation(project(":core:frame"))
    implementation(project(":core:batch"))
    implementation(project(":core:data"))
    implementation(project(":core:export"))
    implementation(project(":feature:start"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:batch"))
    implementation(project(":feature:luts"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    testImplementation(libs.junit)
}
