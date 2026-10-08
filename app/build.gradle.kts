plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val hasSigning = listOf(
    "SIGNING_STORE_FILE",
    "SIGNING_STORE_PASSWORD",
    "SIGNING_KEY_ALIAS",
    "SIGNING_KEY_PASSWORD"
).all { !System.getenv(it).isNullOrBlank() }

android {
    namespace = "com.elitedarkkaiser.redmagic"
    compileSdk = 36

    defaultConfig {
        // The id the phone installs this under. The Kotlin namespace above is deliberately left
        // alone: it is where every source file lives and what R is generated into, and it has no
        // bearing on what the package manager calls the app.
        applicationId = "com.redmagic.control"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        System.getenv("REDMAGIC_PREVIEW_KEYSTORE")?.takeIf { it.isNotBlank() }?.let { path ->
            getByName("debug").storeFile = file(path)
        }
        if (hasSigning) {
            create("release") {
                storeFile = file(System.getenv("SIGNING_STORE_FILE")!!)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Small, installable phone-test build. Keep production signing separate.
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        viewBinding = true
        compose = true
        // CrashLog stamps the build into the report it writes.
        buildConfig = true
    }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    // Spring physics for Material 3 Expressive's motion (press/selection settle).
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")

    // Compose is used only to host the NPatch-style glass bottom nav bar (ComposeView
    // embedded in the otherwise classic-Views UI) via androidx.activity.ComponentActivity.
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    // Material 3 Expressive's ContainedLoadingIndicator (Home's loading overlay), pinned rather
    // than taken from the BOM: the component is not in material3 1.4.0 stable -- it landed in the
    // 1.4.0 alphas, was pulled before 1.4.0-beta01, and returned in the 1.5.0 alphas. This alpha
    // is the version that carries it without moving the Compose runtime; 1.5.0-alpha would drag
    // ui/foundation to 1.12.0-beta01 underneath the glass bar, which is built on 1.10.3.
    implementation("androidx.compose.material3:material3:1.4.0-alpha18")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("io.github.kyant0:backdrop:1.0.6")
    // Settings' animated backgrounds (ported from MorpheApp/morphe-manager) gate their frame
    // loop on the host's lifecycle state via repeatOnLifecycle/LocalLifecycleOwner.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Ported from github.com/Gio470/FixRedMagicWindow. compileOnly: the Xposed/LSPosed framework
    // provides these classes at runtime, so they must not be packaged. Vendored stubs rather than
    // de.robv.android.xposed:api because that artifact is only published on api.xposed.info --
    // see xposed-api/README.md.
    compileOnly(project(":xposed-api"))
}




