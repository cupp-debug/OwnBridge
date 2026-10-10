import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.ownbridge"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.ownbridge"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-demo"
            buildConfigField("boolean", "COMPOSITE_DEPLOYMENT_ENABLED", "true")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            buildConfigField("boolean", "COMPOSITE_DEPLOYMENT_ENABLED", "true")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // The pinned bridge loads .so files by absolute nativeLibraryDir path.
        jniLibs.useLegacyPackaging = true
    }

    sourceSets {
        getByName("main").assets.directories.add(rootProject.file("third_party_licenses").absolutePath)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Pinned GPL-3.0 Android bridge, including Mihomo v1.19.31. Never accept a changed binary silently.
val mihomoAarName = "libmihomo-android-v0.3.5.aar"
val mihomoAarSha256 = "41ddcce67de37f406377798c5adbc7ff925e6cd9cadbef3f187da3a03e564176"
val mihomoAar = layout.buildDirectory.file("verified-dependencies/$mihomoAarName")
val fetchMihomoAar = tasks.register("fetchMihomoAar") {
    outputs.file(mihomoAar)
    outputs.upToDateWhen { false } // Recheck the cached artifact on every build.
    doLast {
        val target = mihomoAar.get().asFile.toPath()
        Files.createDirectories(target.parent)
        if (!Files.exists(target)) {
            val partial = target.resolveSibling("$mihomoAarName.partial")
            try {
                URI("https://github.com/oviron/libmihomo-android/releases/download/v0.3.5/$mihomoAarName")
                    .toURL().openStream().use { input -> Files.newOutputStream(partial).use(input::copyTo) }
                check(sha256(partial) == mihomoAarSha256) { "Mihomo AAR SHA-256 mismatch" }
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(partial)
            }
        }
        check(sha256(target) == mihomoAarSha256) { "Cached Mihomo AAR SHA-256 mismatch" }
    }
}

fun sha256(path: java.nio.file.Path): String = Files.newInputStream(path).use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(8192)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    HexFormat.of().formatHex(digest.digest())
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(files(mihomoAar).builtBy(fetchMihomoAar))
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.github.mwiede:jsch:2.28.7")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
