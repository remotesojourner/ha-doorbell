buildscript {
    dependencies {
        classpath(platform(libs.bouncycastle.bom))
        // The Android Gradle Plugin depends on older versions of these libraries that have known vulnerabilities.
        constraints {
            listOf(libs.guava, libs.commons.lang3, libs.jose4j, libs.jdom2).forEach { add("classpath", it) }
        }
    }
}

plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
