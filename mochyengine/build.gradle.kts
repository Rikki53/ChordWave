import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * The Mochy engine's home in the two-engine architecture (see
 * RENCANA-DUA-MESIN.md): the Modern UI will run on Mochy's player, queues,
 * providers and Room database, kept in Mochy's own package so the boundary
 * with the ChordWave engine stays sharp. This module starts empty on
 * purpose — foundation first, code in later stages.
 */

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mochyy.mochymusic"
    compileSdk = 36
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
