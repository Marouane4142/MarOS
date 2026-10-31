import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val prepareLocalDebugKey = tasks.register("prepareLocalDebugKey") {
    val key = layout.projectDirectory.file(".local/compose-debug.keystore")
    outputs.file(key)
    doLast {
        key.asFile.parentFile.mkdirs()
        if (!key.asFile.exists()) {
            val executable = File(System.getProperty("java.home"), "bin/keytool" + if (System.getProperty("os.name").startsWith("Windows")) ".exe" else "")
            val result = ProcessBuilder(executable.path, "-genkeypair", "-keystore", key.asFile.path,
                "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "10000", "-dname", "CN=MarOS local development", "-noprompt")
                .inheritIO().start().waitFor()
            check(result == 0) { "Unable to generate local debug key" }
        }
    }
}
tasks.matching { it.name == "validateSigningDebug" || it.name == "validateSigningRelease" && providers.gradleProperty("maros.localRelease").orNull == "true" }
    .configureEach { dependsOn(prepareLocalDebugKey) }

android {
    namespace = "io.github.marouane4142.maros"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "io.github.marouane4142.maros"
        minSdk = 30
        targetSdk = 36
        versionCode = 50000
        versionName = "5.0.0-beta.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.directories.add("src")
            kotlin.directories.add("src")
            res.directories.add("res")
            assets.directories.add("assets")
        }
        getByName("test") { java.directories.add("test"); kotlin.directories.add("test") }
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file(".local/compose-debug.keystore")
        }
        create("release") {
            providers.environmentVariable("MAROS_KEYSTORE").orNull?.let {
                storeFile = file(it)
                storePassword = providers.environmentVariable("MAROS_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("MAROS_KEY_ALIAS").orElse("maros").get()
                keyPassword = providers.environmentVariable("MAROS_KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug").takeIf {
                    providers.gradleProperty("maros.localRelease").orNull == "true"
                }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    lint { disable += "MissingTranslation" }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui:1.12.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.12.1")
    implementation("androidx.compose.foundation:foundation:1.12.1")
    implementation("androidx.compose.animation:animation:1.12.1")
    implementation("androidx.tv:tv-material:1.1.0")
    // Only the expressive motion specifications are used; TV owns the MaterialTheme.
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling:1.12.1")
}
