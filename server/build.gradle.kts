import java.util.Properties

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
    application
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

group = "ru.nikitaluga.aichallenge"
version = "1.0.0"
application {
    mainClass.set("ru.nikitaluga.aichallenge.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf(
        "-Dio.ktor.development=$isDevelopment",
        "-Dowm.api.key=${localProperties.getProperty("owm.api.key", "")}",
        "-Drouterai.api.key=${localProperties.getProperty("routerai.api.key", "")}",
    )
}

tasks.withType<JavaExec> {
    val isDevelopment: Boolean = project.ext.has("development")
    jvmArgs(
        "-Dio.ktor.development=$isDevelopment",
        "-Dowm.api.key=${localProperties.getProperty("owm.api.key", "")}",
        "-Drouterai.api.key=${localProperties.getProperty("routerai.api.key", "")}",
    )
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.kotlinx.serialization.json)
    // PDFBox for parsing PDF documents in the RAG indexer
    implementation(libs.pdfbox)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
