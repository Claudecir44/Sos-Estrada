import java.util.Properties

// Define o package name uma vez, como constante imutável
val packageName = "com.cjstudio.sosestrada"

// Chave de release em keystore.properties (fora do git), mesmo padrão do
// Caronas. Enquanto ela não existir (o app ainda não foi publicado), o
// release é assinado com a chave de debug — dá pra instalar e testar o R8,
// mas NÃO serve pra Play Store.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val temKeystoreDeRelease = keystorePropertiesFile.exists()
if (temKeystoreDeRelease) {
    keystoreProperties.load(keystorePropertiesFile.inputStream().reader(Charsets.UTF_8))
}

// AGP 9: o Kotlin vem embutido (sem o plugin org.jetbrains.kotlin.android),
// igual ao Caronas.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)   // Usa o version catalog
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    // Relatório de falhas + envio do mapeamento do R8 (desofusca as falhas).
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = packageName
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = packageName
        minSdk = 24
        // O Play exige 36 desde 2026 (bloqueia o envio com 35).
        targetSdk = 36
        versionCode = 2
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

    signingConfigs {
        if (temKeystoreDeRelease) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // R8: encolhe, otimiza e ofusca (o Play reclama de "otimização
            // DEX abaixo do limite" sem isso). Modelos do Firestore e o que é
            // usado por reflection ficam protegidos em proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (temKeystoreDeRelease) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

configurations.all {
    resolutionStrategy {
        // Mesmo ajuste do Caronas/Match: o compilador Kotlin embutido no AGP 9
        // é fixo numa versão; algumas libs mais novas puxam um kotlin-stdlib
        // mais recente do que esse compilador entende.
        force("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
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
    implementation(libs.firebase.functions)
    implementation(libs.firebase.storage)
    // Push de mensagem/solicitação/resposta (SosFirebaseMessagingService).
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    // Google Play Billing com "User Choice" (escolha Google Play x Mercado Pago) — GooglePlayBillingManager.
    implementation(libs.billing.ktx)
    // Número no ícone do app na tela inicial (AppIconBadgeUtil) — quem
    // desenha é o launcher de cada fabricante; a biblioteca fala com cada um.
    implementation(libs.shortcutbadger)

    // Kotlin/corrotinas — usado pelo módulo de assinatura (AssinaturaActivity)
    // e pela migração do SocorroActivity pra Kotlin.
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.lifecycle.runtime.ktx)

    // Injeção de dependência (mesmo padrão do Caronas/Match)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

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