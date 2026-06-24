# JSON:API OpenAPI Spring Boot Starter

Spring Boot autoconfiguration for JSON:API APIs driven by an OpenAPI 3.1 spec and entity YAML schemas on the classpath.

## Dependency

```kotlin
repositories {
    mavenLocal() // or Maven Central when published
}

dependencies {
    implementation("com.jsonapi.openapi:jsonapi-openapi-spring-boot-starter:0.1.0-SNAPSHOT")
}
```

Publish locally from the monorepo root:

```bash
./gradlew -PpublishOnly=true publishLibrariesToMavenLocal
```

## Prerequisites

1. Apply the [Gradle plugin](../jsonapi-openapi-gradle-plugin/README.md) in your app.
2. Run `./gradlew generateJsonApiSupport` (or `compileJava`) so `openapi/openapi.json` and `schemas/*.yaml` are on the classpath.
3. Implement `@Service` beans referenced by `x-service` in entity schemas.

## Configuration

| Property | Default | Purpose |
|----------|---------|---------|
| `jsonapi.openapi.enabled` | `true` | Disable autoconfig |
| `jsonapi.openapi.location` | `classpath:openapi/openapi.json` | Resolved OpenAPI spec |
| `jsonapi.openapi.entity-package` | `com.example.model` | Domain entity package |
| `jsonapi.openapi.dispatcher-paths` | `[/api/**]` | Dispatcher URL patterns |
| `jsonapi.openapi.entity-schemas-location` | `classpath:openapi/schemas/` | Entity YAML directory |

Example `application.yml`:

```yaml
jsonapi:
  openapi:
    entity-package: com.example.model
    dispatcher-paths:
      - /api/**
```

## Service contract

Every `x-service` method must match:

```java
JsonApiResponseEntity<MyEntity> myMethod(ServiceContext context, MyEntity entity);
```

- GET / list: pass `entity` as `null`.
- GET by id: pass a probe entity or `null`; path `id` is injected when present.
- POST/PATCH: `entity` is mapped from the JSON:API body.

## Registered beans

| Bean | Role |
|------|------|
| `OpenApiSpecRegistry` | Loads spec + entity schemas; matches operations |
| `JsonApiDispatcherController` | Single entry point for configured URL patterns |
| `JsonApiEntityMapper` | Wire document → domain entity |
| `JsonApiResponseAssembler` | Domain result → JSON:API response |
| `JsonApiRequestValidator` | Content-Type, Accept, required attributes, `portal_id` |
| `ServiceInvoker` | Invokes `x-service` Spring beans |
| `JsonApiAtomicProcessor` | JSON:API atomic operations extension |
| `JsonApiExceptionHandler` | JSON:API `errors` array for failures |

## Atomic operations

Atomic POST paths declare:

- `x-atomic-allowed-operations`: list of allowed operation IDs (e.g. `items.add`)
- `x-atomic-operation-services`: map of operation ID → `beanName.methodName`

## Error format

Validation and configuration errors return a JSON:API document:

```json
{
  "errors": [
    { "status": "400", "title": "Bad Request", "detail": "portal_id query parameter is required" }
  ]
}
```
