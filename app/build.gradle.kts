plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.scanner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.verdettoqr.scanner.wywkmb"
        minSdk = 26
        targetSdk = 36
        versionCode = 10204
        versionName = "1.2.4"
        buildConfigField("String", "CORE_COMMIT", "\"prismqr\"")
        vectorDrawables { useSupportLibrary = true }
    }

    flavorDimensions += "store"
    productFlavors {
        create("foss") {
            dimension = "store"
            manifestPlaceholders["store"] = "foss"
        }
    }

    buildTypes {
        debug {
            isPseudoLocalesEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            vcsInfo { include = false }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += "ttf"
        noCompress += "list.bin"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.androidx.exifinterface)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.print)
    implementation(libs.zxing.core)
    implementation(libs.eddsa)
    implementation(libs.libphonenumber)
    implementation(libs.libphonenumber.geocoder)
    implementation(libs.libphonenumber.carrier)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
