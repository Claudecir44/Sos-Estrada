pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ShortcutBadger (número no ícone do app) só é publicado no JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "SOSEstrada"
include(":app")