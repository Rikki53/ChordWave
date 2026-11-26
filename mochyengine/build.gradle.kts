import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * The Mochy engine's home in the two-engine architecture (see
 * RENCANA-DUA-MESIN.md): the Modern UI will run on Mochy's player, queues,
 * providers and Room database, kept in Mochy's own packages so the boundary
 * with the ChordWave engine stays sharp.
 *
 * Stage: Mochy's providers (:providers:* in the Mochy repo) and the module
 * API (:modules:api) are ported here as one merged source tree — compile
 * only for now. Nothing in :app calls into this code yet; the player,
 * database and UI wiring arrive in later stages.
 *
 * Versions deliberately follow ChordWave's own pins, not Mochy's catalog:
 * Ktor 3.5.2 / kotlinx-serialization-json 1.11.0 are what :app and :shared
 * already resolve, so this module never upgrades the app's network stack
 * behind its back. NewPipeExtractor is compileOnly for the same reason:
 * :app supplies its own stripped v0.26.3 jar at runtime (see
 * newPipeExtractorStripped in app/build.gradle.kts), and declaring the
 * module here as well would duplicate every extractor class at dexing.
 * okhttp-dnsoverhttps is held at 4.12.0 to match the OkHttp Ktor 3.5.2
 * brings; Mochy's 5.3.2 would force OkHttp 5 on the whole app.
 */

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.mochyy.mochymusic"
    compileSdk = 37
    // Without a minSdk the library's manifest has no <uses-sdk>, which the
    // manifest merger reads as targetSdk < 4 and answers by granting every app
    // that depends on it READ_PHONE_STATE — a permission nothing here uses.
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // The providers' network stack: every Mochy provider is a Ktor client
    // with kotlinx.serialization models.
    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("io.ktor:ktor-client-okhttp:3.5.2")
    implementation("io.ktor:ktor-client-cio:3.5.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-client-encoding:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Innertube decompresses YouTube's brotli responses itself.
    implementation("org.brotli:dec:0.1.2")

    // Logging in the ported providers (genius, musixmatch, paxsenix, wikipedia).
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Musixmatch resolves its search host over DNS-over-HTTPS.
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    // Innertube's NewPipe bridge (signature deciphering). Compile-only:
    // :app's stripped jar is the runtime copy — see the header note.
    compileOnly("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
}
