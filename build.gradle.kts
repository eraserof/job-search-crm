plugins {
    java
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.6"
    id("com.diffplug.spotless") version "6.25.0"
}

group = "com.jobcrm"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot backbone (no web — this is a CLI app)
    implementation("org.springframework.boot:spring-boot-starter")

    // CLI
    implementation("info.picocli:picocli-spring-boot-starter:4.7.6")

    // Persistence — used from Phase 3 onward.
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("org.flywaydb:flyway-core:10.20.1")
    implementation("com.zaxxer:HikariCP")

    // TODO(Phase 8): JSON schema validation for agent tool inputs.
    //   implementation("com.networknt:json-schema-validator:1.5.2")
    //
    // TODO(Phase 12): Gmail API (google-api-services-gmail + oauth client).
    // TODO(Phase 13): Google Calendar API.
    //   These are pinned to date-stamped revisions; version selection deferred
    //   until we're ready to authenticate against the real APIs.

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("net.jqwik:jqwik:1.9.0")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

tasks.withType<Test> {
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik")
    }
    testLogging {
        events("failed", "skipped")
        showExceptions = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

spotless {
    java {
        googleJavaFormat("1.24.0")
        importOrder()
        removeUnusedImports()
        endWithNewline()
        // Never touch generated code
        targetExclude("build/**")
    }
    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        ktlint("1.3.1")
    }
}

// Fail the build on Spotless violations rather than silently fixing.
tasks.named("check") {
    dependsOn("spotlessCheck")
}
