pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("com.jsonapi.openapi") version "0.1.0-SNAPSHOT"
    }
}

rootProject.name = "jsonapi-openapi-spring"

include("jsonapi-openapi-gradle-plugin")
include("jsonapi-openapi-spring-boot-starter")
