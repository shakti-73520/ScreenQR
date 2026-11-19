import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

// Every configuration of this project is locked (gradle.lockfile beside this file) in strict mode: a version the lock
// state does not carry, or a resolved configuration with no lock state, fails the build. The root build file carries the
// rule; the lock files are rewritten by hand with --write-locks, never in CI (SECURITY-MODEL.md S1, A9, 2026-09-10).
dependencyLocking {
    lockAllConfigurations()
    lockMode.set(org.gradle.api.artifacts.dsl.LockMode.STRICT)
}

android {
    namespace = "app.scanner.baselineprofile"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The app has a store dimension (play, foss) and this module none: the profile is collected on the play build. The foss
        // build carries the same profile, since the two differ only in the Billing implementation (tools/release/flavor_diff.py).
        missingDimensionStrategy("store", "play")
    }

    targetProjectPath = ":app"

    // Gradle provisions this device itself; the profile is collected on it, not on a hand-made AVD.
    testOptions.managedDevices.allDevices {
        create<ManagedVirtualDevice>("pixel6Api35") {
            device = "Pixel 6"
            apiLevel = 35
            systemImageSource = "aosp"
        }
    }
}

baselineProfile {
    // the connected door (perf/lowend, 2026-10-18): with -Pverdetto.profileOnConnected the profile is collected on the emulator
    // already running (an API 33 or later image, one at a time, ANDROID_SERIAL naming it so no attached phone is touched),
    // for a box where the managed device will not start (emulator 37.1.11 rejects the plugin's -gpu auto-no-window and the
    // start timed out, 2026-10-18); without the word, Gradle provisions pixel6Api35 itself as before
    if (providers.gradleProperty("verdetto.profileOnConnected").isPresent) useConnectedDevices = true
    else { managedDevices += "pixel6Api35"; useConnectedDevices = false }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
