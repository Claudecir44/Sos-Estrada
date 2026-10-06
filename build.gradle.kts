// Mesmas versões do Caronas (AGP 9 com Kotlin embutido): o Play passou a
// exigir targetSdk 36 e Play Billing 8+, que não compilam no Kotlin 1.9.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
}
