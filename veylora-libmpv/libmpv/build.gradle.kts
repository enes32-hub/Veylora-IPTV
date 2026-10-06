plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

// Date-based: YYYY.MM.N (see UPDATING.md). The publish workflow passes it from the tag.
version = providers.gradleProperty("libVersion").getOrElse("0.0.0-local")

android {
    // The Java package stays `dev.jdtech.mpv`, so OwnTV's player code is untouched by the swap.
    namespace = "dev.jdtech.mpv"
    compileSdk = 36
    buildToolsVersion = "37.0.0"
    ndkVersion = "30.0.16248370"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("proguard-rules.pro")
        // Must match buildscripts/build.sh `archs`: the JNI glue links against the prebuilt libmpv.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                )
                cFlags += "-Werror"
                cppFlags += "-std=c++11"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release")
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "tv.own.owntv"
            artifactId = "libmpv"
            version = project.version as String
            afterEvaluate { from(components["release"]) }
            pom {
                name = "OwnTV libmpv"
                description = "libmpv for Android as used by OwnTV: pinned mpv master, FFmpeg release, filter allowlist."
                url = "https://github.com/ahXN00/OwnTV_libmpv"
                licenses {
                    // The Kotlin/JNI wrapper is MIT; the bundled FFmpeg (--enable-gpl --enable-version3)
                    // and mpv make the binaries GPLv3 as a whole.
                    license { name = "GPL-3.0-or-later (binaries); MIT (wrapper)" }
                }
            }
        }
    }
    repositories {
        // Local-only staging repository. This fork does not publish to upstream Maven.
        maven {
            name = "Staging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-maven"))
        }
    }
}
