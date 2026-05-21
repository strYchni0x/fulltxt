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
        maven { url = uri("https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1") }
    }
}

rootProject.name = "FULLTXT"
include(":app")

// Redirect build output to D: (same drive as source) to prevent OneDrive sync locks
val buildBase = File("D:/build/fulltxt")
gradle.allprojects {
    layout.buildDirectory.set(buildBase.resolve(project.name))
}
