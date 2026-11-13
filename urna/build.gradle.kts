plugins {
    java
    application
    id("com.gradleup.shadow") version "9.2.2"
}

group = "com.lumi2021"
version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
    val pi4jVersion = "3.0.4"

    implementation("com.google.firebase:firebase-admin:9.11.0")
    implementation("io.javalin:javalin:6.7.0")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("com.pi4j:pi4j-core:$pi4jVersion")
    runtimeOnly("com.pi4j:pi4j-plugin-raspberrypi:$pi4jVersion")
    runtimeOnly("com.pi4j:pi4j-plugin-gpiod:$pi4jVersion")
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

application { mainClass.set("com.lumi2021.urna.Program") }

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get()
        )
    }
}

tasks.test { useJUnitPlatform() }
