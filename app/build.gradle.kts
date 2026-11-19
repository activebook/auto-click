import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Optional release signing. Drop a keystore.properties in the project root to sign release builds
// with your own key; otherwise the debug key is used so that the APK stays installable.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.activebook.autoclick.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.activebook.autoclick.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.5.0"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
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

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

/**
 * Publishable APK names.
 *
 * The file Gradle produces inside `app/build/outputs/apk/<variant>/` is `app-<variant>.apk`: the AGP
 * convention `<module>-<variant>.apk`. Renaming it in place would need `VariantOutputImpl` (an
 * internal AGP class), and every tool that guesses the canonical path — Android Studio's Run,
 * `installDebug`, CI scripts — would follow the old name. So the build output keeps its canonical
 * name and each assemble run drops a copy named the way a human wants to see it in `dist/`:
 *
 *   dist/AutoClick-1.0.0-debug.apk    (debug, not minified)
 *   dist/AutoClick-1.0.0.apk          (release, R8, ready to sideload)
 */
val distDir = rootProject.layout.projectDirectory.dir("dist")
val versionNameForFiles = android.defaultConfig.versionName ?: "0"

fun registerDistCopy(taskName: String, variant: String, artifactName: String) =
    tasks.register<Copy>(taskName) {
        group = "distribution"
        description = "Copies the $variant APK into dist/ as $artifactName."
        from(layout.buildDirectory.file("outputs/apk/$variant/app-$variant.apk"))
        into(distDir)
        rename { artifactName }
    }

registerDistCopy("distDebugApk", "debug", "AutoClick-$versionNameForFiles-debug.apk")
registerDistCopy("distReleaseApk", "release", "AutoClick-$versionNameForFiles.apk")

// `assembleDebug` / `assembleRelease` now also refresh the copy, so one command gives both the
// canonical artifact and the artifact to hand over. `configureEach` instead of `named`: the AGP
// registers the assemble tasks after this script is evaluated.
tasks.configureEach {
    when (name) {
        "assembleDebug" -> finalizedBy("distDebugApk")
        "assembleRelease" -> finalizedBy("distReleaseApk")
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
