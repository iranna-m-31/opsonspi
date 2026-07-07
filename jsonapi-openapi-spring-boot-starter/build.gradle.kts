plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.dependency.management)
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}

dependencies {
    api(libs.spring.boot.starter.webmvc)
    api(libs.spring.boot.autoconfigure)
    api(libs.spring.tx)
    api(libs.swagger.parser)
    api(libs.json.schema.validator)
    api(libs.jackson.databind)
    api(libs.jackson.yaml)
    compileOnly("com.fasterxml.jackson.core:jackson-annotations")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("OpsonAPI Spring Boot Starter")
                description.set("Spring Boot starter for JSON:API APIs driven by OpenAPI 3.1 specs")
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
