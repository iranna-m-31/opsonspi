subprojects {
    group = "com.opsonapi"
    version = property("projectVersion").toString()

    repositories {
        mavenCentral()
    }

    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(project.property("javaVersion").toString().toInt()))
            }
        }
    }
    plugins.withId("java-library") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(project.property("javaVersion").toString().toInt()))
            }
        }
    }
}

tasks.register("publishLibrariesToMavenLocal") {
    group = "publishing"
    description = "Publish plugin and starter to mavenLocal() (use -PpublishOnly=true on first run)"
    dependsOn(
        ":opsonapi-gradle-plugin:publishToMavenLocal",
        ":opsonapi-spring-boot-starter:publishToMavenLocal"
    )
}

tasks.register("checkAll") {
    group = "verification"
    description = "Run check on plugin and starter"
    dependsOn(
        ":opsonapi-gradle-plugin:check",
        ":opsonapi-spring-boot-starter:check"
    )
}
