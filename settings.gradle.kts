pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("com.opsonapi") version "0.1.0-SNAPSHOT"
    }
}

rootProject.name = "opsonapi-spring"

include("opsonapi-gradle-plugin")
include("opsonapi-spring-boot-starter")
