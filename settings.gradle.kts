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
        maven { url = uri("https://maven.google.com") }
        // Vosk's own Maven repo — needed for com.alphacephei:vosk-android
        maven { url = uri("https://alphacephei.com/maven") }
    }
}

rootProject.name = "VoiceAssistant"
include(":app")
