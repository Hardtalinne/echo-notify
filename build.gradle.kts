plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.kover)
}

allprojects {
    group = "com.echonotify"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(11)
    }

    dependencies {
        add("testImplementation", "org.junit.jupiter:junit-jupiter:5.10.3")
        add("testImplementation", "io.mockk:mockk:1.13.12")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
