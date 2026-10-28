import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    // AlmacenSeguroAndroid: EncryptedSharedPreferences + Android Keystore
    implementation(libs.androidx.security.crypto)
    // BaseLocalAndroid: driver de SQLDelight
    implementation(libs.sqldelight.android)
    // Motor HTTP de Android
    implementation(libs.ktor.client.okhttp)
    // OkHttp 5.5.0 (transitiva de Ktor 3.6.0) exige compileSdk 37: se fija la ultima compatible.
    implementation("com.squareup.okhttp3:okhttp") {
        version { strictly(libs.versions.okhttp.get()) }
    }

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "pe.upeu.ecorecicla"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "pe.upeu.ecorecicla"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        // Direccion base centralizada del servicio. Emulador: 10.0.2.2 = localhost de la PC.
        // Celular fisico: ./gradlew installDebug -PurlBase=http://192.168.1.50:8080/api/v1/
        val urlBase = (project.findProperty("urlBase") as String?) ?: "http://10.0.2.2:8080/api/v1/"
        buildConfigField("String", "URL_BASE", "\"$urlBase\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
