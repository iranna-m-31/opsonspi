plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.opsonapi"
version = property("projectVersion").toString()

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(property("javaVersion").toString().toInt()))
    }
    withSourcesJar()
    withJavadocJar()
}

gradlePlugin {
    plugins {
        create("opsonapi") {
            id = "com.opsonapi"
            implementationClass = "com.opsonapi.gradle.OpsonApiPlugin"
            displayName = "OpsonAPI Gradle Plugin"
            description = "Generates JSON:API wire schemas and support code from OpenAPI 3.1 specs"
        }
    }
}

dependencies {
    implementation("io.swagger.parser.v3:swagger-parser-v3:${property("swaggerParserVersion")}")
    implementation("com.squareup:javapoet:${property("javapoetVersion")}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${property("jackson2Version")}")
    implementation("com.fasterxml.jackson.core:jackson-databind:${property("jackson2Version")}")

    testImplementation("org.junit.jupiter:junit-jupiter:${property("junitJupiterVersion")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(gradleTestKit())
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("OpsonAPI Gradle Plugin")
            description.set("Gradle plugin for JSON:API wire schema generation from OpenAPI specs")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
