plugins {
    // Auto-provisions JDKs from Adoptium/Temurin when the required
    // toolchain isn't available locally. See build.gradle.kts.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "job-search-crm"

// Developer tooling: the local plan/code pipeline. Separate project so its dependencies and the
// app's architecture rules never mix. Not part of the app's runtime.
include("codegen")
