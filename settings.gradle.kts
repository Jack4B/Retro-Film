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
    }
}
rootProject.name = "Retro-Film"
include(
    ":app",
    ":core:engine", ":core:frame", ":core:batch", ":core:data", ":core:export",
    ":feature:start", ":feature:editor", ":feature:batch", ":feature:luts", ":feature:settings"
)
