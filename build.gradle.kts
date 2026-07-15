import com.github.gradle.node.npm.task.NpmTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    kotlin("plugin.jpa") version "2.4.10"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    // Builds the Angular frontend as part of the Gradle build. The plugin downloads
    // its own Node, so this also works in the JDK-only Docker image and in CI.
    id("com.github.node-gradle.node") version "7.1.0"
}

group = "de.senya"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget = JvmTarget.JVM_21
    }
}

// The kotlin-jpa plugin makes @Entity classes open and gives them a no-arg constructor.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ---- Angular frontend build wired into the Gradle lifecycle ----
node {
    version.set("22.12.0")
    download.set(true)
    nodeProjectDir.set(file("frontend"))
}

// `ng build` → src/main/resources/static, so processResources bundles the SPA into
// the JAR. Inputs/outputs are declared so Gradle skips the rebuild when unchanged.
val buildFrontend by tasks.registering(NpmTask::class) {
    dependsOn("npmInstall")
    args.set(listOf("run", "build"))
    inputs.dir("frontend/src")
    inputs.files("frontend/package.json", "frontend/package-lock.json", "frontend/angular.json")
    outputs.dir("src/main/resources/static")
}

tasks.named("processResources") {
    dependsOn(buildFrontend)
}

// Keep the generated SPA out of the way on a clean build.
tasks.named<Delete>("clean") {
    delete("src/main/resources/static")
}
