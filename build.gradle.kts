buildscript {
    dependencies {
        // AGP 9 provides Kotlin support directly. Keep KGP current without
        // applying the legacy org.jetbrains.kotlin.android plugin.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.3.0" apply false
}
