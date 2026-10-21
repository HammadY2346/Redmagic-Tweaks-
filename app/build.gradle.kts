plugins { id("com.android.application") }

android {
    namespace = "com.hammad.redmagictweaks"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.hammad.redmagictweaks"
        minSdk = 34
        targetSdk = 35
        // CI passes the run number, so every build installs over the previous one
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.0"
    }
    // One codebase, two apps. "public" is RedMagic Tweaks (github.com/HammadY2346/Redmagic-Tweaks-). "private" adds
    // src/private (only in the private repo) and installs as the original RM Tweaks next to it.
    flavorDimensions += "edition"
    productFlavors {
        create("public") {
            dimension = "edition"
            applicationId = "com.hammad.redmagictweaks"
            resValue("string", "app_name", "RedMagic Tweaks")
            resValue("string", "module_package", "com.hammad.redmagictweaks")
            buildConfigField("String", "APP_NAME", "\"RedMagic Tweaks\"")
            buildConfigField("String", "DATA_ID", "\"redmagictweaks\"")
            buildConfigField("boolean", "PRIVATE_BUILD", "false")
        }
        if (file("src/private").exists()) create("private") {
            dimension = "edition"
            applicationId = "com.hammad.rmtweaks"
            resValue("string", "app_name", "RM Tweaks")
            resValue("string", "module_package", "com.hammad.rmtweaks")
            buildConfigField("String", "APP_NAME", "\"RM Tweaks\"")
            buildConfigField("String", "DATA_ID", "\"rmtweaks\"")
            buildConfigField("boolean", "PRIVATE_BUILD", "true")
        }
    }
    // Release key: RMTWEAKS_KEYSTORE (CI decodes it from a repo secret) or app/rmtweaks.jks (private repo only).
    // Without either (a fork), release builds use the debug key.
    val keystore = System.getenv("RMTWEAKS_KEYSTORE")?.let { file(it) } ?: file("rmtweaks.jks")
    signingConfigs {
        if (keystore.exists()) create("shared") {
            storeFile = keystore
            storePassword = System.getenv("RMTWEAKS_STORE_PASSWORD")?.takeIf { it.isNotEmpty() } ?: "rmtweaks"
            keyAlias = System.getenv("RMTWEAKS_KEY_ALIAS")?.takeIf { it.isNotEmpty() } ?: "rmtweaks"
            keyPassword = System.getenv("RMTWEAKS_KEY_PASSWORD")?.takeIf { it.isNotEmpty() } ?: "rmtweaks"
        }
    }
    val signing = if (keystore.exists()) signingConfigs.getByName("shared") else signingConfigs.getByName("debug")
    buildTypes {
        debug { signingConfig = signing }
        release { isMinifyEnabled = false; signingConfig = signing }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    androidResources { noCompress += listOf("webp", "ttf") }
    lint { abortOnError = false }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    compileOnly("de.robv.android.xposed:api:82")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("de.robv.android.xposed:api:82")
}
