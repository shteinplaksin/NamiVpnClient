plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

apply(from = "../repositories.gradle.kts")

// AGP pulls jetifier -> javapoet 1.10.0 onto the shared buildscript classpath;
// Hilt 2.5x calls javapoet 1.13's ClassName.canonicalName(), so force 1.13.0
// or the Hilt aggregate task fails with NoSuchMethodError at runtime
configurations.all {
    resolutionStrategy.force("com.squareup:javapoet:1.13.0")
}

dependencies {
    // Gradle Plugins
    implementation("com.android.tools.build:gradle:9.4.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.4.20")
}
