// codegen: developer tooling for the local plan/code pipeline (see codegen/README.md).
// Not part of the app. Build the launcher with:  gradlew :codegen:installDist
// then run:  codegen\build\install\codegen\bin\codegen.bat <command>
plugins {
    java
    application
    id("com.diffplug.spotless")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.github.javaparser:javaparser-core:3.26.3")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.jobcrm.codegen.Main")
    applicationName = "codegen"
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        showExceptions = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Same formatter as the app. Google Java Format always removes unused imports, and the stubs
// import types their future bodies need, so files that still contain a TODO(codegen) stub are
// skipped until the pipeline has filled them.
spotless {
    java {
        target(
            fileTree("src") {
                include("**/*.java")
            }.filter { !it.readText().contains("TODO(codegen)") },
        )
        googleJavaFormat("1.28.0")
        importOrder()
        removeUnusedImports()
        endWithNewline()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}
