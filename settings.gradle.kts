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
        // NewPipeExtractor (lecture audio YouTube) est publié sur JitPack
        maven("https://jitpack.io")
    }
}

rootProject.name = "SalatiTimeTV"
include(":app")
