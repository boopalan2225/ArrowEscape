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
        // mavenCentral() resolves to repo.maven.apache.org, which this sandbox's
        // DNS cannot reach (confirmed NXDOMAIN). This CDN mirror serves the same
        // artifacts and is reachable - swap back to mavenCentral() outside this
        // sandbox if preferred, it is not a real dependency change.
        maven { url = uri("https://repo1.maven.org/maven2/") }
    }
}

rootProject.name = "ArrowEscape"
include(":app")
