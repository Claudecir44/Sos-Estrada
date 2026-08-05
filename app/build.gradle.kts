// Define o package name uma vez, como constante imutável
val packageName = "com.cjstudio.sosestrada"

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.google.services)   // Usa o version catalog
}

android {
    namespace = packageName
    compileSdk = 35

    defaultConfig {
        applicationId = packageName
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "app"
    productFlavors {
        create("usuarios") {
            dimension = "app"
            // App completo: motorista, prestador e admin (comportamento atual)
        }
        create("admin") {
            dimension = "app"
            // APK separado: abre direto no Painel Administrativo
            // applicationId próprio (com.cjstudio.sosestrada.admin), já cadastrado
            // no Firebase, permitindo instalar os dois APKs no mesmo aparelho.
            applicationIdSuffix = ".admin"
            versionNameSuffix = "-admin"
        }
    }

    buildFeatures {
        viewBinding = true
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

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-Xjvm-default=enable")
    }
}

dependencies {
    // AndroidX & UI
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.cardview)
    implementation(libs.core.ktx)

    // Firebase (BoM)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation("com.google.firebase:firebase-storage:20.3.0")

    // ✅ Google Play Services – Localização (FusedLocationProviderClient)
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Glide – carregamento de imagens
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Testes
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}