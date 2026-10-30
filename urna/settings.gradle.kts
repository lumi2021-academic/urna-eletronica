plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "urna"

include("firebase-backend", "firebase-importador")
