plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.myapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val envStoreFilePath: String? = System.getenv("MYAPP_STORE_FILE")
    val envStorePassword: String? = System.getenv("MYAPP_STORE_PASSWORD")
    val envKeyAlias: String? = System.getenv("MYAPP_KEY_ALIAS")
    val envKeyPassword: String? = System.getenv("MYAPP_KEY_PASSWORD")
    val storePath = envStoreFilePath?.trim()
    val storeOk = storePath != null && storePath.isNotBlank() &&
            (storePath.endsWith(".jks") || storePath.endsWith(".keystore")) &&
            file(storePath).exists()
    val haveSigning = storeOk && listOf(envStorePassword, envKeyAlias, envKeyPassword).all { it != null && it.isNotBlank() }

    if (haveSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(envStoreFilePath!!)
                storePassword = envStorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (haveSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Usa firma de debug por defecto
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

// El plugin se declara arriba vía alias; no es necesario aplicarlo condicionalmente.

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.text)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.constraintlayout)
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.androidx.cardview)
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
