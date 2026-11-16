plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Paste the licensing key from Play Console -> Monetization setup, or pass
// -PbillingPublicKey=... / RETROBIT_BILLING_KEY. Empty means "skip local signature check".
val billingPublicKey = ((project.findProperty("billingPublicKey") as? String)
    ?: System.getenv("RETROBIT_BILLING_KEY").orEmpty())
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
// CD-key entry is a sideload/testing path. Play release builds hide it unless explicitly
// enabled with -PallowCdKey=true for an internal test.
val cdKeyEntryOverride = (project.findProperty("allowCdKey") as? String)?.toBooleanStrictOrNull()
val releaseStoreFile = System.getenv("RETROBIT_STORE_FILE")
val releaseStorePassword = System.getenv("RETROBIT_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("RETROBIT_KEY_ALIAS")
val releaseKeyPassword = System.getenv("RETROBIT_KEY_PASSWORD")
val releaseSigningValues = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
)
val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }
if (!hasReleaseSigning && releaseSigningValues.any { !it.isNullOrBlank() }) {
    throw GradleException("Set all RETROBIT_STORE_* and RETROBIT_KEY_* signing variables together.")
}

android {
    namespace = "com.retrobit.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.retrobit.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 33
        versionName = "1.19.0"
        resourceConfigurations += listOf("en", "tr")
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "BILLING_PUBLIC_KEY", "\"$billingPublicKey\"")
        // Local test builds may opt in with -PsupporterTestUnlock=true.
        // Play upload builds remain locked by default.
        buildConfigField(
            "boolean",
            "SUPPORTER_TEST_UNLOCK",
            "${(project.findProperty("supporterTestUnlock") as String?)?.toBoolean() ?: false}"
        )
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Store build: no all-files access, folder import goes through SAF and backups
        // are written to the same visible Download/Retrobit_Backups folder via MediaStore.
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "ALL_FILES_ACCESS", "false")
            buildConfigField("boolean", "PLAY_BILLING", "true")
            buildConfigField("boolean", "CD_KEY_ENTRY", "${cdKeyEntryOverride ?: false}")
        }
        // Direct APK build: keeps broad storage access and the in-app folder browser.
        create("sideload") {
            dimension = "distribution"
            buildConfigField("boolean", "ALL_FILES_ACCESS", "true")
            buildConfigField("boolean", "PLAY_BILLING", "false")
            buildConfigField("boolean", "CD_KEY_ENTRY", "${cdKeyEntryOverride ?: true}")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(checkNotNull(releaseStoreFile))
                storePassword = checkNotNull(releaseStorePassword)
                keyAlias = checkNotNull(releaseKeyAlias)
                keyPassword = checkNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*"
        )
        // LibretroDroid dlopen()s cores by file path, so they must be extracted to nativeLibraryDir.
        jniLibs.useLegacyPackaging = true
        jniLibs.excludes += "**/libblastem_libretro_android.so"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.Swordfish90:LibretroDroid:0.14.0")
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.9")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    "playImplementation"("com.android.billingclient:billing:9.1.0")
}
