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

// The generated start script runs whatever JAVA_HOME says, which may be older than Java 25. Record
// the toolchain JDK in the install folder; cg.cmd sets JAVA_HOME from it before calling the script.
val toolchainJavaHome =
    javaToolchains
        .launcherFor(java.toolchain)
        .map { it.metadata.installationPath.asFile.absolutePath }

// A separate task with the path as an input, so it re-runs whenever the toolchain changes (a doLast
// on installDist would be skipped while installDist is up to date and leave a stale path behind).
val writeJavaHome by tasks.registering {
    val home = toolchainJavaHome
    val out = layout.buildDirectory.file("install/codegen/java-home.txt")
    inputs.property("javaHome", home)
    outputs.file(out)
    doLast {
        out.get().asFile.writeText(home.get())
    }
}

// installDist syncs its folder and would delete java-home.txt, so write it afterwards.
tasks.named("installDist") {
    finalizedBy(writeJavaHome)
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
