import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

// Release signing is configured from release-signing.properties (gitignored,
// lives only on the build host). Keystore is archived under
// ~/.local/share/onex-sugar-backups/blackpearl-release/.
// NB: the import is required — without it `java` resolves to the Project's
// java extension and `java.util.Properties` fails to compile.
val releaseProps = Properties().apply {
    val f = rootProject.file("release-signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.visorcraft.ghostgalleon"
    compileSdk = 34

    defaultConfig {
        // One-shot bridge: ./gradlew :app:assembleRelease -PbridgeBlackPearl=true
        // installs as an update over com.visorcraft.blackpearl so private data
        // can be exported before the Ghost Galleon package takes over.
        val bridge = project.hasProperty("bridgeBlackPearl")
        applicationId = if (bridge) "com.visorcraft.blackpearl" else "com.visorcraft.ghostgalleon"
        minSdk = 26
        targetSdk = 34
        versionCode = if (bridge) 12 else 25
        versionName = if (bridge) "0.3.0-migrate" else "0.9.0"
        buildConfigField("boolean", "EXPORT_MIGRATE_ON_BOOT", if (bridge) "true" else "false")

        // Short git SHA shown on the About page (Grexa-style build chip).
        val gitSha = runCatching {
            ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(rootProject.projectDir)
                .start().inputStream.bufferedReader().readText().trim()
        }.getOrDefault("").ifEmpty { "unknown" }
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        // Generates LocaleConfig from values-<locale> directories as translations grow.
        generateLocaleConfig = true
    }

    signingConfigs {
        create("release") {
            keyAlias = releaseProps.getProperty("keyAlias", "blackpearl")
            keyPassword = releaseProps.getProperty("keyPassword", "")
            storeFile = releaseProps.getProperty("storeFile")?.let { file(it) }
            storePassword = releaseProps.getProperty("storePassword", "")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // R8 + resource shrink: smaller APK, fewer methods to verify at
            // install, slightly faster cold start. Keep rules in
            // proguard-rules.pro (JSON/enums/views are non-reflective).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
