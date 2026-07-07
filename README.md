# OpsonAPI Spring

Gradle plugin and Spring Boot starter for JSON:API APIs driven by **OpenAPI 3.1** specs and entity YAML schemas.

## Modules

| Module | Purpose |
|--------|---------|
| [`opsonapi-gradle-plugin`](opsonapi-gradle-plugin/README.md) | Wire schema generation, OpenAPI validation, codegen |
| [`opsonapi-spring-boot-starter`](opsonapi-spring-boot-starter/README.md) | Runtime dispatcher, mapping, validation, atomic ops |

## Reference application

**[Yaatraa](../yaatraa-app)** — standalone travel monolith that consumes this starter from `mavenLocal`. See its [docs](../yaatraa-app/docs/README.md) for local and production setup.

## Consumer quickstart

Minimal Gradle project using published artifacts from `mavenLocal`:

```bash
# From this monorepo (first time only)
./gradlew -PpublishOnly=true publishLibrariesToMavenLocal
```

**settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
    plugins {
        id("com.opsonapi") version "0.1.0-SNAPSHOT"
    }
}
rootProject.name = "my-opsonapi-app"
```

**build.gradle.kts**

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.opsonapi")
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.opsonapi:opsonapi-spring-boot-starter:0.1.0-SNAPSHOT")
}

opsonapi {
    generatedPackage.set("com.example.generated")
}
```

**src/main/resources/application.yml**

```yaml
opsonapi:
  openapi:
    entity-package: com.example.model
```

Add `src/main/resources/openapi/openapi.yaml`, entity files under `schemas/`, and `@Service` beans for each `x-service`. Run `./gradlew bootRun` after `./gradlew compileJava`.

Entity schema conventions: [docs/entity-schemas.md](docs/entity-schemas.md).

## Publishing

```bash
./gradlew -PpublishOnly=true publishLibrariesToMavenLocal
```

Coordinates: `com.opsonapi` / `0.1.0-SNAPSHOT`.

## Architecture

- **Context**: [`JsonApiServiceContext`](opsonapi-spring-boot-starter/src/main/java/com/opsonapi/context/JsonApiServiceContext.java) — `portal_id`, pagination, sort, filter, include, sparse fields.
- **Dispatch**: [`JsonApiDispatcherController`](opsonapi-spring-boot-starter/src/main/java/com/opsonapi/web/JsonApiDispatcherController.java) matches OpenAPI paths and invokes `x-service` beans.
- **Atomic ops**: [`JsonApiAtomicProcessor`](opsonapi-spring-boot-starter/src/main/java/com/opsonapi/atomic/JsonApiAtomicProcessor.java) uses `x-atomic-allowed-operations` and `x-atomic-operation-services`.


## Verification

```bash
./gradlew -PpublishOnly=true publishLibrariesToMavenLocal
./gradlew checkAll
```
