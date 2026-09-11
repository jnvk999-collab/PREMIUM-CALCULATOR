plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// VERSION holds the human-readable number. CI passes a monotonic build number so every
// push yields a strictly newer versionCode and installed phones can self-update.
val baseVersion = rootProject.file("VERSION").readText().trim()
val buildNumber = (System.getenv("VERSION_CODE") ?: "1").toInt()
// Where the app looks for updates. Change when the app moves to its own repository.
val updateRepoOwner = System.getenv("UPDATE_REPO_OWNER") ?: "jnvk999-collab"
val updateRepoName = System.getenv("UPDATE_REPO_NAME") ?: "PREMIUM-CALCULATOR"

// Release signing comes from the environment (GitHub Secrets in CI). Nothing is stored here.
val signingKeystore = System.getenv("KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
val signingPassword = System.getenv("KEYSTORE_PASSWORD")

android {
    namespace = "com.financebrain"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.financebrain"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "$baseVersion.$buildNumber"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "UPDATE_REPO_OWNER", "\"$updateRepoOwner\"")
        buildConfigField("String", "UPDATE_REPO_NAME", "\"$updateRepoName\"")
    }

    signingConfigs {
        if (signingKeystore != null && signingPassword != null) {
            create("release") {
                storeFile = signingKeystore
                storePassword = signingPassword
                keyAlias = System.getenv("KEYSTORE_ALIAS") ?: "financebrain"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
