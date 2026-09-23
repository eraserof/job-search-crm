plugins {
    // Auto-provisions JDKs from Adoptium/Temurin when the required
    // toolchain isn't available locally. See build.gradle.kts.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "job-search-crm"
