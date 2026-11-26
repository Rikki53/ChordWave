import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * The Mochy engine's home in the two-engine architecture (see
 * RENCANA-DUA-MESIN.md): the Modern UI will run on Mochy's player, queues,
 * providers and Room database, kept in Mochy's own packages so the boundary
 * with the ChordWave engine stays sharp.
 *
 * Stage: Mochy's providers (:providers:* in the Mochy repo) and the module
 * API (:modules:api) are ported here as one merged source tree, and now the
 * Room database (MusicDatabase, the library core of Mochy's entities) lives
 * here too — the shared data source both engines read. The player and the
 * rest of the engine arrive in later stages.
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
 *
 * Room is the one exception: ChordWave has no Room pin to follow (its data
 * lived in SharedPreferences + files), so Room follows Mochy's own catalog
 * pin, 2.8.4. The Compose runtime (for @Immutable on the entities) follows
 * :app's BOM, and coroutines follows the 1.9.0 line :app already resolves.
 */

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
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

    // The Room database (MusicDatabase): the shared library store. Version
    // follows Mochy's catalog — ChordWave has no Room pin of its own.
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // @Immutable on the ported entities. Version from :app's Compose BOM.
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.runtime:runtime")

    // Entities fire coroutines for their YouTube sync helpers; Room's Flow
    // support needs it too. 1.9.0 is the line :app resolves.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
