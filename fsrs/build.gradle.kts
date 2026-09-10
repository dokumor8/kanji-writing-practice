// A pure-Kotlin, dependency-free port of the FSRS v6 scheduler.
//
// The plan suggested 'com.github.open-spaced-repetition:fsrs-kotlin' from JitPack,
// but no such artifact is published (those coordinates 404 on both JitPack and
// Maven Central). FSRS is a couple of hundred lines of arithmetic, so it is
// vendored here as a plain JVM module instead: the build stays reproducible and
// offline, and the maths can be unit-tested against the published reference vectors.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
    }
}
