import com.github.gradle.node.npm.task.NpmTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    // Builds the Angular frontend as part of the Gradle build. The plugin downloads
    // its own Node, so this also works in the JDK-only Docker image and in CI.
    id("com.github.node-gradle.node") version "7.1.0"
}

group = "de.yscord"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val exposedVersion = "1.0.0-beta-4"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Exposed — Kotlin SQL framework (query builder + schema in code, Knex-style).
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

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

tasks.withType<Test> {
    useJUnitPlatform()
}

// Local `./gradlew bootRun` uses the dev profile (local DB credentials in
// application-dev.yml); prod/Docker get their datasource from the environment.
tasks.named<BootRun>("bootRun") {
    systemProperty("spring.profiles.active", "dev")
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
