plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM module — no Android dependency of any kind. Depended on by :app (Android) and,
// once it exists, the Camera Edge Device app module, so both share exactly one copy of these
// contracts rather than duplicating them.
kotlin {
    jvmToolchain(11)
}

dependencies {
    testImplementation(libs.junit)
}
