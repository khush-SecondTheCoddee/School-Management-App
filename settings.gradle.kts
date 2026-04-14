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

rootProject.name = "SchoolManagementApp"
include(
    ":app",
    ":core",
    ":data",
    ":feature-auth",
    ":feature-dashboard",
    ":feature-attendance",
    ":feature-homework",
    ":feature-results",
    ":feature-notifications"
)
