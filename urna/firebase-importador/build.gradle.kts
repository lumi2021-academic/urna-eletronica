plugins {
    java
    application
}
group = "br.com.urnapokemon"
version = "1.0.0"
repositories { mavenCentral() }
dependencies {
    implementation("com.google.firebase:firebase-admin:9.11.0")
}
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
application { mainClass.set("br.com.urnapokemon.ImportarPokemons") }

tasks.register<JavaExec>("configurarCandidatos") {
    group = "application"
    description = "Valida o CSV de candidatos e cria apenas registros ausentes quando usado com --gravar"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("br.com.urnapokemon.ConfigurarCandidatos")
    workingDir = projectDir
}
