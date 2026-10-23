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
rootProject.name = "NexGuard"

include(":app")
include(":core:common")
include(":core:database")
include(":core:eventbus")
include(":core:security")
include(":feature:onboarding")
include(":feature:home")
include(":feature:scan")
include(":feature:monitor")
include(":feature:quarantine")
include(":feature:alerts")
include(":feature:settings")
include(":engine:signatures")