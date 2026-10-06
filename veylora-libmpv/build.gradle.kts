plugins {
    alias(libs.plugins.android.library) apply false
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}
