import java.net.URL

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sonothamin.meowlaundry"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sonothamin.meowlaundry"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        vectorDrawables.useSupportLibrary = true
    }

    // A real release key comes from environment variables (RELEASE_KEYSTORE_FILE = path to the
    // keystore, plus _PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD) so it never lives in the
    // repo. Without it the release build is signed with the debug key: fine for installing and
    // testing the minified build, NOT something to publish.
    val releaseKeystore = System.getenv("RELEASE_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
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
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation("junit:junit:4.13.2")

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
}

// Ndot/NType (Nothing) and Samsung Sans are branded fonts that aren't ours to commit to this repo.
// Fetch them into assets at build time instead (app/src/main/assets/fonts/ is gitignored). A failed or
// offline fetch just leaves the files missing; the app then simply doesn't offer those fonts
// (see UiFont.isAvailable in ui/theme/Fonts.kt) rather than failing the build.
val fontAssetDir = File(projectDir, "src/main/assets/fonts")
val fetchedFonts = mapOf(
    "ndot.otf" to "https://raw.githubusercontent.com/xeji01/nothingfont/main/fonts/Ndot57-Regular.otf",
    "ntype.otf" to "https://raw.githubusercontent.com/xeji01/nothingfont/main/fonts/NType82-Headline.otf",
    "samsungsans.ttf" to "https://raw.githubusercontent.com/Odrha23/samsung-sans/master/SamsungSans-Regular.ttf",
)
tasks.register("fetchFonts") {
    doLast {
        fontAssetDir.mkdirs()
        fetchedFonts.forEach { (name, url) ->
            val f = File(fontAssetDir, name)
            if (f.exists() && f.length() > 0) return@forEach
            try {
                println("MeowLaundry: fetching $name…")
                URL(url).openStream().use { input -> f.outputStream().use { input.copyTo(it) } }
            } catch (e: Exception) {
                f.delete()
                println("MeowLaundry: couldn't fetch $name (${e.message}); that font just won't be offered.")
            }
        }
    }
}
tasks.named("preBuild") { dependsOn("fetchFonts") }
